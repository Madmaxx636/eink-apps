package app.eink.core;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import java.util.Arrays;

/** Settings every app shares. An app with its own settings subclasses this and adds rows in extraRows(). */
public class SettingsActivity extends InkActivity {
    private PagedScroll pages;
    private final java.util.List<View> rows = new java.util.ArrayList<>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        pages = new PagedScroll(this);     // pages on e-ink, so swipes and the page buttons work here too
        setScreen("Settings", pages);
        addBarButton("Back", this::finish);
        build();
    }

    @Override protected void onLookChanged() { build(); super.onLookChanged(); }

    /** Override to add this app's own rows above the shared ones. */
    protected void extraRows(LinearLayout col) {}

    protected void add(View row) {
        rows.add(row);
        rows.add(Ui.divider(this));
    }

    @Override protected boolean onPage(int dir) { return pages.turn(dir); }

    /** An app's own Settings stay behind its lock, if it has one. */
    @Override protected boolean usesAppLock() { return true; }

    // ---- app lock settings, for apps that offer a lock ----

    private android.widget.EditText secretField(String hint, boolean pin) {
        android.widget.EditText e = new android.widget.EditText(this);
        e.setInputType(pin ? android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
                : android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setHint(hint);
        return e;
    }

    private void askCurrent(final Runnable ok) {
        final boolean pin = AppLock.PIN.equals(AppLock.kind(this));
        final android.widget.EditText e = secretField(pin ? "Current PIN" : "Current password", pin);
        LinearLayout box = new LinearLayout(this);
        int p = Display.dp(this, 16);
        box.setPadding(p, p / 2, p, 0);
        box.addView(e, new LinearLayout.LayoutParams(-1, -2));
        Ui.dialog(this).setTitle("Enter it to continue").setView(box).setPositiveButton("OK", (d, w) -> {
            if (AppLock.check(this, e.getText().toString())) ok.run();
            else android.widget.Toast.makeText(this, "That is not right", android.widget.Toast.LENGTH_LONG).show();
        }).setNegativeButton("Cancel", null).show();
    }

    private void askNew(final String kind) {
        final boolean pin = AppLock.PIN.equals(kind);
        final android.widget.EditText a = secretField(pin ? "New PIN (4 to 12 digits)" : "New password (at least 4 characters)", pin);
        final android.widget.EditText b = secretField(pin ? "Repeat the PIN" : "Repeat the password", pin);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Display.dp(this, 16);
        box.setPadding(p, p / 2, p, 0);
        box.addView(a, new LinearLayout.LayoutParams(-1, -2));
        box.addView(b, new LinearLayout.LayoutParams(-1, -2));
        Ui.dialog(this).setTitle(pin ? "Set a PIN" : "Set a password").setView(box).setPositiveButton("Set", (d, w) -> {
            String x = a.getText().toString(), y = b.getText().toString();
            String why = !x.equals(y) ? "The two entries do not match"
                    : pin && (x.length() < 4 || x.length() > 12) ? "Use 4 to 12 digits"
                    : !pin && x.length() < 4 ? "Use at least 4 characters" : null;
            if (why != null) { android.widget.Toast.makeText(this, why, android.widget.Toast.LENGTH_LONG).show(); return; }
            AppLock.set(this, kind, x);
            recreate();
        }).setNegativeButton("Cancel", null).show();
    }

    /** Rows for turning the app lock on and off, choosing how long it waits, and fingerprint or face. */
    protected void addLockRows() {
        final boolean on = AppLock.enabled(this);
        final boolean pin = AppLock.PIN.equals(AppLock.kind(this));
        add(Ui.row(this, "App lock", on ? "On: " + (pin ? "PIN" : "password") + ". Tap to change or remove it."
                : "Off. Protect this app with a PIN or a password.", () -> {
            if (!on) {
                Ui.choose(this, "Lock with", new String[]{"A PIN (numbers)", "A password (letters, numbers, symbols)"}, -1, i -> askNew(i == 0 ? AppLock.PIN : AppLock.PASSWORD));
            } else {
                Ui.choose(this, "App lock", new String[]{"Change the PIN or password", "Switch between PIN and password", "Remove the lock"}, -1, i -> {
                    if (i == 2) askCurrent(() -> { AppLock.clear(this); recreate(); });
                    else if (i == 0) askCurrent(() -> askNew(AppLock.kind(this)));
                    else askCurrent(() -> askNew(pin ? AppLock.PASSWORD : AppLock.PIN));
                });
            }
        }));
        if (!on) return;

        final int[] secs = {0, 30, 60, 300};
        final String[] secNames = {"Every time you open the app", "After 30 seconds away", "After 1 minute away", "After 5 minutes away"};
        int cur = AppLock.timeout(this), si = 1;
        for (int i = 0; i < secs.length; i++) if (secs[i] == cur) si = i;
        final int sel = si;
        add(Ui.row(this, "Ask again", secNames[si], () ->
                Ui.choose(this, "Ask again", secNames, sel, i -> { InkPrefs.put(this, "lockTimeout", secs[i]); recreate(); })));

        if (Biometrics.available(this)) {
            boolean bio = AppLock.biometricOn(this);
            add(Ui.row(this, "Unlock with fingerprint or face", bio ? "On. The " + (pin ? "PIN" : "password") + " is still there as a fallback." : "Off", () -> {
                InkPrefs.put(this, "lockBio", !AppLock.biometricOn(this));
                recreate();
            }));
        } else {
            add(Ui.row(this, "Unlock with fingerprint or face", android.os.Build.VERSION.SDK_INT < 29 ? "Needs Android 10 or later."
                    : "Set up a fingerprint or face in Android Settings first.", null));
        }
        boolean secure = InkPrefs.getBool(this, "lockSecure", false);
        add(Ui.row(this, "Hide in recent apps and screenshots", secure ? "On. This app's screens cannot be captured." : "Off", () -> {
            InkPrefs.put(this, "lockSecure", !InkPrefs.getBool(this, "lockSecure", false));
            recreate();
        }));
    }

    /** Swipe to the right to go back. */
    @Override protected boolean onSwipe(int dir) {
        if (dir < 0) { finish(); return true; }
        return false;
    }

    private void build() {
        rows.clear();
        extraRows(new LinearLayout(this));   // the argument is unused now; subclasses call add()

        final String[] sizes = {"compact", "comfortable"};
        final String[] sizeNames = {"Compact: smaller buttons and rows", "Comfortable: larger touch targets"};
        int zi = "comfortable".equals(InkPrefs.getStr(this, InkPrefs.UI_SIZE, "compact")) ? 1 : 0;
        add(Ui.row(this, "Interface size", sizeNames[zi], () ->
                Ui.choose(this, "Interface size", sizeNames, zi, i -> { InkPrefs.put(this, InkPrefs.UI_SIZE, sizes[i]); recreate(); })));

        final String[] styles = {"pill", "box", "underline", "none"};
        final String[] styleNames = {"Round (oval around the text)", "Box around the text", "Underlined text", "Text only, no shape"};
        int bi = Math.max(0, Arrays.asList(styles).indexOf(InkPrefs.getStr(this, InkPrefs.BTN_STYLE, "pill")));
        add(Ui.row(this, "Button style", styleNames[bi], () ->
                Ui.choose(this, "Button style", styleNames, bi, i -> { InkPrefs.put(this, InkPrefs.BTN_STYLE, styles[i]); recreate(); })));

        final String[] positions = {"top", "bottom"};
        final String[] positionNames = {"Top, beside the title", "Bottom, along the screen edge"};
        int pi = "bottom".equals(InkPrefs.getStr(this, InkPrefs.BAR_POS, "top")) ? 1 : 0;
        add(Ui.row(this, "Button position", positionNames[pi], () ->
                Ui.choose(this, "Button position", positionNames, pi, i -> { InkPrefs.put(this, InkPrefs.BAR_POS, positions[i]); recreate(); })));

        boolean icons = InkPrefs.getBool(this, InkPrefs.BTN_ICONS, true);
        add(Ui.row(this, "Icons on buttons", icons ? "On" : "Off", () -> {
            InkPrefs.put(this, InkPrefs.BTN_ICONS, !InkPrefs.getBool(this, InkPrefs.BTN_ICONS, true));
            recreate();
        }));

        final String[] modes = {"auto", "eink", "normal"};
        String reason = Display.detectReason();
        final String[] modeNames = {"Automatic (guess: " + (reason.isEmpty() ? "normal, nothing in the name says e-ink" : "e-ink, " + reason) + ")",
                "E-ink: black and white, pages, no animation", "Normal screen"};
        String cur = InkPrefs.getStr(this, InkPrefs.MODE, "auto");
        int ci = Math.max(0, Arrays.asList(modes).indexOf(cur));
        add(Ui.row(this, "Screen type", (ci == 0 ? "Automatic: " + (reason.isEmpty() ? "normal" : "e-ink") : modeNames[ci]) + "\nThis device: " + Display.describeDevice(), () ->
                Ui.choose(this, "Screen type", modeNames, ci, i -> { InkPrefs.put(this, InkPrefs.MODE, modes[i]); build(); recreate(); })));

        final String[] themes = {"light", "dark", "system"};
        final String[] themeNames = {"Light", "Dark", "Follow the system"};
        String th = InkPrefs.getStr(this, InkPrefs.THEME, Display.isEink(this) ? "light" : "system");
        int ti = Math.max(0, Arrays.asList(themes).indexOf(th));
        add(Ui.row(this, "Theme", themeNames[ti], () ->
                Ui.choose(this, "Theme", themeNames, ti, i -> { InkPrefs.put(this, InkPrefs.THEME, themes[i]); recreate(); })));

        final int[] scales = {80, 100, 120, 150, 180, 220};
        final String[] scaleNames = {"Small (80%)", "Normal (100%)", "Large (120%)", "Larger (150%)", "Big (180%)", "Biggest (220%)"};
        int cs = InkPrefs.getInt(this, InkPrefs.SCALE, 100), si = 1;
        for (int i = 0; i < scales.length; i++) if (scales[i] == cs) si = i;
        final int sel = si;
        add(Ui.row(this, "Text size", scaleNames[si], () ->
                Ui.choose(this, "Text size", scaleNames, sel, i -> { InkPrefs.put(this, InkPrefs.SCALE, scales[i]); recreate(); })));

        if (Display.isEink(this)) {
            final int[] every = {0, 1, 3, 5, 10, 20};
            final String[] everyNames = {"Never", "Every page", "Every 3 pages", "Every 5 pages", "Every 10 pages", "Every 20 pages"};
            int cf = InkPrefs.getInt(this, InkPrefs.FLASH_PAGES, 5), fi = 3;
            for (int i = 0; i < every.length; i++) if (every[i] == cf) fi = i;
            final int fsel = fi;
            add(Ui.row(this, "Clear ghosting", everyNames[fi], () ->
                    Ui.choose(this, "Flash the screen", everyNames, fsel, i -> { InkPrefs.put(this, InkPrefs.FLASH_PAGES, every[i]); build(); })));

            int ki = Math.max(0, Arrays.asList(Eink.STYLE_IDS).indexOf(InkPrefs.getStr(this, InkPrefs.FLASH_STYLE, "bw")));
            add(Ui.row(this, "Flash style", Eink.STYLE_NAMES[ki], () ->
                    Ui.choose(this, "Flash style", Eink.STYLE_NAMES, ki, i -> { InkPrefs.put(this, InkPrefs.FLASH_STYLE, Eink.STYLE_IDS[i]); build(); })));

            final String[] barModes = {"full", "number", "hidden"};
            final String[] barNames = {"Buttons and page number", "Page number only (tap it to bring the buttons back)", "Hidden: turn pages by swiping or the volume keys"};
            int bm = Math.max(0, java.util.Arrays.asList(barModes).indexOf(InkPrefs.getStr(this, InkPrefs.PAGE_BAR, "full")));
            add(Ui.row(this, "Page turner", barNames[bm], () ->
                    Ui.choose(this, "Page turner", barNames, bm, i -> { InkPrefs.put(this, InkPrefs.PAGE_BAR, barModes[i]); recreate(); })));

            final String[] pagePos = {"bottom", "top"};
            final String[] pagePosNames = {"Bottom of the list", "Top of the list"};
            int pp = "top".equals(InkPrefs.getStr(this, InkPrefs.PAGE_POS, "bottom")) ? 1 : 0;
            add(Ui.row(this, "Page buttons", pagePosNames[pp] + ". They show only when there is more than one page.", () ->
                    Ui.choose(this, "Page buttons", pagePosNames, pp, i -> { InkPrefs.put(this, InkPrefs.PAGE_POS, pagePos[i]); recreate(); })));

            add(Ui.row(this, "Refresh screen now", "Flash once to clear ghosting", () -> Eink.flash(this)));
        }

        boolean vk = InkPrefs.getBool(this, InkPrefs.VOLKEYS, false);
        add(Ui.row(this, "Volume keys turn pages", vk ? "On" : "Off", () -> {
            InkPrefs.put(this, InkPrefs.VOLKEYS, !InkPrefs.getBool(this, InkPrefs.VOLKEYS, false));
            build();
        }));
        add(Ui.row(this, "About", appName() + " " + version() + "\nFor e-ink screens, black and white or colour, and for normal screens.", this::showAbout));
        pages.setRows(new java.util.ArrayList<>(rows));
    }

    private String appName() { return getApplicationInfo().loadLabel(getPackageManager()).toString(); }

    private String version() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return ""; }
    }

    private void showAbout() {
        String text = "Made for e-ink screens, black and white or colour, and just as happy on normal screens, 3.5 inch and up.\n\n"
                + "No ads, no accounts, no tracking.\n\n"
                + "Source code and the other apps:\nhttps://github.com/Madmaxx636/eink-apps\n\n"
                + "Also from the same author, a home screen for e-ink:\nMinimal Launcher Proper\nhttps://github.com/Madmaxx636/minimal-launcher-proper";
        android.app.AlertDialog d = Ui.dialog(this).setTitle(appName() + " " + version()).setMessage(text).setPositiveButton("Close", null).create();
        d.show();
        android.widget.TextView t = d.findViewById(android.R.id.message);
        if (t != null) {
            android.text.util.Linkify.addLinks(t, android.text.util.Linkify.WEB_URLS);
            t.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        }
    }
}
