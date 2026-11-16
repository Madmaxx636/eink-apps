package app.eink.clock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Wakes up when an alarm or the timer is due, and again after a reboot or a clock change to set them up anew. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (Scheduler.ACTION_ALARM.equals(a)) {
            int id = i.getIntExtra("id", 0);
            Alarm al = AlarmStore.get(c, id);
            if (al == null) return;
            if (!i.getBooleanExtra("snooze", false)) {
                if (al.days == 0) { al.enabled = false; AlarmStore.save(c, al); }   // a one-time alarm switches itself off
                else Scheduler.schedule(c, al);                                      // a repeating one is set for its next day
            }
            RingService.start(c, "alarm", id, al.label, al.sound, al.vibrate);
        } else if (Scheduler.ACTION_TIMER.equals(a)) {
            app.eink.core.InkPrefs.sp(c).edit().putLong("timerEnd", 0).putLong("timerLeft", 0).apply();
            RingService.start(c, "timer", 0, "", "", true);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)
                || Intent.ACTION_TIME_CHANGED.equals(a) || Intent.ACTION_TIMEZONE_CHANGED.equals(a)) {
            Scheduler.rescheduleAll(c);
        }
    }
}
