package app.eink.calendar;

import android.widget.LinearLayout;

import java.util.Calendar;

import app.eink.core.InkPrefs;
import app.eink.core.SettingsActivity;
import app.eink.core.Ui;

public class CalendarSettings extends SettingsActivity {
    @Override protected void extraRows(LinearLayout col) {
        final String[] pos = {"top", "bottom"};
        final String[] posNames = {"Top, above the grid", "Bottom, below the list"};
        int np = "bottom".equals(InkPrefs.getStr(this, "navPos", "top")) ? 1 : 0;
        add(Ui.row(this, "Month buttons (previous, next)", posNames[np], () ->
                Ui.choose(this, "Month buttons", posNames, np, i -> { InkPrefs.put(this, "navPos", pos[i]); recreate(); })));

        final int[] ids = {0, Calendar.SUNDAY, Calendar.MONDAY, Calendar.SATURDAY};
        final String[] names = {"Follow the language setting", "Sunday", "Monday", "Saturday"};
        int cur = InkPrefs.getInt(this, "weekStart", 0), idx = 0;
        for (int i = 0; i < ids.length; i++) if (ids[i] == cur) idx = i;
        final int sel = idx;
        add(Ui.row(this, "Week starts on", names[idx], () ->
                Ui.choose(this, "Week starts on", names, sel, i -> { InkPrefs.put(this, "weekStart", ids[i]); recreate(); })));

        final int[] days = {7, 14, 30, 60, 90};
        final String[] dayNames = {"7 days", "14 days", "30 days", "60 days", "90 days"};
        int cd = InkPrefs.getInt(this, "agendaDays", 30), di = 2;
        for (int i = 0; i < days.length; i++) if (days[i] == cd) di = i;
        final int dsel = di;
        add(Ui.row(this, "Agenda length", dayNames[di], () ->
                Ui.choose(this, "Agenda length", dayNames, dsel, i -> { InkPrefs.put(this, "agendaDays", days[i]); recreate(); })));

        boolean wk = InkPrefs.getBool(this, "weekNums", false);
        add(Ui.row(this, "Week numbers", wk ? "On" : "Off", () -> {
            InkPrefs.put(this, "weekNums", !InkPrefs.getBool(this, "weekNums", false));
            recreate();
        }));
    }
}
