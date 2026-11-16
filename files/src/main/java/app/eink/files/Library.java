package app.eink.files;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Reads Android's own index of the files on the device: much faster than walking every folder. */
final class Library {
    static final class Item {
        final File file;
        final long size, modified;

        Item(File file, long size, long modified) { this.file = file; this.size = size; this.modified = modified; }
    }

    static final class Totals {
        long images, videos, audio, docs, archives, others;

        long total() { return images + videos + audio + docs + archives + others; }
    }

    private Library() {}

    /** The column holds 12289 for folders. It is not in the public constants, but it has been in the files table since Android 1. */
    private static final String FORMAT = "format";

    private static Uri files() { return MediaStore.Files.getContentUri("external"); }

    @SuppressWarnings("deprecation")
    private static final String DATA = MediaStore.Files.FileColumns.DATA;

    private static final String NOT_FOLDER_NOT_APP_DATA = MediaStore.Files.FileColumns.SIZE + " > 0 AND " + FORMAT
            + " != 12289 AND " + DATA + " NOT LIKE '%/Android/data/%' AND " + DATA + " NOT LIKE '%/Android/obb/%' AND " + DATA + " NOT LIKE '%/.%'";

    private static List<Item> query(Context c, String order, int limit) {
        List<Item> out = new ArrayList<>();
        Bundle args = new Bundle();
        args.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, NOT_FOLDER_NOT_APP_DATA);
        args.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, order);
        args.putInt(ContentResolver.QUERY_ARG_LIMIT, limit * 2);     // some rows are files that no longer exist
        String[] cols = {DATA, MediaStore.Files.FileColumns.SIZE, MediaStore.Files.FileColumns.DATE_MODIFIED};
        try (Cursor q = c.getContentResolver().query(files(), cols, args, null)) {
            if (q == null) return out;
            while (q.moveToNext() && out.size() < limit) {
                String path = q.getString(0);
                if (path == null) continue;
                File f = new File(path);
                if (f.isFile()) out.add(new Item(f, q.getLong(1), q.getLong(2) * 1000L));
            }
        } catch (RuntimeException e) {
            return out;
        }
        return out;
    }

    /** Recently changed files, newest first. Falls back to looking through the usual folders if the index gives nothing. */
    static List<Item> recents(Context c, int limit) {
        List<Item> out = query(c, MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC", limit);
        if (out.size() >= 3) return out;
        List<Item> walk = new ArrayList<>();
        walk(android.os.Environment.getExternalStorageDirectory(), 0, walk);
        Collections.sort(walk, (a, b) -> Long.compare(b.modified, a.modified));
        return walk.size() > limit ? new ArrayList<>(walk.subList(0, limit)) : walk;
    }

    private static void walk(File dir, int depth, List<Item> out) {
        if (depth > 5 || out.size() > 6000) return;
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            String n = k.getName();
            if (n.startsWith(".") || n.equals("Android")) continue;
            if (k.isDirectory()) { if (!FileOps.isLink(k)) walk(k, depth + 1, out); }
            else if (k.length() > 0) out.add(new Item(k, k.length(), k.lastModified()));
        }
    }

    static List<Item> largest(Context c, int limit) {
        return query(c, MediaStore.Files.FileColumns.SIZE + " DESC", limit);
    }

    private static boolean ext(String path, String... exts) {
        String p = path.toLowerCase(Locale.ROOT);
        for (String e : exts) if (p.endsWith("." + e)) return true;
        return false;
    }

    /** Bytes in each kind of file, counted from the index. */
    static Totals totals(Context c) {
        Totals t = new Totals();
        String[] cols = {DATA, MediaStore.Files.FileColumns.SIZE, MediaStore.Files.FileColumns.MEDIA_TYPE};
        Bundle args = new Bundle();
        args.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, MediaStore.Files.FileColumns.SIZE + " > 0 AND " + FORMAT + " != 12289");
        try (Cursor q = c.getContentResolver().query(files(), cols, args, null)) {
            if (q == null) return t;
            while (q.moveToNext()) {
                String path = q.getString(0);
                long size = q.getLong(1);
                int type = q.getInt(2);
                if (path == null) continue;
                if (type == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) t.images += size;
                else if (type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) t.videos += size;
                else if (type == MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO) t.audio += size;
                else if (ext(path, "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "txt", "rtf", "csv", "epub", "md", "mobi", "azw3", "html", "htm")) t.docs += size;
                else if (ext(path, "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz", "iso")) t.archives += size;
                else t.others += size;
            }
        } catch (RuntimeException ignored) {}
        return t;
    }
}
