package app.eink.files;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.text.InputType;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import app.eink.core.Display;
import app.eink.core.InkActivity;
import app.eink.core.InkPrefs;
import app.eink.core.PagedScroll;
import app.eink.core.Ui;

/**
 * Files, Recents and Storage tabs, in the manner of Fossify File Manager: a tappable path, favorites, sorting
 * per folder, compact or detailed lists, a storage breakdown, an optional PIN.
 */
public class FilesActivity extends InkActivity {
    private static final int REQ = 21;
    private static final int TAB_FILES = 0, TAB_RECENTS = 1, TAB_STORAGE = 2;
    private static final String[] TAB_NAMES = {"Files", "Recents", "Storage"};

    /** Copy and cut survive leaving the screen, so you can pick things up in one place and drop them in another. */
    private static List<File> clip = new ArrayList<>();
    private static boolean clipCut;
    /** When the app was last on screen, to know when the PIN is needed again. */
    private static long lastActive;

    private File root, cur;
    private String rootName = "Internal storage";
    private final Set<File> sel = new LinkedHashSet<>();
    private List<File> results;           // non-null while showing search results
    private String resultsFor;
    private PagedScroll list;
    private LinearLayout outer, body, tabsRow;
    private TextView upBtn, moreBtn;
    private int tab;
    private boolean busy, locked, pinShowing;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = Environment.getExternalStorageDirectory();
        File start = startFolder(b);
        setLocation(start);
        tab = b != null ? b.getInt("tab", TAB_FILES) : TAB_FILES;

        outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        tabsRow = new LinearLayout(this);
        int p = Display.dp(this, 4);
        tabsRow.setPadding(p, p, p, p);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        boolean tabsBottom = "bottom".equals(InkPrefs.getStr(this, "tabsPos", "top"));
        if (!tabsBottom) { outer.addView(tabsRow, new LinearLayout.LayoutParams(-1, -2)); outer.addView(Ui.divider(this)); }
        outer.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        if (tabsBottom) { outer.addView(Ui.divider(this)); outer.addView(tabsRow, new LinearLayout.LayoutParams(-1, -2)); }

        setScreen("Files", outer);
        upBtn = addBarButton("Up", this::goUp);
        moreBtn = addBarButton("More", this::more);
    }

    /** Where to open: a home-screen shortcut's folder, the saved one, or the one chosen in Settings. */
    private File startFolder(Bundle b) {
        String shortcut = getIntent().getStringExtra("path");
        if (shortcut != null && new File(shortcut).isDirectory()) return new File(shortcut);
        if (b != null && b.getString("cur") != null) return new File(b.getString("cur"));
        String mode = InkPrefs.getStr(this, "startIn", "last");
        if (mode.equals("downloads")) {
            File d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (d.isDirectory()) return d;
        } else if (mode.equals("last")) {
            File l = new File(InkPrefs.getStr(this, "lastFolder", ""));
            if (l.isDirectory()) return l;
        }
        return Environment.getExternalStorageDirectory();
    }

    /** Makes dir the current folder and sets the top level (internal storage or the card it is on) to match. */
    private void setLocation(File dir) {
        File primary = Environment.getExternalStorageDirectory();
        String path = dir.getAbsolutePath();
        if (path.equals(primary.getAbsolutePath()) || path.startsWith(primary.getAbsolutePath() + "/")) {
            root = primary;
            rootName = "Internal storage";
        } else {
            root = dir;
            rootName = dir.getName();
            for (Vol v : volumes()) {
                String vp = v.dir.getAbsolutePath();
                if (path.equals(vp) || path.startsWith(vp + "/")) { root = v.dir; rootName = v.name; break; }
            }
        }
        cur = dir;
    }

    @Override protected void onSaveInstanceState(Bundle o) {
        super.onSaveInstanceState(o);
        o.putString("cur", cur.getAbsolutePath());
        o.putInt("tab", tab);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!hasAccess()) { showNoAccess(); return; }
        // With a PIN set, ask again if the app has been away for a while.
        if (FilesPrefs.hasPin(this) && (locked || System.currentTimeMillis() - lastActive > 30000)) { locked = true; showLock(); return; }
        locked = false;
        refresh();
    }

    @Override protected void onPause() {
        lastActive = System.currentTimeMillis();
        InkPrefs.put(this, "lastFolder", cur.getAbsolutePath());
        super.onPause();
    }

    @Override protected boolean onPage(int dir) { return list != null && list.turn(dir); }

    @Override public void onBackPressed() {
        if (!sel.isEmpty()) { sel.clear(); refresh(); }
        else if (tab != TAB_FILES) { tab = TAB_FILES; refresh(); }
        else if (results != null) { results = null; refresh(); }
        else if (!cur.equals(root) && cur.getParentFile() != null) goUp();
        else super.onBackPressed();
    }

    /** Swipe right goes up a folder; at the top level, swipes move between the three tabs. */
    @Override protected boolean onSwipe(int dir) {
        if (locked) return false;
        if (tab == TAB_FILES && dir < 0 && (results != null || !cur.equals(root))) { goUp(); return true; }
        int to = tab + dir;
        if (to < 0 || to > TAB_STORAGE) return false;
        tab = to;
        sel.clear();
        refresh();
        return true;
    }

    // ---- PIN ----

    private android.os.CancellationSignal bioCancel;

    private void unlocked() {
        locked = false;
        lastActive = System.currentTimeMillis();
        refresh();
    }

    /** The lock screen: fingerprint or face first if it is switched on, with the PIN as the way on if that fails or is declined. */
    private void showLock() {
        body.removeAllViews();
        body.setGravity(Gravity.CENTER);
        body.addView(Ui.text(this, "Locked", 20, true));
        if (pinShowing || bioCancel != null) return;
        if (InkPrefs.getBool(this, "biometric", false) && Biometrics.available(this)) {
            TextView again = Ui.button(this, "Use fingerprint or face", this::startBiometric);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = Display.dp(this, 14);
            body.addView(again, lp);
            // If the prompt is dismissed some other way, these are still on screen, so the PIN is never out of reach.
            TextView pin = Ui.button(this, "Use PIN", () -> { if (bioCancel != null) { bioCancel.cancel(); bioCancel = null; } askPin(); });
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(-2, -2);
            lp2.topMargin = Display.dp(this, 8);
            body.addView(pin, lp2);
            startBiometric();
        } else askPin();
    }

    private void startBiometric() {
        if (bioCancel != null) return;
        if (Build.VERSION.SDK_INT < 29) { askPin(); return; }
        bioCancel = Biometrics.prompt(this, "Unlock Files", "Use PIN", new Biometrics.Result() {
            @Override public void ok() { bioCancel = null; unlocked(); }

            @Override public void usePin(String why) { bioCancel = null; if (locked && !isFinishing()) askPin(); }

            @Override public void dismissed() { bioCancel = null; }
        });
    }

    private void askPin() {
        if (pinShowing) return;
        pinShowing = true;
        final EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setHint("PIN");
        LinearLayout box = new LinearLayout(this);
        int p = Display.dp(this, 16);
        box.setPadding(p, p / 2, p, 0);
        box.addView(e, new LinearLayout.LayoutParams(-1, -2));
        Ui.dialog(this).setTitle("Enter your PIN").setView(box).setCancelable(false)
                .setPositiveButton("Unlock", (d, w) -> {
                    pinShowing = false;
                    if (FilesPrefs.checkPin(this, e.getText().toString())) unlocked();
                    else { toast("Wrong PIN"); askPin(); }
                })
                .setNegativeButton("Exit", (d, w) -> { pinShowing = false; finish(); }).show();
    }

    // ---- access ----

    private boolean hasAccess() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void showNoAccess() {
        body.removeAllViews();
        body.setGravity(Gravity.CENTER);
        int p = Display.dp(this, 16);
        body.setPadding(p, p, p, p);
        TextView t = Ui.text(this, "Files needs access to all files on this device.", 18, false);
        t.setGravity(Gravity.CENTER);
        body.addView(t, new LinearLayout.LayoutParams(-1, -2));
        TextView d = Ui.dimText(this, "Android asks you to turn this on in system settings. The app has no internet access, so nothing leaves the device.", 14);
        d.setGravity(Gravity.CENTER);
        d.setPadding(0, p, 0, p);
        body.addView(d, new LinearLayout.LayoutParams(-1, -2));
        body.addView(Ui.button(this, "Allow access", () -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()));
                try { startActivity(i); } catch (ActivityNotFoundException e) { startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
            } else {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ);
            }
        }), new LinearLayout.LayoutParams(-1, -2));
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (hasAccess()) { body.setPadding(0, 0, 0, 0); refresh(); }
    }

    // ---- tabs ----

    private void buildTabs() {
        tabsRow.removeAllViews();
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int idx = i;
            TextView b = Ui.button(this, TAB_NAMES[i], () -> { if (!locked && tab != idx) { tab = idx; sel.clear(); refresh(); } });
            b.setMinWidth(0);
            Ui.style(b, i == tab);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(Display.dp(this, 2), 0, Display.dp(this, 2), 0);
            tabsRow.addView(b, lp);
        }
    }

    private void refresh() {
        if (locked) { showLock(); return; }      // nothing is drawn while the PIN is waiting
        buildTabs();
        body.removeAllViews();
        body.setGravity(Gravity.TOP);
        body.setPadding(0, 0, 0, 0);
        upBtn.setVisibility(tab == TAB_FILES ? View.VISIBLE : View.GONE);
        if (tab == TAB_RECENTS) { buildRecents(); moreBtn.setText("More"); return; }
        if (tab == TAB_STORAGE) { buildStorage(); moreBtn.setText("More"); return; }
        refreshFiles();
    }

    // ---- Files tab ----

    private boolean compactView() { return "compact".equals(InkPrefs.getStr(this, "view", "detailed")); }

    private void refreshFiles() {
        int p = Display.dp(this, 12);
        body.addView(pathBar(), new LinearLayout.LayoutParams(-1, -2));
        body.addView(Ui.divider(this));

        list = new PagedScroll(this);
        body.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));

        String[] order = FilesPrefs.sortFor(this, cur);
        List<File> files = results != null ? results
                : FileOps.list(cur, order[0], "asc".equals(order[1]), InkPrefs.getBool(this, "hidden", false));
        List<View> rows = new ArrayList<>();
        if (results != null) rows.add(Ui.row(this, results.size() + " found for \"" + resultsFor + "\"", "Tap to clear the search", () -> { results = null; refresh(); }));
        if (files.isEmpty()) rows.add(Ui.row(this, results != null ? "Nothing found" : "This folder is empty", null, null));
        for (final File f : files) {
            rows.add(fileRow(f));
            rows.add(Ui.divider(this));
        }
        list.setRows(rows);
        upBtn.setEnabled(!cur.equals(root) || results != null);
        moreBtn.setText(sel.isEmpty() ? (clip.isEmpty() ? "More" : "More (" + clip.size() + ")") : "Actions");
    }

    /** The tappable path: each folder name jumps to that folder, the first one opens the list of places. */
    private View pathBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int p = Display.dp(this, 10);
        bar.setPadding(p, Display.dp(this, 2), p, Display.dp(this, 2));
        if (!sel.isEmpty()) {
            bar.addView(Ui.text(this, sel.size() + " selected  ·  tap Actions", 14, true));
            return bar;
        }
        if (results != null) {
            bar.addView(Ui.dimText(this, "Search in " + cur.getName(), 13));
            return bar;
        }
        final HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout crumbs = new LinearLayout(this);
        crumbs.setGravity(Gravity.CENTER_VERTICAL);
        String rel = cur.getAbsolutePath().substring(Math.min(cur.getAbsolutePath().length(), root.getAbsolutePath().length()));
        List<String> parts = new ArrayList<>();
        for (String s : rel.split("/")) if (!s.isEmpty()) parts.add(s);
        crumbs.addView(crumb(rootName + " ▾", null, parts.isEmpty()));
        File at = root;
        for (int i = 0; i < parts.size(); i++) {
            at = new File(at, parts.get(i));
            crumbs.addView(Ui.dimText(this, " › ", 14));
            crumbs.addView(crumb(parts.get(i), at, i == parts.size() - 1));
        }
        hs.addView(crumbs);
        bar.addView(hs, new LinearLayout.LayoutParams(0, -2, 1f));
        hs.post(() -> hs.fullScroll(View.FOCUS_RIGHT));    // keep the current folder in view
        long free = freeSpace(cur.exists() ? cur : root);
        TextView f = Ui.dimText(this, "  " + FileOps.humanSize(free) + " free", 12);
        bar.addView(f);
        return bar;
    }

    private TextView crumb(String name, final File to, boolean current) {
        TextView t = Ui.text(this, name, 14, current);
        t.setPadding(Display.dp(this, 4), Display.dp(this, 8), Display.dp(this, 4), Display.dp(this, 8));
        t.setClickable(true);
        t.setOnClickListener(v -> { if (to == null) places(); else { results = null; cur = to; sel.clear(); refresh(); } });
        return t;
    }

    private View fileRow(final File f) {
        boolean on = sel.contains(f);
        boolean fav = FilesPrefs.isFavorite(this, f);
        String name = f.getName() + (fav ? "  ★" : "");
        String size;
        if (f.isDirectory()) {
            String[] kids = f.list();
            int cnt = kids == null ? 0 : kids.length;
            size = cnt + (cnt == 1 ? " item" : " items");
        } else size = FileOps.humanSize(f.length());
        View r;
        if (compactView() && results == null) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int p = Display.dp(this, 12);
            row.setPadding(p, Display.dp(this, 7), p, Display.dp(this, 7));
            TextView n = Ui.text(this, name, 15, f.isDirectory());     // folders are bold
            n.setSingleLine(true);
            n.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            row.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView s = Ui.dimText(this, "  " + size, 12);
            row.addView(s);
            row.setClickable(true);
            row.setOnClickListener(v -> tap(f));
            r = row;
        } else {
            String sub = size + "   " + DateUtils.formatDateTime(this, f.lastModified(), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_SHOW_YEAR);
            if (results != null && f.getParentFile() != null) sub += "\n" + f.getParentFile().getAbsolutePath();
            r = Ui.row(this, name, sub, () -> tap(f));
            if (f.isDirectory() && r instanceof LinearLayout && ((LinearLayout) r).getChildAt(0) instanceof TextView)
                ((TextView) ((LinearLayout) r).getChildAt(0)).setTypeface(null, android.graphics.Typeface.BOLD);   // folders are bold
        }
        r.setOnLongClickListener(v -> { toggle(f); return true; });
        if (on) invert(r);
        return r;
    }

    /** A selected row is drawn inverted: no color needed, and it is obvious on e-ink. */
    private void invert(View row) {
        row.setBackgroundColor(Display.fg(this));
        if (row instanceof LinearLayout) {
            LinearLayout l = (LinearLayout) row;
            for (int i = 0; i < l.getChildCount(); i++)
                if (l.getChildAt(i) instanceof TextView) ((TextView) l.getChildAt(i)).setTextColor(Display.bg(this));
        }
    }

    private void toggle(File f) {
        if (!sel.remove(f)) sel.add(f);
        refresh();
    }

    private void tap(File f) {
        if (!sel.isEmpty()) { toggle(f); return; }
        if (f.isDirectory()) { results = null; cur = f; refresh(); }
        else openFile(f);
    }

    private void goUp() {
        if (locked) return;
        if (results != null) { results = null; refresh(); return; }
        File p = cur.getParentFile();
        if (p != null && p.canRead() && !cur.equals(root)) { cur = p; sel.clear(); refresh(); }
    }

    private void openFile(File f) {
        Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(FileShare.uriFor(f), FileOps.mime(f))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(Intent.createChooser(i, "Open with")); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, "No app can open this file", Toast.LENGTH_SHORT).show(); }
    }

    /** Goes to a folder, or opens a file, from favorites and the other tabs. */
    private void openPath(File f) {
        if (f.isDirectory()) { tab = TAB_FILES; results = null; sel.clear(); setLocation(f); refresh(); }
        else if (f.isFile()) openFile(f);
        else toast("That is no longer there");
    }

    private void showInFolder(File f) {
        if (f.getParentFile() != null) { tab = TAB_FILES; results = null; sel.clear(); setLocation(f.getParentFile()); refresh(); }
    }

    private void share(List<File> files) {
        Intent i;
        if (files.size() == 1) {
            i = new Intent(Intent.ACTION_SEND).setType(FileOps.mime(files.get(0))).putExtra(Intent.EXTRA_STREAM, FileShare.uriFor(files.get(0)));
        } else {
            ArrayList<Uri> uris = new ArrayList<>();
            for (File f : files) uris.add(FileShare.uriFor(f));
            i = new Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Share"));
    }

    // ---- Recents tab ----

    private void buildRecents() {
        list = new PagedScroll(this);
        body.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        List<View> loading = new ArrayList<>();
        loading.add(Ui.row(this, "Loading…", null, null));
        list.setRows(loading);
        final PagedScroll mine = list;
        new Thread(() -> {
            final List<Library.Item> items = Library.recents(this, 100);
            runOnUiThread(() -> {
                if (isDestroyed() || list != mine || tab != TAB_RECENTS) return;
                list.setRows(itemRows(items, items.isEmpty() ? "Nothing recent found" : null));
            });
        }).start();
    }

    private List<View> itemRows(List<Library.Item> items, String emptyText) {
        List<View> rows = new ArrayList<>();
        if (emptyText != null) rows.add(Ui.row(this, emptyText, null, null));
        for (final Library.Item it : items) {
            String where = it.file.getParentFile() == null ? "" : it.file.getParentFile().getAbsolutePath().replace(Environment.getExternalStorageDirectory().getAbsolutePath(), "");
            if (where.isEmpty()) where = "/";
            View r = Ui.row(this, it.file.getName(), FileOps.humanSize(it.size) + "   "
                    + DateUtils.formatDateTime(this, it.modified, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL) + "\n" + where, () -> openFile(it.file));
            r.setOnLongClickListener(v -> {
                Ui.choose(this, it.file.getName(), new String[]{"Open", "Show in folder", "Share", "Details"}, -1, i -> {
                    if (i == 0) openFile(it.file);
                    else if (i == 1) showInFolder(it.file);
                    else if (i == 2) share(java.util.Collections.singletonList(it.file));
                    else details(it.file);
                });
                return true;
            });
            rows.add(r);
            rows.add(Ui.divider(this));
        }
        return rows;
    }

    // ---- Storage tab ----

    private String bar(double frac) {
        int n = 22, filled = (int) Math.round(Math.max(0, Math.min(1, frac)) * n);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(i < filled ? '█' : '░');
        return b.toString();
    }

    private View storageLine(String name, File dir) {
        long total = 0, free = 0;
        try {
            StatFs s = new StatFs(dir.getAbsolutePath());
            total = s.getTotalBytes();
            free = s.getAvailableBytes();
        } catch (RuntimeException ignored) {}
        long used = Math.max(0, total - free);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = Display.dp(this, 12);
        l.setPadding(p, Display.dp(this, 6), p, Display.dp(this, 6));
        l.addView(Ui.text(this, name, 16, true));
        l.addView(Ui.text(this, bar(total == 0 ? 0 : (double) used / total), 14, false));
        l.addView(Ui.dimText(this, FileOps.humanSize(used) + " used of " + FileOps.humanSize(total) + "   ·   " + FileOps.humanSize(free) + " free", 12));
        return l;
    }

    private void buildStorage() {
        list = new PagedScroll(this);
        body.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        final List<View> top = new ArrayList<>();
        top.add(storageLine("Internal storage", Environment.getExternalStorageDirectory()));
        top.add(Ui.divider(this));
        for (Vol v : volumes()) { top.add(storageLine(v.name, v.dir)); top.add(Ui.divider(this)); }
        List<View> first = new ArrayList<>(top);
        first.add(Ui.row(this, "Counting what is using the space…", null, null));
        list.setRows(first);
        final PagedScroll mine = list;
        new Thread(() -> {
            final Library.Totals t = Library.totals(this);
            runOnUiThread(() -> {
                if (isDestroyed() || list != mine || tab != TAB_STORAGE) return;
                List<View> rows = new ArrayList<>(top);
                long all = Math.max(1, t.total());
                Object[][] cats = {{"Images", t.images}, {"Videos", t.videos}, {"Audio", t.audio}, {"Documents", t.docs}, {"Archives", t.archives}, {"Everything else", t.others}};
                for (Object[] c : cats) {
                    long sz = (Long) c[1];
                    LinearLayout l = new LinearLayout(this);
                    l.setOrientation(LinearLayout.VERTICAL);
                    int p = Display.dp(this, 12);
                    l.setPadding(p, Display.dp(this, 5), p, Display.dp(this, 5));
                    LinearLayout line = new LinearLayout(this);
                    TextView n = Ui.text(this, (String) c[0], 15, true);
                    line.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
                    line.addView(Ui.text(this, FileOps.humanSize(sz) + "   " + Math.round(100.0 * sz / all) + "%", 14, false));
                    l.addView(line);
                    l.addView(Ui.dimText(this, bar((double) sz / all), 12));
                    rows.add(l);
                    rows.add(Ui.divider(this));
                }
                rows.add(Ui.row(this, "Largest files", "The biggest 100 files on this device", this::showLargest));
                rows.add(Ui.divider(this));
                rows.add(Ui.dimText(this, "  Counted from Android's file index. Files it has not scanned yet are not included.", 11));
                list.setRows(rows);
            });
        }).start();
    }

    private void showLargest() {
        toast("Finding the largest files…");
        new Thread(() -> {
            final List<Library.Item> items = Library.largest(this, 100);
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                tab = TAB_RECENTS;                       // the same kind of list, so reuse it
                body.removeAllViews();
                buildTabs();
                list = new PagedScroll(this);
                body.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
                List<View> rows = new ArrayList<>();
                rows.add(Ui.row(this, "Largest files", "Tap to go back to Recents", this::refresh));
                rows.addAll(itemRows(items, items.isEmpty() ? "Nothing found" : null));
                list.setRows(rows);
            });
        }).start();
    }

    // ---- places ----

    /** A mounted SD card or USB drive, with the name Android shows for it. */
    private static final class Vol {
        final File dir;
        final String name;

        Vol(File dir, String name) { this.dir = dir; this.name = name; }
    }

    /** Removable storage, found through the storage service (reliable) with the old ways as backup. */
    private List<Vol> volumes() {
        List<Vol> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        File primary = Environment.getExternalStorageDirectory();
        try {
            seen.add(primary.getCanonicalPath());
        } catch (IOException ignored) {}
        try {
            android.os.storage.StorageManager sm = (android.os.storage.StorageManager) getSystemService(STORAGE_SERVICE);
            for (android.os.storage.StorageVolume v : sm.getStorageVolumes()) {
                if (v.isPrimary()) continue;
                String st = v.getState();
                if (!Environment.MEDIA_MOUNTED.equals(st) && !Environment.MEDIA_MOUNTED_READ_ONLY.equals(st)) continue;
                File dir = null;
                if (Build.VERSION.SDK_INT >= 30) dir = v.getDirectory();
                else try { dir = new File((String) v.getClass().getMethod("getPath").invoke(v)); } catch (Exception ignored) {}
                if (dir == null) continue;
                String canon = dir.getCanonicalPath();
                if (seen.add(canon)) out.add(new Vol(dir, cardName(v.getDescription(this))));
            }
        } catch (Exception ignored) {}
        try {
            for (File f : getExternalFilesDirs(null)) {
                if (f == null) continue;
                String path = f.getAbsolutePath();
                int i = path.indexOf("/Android/data");
                if (i <= 0) continue;
                File r = new File(path.substring(0, i));
                if (r.canRead() && seen.add(r.getCanonicalPath())) out.add(new Vol(r, "Card or drive"));
            }
        } catch (Exception ignored) {}
        File[] vols = new File("/storage").listFiles();
        if (vols != null) for (File v : vols) {
            String n = v.getName();
            try {
                if (!n.equals("emulated") && !n.equals("self") && v.isDirectory() && v.canRead() && seen.add(v.getCanonicalPath())) out.add(new Vol(v, "Card or drive " + n));
            } catch (IOException ignored) {}
        }
        return out;
    }

    /** "512GB" on its own does not say what it is, so name it. */
    private static String cardName(String description) {
        String d = description == null ? "" : description;
        String low = d.toLowerCase(Locale.ROOT);
        if (low.contains("sd") || low.contains("card") || low.contains("usb") || low.contains("drive")) return d;
        return "SD card · " + (d.isEmpty() ? "removable" : d);
    }

    private static long freeSpace(File f) {
        try { return new StatFs(f.getAbsolutePath()).getAvailableBytes(); } catch (RuntimeException e) { return 0; }
    }

    /** Storage, favorites and the usual folders in one list. */
    private void places() {
        final List<File> locs = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        locs.add(Environment.getExternalStorageDirectory()); names.add("Internal storage");
        for (Vol v : volumes()) { locs.add(v.dir); names.add(v.name + "  (" + FileOps.humanSize(freeSpace(v.dir)) + " free)"); }
        for (String fav : FilesPrefs.favorites(this)) {
            File f = new File(fav);
            if (f.exists()) { locs.add(f); names.add("★ " + f.getName() + (f.isDirectory() ? "" : "  (file)")); }
        }
        String[][] std = {{Environment.DIRECTORY_DOWNLOADS, "Downloads"}, {Environment.DIRECTORY_DOCUMENTS, "Documents"},
                {Environment.DIRECTORY_DCIM, "Camera"}, {Environment.DIRECTORY_PICTURES, "Pictures"},
                {Environment.DIRECTORY_MUSIC, "Music"}, {Environment.DIRECTORY_MOVIES, "Movies"}};
        for (String[] s : std) {
            File d = Environment.getExternalStoragePublicDirectory(s[0]);
            if (d.isDirectory()) { locs.add(d); names.add(s[1]); }
        }
        if (volumes().isEmpty()) names.add("No SD card or drive found. Insert it, then check Android Settings → Storage.");
        int here = -1, best = -1;
        String now = cur.getAbsolutePath();
        for (int i = 0; i < locs.size(); i++) {
            String lp = locs.get(i).getAbsolutePath();
            if ((now.equals(lp) || now.startsWith(lp + "/")) && lp.length() > best) { best = lp.length(); here = i; }
        }
        Ui.choose(this, "Go to", names.toArray(new String[0]), here, i -> {
            if (i >= locs.size()) return;
            openPath(locs.get(i));
        });
    }

    // ---- menus ----

    private void more() {
        if (locked) return;
        if (tab != TAB_FILES) {
            Ui.choose(this, "Menu", new String[]{"Refresh", "Settings"}, -1, i -> {
                if (i == 0) refresh(); else startActivity(new Intent(this, FilesSettings.class));
            });
            return;
        }
        if (!sel.isEmpty()) { actions(); return; }
        final List<String> items = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        if (!clip.isEmpty()) { items.add((clipCut ? "Move here" : "Paste here") + " (" + clip.size() + ")"); acts.add(this::paste); }
        items.add("New file or folder"); acts.add(this::newItem);
        items.add("Search here"); acts.add(() -> search(cur, "Search names below this folder"));
        items.add("Search everywhere"); acts.add(() -> search(Environment.getExternalStorageDirectory(), "Search names on this device"));
        items.add("Select all"); acts.add(() -> {
            String[] o = FilesPrefs.sortFor(this, cur);
            sel.addAll(FileOps.list(cur, o[0], "asc".equals(o[1]), InkPrefs.getBool(this, "hidden", false)));
            refresh();
        });
        items.add("Sort…"); acts.add(this::sortDialog);
        items.add(compactView() ? "View: detailed list" : "View: compact list"); acts.add(() -> {
            InkPrefs.put(this, "view", compactView() ? "detailed" : "compact");
            refresh();
        });
        items.add(InkPrefs.getBool(this, "hidden", false) ? "Hide hidden files" : "Show hidden files"); acts.add(() -> {
            InkPrefs.put(this, "hidden", !InkPrefs.getBool(this, "hidden", false));
            refresh();
        });
        items.add(FilesPrefs.isFavorite(this, cur) ? "Remove this folder from favorites" : "Add this folder to favorites"); acts.add(() -> {
            FilesPrefs.setFavorite(this, cur, !FilesPrefs.isFavorite(this, cur));
            toast(FilesPrefs.isFavorite(this, cur) ? "Added to favorites" : "Removed from favorites");
        });
        items.add("Go to… (storage, favorites, folders)"); acts.add(this::places);
        items.add("Settings"); acts.add(() -> startActivity(new Intent(this, FilesSettings.class)));
        Ui.choose(this, "Menu", items.toArray(new String[0]), -1, i -> acts.get(i).run());
    }

    private void actions() {
        final List<File> picked = new ArrayList<>(sel);
        final List<String> items = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        items.add("Copy"); acts.add(() -> { clip = picked; clipCut = false; sel.clear(); refresh(); toast("Copied " + picked.size() + ". Open the destination and choose Paste."); });
        items.add("Cut"); acts.add(() -> { clip = picked; clipCut = true; sel.clear(); refresh(); toast("Cut " + picked.size() + ". Open the destination and choose Move here."); });
        items.add("Delete"); acts.add(() -> delete(picked));
        boolean allFiles = true;
        for (File f : picked) if (f.isDirectory()) allFiles = false;
        if (allFiles) { items.add("Share"); acts.add(() -> share(picked)); }
        if (picked.size() == 1) {
            final File one = picked.get(0);
            items.add("Rename"); acts.add(() -> rename(one));
            items.add("Details"); acts.add(() -> details(one));
            items.add("Copy path"); acts.add(() -> copyPath(one));
            boolean fav = FilesPrefs.isFavorite(this, one);
            items.add(fav ? "Remove from favorites" : "Add to favorites"); acts.add(() -> { FilesPrefs.setFavorite(this, one, !fav); sel.clear(); refresh(); });
            if (one.isDirectory()) { items.add("Add folder to home screen"); acts.add(() -> pinShortcut(one)); }
            if (one.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) { items.add("Extract here"); acts.add(() -> extract(one)); }
        }
        items.add("Compress to zip"); acts.add(() -> compress(picked));
        items.add("Select all"); acts.add(() -> {
            String[] o = FilesPrefs.sortFor(this, cur);
            sel.addAll(FileOps.list(cur, o[0], "asc".equals(o[1]), InkPrefs.getBool(this, "hidden", false)));
            refresh();
        });
        items.add("Clear selection"); acts.add(() -> { sel.clear(); refresh(); });
        Ui.choose(this, picked.size() + " selected", items.toArray(new String[0]), -1, i -> acts.get(i).run());
    }

    // ---- sorting ----

    private void sortDialog() {
        String[] now = FilesPrefs.sortFor(this, cur);
        final String[] keys = {FileOps.SORT_NAME, FileOps.SORT_SIZE, FileOps.SORT_DATE, FileOps.SORT_EXT};
        String[] labels = {"Name", "Size", "Last modified", "Extension"};
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Display.dp(this, 20);
        box.setPadding(p, p / 2, p, 0);
        final RadioGroup rk = new RadioGroup(this);
        final int[] ids = new int[keys.length];
        for (int i = 0; i < keys.length; i++) {
            RadioButton b = new RadioButton(this);
            b.setText(labels[i]);
            b.setId(View.generateViewId());
            ids[i] = b.getId();
            rk.addView(b);
            if (keys[i].equals(now[0])) rk.check(b.getId());
        }
        box.addView(rk);
        final RadioGroup rd = new RadioGroup(this);
        RadioButton asc = new RadioButton(this), desc = new RadioButton(this);
        asc.setText("Ascending");
        desc.setText("Descending");
        asc.setId(View.generateViewId());
        desc.setId(View.generateViewId());
        rd.addView(asc);
        rd.addView(desc);
        rd.check("asc".equals(now[1]) ? asc.getId() : desc.getId());
        box.addView(rd);
        final CheckBox only = new CheckBox(this);
        only.setText("Use for this folder only");
        only.setChecked(FilesPrefs.hasFolderSort(this, cur));
        box.addView(only);
        final int ascId = asc.getId();
        Ui.dialog(this).setTitle("Sort by").setView(box).setPositiveButton("OK", (d, w) -> {
            String key = keys[0];
            for (int i = 0; i < ids.length; i++) if (rk.getCheckedRadioButtonId() == ids[i]) key = keys[i];
            boolean up = rd.getCheckedRadioButtonId() == ascId;
            if (only.isChecked()) FilesPrefs.setSort(this, cur, key, up);
            else { FilesPrefs.clearFolderSort(this, cur); FilesPrefs.setSort(this, null, key, up); }
            refresh();
        }).setNegativeButton("Cancel", null).show();
    }

    // ---- actions ----

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    /** Runs slow work off the main thread, then redraws and reports any failure in plain words. */
    private void work(final String label, final Task t) {
        if (busy) { toast("Still working on the last task"); return; }
        busy = true;
        toast(label + "…");
        new Thread(() -> {
            String err = null;
            try { t.run(); } catch (Exception e) { err = e.getMessage() == null ? e.toString() : e.getMessage(); }
            final String er = err;
            runOnUiThread(() -> {
                busy = false;
                if (isDestroyed()) return;
                sel.clear();
                if (er != null) toast("Failed: " + er);
                refresh();
            });
        }).start();
    }

    private interface Task { void run() throws Exception; }

    private void paste() {
        final List<File> items = new ArrayList<>(clip);
        final boolean cut = clipCut;
        final File dest = cur;
        work(cut ? "Moving" : "Copying", () -> {
            for (File f : items) {
                if (!f.exists()) continue;
                if (cut) FileOps.move(f, dest); else FileOps.copy(f, dest);
            }
            if (cut) clip = new ArrayList<>();
        });
    }

    private void delete(final List<File> files) {
        final Runnable go = () -> work("Deleting", () -> { for (File f : files) FileOps.delete(f); });
        if (!InkPrefs.getBool(this, "confirmDelete", true)) { go.run(); return; }
        String what = files.size() == 1 ? "\"" + files.get(0).getName() + "\"" : files.size() + " items";
        Ui.confirm(this, "Delete?", what + " will be deleted for good. There is no trash.", "Delete", go);
    }

    private void rename(final File f) {
        Ui.prompt(this, "Rename", f.getName(), "Rename", name -> {
            if (name.isEmpty() || name.contains("/")) { toast("That name will not work"); return; }
            File to = new File(f.getParentFile(), name);
            if (to.exists()) { toast("Something with that name is already here"); return; }
            if (!f.renameTo(to)) toast("Could not rename it");
            sel.clear();
            refresh();
        });
    }

    private void newItem() {
        Ui.choose(this, "Create", new String[]{"New folder", "New file"}, -1, i -> {
            final boolean folder = i == 0;
            Ui.prompt(this, folder ? "New folder" : "New file", "", "Create", name -> {
                if (name.isEmpty() || name.contains("/")) return;
                File d = new File(cur, name);
                if (d.exists()) toast("That already exists");
                else try {
                    if (folder ? !d.mkdirs() : !d.createNewFile()) toast("Could not create it");
                } catch (IOException e) { toast("Could not create it: " + e.getMessage()); }
                refresh();
            });
        });
    }

    private void search(final File from, String title) {
        Ui.prompt(this, title, "", "Search", q -> {
            if (q.isEmpty()) return;
            final boolean hidden = InkPrefs.getBool(this, "hidden", false);
            toast("Searching…");
            new Thread(() -> {
                final List<File> found = FileOps.search(from, q, hidden, 500);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    results = found;
                    resultsFor = q;
                    refresh();
                    if (found.size() >= 500) toast("Showing the first 500 matches");
                });
            }).start();
        });
    }

    private void compress(final List<File> files) {
        final File out = FileOps.freeName(cur, (files.size() == 1 ? files.get(0).getName() : "Archive") + ".zip");
        work("Compressing", () -> FileOps.zip(files, out));
    }

    private void extract(final File zip) {
        work("Extracting", () -> FileOps.unzip(zip));
    }

    private void copyPath(File f) {
        ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("path", f.getAbsolutePath()));
        toast("Copied " + f.getAbsolutePath());
        sel.clear();
        refresh();
    }

    /** A home-screen icon that opens straight into a folder. */
    private void pinShortcut(File dir) {
        ShortcutManager sm = getSystemService(ShortcutManager.class);
        if (sm == null || !sm.isRequestPinShortcutSupported()) { toast("This launcher cannot add shortcuts"); return; }
        Intent open = new Intent(this, FilesActivity.class).setAction(Intent.ACTION_VIEW).putExtra("path", dir.getAbsolutePath());
        ShortcutInfo info = new ShortcutInfo.Builder(this, "dir:" + dir.getAbsolutePath()).setShortLabel(dir.getName()).setLongLabel(dir.getAbsolutePath())
                .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher)).setIntent(open).build();
        sm.requestPinShortcut(info, null);
        sel.clear();
        refresh();
    }

    private void details(final File f) {
        final long[][] m = {null};
        Thread t = new Thread(() -> m[0] = FileOps.measure(f));
        t.start();
        try { t.join(1500); } catch (InterruptedException ignored) {}
        String size = m[0] == null ? "Still counting…" : FileOps.humanSize(m[0][0]) + (f.isDirectory() ? ", " + (m[0][1] - 1) + " items inside" : "");
        String msg = f.getAbsolutePath() + "\n\nSize: " + size + "\nChanged: "
                + DateUtils.formatDateTime(this, f.lastModified(), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_YEAR)
                + (f.isDirectory() ? "" : "\nType: " + FileOps.mime(f))
                + "\nReadable: " + (f.canRead() ? "yes" : "no") + "   Writable: " + (f.canWrite() ? "yes" : "no");
        Ui.dialog(this).setTitle(f.getName()).setMessage(msg).setPositiveButton("Close", null).show();
    }

    @Override protected Class<? extends android.app.Activity> settingsClass() { return FilesSettings.class; }

    @Override protected boolean asksScreenType() { return true; }
}
