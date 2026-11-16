package app.eink.clock;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.eink.core.Display;
import app.eink.core.PagedScroll;
import app.eink.core.Ui;

/** The list of alarms, each with an on/off switch. Tap one to change it. */
final class AlarmsTab extends Tab {
    private PagedScroll list;

    AlarmsTab(ClockActivity a) { super(a); }

    @Override View build() {
        list = new PagedScroll(a);
        refresh();
        return list;
    }

    @Override void onShow() { refresh(); }

    @Override boolean onPage(int dir) { return list != null && list.turn(dir); }

    static String[] dayNames() {
        String[] s = new DateFormatSymbols(Locale.getDefault()).getShortWeekdays();   // index 1 is Sunday
        String[] out = new String[7];
        for (int i = 0; i < 7; i++) out[i] = s[i + 1];
        return out;
    }

    private void refresh() {
        List<View> rows = new ArrayList<>();
        rows.add(Ui.button(a, "+  New alarm", () -> edit(0)));

        // Things Android needs from you before alarms can wake the screen.
        if (Build.VERSION.SDK_INT >= 33 && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            rows.add(Ui.row(a, "Allow notifications", "Alarms show a notification with Snooze and Dismiss. Tap to allow.",
                    () -> a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 5)));
        if (Build.VERSION.SDK_INT >= 34 && !((NotificationManager) a.getSystemService(android.content.Context.NOTIFICATION_SERVICE)).canUseFullScreenIntent())
            rows.add(Ui.row(a, "Allow full-screen alarms", "Needed to show the alarm over the lock screen. Tap to open the setting.",
                    () -> a.startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + a.getPackageName())))));

        String[] names = dayNames();
        List<Alarm> alarms = AlarmStore.all(a);
        if (alarms.isEmpty()) rows.add(Ui.row(a, "No alarms", "Tap New alarm to set one", null));
        for (final Alarm al : alarms) {
            rows.add(Ui.divider(a));
            LinearLayout r = new LinearLayout(a);
            r.setGravity(Gravity.CENTER_VERTICAL);
            int p = Display.dp(a, 12);
            r.setPadding(p, Display.dp(a, 8), p, Display.dp(a, 8));
            LinearLayout col = new LinearLayout(a);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView t = Ui.text(a, time(al), 30, true);
            col.addView(t);
            String sub = al.daysText(names) + (al.label.isEmpty() ? "" : "  ·  " + al.label);
            col.addView(Ui.dimText(a, sub, 14));
            col.setClickable(true);
            col.setOnClickListener(v -> edit(al.id));
            r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            final TextView sw = Ui.button(a, al.enabled ? "On" : "Off", null);
            sw.setMinWidth(Display.dp(a, 64));
            Ui.style(sw, al.enabled);
            sw.setOnClickListener(v -> {
                al.enabled = !al.enabled;
                AlarmStore.save(a, al);
                Scheduler.schedule(a, al);
                if (al.enabled) a.toastNext(al);
                refresh();
            });
            r.addView(sw, new LinearLayout.LayoutParams(-2, -2));
            rows.add(r);
        }
        list.setRows(rows);
    }

    private String time(Alarm al) {
        if (a.is24()) return String.format(Locale.ROOT, "%02d:%02d", al.hour, al.minute);
        int h = al.hour % 12 == 0 ? 12 : al.hour % 12;
        return String.format(Locale.ROOT, "%d:%02d %s", h, al.minute, al.hour < 12 ? "AM" : "PM");
    }

    private void edit(int id) {
        a.startActivity(new Intent(a, AlarmEditActivity.class).putExtra("id", id));
    }
}
