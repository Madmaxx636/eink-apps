package app.eink.clock;

import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.eink.core.Display;
import app.eink.core.InkPrefs;
import app.eink.core.Ui;

/** Stopwatch with laps. On e-ink it does not tick: tap the time to read it. */
final class WatchTab extends Tab {
    private TextView time, startBtn;
    private LinearLayout laps;

    WatchTab(ClockActivity a) { super(a); }

    private long accum() { return InkPrefs.sp(a).getLong("swAccum", 0); }
    private long started() { return InkPrefs.sp(a).getLong("swStarted", 0); }
    private boolean running() { return started() > 0; }

    private long elapsed() {
        long s = started(), now = SystemClock.elapsedRealtime();
        if (s > now) { // the phone rebooted while it was running; the start time no longer means anything
            InkPrefs.sp(a).edit().putLong("swStarted", 0).apply();
            return accum();
        }
        return accum() + (s > 0 ? now - s : 0);
    }

    @Override View build() {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = Display.dp(a, 12);
        col.setPadding(p, p, p, p);
        time = Ui.text(a, "00:00", 56, true);
        time.setGravity(Gravity.CENTER);
        time.setAutoSizeTextTypeUniformWithConfiguration(24, Math.round(Display.sp(a, 64)), 2, android.util.TypedValue.COMPLEX_UNIT_SP);
        time.setSingleLine(true);
        time.setPadding(0, p, 0, p);
        col.addView(time, new LinearLayout.LayoutParams(-1, -2));
        if (!a.live()) {
            TextView hint = Ui.dimText(a, "Live time is off. Tap the time to update it", 13);
            hint.setGravity(Gravity.CENTER);
            col.addView(hint, new LinearLayout.LayoutParams(-1, -2));
            time.setOnClickListener(v -> update(true));
        }
        LinearLayout btns = new LinearLayout(a);
        btns.setPadding(0, p, 0, p);
        startBtn = Ui.button(a, "Start", this::toggle);
        btns.addView(startBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        btns.addView(space());
        btns.addView(Ui.button(a, "Lap", this::lap), new LinearLayout.LayoutParams(0, -2, 1f));
        btns.addView(space());
        btns.addView(Ui.button(a, "Reset", this::reset), new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(btns, new LinearLayout.LayoutParams(-1, -2));
        ScrollView sv = new ScrollView(a);
        laps = new LinearLayout(a);
        laps.setOrientation(LinearLayout.VERTICAL);
        sv.addView(laps);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        update(true);
        return col;
    }

    private View space() { View v = new View(a); v.setLayoutParams(new LinearLayout.LayoutParams(Display.dp(a, 8), 1)); return v; }

    private String fmt(long ms) {
        long s = ms / 1000, m = s / 60, hr = m / 60;
        String frac = a.precision() == 1 ? String.format(Locale.ROOT, ".%d", ms % 1000 / 100) : a.precision() == 2 ? String.format(Locale.ROOT, ".%02d", ms % 1000 / 10) : "";
        return (hr > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", hr, m % 60, s % 60) : String.format(Locale.ROOT, "%02d:%02d", m, s % 60)) + frac;
    }

    private void update(boolean full) {
        if (time == null) return;
        time.setText(fmt(elapsed()));
        startBtn.setText(running() ? "Stop" : "Start");
        if (!full) return;
        laps.removeAllViews();
        String l = InkPrefs.getStr(a, "swLaps", "");
        if (l.isEmpty()) return;
        String[] parts = l.split(",");
        long prev = 0;
        List<View> rows = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            long t = Long.parseLong(parts[i]);
            rows.add(0, Ui.row(a, "Lap " + (i + 1) + "   " + fmt(t - prev), "Total " + fmt(t), null));
            prev = t;
        }
        for (View v : rows) { laps.addView(v); laps.addView(Ui.divider(a)); }
    }

    private void toggle() {
        long now = SystemClock.elapsedRealtime();
        if (running()) InkPrefs.sp(a).edit().putLong("swAccum", elapsed()).putLong("swStarted", 0).apply();
        else InkPrefs.sp(a).edit().putLong("swStarted", now).apply();
        update(true);
        a.rescheduleTicks();
    }

    private void lap() {
        if (elapsed() == 0) return;
        String l = InkPrefs.getStr(a, "swLaps", "");
        InkPrefs.put(a, "swLaps", (l.isEmpty() ? "" : l + ",") + elapsed());
        update(true);
    }

    private void reset() {
        InkPrefs.sp(a).edit().putLong("swAccum", 0).putLong("swStarted", 0).putString("swLaps", "").apply();
        update(true);
    }

    @Override void onShow() { update(true); }

    @Override void tick() { update(false); }

    /** Counts live while running: each second, tenth or hundredth of a second, aligned to the stopwatch's own time. */
    @Override long nextTick() {
        if (!a.live() || !running()) return -1;
        long step = a.precision() == 0 ? 1000 : a.precision() == 1 ? 100 : 40;
        return Math.max(16, step - elapsed() % step + 1);
    }
}
