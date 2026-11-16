package app.eink.clock;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import app.eink.core.Display;
import app.eink.core.InkPrefs;
import app.eink.core.Ui;

/** Big clock: digital, analog or both, with the date, the next alarm and the battery. */
final class ClockTab extends Tab {
    private TextView timeView, ampmView, dateView, alarmView, batteryView;
    private AnalogView analog;
    private boolean seconds;

    ClockTab(ClockActivity a) { super(a); }

    @Override View build() {
        seconds = a.live() && InkPrefs.getBool(a, "seconds", true);
        String style = InkPrefs.getStr(a, "style", "digital");
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        int p = Display.dp(a, 12);
        col.setPadding(p, p, p, p);

        if (!style.equals("digital")) {
            analog = new AnalogView(a);
            analog.setShowSeconds(seconds);
            int size = Math.min(Display.dp(a, style.equals("both") ? 200 : 300), a.getResources().getDisplayMetrics().widthPixels - 4 * p);
            size = Math.min(size, (int) (a.getResources().getDisplayMetrics().heightPixels * (style.equals("both") ? 0.3f : 0.5f)));
            col.addView(analog, new LinearLayout.LayoutParams(size, size));
        } else analog = null;

        if (!style.equals("analog")) {
            LinearLayout line = new LinearLayout(a);
            line.setGravity(Gravity.CENTER | Gravity.BOTTOM);
            timeView = Ui.text(a, "", 72, true);
            timeView.setSingleLine(true);
            timeView.setAutoSizeTextTypeUniformWithConfiguration(24, Math.round(Display.sp(a, 80)), 2, TypedValue.COMPLEX_UNIT_SP);
            ampmView = Ui.text(a, "", 20, true);
            ampmView.setPadding(Display.dp(a, 6), 0, 0, Display.dp(a, 10));
            line.addView(timeView, new LinearLayout.LayoutParams(-2, -2));
            line.addView(ampmView, new LinearLayout.LayoutParams(-2, -2));
            col.addView(line, new LinearLayout.LayoutParams(-1, -2));
        } else { timeView = null; ampmView = null; }

        dateView = Ui.text(a, "", 22, false);
        dateView.setGravity(Gravity.CENTER);
        col.addView(dateView, new LinearLayout.LayoutParams(-1, -2));

        alarmView = Ui.dimText(a, "", 16);
        alarmView.setGravity(Gravity.CENTER);
        alarmView.setPadding(0, Display.dp(a, 8), 0, Display.dp(a, 8));
        alarmView.setClickable(true);
        alarmView.setOnClickListener(v -> a.showTab(ClockActivity.TAB_ALARMS));
        col.addView(alarmView, new LinearLayout.LayoutParams(-1, -2));

        batteryView = Ui.dimText(a, "", 16);
        batteryView.setGravity(Gravity.CENTER);
        col.addView(batteryView, new LinearLayout.LayoutParams(-1, -2));
        tick();
        return col;
    }

    @Override void tick() {
        if (dateView == null) return;
        Date now = new Date();
        boolean h24 = a.is24();
        if (timeView != null) {
            timeView.setText(new SimpleDateFormat(h24 ? (seconds ? "HH:mm:ss" : "HH:mm") : (seconds ? "h:mm:ss" : "h:mm"), Locale.getDefault()).format(now));
            ampmView.setText(h24 ? "" : new SimpleDateFormat("a", Locale.getDefault()).format(now));
        }
        if (analog != null) analog.invalidate();
        dateView.setText(DateUtils.formatDateTime(a, now.getTime(), DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR));

        long next = 0;
        List<Alarm> list = AlarmStore.all(a);
        for (Alarm al : list) if (al.enabled) { long t = al.nextTrigger(now.getTime()); if (next == 0 || t < next) next = t; }
        alarmView.setText(next == 0 ? "No alarm set" : "Next alarm  " + DateUtils.formatDateTime(a, next,
                DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_WEEKDAY | DateUtils.FORMAT_SHOW_TIME));

        Intent bat = a.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bat != null) {
            int lvl = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), sc = bat.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int st = bat.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean chg = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
            batteryView.setText("Battery " + (lvl * 100 / Math.max(1, sc)) + "%" + (chg ? ", charging" : ""));
        }
    }

    /** On the minute, or the second if seconds are shown, so the clock is never late. */
    @Override long nextTick() {
        long now = System.currentTimeMillis(), step = seconds ? 1000 : 60000;
        return step - now % step + 50;
    }
}
