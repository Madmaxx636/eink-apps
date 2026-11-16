package app.eink.clock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.List;

import app.eink.core.InkPrefs;

/**
 * Sets and cancels the system alarms. Both alarms and the timer use AlarmManager.setAlarmClock, which
 * Android treats as an alarm clock: it fires on time even in Doze and needs no special permission.
 */
final class Scheduler {
    static final String ACTION_ALARM = "app.eink.clock.ALARM", ACTION_TIMER = "app.eink.clock.TIMER";
    private static final int TIMER_CODE = 900000, SNOOZE_CODE = 910000;

    private Scheduler() {}

    private static PendingIntent fire(Context c, String action, int id, int code, boolean snooze) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(action).putExtra("id", id).putExtra("snooze", snooze);
        i.setData(android.net.Uri.parse("eink-clock://" + action + "/" + code));   // distinct per alarm so they do not replace each other
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent show(Context c, int tab) {
        Intent i = new Intent(c, ClockActivity.class).putExtra("tab", tab).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 800000 + tab, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static AlarmManager am(Context c) { return (AlarmManager) c.getSystemService(Context.ALARM_SERVICE); }

    /** Sets an alarm clock. If Android refuses (the user turned off "Alarms & reminders" on Android 12), fall back to an inexact alarm. */
    private static void set(Context c, long at, int tab, PendingIntent pi) {
        try {
            am(c).setAlarmClock(new AlarmManager.AlarmClockInfo(at, show(c, tab)), pi);
        } catch (SecurityException e) {
            am(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    static void schedule(Context c, Alarm a) {
        PendingIntent pi = fire(c, ACTION_ALARM, a.id, a.id, false);
        if (!a.enabled) { am(c).cancel(pi); return; }
        set(c, a.nextTrigger(System.currentTimeMillis()), ClockActivity.TAB_ALARMS, pi);
    }

    static void cancel(Context c, Alarm a) { am(c).cancel(fire(c, ACTION_ALARM, a.id, a.id, false)); }

    static void snooze(Context c, int alarmId, int minutes) {
        long at = System.currentTimeMillis() + minutes * 60000L;
        set(c, at, ClockActivity.TAB_ALARMS, fire(c, ACTION_ALARM, alarmId, SNOOZE_CODE + alarmId, true));
    }

    static void rescheduleAll(Context c) {
        List<Alarm> list = AlarmStore.all(c);
        for (Alarm a : list) schedule(c, a);
        long end = InkPrefs.sp(c).getLong("timerEnd", 0);
        if (end > System.currentTimeMillis()) setTimer(c, end);
    }

    static void setTimer(Context c, long endWall) {
        set(c, endWall, ClockActivity.TAB_TIMER, fire(c, ACTION_TIMER, 0, TIMER_CODE, false));
    }

    static void cancelTimer(Context c) { am(c).cancel(fire(c, ACTION_TIMER, 0, TIMER_CODE, false)); }
}
