package app.eink.clock;

import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import app.eink.core.Display;
import app.eink.core.InkPrefs;
import app.eink.core.Ui;

/** A countdown that rings through the same alarm service, so it works with the screen off and the app closed. */
final class TimerTab extends Tab {
    private LinearLayout body;
    private TextView big, ends;

    TimerTab(ClockActivity a) { super(a); }

    private long end() { return InkPrefs.sp(a).getLong("timerEnd", 0); }
    private long left() { return InkPrefs.sp(a).getLong("timerLeft", 0); }      // time remaining while paused
    private long total() { return InkPrefs.sp(a).getLong("timerTotal", 0); }
    private boolean running() { return end() > System.currentTimeMillis(); }
    private boolean paused() { return !running() && left() > 0; }

    @Override View build() {
        body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        int p = Display.dp(a, 14);
        body.setPadding(p, p, p, p);
        draw();
        return body;
    }

    @Override void onShow() { draw(); }

    private static String fmt(long ms) {
        long s = (ms + 999) / 1000, h = s / 3600, m = s / 60 % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s % 60) : String.format(Locale.ROOT, "%02d:%02d", m, s % 60);
    }

    private void draw() {
        body.removeAllViews();
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Display.dp(a, 12);
        if (!running() && !paused()) { drawSetup(); return; }

        big = Ui.text(a, "", 64, true);
        big.setGravity(Gravity.CENTER);
        big.setSingleLine(true);
        big.setAutoSizeTextTypeUniformWithConfiguration(24, Math.round(Display.sp(a, 72)), 2, TypedValue.COMPLEX_UNIT_SP);
        big.setPadding(0, p * 2, 0, p);
        body.addView(big, new LinearLayout.LayoutParams(-1, -2));
        ends = Ui.dimText(a, "", 16);
        ends.setGravity(Gravity.CENTER);
        body.addView(ends, new LinearLayout.LayoutParams(-1, -2));
        if (!a.live()) {
            TextView hint = Ui.dimText(a, "Live time is off. Tap the time to update it", 13);
            hint.setGravity(Gravity.CENTER);
            body.addView(hint, new LinearLayout.LayoutParams(-1, -2));
            big.setOnClickListener(v -> tick());
        }
        LinearLayout btns = new LinearLayout(a);
        btns.setPadding(0, p * 2, 0, 0);
        btns.addView(Ui.button(a, running() ? "Pause" : "Resume", running() ? this::pause : this::resume), w());
        btns.addView(gap());
        btns.addView(Ui.button(a, "+1 min", () -> add(60000)), w());
        btns.addView(gap());
        btns.addView(Ui.button(a, "Cancel", this::cancel), w());
        body.addView(btns, new LinearLayout.LayoutParams(-1, -2));
        tick();
    }

    private LinearLayout.LayoutParams w() { return new LinearLayout.LayoutParams(0, -2, 1f); }

    private View gap() { View v = new View(a); v.setLayoutParams(new LinearLayout.LayoutParams(Display.dp(a, 8), 1)); return v; }

    private void drawSetup() {
        int p = Display.dp(a, 12);
        TextView t = Ui.text(a, "Start a timer", 20, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, p, 0, p);
        body.addView(t, new LinearLayout.LayoutParams(-1, -2));
        int[] mins = {1, 3, 5, 10, 15, 30};
        LinearLayout row = null;
        for (int i = 0; i < mins.length; i++) {
            if (i % 3 == 0) { row = new LinearLayout(a); row.setPadding(0, 0, 0, Display.dp(a, 8)); body.addView(row, new LinearLayout.LayoutParams(-1, -2)); }
            final int m = mins[i];
            TextView b = Ui.button(a, m + " min", () -> start(m * 60000L));
            b.setMinHeight(Display.dp(a, 56));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.rightMargin = Display.dp(a, 6);
            row.addView(b, lp);
        }
        TextView custom = Ui.button(a, "Set your own time", this::custom);
        custom.setMinHeight(Display.dp(a, 56));
        Ui.style(custom, true);
        body.addView(custom, new LinearLayout.LayoutParams(-1, -2));
    }

    private void custom() {
        LinearLayout box = new LinearLayout(a);
        box.setGravity(Gravity.CENTER);
        final NumberPicker h = picker(0, 23, 0), m = picker(0, 59, 5), s = picker(0, 59, 0);
        for (NumberPicker np : new NumberPicker[]{h, m, s}) box.addView(np, new LinearLayout.LayoutParams(Display.dp(a, 72), -2));
        TextView l = Ui.dimText(a, "hours       minutes      seconds", 12);
        LinearLayout wrap = new LinearLayout(a);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(Gravity.CENTER_HORIZONTAL);
        wrap.addView(box);
        wrap.addView(l);
        Ui.dialog(a).setTitle("Timer length").setView(wrap).setPositiveButton("Start", (d, w) -> {
            long ms = ((h.getValue() * 60L + m.getValue()) * 60 + s.getValue()) * 1000;
            if (ms > 0) start(ms);
        }).setNegativeButton("Cancel", null).show();
    }

    private NumberPicker picker(int min, int max, int val) {
        NumberPicker np = new NumberPicker(a);
        np.setMinValue(min);
        np.setMaxValue(max);
        np.setValue(val);
        np.setFormatter(v -> String.format(Locale.ROOT, "%02d", v));
        return np;
    }

    private void start(long ms) {
        long end = System.currentTimeMillis() + ms;
        InkPrefs.sp(a).edit().putLong("timerEnd", end).putLong("timerLeft", 0).putLong("timerTotal", ms).apply();
        Scheduler.setTimer(a, end);
        a.askNotifications();
        draw();
        a.rescheduleTicks();
    }

    private void pause() {
        long remain = Math.max(1000, end() - System.currentTimeMillis());
        Scheduler.cancelTimer(a);
        InkPrefs.sp(a).edit().putLong("timerEnd", 0).putLong("timerLeft", remain).apply();
        draw();
    }

    private void resume() {
        start(left());
    }

    private void add(long ms) {
        if (running()) start(end() - System.currentTimeMillis() + ms);
        else { InkPrefs.sp(a).edit().putLong("timerLeft", left() + ms).apply(); draw(); }
    }

    private void cancel() {
        Scheduler.cancelTimer(a);
        InkPrefs.sp(a).edit().putLong("timerEnd", 0).putLong("timerLeft", 0).apply();
        draw();
    }

    @Override void tick() {
        if (big == null) return;
        if (!running() && !paused()) { draw(); return; }   // it finished while this screen was open
        long remain = running() ? end() - System.currentTimeMillis() : left();
        big.setText(fmt(remain));
        ends.setText(running() ? "Rings at " + new SimpleDateFormat(a.is24() ? "HH:mm" : "h:mm a", Locale.getDefault()).format(new Date(end())) : "Paused");
    }

    /** With live time on it counts every second. Off, it refreshes each minute, or when you tap. */
    @Override long nextTick() {
        if (!running()) return -1;
        long now = System.currentTimeMillis(), step = a.live() ? 1000 : 60000;
        long t = step - now % step + 5;
        return Math.min(t, Math.max(50, end() - now + 100));   // always wake right as it ends
    }
}
