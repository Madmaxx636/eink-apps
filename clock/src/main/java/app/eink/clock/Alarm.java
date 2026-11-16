package app.eink.clock;

import java.util.Calendar;

/** One alarm. No Android classes here, so the scheduling arithmetic can be tested on a plain JVM. */
final class Alarm {
    int id;
    int hour, minute;
    int days;               // bit 0 is Sunday ... bit 6 is Saturday; 0 means ring once
    boolean enabled = true;
    boolean vibrate = true;
    String label = "";
    String sound = "";      // ringtone URI, empty for the phone's default alarm sound

    static final int ALL_DAYS = 0x7F, WEEKDAYS = 0x3E, WEEKEND = 0x41;

    /** The next time this alarm should ring after `now`, in epoch milliseconds. */
    long nextTrigger(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        for (int i = 0; i < 8; i++) {
            boolean dayOk = days == 0 || (days & (1 << (c.get(Calendar.DAY_OF_WEEK) - 1))) != 0;
            if (dayOk && c.getTimeInMillis() > now) return c.getTimeInMillis();
            c.add(Calendar.DAY_OF_YEAR, 1);
            c.set(Calendar.HOUR_OF_DAY, hour);   // keeps the wall-clock time across daylight saving changes
            c.set(Calendar.MINUTE, minute);
        }
        return now + 24 * 3600 * 1000L;
    }

    /** "Once", "Every day", "Weekdays" or a list of day names. */
    String daysText(String[] shortNames) {
        if (days == 0) return "Once";
        if (days == ALL_DAYS) return "Every day";
        if (days == WEEKDAYS) return "Weekdays";
        if (days == WEEKEND) return "Weekends";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 7; i++) if ((days & (1 << i)) != 0) b.append(b.length() > 0 ? ", " : "").append(shortNames[i]);
        return b.toString();
    }
}
