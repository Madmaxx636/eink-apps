package app.eink.notes;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import app.eink.core.Display;
import app.eink.core.InkActivity;
import app.eink.core.Ui;
import app.eink.notes.format.Doc;

/** The word processor: formatted text, headings, lists, alignment, undo, find and replace, and export. */
public class EditorActivity extends InkActivity {
    private static final int REQ_EXPORT = 31;
    private static final int UNDO_LIMIT = 100;

    private String id;
    private RichEdit edit;
    private boolean deleted;
    private boolean internal;               // set while this class edits the text itself
    private int listMargin;
    private final Handler h = new Handler(Looper.getMainLooper());

    // toolbar
    private final Map<String, TextView> toggles = new HashMap<>();
    private TextView fontBtn, sizeLabel;
    private final Map<String, Object> pending = new HashMap<>(); // formats chosen with no selection, applied to the next typed text
    private boolean typing;

    // undo
    private static final class Snap {
        final SpannableStringBuilder text;
        final int a, b;

        Snap(Editable e, int a, int b) { text = new SpannableStringBuilder(e); this.a = a; this.b = b; }
    }
    private final List<Snap> undo = new ArrayList<>(), redo = new ArrayList<>();
    private Snap baseline;
    private boolean dirty;

    // find and replace
    private LinearLayout findBar;
    private EditText findField, replaceField;
    private TextView status;

    // paragraph edits to settle after the text changes
    private boolean needSettle;
    private int newlineAt = -1;
    private Exporter.Kind exportKind;

    private final Runnable saver = this::save;
    private final Runnable committer = this::commit;
    private final Runnable statusUpdate = this::updateStatus;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        listMargin = Display.dp(this, 28);
        id = b != null ? b.getString("id") : getIntent().getStringExtra("id");
        Doc doc;
        if (id == null) {
            id = NoteStore.newId();
            doc = new Doc();
            doc.newPara();
            String shared = getIntent().getStringExtra(Intent.EXTRA_TEXT);   // text shared from another app
            if (shared != null) doc = app.eink.notes.format.TxtFormat.read(shared);
        } else doc = NoteStore.load(this, id);

        edit = new RichEdit(this);
        edit.setBackground(null);
        edit.setTextColor(Display.fg(this));
        edit.setHintTextColor(Display.dim(this));
        edit.setHint("Write something");
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, Display.sp(this, 17));
        edit.setGravity(Gravity.TOP | Gravity.START);
        edit.setLineSpacing(0, 1.1f);
        edit.setHighlightColor(Display.isEink(this) ? Color.LTGRAY : (Display.accent(this) & 0x00FFFFFF) | 0x55000000);
        edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        edit.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        int p = Display.dp(this, 14);
        edit.setPadding(p, p, p, p);
        edit.setText(Spans.toEditable(doc, listMargin), TextView.BufferType.EDITABLE);
        edit.setSelection(edit.getText().length());
        edit.addTextChangedListener(new TextWatcher() {
            private boolean removedNewline;

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                removedNewline = count > 0 && s.subSequence(start, start + count).toString().indexOf('\n') >= 0;
            }

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (internal) return;
                typing = true;
                boolean insertedNewline = count > 0 && s.subSequence(start, start + count).toString().indexOf('\n') >= 0;
                if (before == 0 && count > 0 && !pending.isEmpty()) applyPending(start, start + count);
                needSettle = insertedNewline || removedNewline;
                newlineAt = before == 0 && count == 1 && s.charAt(start) == '\n' ? start : -1;
            }

            @Override public void afterTextChanged(Editable s) {
                if (internal) return;
                if (needSettle) { needSettle = false; settleParagraphs(); }
                dirty = true;
                h.removeCallbacks(saver);
                h.postDelayed(saver, 1500);
                h.removeCallbacks(committer);
                h.postDelayed(committer, 800);
                h.removeCallbacks(statusUpdate);
                h.postDelayed(statusUpdate, 400);
                h.post(() -> typing = false);
            }
        });
        edit.setSelectionListener((a, c) -> {
            if (!typing) pending.clear();   // moving the cursor drops formats chosen for the next letters
            refreshToolbar();
        });
        baseline = new Snap(edit.getText(), 0, 0);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        boolean toolbarBottom = "bottom".equals(app.eink.core.InkPrefs.getStr(this, "toolbarPos", "top"));
        View toolbar = buildToolbar();
        if (!toolbarBottom) { root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2)); root.addView(Ui.divider(this)); }
        root.addView(edit, new LinearLayout.LayoutParams(-1, 0, 1f));
        if (toolbarBottom) { root.addView(Ui.divider(this)); root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2)); }
        buildFindBar();
        root.addView(findBar, new LinearLayout.LayoutParams(-1, -2));
        status = Ui.dimText(this, "", 12);
        status.setPadding(Display.dp(this, 12), Display.dp(this, 2), Display.dp(this, 12), Display.dp(this, 4));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        setScreen("Document", root);
        addBarButton("Menu", this::menu);
        addBarButton("Done", this::finish);
        updateStatus();
        refreshToolbar();
    }

    // ---- toolbar ----

    private View buildToolbar() {
        HorizontalScrollView sv = new HorizontalScrollView(this);
        sv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        int p = Display.dp(this, 4);
        row.setPadding(p, p, p, p);
        sv.addView(row);

        toggle(row, "b", "B", Typeface.BOLD);
        toggle(row, "i", "I", Typeface.ITALIC);
        toggle(row, "u", "U", Typeface.NORMAL).setPaintFlags(android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        toggle(row, "s", "S", Typeface.NORMAL).setPaintFlags(android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        gap(row);
        tool(row, "A−", () -> stepSize(-1));
        sizeLabel = Ui.dimText(this, "", 12);
        sizeLabel.setGravity(Gravity.CENTER);
        sizeLabel.setMinWidth(Display.dp(this, 34));
        row.addView(sizeLabel, new LinearLayout.LayoutParams(-2, Display.btnH(this)));
        tool(row, "A+", () -> stepSize(1));
        fontBtn = tool(row, "Font", this::chooseFont);
        gap(row);
        toggle(row, "h1", "H1", Typeface.BOLD);
        toggle(row, "h2", "H2", Typeface.BOLD);
        toggle(row, "h3", "H3", Typeface.BOLD);
        gap(row);
        toggle(row, "ul", "•", Typeface.BOLD);
        toggle(row, "ol", "1.", Typeface.BOLD);
        gap(row);
        toggle(row, "al0", "Left", Typeface.NORMAL);
        toggle(row, "al1", "Mid", Typeface.NORMAL);
        toggle(row, "al2", "Right", Typeface.NORMAL);
        gap(row);
        tool(row, "Undo", () -> step(true));
        tool(row, "Redo", () -> step(false));
        return sv;
    }

    private TextView tool(LinearLayout row, String label, Runnable r) {
        TextView t = Ui.button(this, label, r);
        t.setMinWidth(Display.btnH(this));
        t.setMinHeight(Display.btnH(this));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, Display.sp(this, 13));
        t.setPadding(Display.dp(this, 8), 0, Display.dp(this, 8), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Display.btnH(this));
        lp.rightMargin = Display.dp(this, 3);
        row.addView(t, lp);
        t.setFocusable(false); // keep the cursor and keyboard where they are
        return t;
    }

    private TextView toggle(LinearLayout row, final String key, String label, int style) {
        TextView t = tool(row, label, () -> toggleFormat(key));
        t.setTypeface(null, style);
        toggles.put(key, t);
        return t;
    }

    private void gap(LinearLayout row) { row.addView(new View(this), new LinearLayout.LayoutParams(Display.dp(this, 8), 1)); }

    /** Draws each toggle on or off to match the text at the cursor. */
    private void refreshToolbar() {
        if (toggles.isEmpty() || sizeLabel == null) return;
        Editable e = edit.getText();
        int a = Math.min(edit.getSelectionStart(), edit.getSelectionEnd()), b = Math.max(edit.getSelectionStart(), edit.getSelectionEnd());
        if (a < 0) return;
        for (String k : new String[]{"b", "i", "u", "s"})
            Ui.style(toggles.get(k), pending.containsKey(k) ? Boolean.TRUE.equals(pending.get(k)) : Spans.isOn(e, a, b, k));
        List<Spans.PA> paras = Spans.paragraphs(e);
        int[] t = Spans.touched(paras, a, b);
        Spans.PA pa = paras.get(t[0]);
        for (int i = 1; i <= 3; i++) Ui.style(toggles.get("h" + i), pa.heading == i);
        Ui.style(toggles.get("ul"), Doc.UL.equals(pa.list));
        Ui.style(toggles.get("ol"), Doc.OL.equals(pa.list));
        for (int i = 0; i <= 2; i++) Ui.style(toggles.get("al" + i), pa.align == i);
        float size = pending.containsKey("z") ? (Float) pending.get("z") : Spans.sizeAt(e, a);
        sizeLabel.setText(Math.round(Doc.BODY_PT * size) + "pt");
        String fam = pending.containsKey("f") ? (String) pending.get("f") : Spans.familyAt(e, a);
        fontBtn.setText(fam == null ? "Sans" : fam.equals("serif") ? "Serif" : "Mono");
    }

    private int selA() { return Math.min(edit.getSelectionStart(), edit.getSelectionEnd()); }
    private int selB() { return Math.max(edit.getSelectionStart(), edit.getSelectionEnd()); }

    private void toggleFormat(String key) {
        if (key.length() == 1) {                      // b, i, u, s
            int a = selA(), b = selB();
            boolean on = !(pending.containsKey(key) ? Boolean.TRUE.equals(pending.get(key)) : Spans.isOn(edit.getText(), a, b, key));
            if (a == b) { pending.put(key, on); refreshToolbar(); return; }
            edit(() -> Spans.setChar(edit.getText(), a, b, key, on));
            return;
        }
        final int a = selA(), b = selB();
        edit(() -> {
            List<Spans.PA> paras = Spans.paragraphs(edit.getText());
            int[] t = Spans.touched(paras, a, b);
            if (key.startsWith("h")) {
                int level = key.charAt(1) - '0';
                boolean all = true;
                for (int i = t[0]; i <= t[1]; i++) if (paras.get(i).heading != level) all = false;
                for (int i = t[0]; i <= t[1]; i++) { paras.get(i).heading = all ? 0 : level; if (!all) paras.get(i).list = null; }
            } else if (key.startsWith("al")) {
                for (int i = t[0]; i <= t[1]; i++) paras.get(i).align = key.charAt(2) - '0';
            } else {
                boolean all = true;
                for (int i = t[0]; i <= t[1]; i++) if (!key.equals(paras.get(i).list)) all = false;
                for (int i = t[0]; i <= t[1]; i++) { paras.get(i).list = all ? null : key; if (!all) paras.get(i).heading = 0; }
            }
            Spans.writeParas(edit.getText(), paras, listMargin);
        });
    }

    private void stepSize(int dir) {
        int a = selA(), b = selB();
        float cur = pending.containsKey("z") ? (Float) pending.get("z") : Spans.sizeAt(edit.getText(), a);
        int idx = 1;
        for (int i = 0; i < Doc.SIZES.length; i++) if (Doc.SIZES[i] == cur) idx = i;
        final float next = Doc.SIZES[Math.max(0, Math.min(Doc.SIZES.length - 1, idx + dir))];
        if (a == b) { pending.put("z", next); refreshToolbar(); return; }
        edit(() -> Spans.setChar(edit.getText(), a, b, "z", next));
    }

    private void chooseFont() {
        final String[] ids = {null, "serif", "monospace"};
        String[] names = {"Sans-serif", "Serif", "Monospace"};
        String cur = Spans.familyAt(edit.getText(), selA());
        int sel = cur == null ? 0 : cur.equals("serif") ? 1 : 2;
        final int a = selA(), b = selB();
        Ui.choose(this, "Font", names, sel, i -> {
            if (a == b) { pending.put("f", ids[i]); refreshToolbar(); return; }
            edit(() -> Spans.setChar(edit.getText(), a, b, "f", ids[i]));
        });
    }

    /** Applies formats chosen with no selection to text just typed. */
    private void applyPending(int start, int end) {
        internal = true;
        try {
            Editable e = edit.getText();
            for (Map.Entry<String, Object> en : pending.entrySet()) Spans.setChar(e, start, end, en.getKey(), en.getValue());
        } finally { internal = false; }
    }

    /** Runs a formatting change as one undo step. */
    private void edit(Runnable change) {
        commit();
        int a = selA(), b = selB();
        internal = true;
        try { change.run(); } finally { internal = false; }
        dirty = true;
        commit();
        edit.setSelection(Math.min(a, edit.length()), Math.min(b, edit.length()));
        h.removeCallbacks(saver);
        h.postDelayed(saver, 1500);
        refreshToolbar();
    }

    // ---- paragraphs after a line break was added or removed ----

    private void settleParagraphs() {
        internal = true;
        try {
            Editable e = edit.getText();
            int at = newlineAt;
            int cursor = edit.getSelectionStart();
            List<Spans.PA> paras = Spans.paragraphs(e);
            if (at >= 0) {
                int prev = -1;
                for (int i = 0; i < paras.size(); i++) if (at >= paras.get(i).s && at < paras.get(i).e) prev = i;
                if (prev >= 0 && prev + 1 < paras.size()) {
                    Spans.PA before = paras.get(prev), after = paras.get(prev + 1);
                    boolean emptyBefore = before.e - before.s == 1;
                    if (emptyBefore && before.list != null) {
                        // Enter on an empty list item ends the list instead of adding a line.
                        e.delete(at, at + 1);
                        paras = Spans.paragraphs(e);
                        paras.get(prev).list = null;
                        Spans.writeParas(e, paras, listMargin);
                        edit.setSelection(Math.min(at, e.length()));
                        return;
                    }
                    boolean atEnd = after.e - after.s == 0 || (after.e - after.s == 1 && e.charAt(after.s) == '\n');
                    if (before.heading > 0 && atEnd) after.heading = 0;   // the line after a heading is body text
                }
            }
            Spans.writeParas(e, paras, listMargin);
            if (cursor >= 0 && cursor <= e.length()) edit.setSelection(cursor);
        } finally { internal = false; newlineAt = -1; }
    }

    // ---- undo ----

    /** Closes the current stretch of typing or formatting into one undo step. */
    private void commit() {
        h.removeCallbacks(committer);
        if (!dirty) return;
        dirty = false;
        undo.add(baseline);
        if (undo.size() > UNDO_LIMIT) undo.remove(0);
        redo.clear();
        baseline = new Snap(edit.getText(), selA(), selB());
    }

    private void step(boolean back) {
        commit();
        List<Snap> from = back ? undo : redo, to = back ? redo : undo;
        if (from.isEmpty()) { Toast.makeText(this, back ? "Nothing to undo" : "Nothing to redo", Toast.LENGTH_SHORT).show(); return; }
        to.add(new Snap(edit.getText(), selA(), selB()));
        Snap s = from.remove(from.size() - 1);
        internal = true;
        try {
            edit.setText(new SpannableStringBuilder(s.text), TextView.BufferType.EDITABLE);
            edit.setSelection(Math.min(s.a, edit.length()), Math.min(s.b, edit.length()));
        } finally { internal = false; }
        baseline = new Snap(edit.getText(), s.a, s.b);
        pending.clear();
        h.removeCallbacks(saver);
        h.postDelayed(saver, 1500);
        updateStatus();
        refreshToolbar();
    }

    // ---- find and replace ----

    private void buildFindBar() {
        findBar = new LinearLayout(this);
        findBar.setOrientation(LinearLayout.VERTICAL);
        findBar.setVisibility(View.GONE);
        int p = Display.dp(this, 6);
        findBar.setPadding(p, p, p, p);
        findBar.addView(Ui.divider(this));
        findField = field("Find");
        replaceField = field("Replace with");
        findBar.addView(findField, new LinearLayout.LayoutParams(-1, -2));
        findBar.addView(replaceField, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout btns = new LinearLayout(this);
        String[] names = {"Next", "Replace", "All", "Close"};
        Runnable[] acts = {() -> findNext(true), this::replaceOne, this::replaceAll, () -> findBar.setVisibility(View.GONE)};
        for (int i = 0; i < names.length; i++) {
            TextView b = Ui.button(this, names[i], acts[i]);
            b.setMinHeight(Display.dp(this, 44));
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, Display.sp(this, 14));
            b.setFocusable(false);
            ((app.eink.core.IconButton) b).setNoIcon(true);   // four buttons share one row: words only
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.rightMargin = Display.dp(this, 3);
            btns.addView(b, lp);
        }
        findBar.addView(btns, new LinearLayout.LayoutParams(-1, -2));
    }

    private EditText field(String hint) {
        EditText f = new EditText(this);
        f.setHint(hint);
        f.setSingleLine(true);
        f.setTextColor(Display.fg(this));
        f.setHintTextColor(Display.dim(this));
        f.setTextSize(TypedValue.COMPLEX_UNIT_SP, Display.sp(this, 15));
        return f;
    }

    private int find(String q, int from) {
        if (q.isEmpty()) return -1;
        String t = edit.getText().toString().toLowerCase(Locale.ROOT), n = q.toLowerCase(Locale.ROOT);
        int i = t.indexOf(n, from);
        return i < 0 && from > 0 ? t.indexOf(n) : i;   // wrap around
    }

    private void findNext(boolean fromEnd) {
        String q = findField.getText().toString();
        int i = find(q, fromEnd ? selB() : selA());
        if (i < 0) { Toast.makeText(this, "Not found", Toast.LENGTH_SHORT).show(); return; }
        edit.requestFocus();
        edit.setSelection(i, i + q.length());
    }

    private void replaceOne() {
        String q = findField.getText().toString();
        int a = selA(), b = selB();
        if (q.isEmpty()) return;
        if (b - a == q.length() && edit.getText().subSequence(a, b).toString().equalsIgnoreCase(q)) {
            final String with = replaceField.getText().toString();
            edit(() -> edit.getText().replace(a, b, with));
            edit.setSelection(a + with.length());
        }
        findNext(true);
    }

    private void replaceAll() {
        final String q = findField.getText().toString(), with = replaceField.getText().toString();
        if (q.isEmpty()) return;
        final int[] count = {0};
        edit(() -> {
            Editable e = edit.getText();
            String t = e.toString().toLowerCase(Locale.ROOT), n = q.toLowerCase(Locale.ROOT);
            int i = t.lastIndexOf(n);
            while (i >= 0) {            // from the end, so earlier positions stay valid
                e.replace(i, i + q.length(), with);
                count[0]++;
                i = i == 0 ? -1 : t.lastIndexOf(n, i - 1);
            }
        });
        Toast.makeText(this, count[0] + (count[0] == 1 ? " replacement" : " replacements"), Toast.LENGTH_SHORT).show();
    }

    // ---- menu, export, save ----

    private void menu() {
        final String[] items = {"Find and replace", "Word count", "Save as…", "Share text", "Delete document"};
        Ui.choose(this, "Document", items, -1, i -> {
            if (i == 0) { findBar.setVisibility(View.VISIBLE); findField.requestFocus(); }
            else if (i == 1) wordCount();
            else if (i == 2) exportMenu();
            else if (i == 3) {
                Intent s = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, edit.getText().toString());
                startActivity(Intent.createChooser(s, "Share text"));
            } else Ui.confirm(this, "Delete document?", "This cannot be undone.", "Delete", () -> {
                deleted = true;
                NoteStore.delete(this, id);
                finish();
            });
        });
    }

    private void wordCount() {
        String t = edit.getText().toString().trim();
        int words = t.isEmpty() ? 0 : t.split("\\s+").length;
        int paras = Spans.paragraphs(edit.getText()).size();
        Ui.dialog(this).setTitle("Word count")
                .setMessage(words + " words\n" + edit.length() + " characters\n" + paras + " paragraphs\nAbout " + Math.max(1, words / 200) + " min to read")
                .setPositiveButton("Close", null).show();
    }

    private void updateStatus() {
        String t = edit.getText().toString().trim();
        int words = t.isEmpty() ? 0 : t.split("\\s+").length;
        status.setText(words + (words == 1 ? " word" : " words"));
    }

    private void exportMenu() {
        final Exporter.Kind[] kinds = Exporter.Kind.values();
        String[] names = new String[kinds.length];
        for (int i = 0; i < kinds.length; i++) names[i] = kinds[i].label;
        Ui.choose(this, "Save as", names, -1, i -> {
            exportKind = kinds[i];
            Intent c = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(exportKind.mime).putExtra(Intent.EXTRA_TITLE, fileName() + exportKind.ext);
            startActivityForResult(c, REQ_EXPORT);
        });
    }

    private String fileName() {
        String first = "";
        for (String line : edit.getText().toString().split("\n")) if (!line.trim().isEmpty()) { first = line.trim(); break; }
        first = first.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "").trim();
        if (first.length() > 40) first = first.substring(0, 40).trim();
        return first.isEmpty() ? "Document" : first;
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_EXPORT || res != Activity.RESULT_OK || data == null || data.getData() == null || exportKind == null) return;
        final Uri uri = data.getData();
        final Exporter.Kind kind = exportKind;
        final Doc doc = Spans.toDoc(edit.getText());
        final String title = fileName();
        Toast.makeText(this, "Saving…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String err = null;
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IOException("Could not open the file");
                Exporter.write(kind, doc, title, out);
            } catch (Exception e) {
                err = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final String er = err;
            runOnUiThread(() -> Toast.makeText(this, er == null ? "Saved" : "Could not save: " + er, Toast.LENGTH_LONG).show());
        }).start();
    }

    private void save() {
        if (deleted) return;
        Doc d = Spans.toDoc(edit.getText());
        // A document you opened and left empty is not kept.
        if (d.plainText().trim().isEmpty()) NoteStore.delete(this, id);
        else NoteStore.save(this, id, d);
    }

    @Override protected void onSaveInstanceState(Bundle o) {
        super.onSaveInstanceState(o);
        o.putString("id", id);
    }

    @Override protected void onPause() {
        h.removeCallbacks(saver);
        save();
        super.onPause();
    }

    @Override protected void onLookChanged() { recreate(); }

    /** Notes stay behind the app lock when one is set in Settings. */
    @Override protected boolean usesAppLock() { return true; }
}
