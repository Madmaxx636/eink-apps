package app.eink.files;

import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** The file work itself. Everything here can block, so callers run it off the main thread. */
final class FileOps {
    private FileOps() {}

    static final String SORT_NAME = "name", SORT_DATE = "date", SORT_SIZE = "size", SORT_EXT = "ext";

    static List<File> list(File dir, String key, boolean asc, boolean hidden) {
        File[] fs = dir.listFiles();
        List<File> out = new ArrayList<>();
        if (fs == null) return out;
        for (File f : fs) if (hidden || !f.getName().startsWith(".")) out.add(f);
        sortList(out, key, asc);
        return out;
    }

    private static String extension(File f) {
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        return dot <= 0 ? "" : n.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Folders first, then by name, size, date or extension, either way round. Ties go by name. */
    static void sortList(List<File> files, final String key, final boolean asc) {
        Collections.sort(files, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                int r = 0;
                if (SORT_DATE.equals(key)) r = Long.compare(a.lastModified(), b.lastModified());
                else if (SORT_SIZE.equals(key) && !a.isDirectory()) r = Long.compare(a.length(), b.length());
                else if (SORT_EXT.equals(key) && !a.isDirectory()) r = extension(a).compareTo(extension(b));
                if (r == 0) { r = a.getName().compareToIgnoreCase(b.getName()); return asc ? r : (SORT_NAME.equals(key) ? -r : r); }
                return asc ? r : -r;
            }
        });
    }

    static String humanSize(long b) {
        if (b < 1024) return b + " B";
        String[] u = {"KB", "MB", "GB", "TB"};
        double v = b;
        int i = -1;
        do { v /= 1024; i++; } while (v >= 1024 && i < u.length - 1);
        return String.format(Locale.getDefault(), v >= 100 ? "%.0f %s" : "%.1f %s", v, u[i]);
    }

    static String mime(File f) {
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        if (dot < 0) return "application/octet-stream";
        String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(n.substring(dot + 1).toLowerCase(Locale.ROOT));
        return m == null ? "application/octet-stream" : m;
    }

    /** Total size and item count under a file or folder. */
    static long[] measure(File f) {
        if (!f.isDirectory()) return new long[]{f.length(), 1};
        long size = 0, count = 0;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) {
            if (isLink(k)) continue; // never follow links out of the tree
            long[] m = measure(k);
            size += m[0];
            count += m[1];
        }
        return new long[]{size, count + 1};
    }

    static boolean isLink(File f) {
        try { return java.nio.file.Files.isSymbolicLink(f.toPath()); } catch (Exception e) { return false; }
    }

    /** "name (1).txt" if "name.txt" is taken. */
    static File freeName(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; ; i++) {
            f = new File(dir, base + " (" + i + ")" + ext);
            if (!f.exists()) return f;
        }
    }

    static void copy(File src, File dstDir) throws IOException {
        File canonSrc = src.getCanonicalFile(), canonDst = dstDir.getCanonicalFile();
        if (src.isDirectory() && (canonDst.equals(canonSrc) || canonDst.getPath().startsWith(canonSrc.getPath() + File.separator)))
            throw new IOException("Cannot copy a folder into itself");
        File dst = freeName(dstDir, src.getName());
        if (src.isDirectory()) {
            if (!dst.mkdirs()) throw new IOException("Could not create " + dst.getName());
            File[] kids = src.listFiles();
            if (kids != null) for (File k : kids) if (!isLink(k)) copy(k, dst);
        } else {
            try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            dst.setLastModified(src.lastModified());
        }
    }

    static void move(File src, File dstDir) throws IOException {
        if (src.getParentFile() != null && src.getParentFile().getCanonicalFile().equals(dstDir.getCanonicalFile())) return;
        File dst = freeName(dstDir, src.getName());
        if (src.renameTo(dst)) return;
        copy(src, dstDir); // different volume: copy, then remove the original only if the copy worked
        delete(src);
    }

    static void delete(File f) throws IOException {
        if (f.isDirectory() && !isLink(f)) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) delete(k);
        }
        if (!f.delete() && f.exists()) throw new IOException("Could not delete " + f.getName());
    }

    static void zip(List<File> files, File out) throws IOException {
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(out))) {
            for (File f : files) addZip(z, f, f.getName());
        }
    }

    private static void addZip(ZipOutputStream z, File f, String name) throws IOException {
        if (f.isDirectory()) {
            z.putNextEntry(new ZipEntry(name + "/"));
            z.closeEntry();
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) if (!isLink(k)) addZip(z, k, name + "/" + k.getName());
        } else {
            ZipEntry e = new ZipEntry(name);
            e.setTime(f.lastModified());
            z.putNextEntry(e);
            try (InputStream in = new FileInputStream(f)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) z.write(buf, 0, n);
            }
            z.closeEntry();
        }
    }

    /** Extracts into a new folder next to the archive. Entries that try to escape the folder are refused. */
    static File unzip(File zip) throws IOException {
        String n = zip.getName();
        File dest = freeName(zip.getParentFile(), n.toLowerCase(Locale.ROOT).endsWith(".zip") ? n.substring(0, n.length() - 4) : n + " extracted");
        if (!dest.mkdirs()) throw new IOException("Could not create " + dest.getName());
        String root = dest.getCanonicalPath() + File.separator;
        try (ZipInputStream z = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                File f = new File(dest, e.getName());
                if (!f.getCanonicalPath().startsWith(root)) throw new IOException("Archive contains an unsafe path");
                if (e.isDirectory()) { f.mkdirs(); continue; }
                f.getParentFile().mkdirs();
                try (OutputStream out = new FileOutputStream(f)) {
                    byte[] buf = new byte[64 * 1024];
                    int c;
                    while ((c = z.read(buf)) > 0) out.write(buf, 0, c);
                }
            }
        }
        return dest;
    }

    /** Name matches anywhere below dir, capped so a huge tree cannot flood the list. */
    static List<File> search(File dir, String query, boolean hidden, int cap) {
        List<File> out = new ArrayList<>();
        walk(dir, query.toLowerCase(Locale.ROOT), hidden, cap, out);
        return out;
    }

    private static void walk(File dir, String q, boolean hidden, int cap, List<File> out) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        Arrays.sort(kids, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File k : kids) {
            if (out.size() >= cap) return;
            if (!hidden && k.getName().startsWith(".")) continue;
            if (k.getName().toLowerCase(Locale.ROOT).contains(q)) out.add(k);
            if (k.isDirectory() && !isLink(k)) walk(k, q, hidden, cap, out);
        }
    }
}
