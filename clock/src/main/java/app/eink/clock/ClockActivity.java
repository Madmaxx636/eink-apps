package app.eink.clock;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import app.eink.core.Display;
import app.eink.core.InkActivity;
import app.eink.core.InkPrefs;
import app.eink.core.Ui;

/** Clock, alarms, timer, stopwatch and world clock in one app. Nothing here hands off to another app. */
public class ClockActivity extends InkActivity {
    static final int TAB_CLOCK = 0, TAB_ALARMS = 1, TAB_TIMER = 2, TAB_WATCH = 3, TAB_WORLD = 4;
    private static final String[] NAMES = {"Clock", "Alarms", "Timer", "Watch", "World"};

    private final Handler h = new Handler(Looper.getMainLooper());
    private FrameLayout body;
    private LinearLayout tabs;
    private final Tab[] all = new Tab[5];
    private int tab;
    boolean eink;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            Tab t = all[tab];
            t.tick();
            long next = t.nextTick();
            if (next >= 0) h.postDelayed(this, next);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        eink = Display.isEink(this);
        tab = b != null ? b.getInt("tab", TAB_CLOCK) : getIntent().getIntExtra("tab", TAB_CLOCK);
        all[TAB_CLOCK] = new ClockTab(this);
        all[TAB_ALARMS] = new AlarmsTab(this);
        all[TAB_TIMER] = new TimerTab(this);
        all[TAB_WATCH] = new WatchTab(this);
        all[TAB_WORLD] = new WorldTab(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        tabs = new LinearLayout(this);
        int p = Display.dp(this, 4);
        tabs.setPadding(p, p, p, p);
        body = new FrameLayout(this);
        boolean tabsBottom = "bottom".equals(InkPrefs.getStr(this, "tabsPos", "top"));
        if (!tabsBottom) root.addView(tabs, new LinearLayout.LayoutParams(-1, -2));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        if (tabsBottom) { root.addView(Ui.divider(this)); root.addView(tabs, new LinearLayout.LayoutParams(-1, -2)); }
        setScreen("Clock", root);
        addSettingsButton();
        showTab(tab);
    }

    @Override protected Class<? extends android.app.Activity> settingsClass() { return ClockSettings.class; }

    @Override protected void onSaveInstanceState(Bundle o) {
        super.onSaveInstanceState(o);
        o.putInt("tab", tab);
    }

    @Override protected void onNewIntent(android.content.Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        if (i.hasExtra("tab")) showTab(i.getIntExtra("tab", tab));
    }

    @Override protected void onResume() {
        super.onResume();
        all[tab].onShow();
        rescheduleTicks();
    }

    @Override protected void onPause() {
        h.removeCallbacks(tick);
        super.onPause();
    }

    @Override protected boolean onPage(int dir) { return all[tab].onPage(dir); }

    void rescheduleTicks() {
        h.removeCallbacks(tick);
        h.post(tick);
    }

    void showTab(int t) {
        tab = t;
        h.removeCallbacks(tick);
        tabs.removeAllViews();
        for (int i = 0; i < NAMES.length; i++) {
            final int idx = i;
            TextView b = Ui.button(this, NAMES[i], () -> { if (tab != idx) showTab(idx); });
            b.setMinWidth(0);
            b.setMinHeight(0);
            b.setPadding(0, Display.dp(this, 5), 0, Display.dp(this, 5));
            b.setSingleLine(true);
            b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, Display.sp(this, 12));
            ((app.eink.core.IconButton) b).setVertical(true);   // icon above the word, so five tabs fit
            Ui.style(b, i == t);      // the current tab is drawn filled
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);   // as tall as the icon and word need, so the label is never clipped
            lp.setMargins(Display.dp(this, 2), 0, Display.dp(this, 2), 0);
            tabs.addView(b, lp);
        }
        body.removeAllViews();
        View v = all[t].build();
        body.addView(v, new FrameLayout.LayoutParams(-1, -1));
        h.post(tick);
    }

    /** Live time: the clock shows seconds and the stopwatch and timer count on screen. Off means update on the minute or when tapped. */
    boolean live() { return InkPrefs.getBool(this, "live", true); }

    /** 0 whole seconds, 1 tenths, 2 hundredths. */
    int precision() { return InkPrefs.getInt(this, "swPrec", eink ? 0 : 1); }

    boolean is24() {
        String m = InkPrefs.getStr(this, "hours", "system");
        return m.equals("24") || (m.equals("system") && DateFormat.is24HourFormat(this));
    }

    /** Alarms and timers show a notification when they ring; Android 13 and later asks first. */
    void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 5);
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (tab == TAB_ALARMS) all[tab].onShow();
    }

    void toastNext(Alarm al) { toastIn(this, al); }

    /** "Alarm set for 7 hours 30 minutes from now". */
    static void toastIn(Context c, Alarm al) {
        long mins = Math.max(1, (al.nextTrigger(System.currentTimeMillis()) - System.currentTimeMillis() + 59999) / 60000);
        long d = mins / 1440, hr = mins / 60 % 24, m = mins % 60;
        StringBuilder b = new StringBuilder("Alarm set for ");
        if (d > 0) b.append(d).append(d == 1 ? " day " : " days ");
        if (hr > 0) b.append(hr).append(hr == 1 ? " hour " : " hours ");
        if (m > 0 && d == 0) b.append(m).append(m == 1 ? " minute " : " minutes ");
        Toast.makeText(c, b.toString().trim() + " from now", Toast.LENGTH_LONG).show();
    }

    @Override protected void onLookChanged() { recreate(); }

    /** Swipe left for the next tab, right for the one before. */
    @Override protected boolean onSwipe(int dir) {
        int to = tab + dir;
        if (to < 0 || to >= NAMES.length) return false;
        showTab(to);
        return true;
    }

    @Override protected boolean asksScreenType() { return true; }
}
