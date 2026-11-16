package app.eink.notes;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import app.eink.core.InkActivity;
import app.eink.core.PagedScroll;
import app.eink.core.Ui;
import app.eink.notes.format.Doc;

public class NotesActivity extends InkActivity {
    private static final int REQ_IMPORT = 41;

    private PagedScroll list;
    private String query = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        list = new PagedScroll(this);
        setScreen("Ink Pad", list);
        addBarButton("New", () -> open(null));
        addBarButton("More", this::more);
        // A document opened from a file manager or another app is imported as a new note.
        Intent in = getIntent();
        if (b == null && Intent.ACTION_VIEW.equals(in.getAction()) && in.getData() != null) importUri(in.getData(), in.getType());
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override protected boolean onPage(int dir) { return list.turn(dir); }

    private void more() {
        final String[] items = {"Find in notes", "Open a document (.docx, .odt, .html, .txt)", "Settings"};
        Ui.choose(this, "Ink Pad", items, -1, i -> {
            if (i == 0) Ui.prompt(this, "Find in notes", query, "Find", q -> { query = q; refresh(); });
            else if (i == 1) {
                Intent c = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                        .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/vnd.oasis.opendocument.text", "text/plain", "text/html", "text/markdown"});
                startActivityForResult(c, REQ_IMPORT);
            } else startActivity(new Intent(this, NotesSettings.class));
        });
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_IMPORT && res == Activity.RESULT_OK && data != null && data.getData() != null) importUri(data.getData(), data.getType());
    }

    private void importUri(final Uri uri, final String mime) {
        Toast.makeText(this, "Opening…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String err = null, newId = null;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new java.io.IOException("Could not open the file");
                Doc d = Importer.read(displayName(uri), mime != null ? mime : getContentResolver().getType(uri), in);
                newId = NoteStore.newId();
                NoteStore.save(this, newId, d);
            } catch (Exception e) {
                err = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final String er = err, id = newId;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                if (er != null) Toast.makeText(this, "Could not open it: " + er, Toast.LENGTH_LONG).show();
                else { refresh(); open(id); }
            });
        }).start();
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (RuntimeException ignored) {}
        return uri.getLastPathSegment();
    }

    private void open(String id) {
        Intent i = new Intent(this, EditorActivity.class);
        if (id != null) i.putExtra("id", id);
        startActivity(i);
    }

    private void refresh() {
        List<View> rows = new ArrayList<>();
        if (!query.isEmpty()) {
            rows.add(Ui.row(this, "Showing notes with \"" + query + "\"", "Tap to clear", () -> { query = ""; refresh(); }));
            rows.add(Ui.divider(this));
        }
        List<NoteStore.Note> notes = NoteStore.all(this, query);
        if (notes.isEmpty()) {
            rows.add(Ui.row(this, query.isEmpty() ? "No documents yet" : "Nothing found",
                    query.isEmpty() ? "Tap New to write one, or More to open a Word or OpenDocument file" : null, null));
        }
        for (final NoteStore.Note n : notes) {
            String when = DateUtils.formatDateTime(this, n.modified,
                    DateUtils.isToday(n.modified) ? DateUtils.FORMAT_SHOW_TIME : DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH);
            String prev = n.preview();
            if (prev.length() > 80) prev = prev.substring(0, 80) + "…";
            View r = Ui.row(this, n.title(), when + (prev.isEmpty() ? "" : "  ·  " + prev), () -> open(n.id));
            r.setOnLongClickListener(v -> {
                Ui.confirm(this, "Delete document?", "\"" + n.title() + "\" will be deleted. This cannot be undone.", "Delete", () -> {
                    NoteStore.delete(this, n.id);
                    refresh();
                });
                return true;
            });
            rows.add(r);
            rows.add(Ui.divider(this));
        }
        list.setRows(rows);
    }

    @Override protected boolean asksScreenType() { return true; }

    /** Notes stay behind the app lock when one is set in Settings. */
    @Override protected boolean usesAppLock() { return true; }
}
