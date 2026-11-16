package app.eink.core;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

/** Reads events from the device calendar. Read only; nothing is stored. The app must hold READ_CALENDAR. */
public final class CalEvents {
    public static final class Event {
        public long id, begin, end;
        public String title, where;
        public boolean allDay;
    }

    private CalEvents() {}

    public static long startOfDay(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** Events overlapping [start, end), earliest first. Returns an empty list if there is no permission. */
    public static List<Event> load(Context c, long start, long end) {
        List<Event> out = new ArrayList<>();
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, start);
        ContentUris.appendId(b, end);
        String[] cols = {CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION};
        try (Cursor q = c.getContentResolver().query(b.build(), cols, null, null, CalendarContract.Instances.BEGIN + " ASC")) {
            if (q == null) return out;
            while (q.moveToNext()) {
                Event e = new Event();
                e.id = q.getLong(0);
                e.title = q.getString(1);
                e.begin = q.getLong(2);
                e.end = q.getLong(3);
                e.allDay = q.getInt(4) != 0;
                e.where = q.getString(5);
                if (e.allDay) { // all-day events are stored as UTC midnights; show them on the same local date
                    e.begin = utcDayToLocal(e.begin);
                    e.end = utcDayToLocal(e.end);
                    if (e.end <= start || e.begin >= end) continue;
                }
                out.add(e);
            }
        } catch (SecurityException ex) {
            return new ArrayList<>();
        }
        Collections.sort(out, (x, y) -> Long.compare(x.begin, y.begin));
        return out;
    }

    private static long utcDayToLocal(long utc) {
        Calendar u = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        u.setTimeInMillis(utc);
        Calendar l = Calendar.getInstance();
        l.clear();
        l.set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH));
        return l.getTimeInMillis();
    }
}
