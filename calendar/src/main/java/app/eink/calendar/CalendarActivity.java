package app.eink.calendar;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import app.eink.core.CalEvents;
import app.eink.core.Display;
import app.eink.core.InkActivity;
import app.eink.core.InkPrefs;
import app.eink.core.PagedScroll;
import app.eink.core.Ui;

public class CalendarActivity extends InkActivity {
    private static final int REQ = 11;

    private final Calendar shown = Calendar.getInstance();   // any day inside the month on screen
    private long selected = CalEvents.startOfDay(System.currentTimeMillis());
    private boolean agendaMode;
    private boolean allowed;

    private LinearLayout root, grid;
    private TextView title, modeBtn;
    private PagedScroll list;
    private Map<Long, List<CalEvents.Event>> byDay = new HashMap<>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (b != null) {
            selected = b.getLong("selected", selected);
            shown.setTimeInMillis(selected);
            agendaMode = b.getBoolean("agenda");
        }
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        setScreen("Calendar", root);
        modeBtn = addBarButton(agendaMode ? "Month" : "Agenda", () -> { agendaMode = !agendaMode; modeBtn.setText(agendaMode ? "Month" : "Agenda"); build(); });
        addBarButton("Add", this::addEvent);
        addSettingsButton();
    }

    @Override protected void onSaveInstanceState(Bundle o) {
        super.onSaveInstanceState(o);
        o.putLong("selected", selected);
        o.putBoolean("agenda", agendaMode);
    }

    @Override protected void onResume() {
        super.onResume();
        allowed = checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
        build(); // events may have been added in the calendar app
    }

    @Override protected boolean onPage(int dir) { return list != null && list.turn(dir); }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        allowed = checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
        build();
    }

    // ---- structure ----

    private int firstDay() {
        int w = InkPrefs.getInt(this, "weekStart", 0);
        return w == 0 ? Calendar.getInstance().getFirstDayOfWeek() : w;
    }

    private boolean is24() { return DateFormat.is24HourFormat(this); }

    private void build() {
        root.removeAllViews();
        grid = null;
        list = new PagedScroll(this);
        if (!allowed) {
            List<View> rows = new ArrayList<>();
            rows.add(Ui.row(this, "Calendar access needed", "Tap to allow reading your calendars. Events never leave this phone.",
                    () -> requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, REQ)));
            list.setRows(rows);
            root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
            return;
        }
        if (agendaMode) { buildAgenda(); return; }
        boolean navBottom = "bottom".equals(InkPrefs.getStr(this, "navPos", "top"));
        View nav = buildNav();
        if (!navBottom) root.addView(nav, new LinearLayout.LayoutParams(-1, -2));
        buildWeekdays();
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1.25f));
        root.addView(Ui.divider(this));
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        if (navBottom) { root.addView(Ui.divider(this)); root.addView(nav, new LinearLayout.LayoutParams(-1, -2)); }
        loadMonth();
        drawGrid();
        showDay();
    }

    private View buildNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        int p = Display.dp(this, 4);
        nav.setPadding(p, p, p, p);
        TextView prev = Ui.button(this, "‹", () -> moveMonth(-1));
        TextView next = Ui.button(this, "›", () -> moveMonth(1));
        title = Ui.text(this, "", 19, true);
        title.setGravity(Gravity.CENTER);
        title.setClickable(true);
        title.setOnClickListener(v -> { // tap the month name to jump back to today
            selected = CalEvents.startOfDay(System.currentTimeMillis());
            shown.setTimeInMillis(selected);
            build();
        });
        nav.addView(prev, new LinearLayout.LayoutParams(Display.dp(this, 56), -2));
        nav.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        nav.addView(next, new LinearLayout.LayoutParams(Display.dp(this, 56), -2));
        return nav;
    }

    private void moveMonth(int d) {
        shown.set(Calendar.DAY_OF_MONTH, 1);
        shown.add(Calendar.MONTH, d);
        // Keep the selection in step so the list below always matches the month you are looking at.
        Calendar s = Calendar.getInstance();
        s.setTimeInMillis(selected);
        int day = Math.min(s.get(Calendar.DAY_OF_MONTH), shown.getActualMaximum(Calendar.DAY_OF_MONTH));
        s.set(shown.get(Calendar.YEAR), shown.get(Calendar.MONTH), day);
        selected = CalEvents.startOfDay(s.getTimeInMillis());
        build();
    }

    private void buildWeekdays() {
        LinearLayout row = new LinearLayout(this);
        boolean wk = InkPrefs.getBool(this, "weekNums", false);
        if (wk) row.addView(cell("", 12), new LinearLayout.LayoutParams(0, -2, 0.6f));
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_WEEK, firstDay());
        SimpleDateFormat f = new SimpleDateFormat("EEEEE", Locale.getDefault()); // single letter
        for (int i = 0; i < 7; i++) {
            row.addView(cell(f.format(c.getTime()), 13), new LinearLayout.LayoutParams(0, -2, 1f));
            c.add(Calendar.DAY_OF_WEEK, 1);
        }
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private TextView cell(String s, float sp) {
        TextView t = Ui.text(this, s, sp, true);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    // ---- month grid ----

    private Calendar gridStart() {
        Calendar c = (Calendar) shown.clone();
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        int back = (c.get(Calendar.DAY_OF_WEEK) - firstDay() + 7) % 7;
        c.add(Calendar.DAY_OF_MONTH, -back);
        return c;
    }

    private int weeks() {
        Calendar c = (Calendar) shown.clone();
        c.set(Calendar.DAY_OF_MONTH, 1);
        int lead = (c.get(Calendar.DAY_OF_WEEK) - firstDay() + 7) % 7;
        return (lead + c.getActualMaximum(Calendar.DAY_OF_MONTH) + 6) / 7;
    }

    private void loadMonth() {
        byDay.clear();
        Calendar s = gridStart(), e = (Calendar) s.clone();
        e.add(Calendar.DAY_OF_MONTH, weeks() * 7);
        for (CalEvents.Event ev : CalEvents.load(this, s.getTimeInMillis(), e.getTimeInMillis())) {
            // An event that runs over several days is listed on each of them.
            Calendar d = Calendar.getInstance();
            d.setTimeInMillis(CalEvents.startOfDay(ev.begin));
            long last = ev.end > ev.begin ? ev.end - 1 : ev.begin;
            while (d.getTimeInMillis() <= last) {
                List<CalEvents.Event> l = byDay.get(d.getTimeInMillis());
                if (l == null) byDay.put(d.getTimeInMillis(), l = new ArrayList<>());
                l.add(ev);
                d.add(Calendar.DAY_OF_MONTH, 1);
            }
        }
    }

    private void drawGrid() {
        grid.removeAllViews();
        title.setText(DateUtils.formatDateTime(this, shown.getTimeInMillis(), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_MONTH_DAY | DateUtils.FORMAT_SHOW_YEAR));
        boolean wk = InkPrefs.getBool(this, "weekNums", false);
        int month = shown.get(Calendar.MONTH), n = weeks();
        long today = CalEvents.startOfDay(System.currentTimeMillis());
        Calendar c = gridStart();
        for (int w = 0; w < n; w++) {
            LinearLayout row = new LinearLayout(this);
            if (wk) {
                TextView wn = Ui.dimText(this, String.valueOf(c.get(Calendar.WEEK_OF_YEAR)), 11);
                wn.setGravity(Gravity.CENTER);
                row.addView(wn, new LinearLayout.LayoutParams(0, -1, 0.6f));
            }
            for (int d = 0; d < 7; d++) {
                final long day = c.getTimeInMillis();
                int count = byDay.containsKey(day) ? byDay.get(day).size() : 0;
                row.addView(dayCell(c.get(Calendar.DAY_OF_MONTH), count, c.get(Calendar.MONTH) == month, day == selected, day == today, day),
                        new LinearLayout.LayoutParams(0, -1, 1f));
                c.add(Calendar.DAY_OF_MONTH, 1);
            }
            grid.addView(row, new LinearLayout.LayoutParams(-1, 0, 1f));
        }
    }

    private View dayCell(int num, int count, boolean inMonth, boolean sel, boolean today, final long day) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER);
        int fg = Display.fg(this), bg = Display.bg(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(sel ? fg : bg);
        g.setStroke(Display.dp(this, today ? 2 : 0), fg); // today has a frame, the selected day is filled
        g.setCornerRadius(Display.dp(this, Display.isEink(this) ? 0 : 6));
        v.setBackground(g);
        int color = sel ? bg : (inMonth ? fg : Display.dim(this));
        TextView t = new TextView(this);
        t.setText(String.valueOf(num));
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, Display.sp(this, inMonth ? 16 : 12));
        t.setTypeface(t.getTypeface(), inMonth ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        t.setGravity(Gravity.CENTER);
        v.addView(t, new LinearLayout.LayoutParams(-2, -2));
        TextView dots = new TextView(this);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, count); i++) sb.append('•');
        dots.setText(sb.length() == 0 ? " " : sb);
        dots.setTextColor(color);
        dots.setTextSize(TypedValue.COMPLEX_UNIT_SP, Display.sp(this, 10));
        dots.setGravity(Gravity.CENTER);
        dots.setIncludeFontPadding(false);
        v.addView(dots, new LinearLayout.LayoutParams(-2, -2));
        v.setClickable(true);
        v.setOnClickListener(x -> {
            selected = day;
            Calendar d = Calendar.getInstance();
            d.setTimeInMillis(day);
            if (d.get(Calendar.MONTH) != shown.get(Calendar.MONTH)) { shown.setTimeInMillis(day); build(); }
            else { drawGrid(); showDay(); }
        });
        return v;
    }

    // ---- events ----

    private void showDay() {
        List<View> rows = new ArrayList<>();
        TextView head = Ui.text(this, DateUtils.formatDateTime(this, selected,
                DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR), 16, true);
        int p = Display.dp(this, 12);
        head.setPadding(p, Display.dp(this, 8), p, Display.dp(this, 4));
        rows.add(head);
        List<CalEvents.Event> evs = byDay.get(selected);
        if (evs == null || evs.isEmpty()) rows.add(Ui.row(this, "Nothing planned", "Tap Add to make an event", null));
        else for (CalEvents.Event e : evs) { rows.add(eventRow(e)); rows.add(Ui.divider(this)); }
        list.setRows(rows);
    }

    private View eventRow(final CalEvents.Event e) {
        SimpleDateFormat tf = new SimpleDateFormat(is24() ? "HH:mm" : "h:mm a", Locale.getDefault());
        String when = e.allDay ? "All day" : tf.format(new Date(e.begin)) + " – " + tf.format(new Date(e.end));
        String sub = when + (e.where == null || e.where.isEmpty() ? "" : "  ·  " + e.where);
        return Ui.row(this, e.title == null || e.title.isEmpty() ? "(No title)" : e.title, sub, () -> {
            Intent i = new Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id));
            i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.end);
            open(i);
        });
    }

    private void buildAgenda() {
        int days = InkPrefs.getInt(this, "agendaDays", 30);
        Calendar end = Calendar.getInstance();
        end.setTimeInMillis(selected);
        end.add(Calendar.DAY_OF_MONTH, days);
        List<CalEvents.Event> evs = CalEvents.load(this, selected, end.getTimeInMillis());
        List<View> rows = new ArrayList<>();
        long lastDay = -1;
        int p = Display.dp(this, 12);
        for (CalEvents.Event e : evs) {
            long d = Math.max(CalEvents.startOfDay(e.begin), selected);
            if (d != lastDay) {
                lastDay = d;
                TextView h = Ui.text(this, DateUtils.formatDateTime(this, d, DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_ALL), 15, true);
                h.setPadding(p, Display.dp(this, 14), p, Display.dp(this, 4));
                rows.add(h);
                rows.add(Ui.divider(this));
            }
            rows.add(eventRow(e));
            rows.add(Ui.divider(this));
        }
        if (evs.isEmpty()) rows.add(Ui.row(this, "Nothing in the next " + days + " days", null, null));
        list.setRows(rows);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private void addEvent() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(selected);
        Calendar now = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, Math.min(23, now.get(Calendar.HOUR_OF_DAY) + 1));
        Intent i = new Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, c.getTimeInMillis())
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, c.getTimeInMillis() + 3600000L);
        open(i);
    }

    private void open(Intent i) {
        try { startActivity(i); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, "No calendar app found to do that", Toast.LENGTH_LONG).show(); }
    }

    @Override protected Class<? extends android.app.Activity> settingsClass() { return CalendarSettings.class; }

    /** Swipe left for the next month (or the next stretch of days in the agenda), right for the one before. */
    @Override protected boolean onSwipe(int dir) {
        if (!allowed) return false;
        if (agendaMode) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(selected);
            c.add(Calendar.DAY_OF_MONTH, dir * InkPrefs.getInt(this, "agendaDays", 30));
            selected = CalEvents.startOfDay(c.getTimeInMillis());
            shown.setTimeInMillis(selected);
            build();
        } else moveMonth(dir);
        return true;
    }

    @Override protected boolean asksScreenType() { return true; }
}
