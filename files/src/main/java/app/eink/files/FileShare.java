package app.eink.files;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Lets this app give one file to another app (open with, share). Read only, and only for URIs this app
 * grants with FLAG_GRANT_READ_URI_PERMISSION: the provider is not exported.
 */
public class FileShare extends ContentProvider {
    static final String AUTHORITY = "app.eink.files.share";

    static Uri uriFor(File f) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath("f").appendEncodedPath(Uri.encode(f.getAbsolutePath(), "/").substring(1)).build();
    }

    private File fileFor(Uri uri) throws FileNotFoundException {
        String p = uri.getPath();
        if (p == null || !p.startsWith("/f/")) throw new FileNotFoundException();
        try {
            File f = new File(p.substring(2)).getCanonicalFile();
            String path = f.getPath(), ext = Environment.getExternalStorageDirectory().getCanonicalPath();
            if (!(path.startsWith(ext + "/") || path.startsWith("/storage/"))) throw new FileNotFoundException();
            return f;
        } catch (IOException e) {
            throw new FileNotFoundException();
        }
    }

    @Override public boolean onCreate() { return true; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new SecurityException("read only");
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] projection, String sel, String[] args, String order) {
        try {
            File f = fileFor(uri);
            String[] cols = projection != null ? projection : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
            MatrixCursor c = new MatrixCursor(cols);
            Object[] row = new Object[cols.length];
            for (int i = 0; i < cols.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
                else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
            }
            c.addRow(row);
            return c;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override public String getType(Uri uri) {
        try { return FileOps.mime(fileFor(uri)); } catch (FileNotFoundException e) { return null; }
    }

    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
