package app.eink.clock;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;

import app.eink.core.Display;
import app.eink.core.InkActivity;
import app.eink.core.Ui;

/** One alarm: time, days, label, sound and vibration. */
public class AlarmEditActivity extends InkActivity {
    private static final int REQ_SOUND = 51;

    private Alarm alarm;
    private TextView timeBtn, soundRow;
    private final TextView[] dayBtns = new TextView[7];
    private EditText label;
    private LinearLayout col;
    private View vibrateRow;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        int id = getIntent().getIntExtra("id", 0);
        alarm = id == 0 ? null : AlarmStore.get(this, id);
        if (alarm == null) {
            alarm = new Alarm();
            Calendar c = Calendar.getInstance();
            alarm.hour = (c.get(Calendar.HOUR_OF_DAY) + 1) % 24;   // an hour from now is a better start than midnight
        }
        if (b != null) { alarm.hour = b.getInt("h", alarm.hour); alarm.minute = b.getInt("m", alarm.minute); alarm.days = b.getInt("d", alarm.days); }

        ScrollView sv = new ScrollView(this);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = Display.dp(this, 14);
        col.setPadding(p, p, p, p);
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));

        timeBtn = Ui.button(this, "", this::pickTime);
        timeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, Display.sp(this, 44));
        timeBtn.setMinHeight(Display.dp(this, 90));
        timeBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        col.addView(timeBtn, new LinearLayout.LayoutParams(-1, -2));

        col.addView(Ui.dimText(this, "Repeat", 14), margin(14, 6));
        LinearLayout days = new LinearLayout(this);
        String[] names = AlarmsTab.dayNames();
        int first = Calendar.getInstance().getFirstDayOfWeek() - 1;     // 0 is Sunday
        for (int k = 0; k < 7; k++) {
            final int day = (first + k) % 7;
            String n = names[day];
            TextView d = Ui.button(this, n.length() > 2 ? n.substring(0, 2) : n, () -> { alarm.days ^= 1 << day; drawDays(); });
            d.setMinWidth(0);
            d.setPadding(0, 0, 0, 0);
            d.setTextSize(TypedValue.COMPLEX_UNIT_SP, Display.sp(this, 13));
            dayBtns[day] = d;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Display.dp(this, 44), 1f);
            lp.rightMargin = Display.dp(this, 3);
            days.addView(d, lp);
        }
        col.addView(days, new LinearLayout.LayoutParams(-1, -2));
        col.addView(Ui.dimText(this, "None selected means it rings once.", 12), margin(2, 8));

        col.addView(Ui.dimText(this, "Label", 14), margin(8, 2));
        label = new EditText(this);
        label.setSingleLine(true);
        label.setHint("Alarm");
        label.setText(alarm.label);
        label.setTextColor(Display.fg(this));
        label.setHintTextColor(Display.dim(this));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, Display.sp(this, 17));
        col.addView(label, new LinearLayout.LayoutParams(-1, -2));

        col.addView(Ui.divider(this), margin(8, 0));
        soundRow = new TextView(this);
        col.addView(row("Sound", "", this::pickSound), new LinearLayout.LayoutParams(-1, -2));
        vibrateRow = row("Vibrate", "", () -> { alarm.vibrate = !alarm.vibrate; refreshRows(); });
        col.addView(vibrateRow, new LinearLayout.LayoutParams(-1, -2));
        col.addView(Ui.divider(this));
        if (alarm.id != 0) {
            col.addView(Ui.row(this, "Delete this alarm", null, () -> Ui.confirm(this, "Delete alarm?", "This cannot be undone.", "Delete", () -> {
                Scheduler.cancel(this, alarm);
                AlarmStore.delete(this, alarm.id);
                finish();
            })), new LinearLayout.LayoutParams(-1, -2));
        }

        setScreen(alarm.id == 0 ? "New alarm" : "Edit alarm", sv);
        addBarButton("Cancel", this::finish);
        addBarButton("Save", this::save);
        drawTime();
        drawDays();
        refreshRows();
    }

    private LinearLayout.LayoutParams margin(int top, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Display.dp(this, top);
        lp.bottomMargin = Display.dp(this, bottom);
        return lp;
    }

    private View row(String title, String sub, Runnable r) {
        View v = Ui.row(this, title, sub, r);
        v.setTag(title);
        return v;
    }

    private void refreshRows() {
        setRowSub(col.findViewWithTag("Sound"), soundName());
        setRowSub(vibrateRow, alarm.vibrate ? "On" : "Off");
    }

    private void setRowSub(View row, String sub) {
        if (!(row instanceof LinearLayout)) return;
        LinearLayout l = (LinearLayout) row;
        if (l.getChildCount() > 1) ((TextView) l.getChildAt(1)).setText(sub);
        else l.addView(Ui.dimText(this, sub, 13));
    }

    private String soundName() {
        if (alarm.sound.isEmpty()) return "Default alarm sound";
        try {
            Ringtone r = RingtoneManager.getRingtone(this, Uri.parse(alarm.sound));
            return r == null ? "Custom" : r.getTitle(this);
        } catch (RuntimeException e) {
            return "Custom";
        }
    }

    private void pickSound() {
        Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, alarm.sound.isEmpty() ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) : Uri.parse(alarm.sound));
        startActivityForResult(i, REQ_SOUND);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_SOUND && res == Activity.RESULT_OK && data != null) {
            Uri u = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            alarm.sound = u == null || u.equals(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)) ? "" : u.toString();
            refreshRows();
        }
    }

    private void pickTime() {
        // Spinner style (Holo) rather than the clock face: easier to use on e-ink and with a stylus or a button phone.
        new TimePickerDialog(this, android.R.style.Theme_Holo_Light_Dialog, (v, h, m) -> { alarm.hour = h; alarm.minute = m; drawTime(); },
                alarm.hour, alarm.minute, is24()).show();
    }

    private boolean is24() {
        String m = app.eink.core.InkPrefs.getStr(this, "hours", "system");
        return m.equals("24") || (m.equals("system") && android.text.format.DateFormat.is24HourFormat(this));
    }

    private void drawTime() {
        if (is24()) timeBtn.setText(String.format(Locale.ROOT, "%02d:%02d", alarm.hour, alarm.minute));
        else timeBtn.setText(String.format(Locale.ROOT, "%d:%02d %s", alarm.hour % 12 == 0 ? 12 : alarm.hour % 12, alarm.minute, alarm.hour < 12 ? "AM" : "PM"));
    }

    private void drawDays() {
        for (int d = 0; d < 7; d++) Ui.style(dayBtns[d], (alarm.days & (1 << d)) != 0);
    }

    private void save() {
        alarm.label = label.getText().toString().trim();
        alarm.enabled = true;
        AlarmStore.save(this, alarm);
        Scheduler.schedule(this, alarm);
        ClockActivity.toastIn(this, alarm);
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 5);
        finish();
    }

    @Override protected void onSaveInstanceState(Bundle o) {
        super.onSaveInstanceState(o);
        o.putInt("h", alarm.hour);
        o.putInt("m", alarm.minute);
        o.putInt("d", alarm.days);
    }

    @Override protected void onLookChanged() { /* the picker and fields keep their state; redraw only on the next open */ }
}
