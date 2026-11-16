package app.eink.notes;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import app.eink.notes.format.Doc;
import app.eink.notes.format.TxtFormat;

/**
 * One file per document in the app's private folder. Documents are saved as .ink (JSON with formatting).
 * Plain .txt files from the first version of Notes are still read, and turn into .ink on the next save.
 */
final class NoteStore {
    static final class Note {
        final String id;
        final long modified;
        final String text;   // plain text, for the list and for search

        Note(String id, long modified, String text) {
            this.id = id;
            this.modified = modified;
            this.text = text;
        }

        String title() {
            for (String line : text.split("\n")) if (!line.trim().isEmpty()) return line.trim();
            return "Empty note";
        }

        /** The text after the title line, flattened to one line for the list. */
        String preview() {
            String t = text.trim();
            int nl = t.indexOf('\n');
            return nl < 0 ? "" : t.substring(nl + 1).trim().replaceAll("\\s+", " ");
        }
    }

    private static final Map<String, Note> cache = new HashMap<>(); // id + modified -> note, so the list does not reparse everything

    private NoteStore() {}

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "notes");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static String newId() { return Long.toString(System.currentTimeMillis(), 36) + Integer.toString((int) (Math.random() * 1296), 36); }

    private static String clean(String id) { return id.replaceAll("[^A-Za-z0-9]", ""); }

    private static File ink(Context c, String id) { return new File(dir(c), clean(id) + ".ink"); }

    private static File txt(Context c, String id) { return new File(dir(c), clean(id) + ".txt"); }

    private static String readFile(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int n = 0;
            while (n < b.length) { int r = in.read(b, n, b.length - n); if (r < 0) break; n += r; }
            return new String(b, 0, n, StandardCharsets.UTF_8);
        }
    }

    static Doc load(Context c, String id) {
        try {
            File f = ink(c, id);
            if (f.exists()) return DocJson.read(readFile(f));
            File t = txt(c, id);
            if (t.exists()) return TxtFormat.read(readFile(t));
        } catch (Exception e) {
            // fall through to an empty document
        }
        Doc d = new Doc();
        d.newPara();
        return d;
    }

    /** Writes to a temp file first so a crash mid-save cannot leave a half-written note. */
    static void save(Context c, String id, Doc doc) {
        File f = ink(c, id), tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(DocJson.write(doc).getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (IOException e) {
            return;
        }
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f); }
        txt(c, id).delete(); // an old plain-text copy is now replaced
    }

    static void delete(Context c, String id) {
        ink(c, id).delete();
        txt(c, id).delete();
    }

    /** Newest first, optionally only notes containing the query. */
    static List<Note> all(Context c, String query) {
        File[] fs = dir(c).listFiles((d, n) -> n.endsWith(".ink") || n.endsWith(".txt"));
        List<Note> out = new ArrayList<>();
        if (fs == null) return out;
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        for (File f : fs) {
            String id = f.getName().substring(0, f.getName().length() - 4);
            String key = f.getName() + f.lastModified();
            Note n = cache.get(key);
            if (n == null) {
                n = new Note(id, f.lastModified(), load(c, id).plainText());
                cache.put(key, n);
            }
            if (q.isEmpty() || n.text.toLowerCase(Locale.ROOT).contains(q)) out.add(n);
        }
        Collections.sort(out, (a, b) -> Long.compare(b.modified, a.modified));
        return out;
    }
}
