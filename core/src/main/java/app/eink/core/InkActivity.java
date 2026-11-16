package app.eink.core;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Base for every screen in the suite: themed to the current display mode, with a title bar and
 * optional volume-key paging. Call setScreen() once from onCreate().
 */
public abstract class InkActivity extends Activity {
    protected LinearLayout bar;           // where action buttons go: beside the title, or along the bottom
    private LinearLayout titleRow, actionBar;
    private boolean bottom;
    private int themeStamp;

    /** Fingerprint of settings that change how the screen looks; used to redraw after Settings. */
    private int stamp() {
        return InkPrefs.sp(this).getAll().toString().hashCode() * 31 + (Display.isEink(this) ? 1 : 0);
    }

    protected void setScreen(String title, View body) {
        setTheme(R.style.InkTheme);
        int bg = Display.bg(this), fg = Display.fg(this);
        getWindow().getDecorView().setBackgroundColor(bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        boolean light = !Display.isDark(this);
        flags = light ? (flags | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
                : (flags & ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));
        getWindow().getDecorView().setSystemUiVisibility(flags);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        boolean compact = Display.compact(this);
        int p = Display.dp(this, compact ? 6 : 8);
        bottom = "bottom".equals(InkPrefs.getStr(this, InkPrefs.BAR_POS, "top"));

        titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        titleRow.setPadding(p, p / 2, p, p / 2);
        TextView t = Ui.text(this, title, compact ? 18 : 20, true);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(p, bottom ? p / 2 : 0, p, bottom ? p / 2 : 0);
        titleRow.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));
        root.addView(Ui.divider(this));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        if (bottom) {
            root.addView(Ui.divider(this));
            actionBar = new LinearLayout(this);
            actionBar.setOrientation(LinearLayout.HORIZONTAL);
            actionBar.setGravity(android.view.Gravity.CENTER);
            actionBar.setPadding(p / 2, p / 2, p / 2, p / 2);
            root.addView(actionBar, new LinearLayout.LayoutParams(-1, -2));
        }
        bar = bottom ? actionBar : titleRow;
        setContentView(root, new ViewGroup.LayoutParams(-1, -1));
        themeStamp = stamp();
        if (asksScreenType() && !InkPrefs.getBool(this, InkPrefs.ASKED_SCREEN, false)) root.post(this::askScreenType);
        // With the lock on, "hide in recent apps" also blocks screenshots of this screen.
        if (usesAppLock() && AppLock.enabled(this) && InkPrefs.getBool(this, "lockSecure", false)) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
    }

    /** Main screens return true so the first launch asks what kind of screen this is, instead of guessing. */
    protected boolean asksScreenType() { return false; }

    private void askScreenType() {
        if (isFinishing() || isDestroyed()) return;
        InkPrefs.put(this, InkPrefs.ASKED_SCREEN, true);
        String guess = Display.detectedEink() ? "e-ink" : "a normal screen";
        Ui.dialog(this).setTitle("What kind of screen is this?")
                .setMessage("This app looks different on e-ink (black and white, pages instead of scrolling, no animation) and on a normal screen. "
                        + "From the device name this looks like " + guess + ", but that can be wrong.\n\nYou can change it later in Settings.")
                .setPositiveButton("E-ink", (d, w) -> { InkPrefs.put(this, InkPrefs.MODE, "eink"); recreate(); })
                .setNegativeButton("Normal", (d, w) -> { InkPrefs.put(this, InkPrefs.MODE, "normal"); recreate(); })
                .setNeutralButton("Guess for me", null)
                .setCancelable(false)
                .show();
    }

    /**
     * A button in the action bar, which Settings puts at the top (beside the title) or at the bottom (equal
     * buttons, icon above the label). Up top, three or more buttons drop their words and keep the icons so
     * the title still fits.
     */
    protected TextView addBarButton(String label, Runnable onClick) {
        TextView b = Ui.button(this, label, onClick);
        IconButton ib = (IconButton) b;
        b.setMinWidth(0);
        b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, Display.sp(this, bottom ? 11 : 13));
        if (bottom) {
            ib.setVertical(true);
            b.setPadding(Display.dp(this, 2), Display.dp(this, 2), Display.dp(this, 2), Display.dp(this, 2));
            b.setMinHeight(0);
            b.setSingleLine(true);
            b.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(Display.dp(this, 3), 0, Display.dp(this, 3), 0);
            bar.addView(b, lp);
        } else {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = Display.dp(this, 6);
            bar.addView(b, lp);
            int n = 0;
            for (int i = 0; i < bar.getChildCount(); i++) if (bar.getChildAt(i) instanceof IconButton) n++;
            if (n >= 3) for (int i = 0; i < bar.getChildCount(); i++)
                if (bar.getChildAt(i) instanceof IconButton && ((IconButton) bar.getChildAt(i)).hasIcon()) ((IconButton) bar.getChildAt(i)).setIconOnly(true);
        }
        return b;
    }

    protected void addSettingsButton() {
        addBarButton("Settings", () -> startActivity(new Intent(this, settingsClass())));
    }

    /** Apps with settings of their own return their subclass of SettingsActivity here. */
    protected Class<? extends Activity> settingsClass() { return SettingsActivity.class; }

    // ---- sideways swipes, for every app ----

    private float swipeX, swipeY;
    private boolean swipeBlocked;

    /**
     * A swipe across the screen: dir is 1 for a swipe to the left (go forward, like turning a page) and -1 to the
     * right (go back). Apps use it for months, cities, tabs and folders. Return true if it did something.
     */
    protected boolean onSwipe(int dir) { return false; }

    /** A touch that starts on something that scrolls or edits sideways is that thing's, not a page swipe. */
    private static boolean ownsSideways(View v, float x, float y) {
        if (v.getVisibility() != View.VISIBLE) return false;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        if (x < loc[0] || x > loc[0] + v.getWidth() || y < loc[1] || y > loc[1] + v.getHeight()) return false;
        if (v instanceof android.widget.EditText || v instanceof android.widget.AbsSeekBar || v instanceof android.widget.NumberPicker) return true;
        if (v instanceof android.widget.HorizontalScrollView && (v.canScrollHorizontally(1) || v.canScrollHorizontally(-1)
                || (((android.widget.HorizontalScrollView) v).getChildCount() > 0
                && ((android.widget.HorizontalScrollView) v).getChildAt(0).getWidth() > v.getWidth()))) return true;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) if (ownsSideways(g.getChildAt(i), x, y)) return true;
        }
        return false;
    }

    @Override public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        if (lockView != null) return super.dispatchTouchEvent(e);          // a locked screen takes no swipes
        if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
            swipeX = e.getRawX();
            swipeY = e.getRawY();
            swipeBlocked = ownsSideways(getWindow().getDecorView(), e.getRawX(), e.getRawY());
        }
        boolean handled = super.dispatchTouchEvent(e);
        if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP && !swipeBlocked) {
            float dx = e.getRawX() - swipeX, dy = e.getRawY() - swipeY;
            if (Math.abs(dx) > Display.dp(this, 72) && Math.abs(dx) > Math.abs(dy) * 2) onSwipe(dx < 0 ? 1 : -1);
        }
        return handled;
    }

    // ---- app lock ----

    /** An app returns true for screens that must stay behind its lock, if one is set in its Settings. */
    protected boolean usesAppLock() { return false; }

    private android.widget.FrameLayout lockView;
    private android.os.CancellationSignal lockBio;
    private boolean askingSecret;

    private void showLock() {
        if (lockView != null) return;
        final android.view.ViewGroup content = findViewById(android.R.id.content);
        if (content == null) return;
        lockView = new android.widget.FrameLayout(this);
        lockView.setBackgroundColor(Display.bg(this));
        lockView.setClickable(true);                       // nothing underneath can be touched
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(android.view.Gravity.CENTER);
        int p = Display.dp(this, 24);
        col.setPadding(p, p, p, p);
        col.addView(Ui.text(this, "Locked", 24, true));
        TextView sub = Ui.dimText(this, "Unlock to continue.", 14);
        sub.setPadding(0, Display.dp(this, 6), 0, Display.dp(this, 16));
        col.addView(sub);
        boolean bio = AppLock.biometricOn(this) && Biometrics.available(this);
        if (bio) {
            TextView b = Ui.button(this, "Use fingerprint or face", this::lockBiometric);
            ((IconButton) b).setNoIcon(true);
            col.addView(b, new LinearLayout.LayoutParams(-2, -2));
        }
        TextView secret = Ui.button(this, AppLock.PIN.equals(AppLock.kind(this)) ? "Enter PIN" : "Enter password", this::lockAskSecret);
        ((IconButton) secret).setNoIcon(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = Display.dp(this, 8);
        col.addView(secret, lp);
        TextView exit = Ui.button(this, "Exit", this::finish);
        ((IconButton) exit).setNoIcon(true);
        LinearLayout.LayoutParams le = new LinearLayout.LayoutParams(-2, -2);
        le.topMargin = Display.dp(this, 8);
        col.addView(exit, le);
        lockView.addView(col, new android.widget.FrameLayout.LayoutParams(-1, -1));
        content.addView(lockView, new android.widget.FrameLayout.LayoutParams(-1, -1));
        if (bio) lockBiometric(); else lockAskSecret();
    }

    private void unlockNow() {
        AppLock.markUnlocked();
        if (lockBio != null) { lockBio.cancel(); lockBio = null; }
        if (lockView != null && lockView.getParent() instanceof ViewGroup) ((ViewGroup) lockView.getParent()).removeView(lockView);
        lockView = null;
    }

    private void lockBiometric() {
        if (lockBio != null || android.os.Build.VERSION.SDK_INT < 29) return;
        lockBio = Biometrics.prompt(this, "Unlock", "Use " + (AppLock.PIN.equals(AppLock.kind(this)) ? "PIN" : "password"), new Biometrics.Result() {
            @Override public void ok() { lockBio = null; unlockNow(); }
            @Override public void usePin(String why) { lockBio = null; if (lockView != null && !isFinishing()) lockAskSecret(); }
            @Override public void dismissed() { lockBio = null; }
        });
    }

    private void lockAskSecret() {
        if (askingSecret || isFinishing()) return;
        askingSecret = true;
        final boolean pin = AppLock.PIN.equals(AppLock.kind(this));
        final android.widget.EditText e = new android.widget.EditText(this);
        e.setInputType(pin ? android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
                : android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setHint(pin ? "PIN" : "Password");
        LinearLayout box = new LinearLayout(this);
        int p = Display.dp(this, 16);
        box.setPadding(p, p / 2, p, 0);
        box.addView(e, new LinearLayout.LayoutParams(-1, -2));
        Ui.dialog(this).setTitle(pin ? "Enter your PIN" : "Enter your password").setView(box).setCancelable(false)
                .setPositiveButton("Unlock", (d, w) -> {
                    askingSecret = false;
                    if (AppLock.check(this, e.getText().toString())) unlockNow();
                    else { android.widget.Toast.makeText(this, pin ? "Wrong PIN" : "Wrong password", android.widget.Toast.LENGTH_SHORT).show(); lockAskSecret(); }
                })
                .setNegativeButton("Exit", (d, w) -> { askingSecret = false; finish(); }).show();
    }

    /** Volume keys turn pages when the setting is on. Return true if you handled the turn. */
    protected boolean onPage(int dir) { return false; }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (InkPrefs.getBool(this, InkPrefs.VOLKEYS, false)) {
            if (code == KeyEvent.KEYCODE_VOLUME_DOWN) { onPage(1); return true; }
            if (code == KeyEvent.KEYCODE_VOLUME_UP) { onPage(-1); return true; }
        }
        return super.onKeyDown(code, e);
    }

    @Override public boolean onKeyUp(int code, KeyEvent e) {
        if (InkPrefs.getBool(this, InkPrefs.VOLKEYS, false)
                && (code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_UP)) return true;
        return super.onKeyUp(code, e);
    }

    /** Called when the screen comes back and the look has changed (mode, theme or text size). Default: rebuild. */
    protected void onLookChanged() { recreate(); }

    @Override protected void onPause() {
        if (usesAppLock() && lockView == null) AppLock.touch();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (themeStamp != 0 && themeStamp != stamp()) onLookChanged();
        if (usesAppLock() && AppLock.needed(this)) showLock();
    }
}
