package app.eink.clock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.format.DateFormat;

import java.util.Date;

import app.eink.core.InkPrefs;

/** Rings: plays the sound, vibrates and shows the alarm on the lock screen until you dismiss or snooze it. */
public class RingService extends Service {
    static final String ACTION_DISMISS = "app.eink.clock.DISMISS", ACTION_SNOOZE = "app.eink.clock.SNOOZE", STOPPED = "app.eink.clock.RING_STOPPED";
    private static final String CHANNEL = "ring";
    private static final int NOTE_ID = 77;

    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock lock;
    private final Handler h = new Handler(Looper.getMainLooper());
    private String kind = "alarm";
    private int alarmId;
    private boolean running;

    static void start(Context c, String kind, int id, String label, String sound, boolean vibrate) {
        Intent i = new Intent(c, RingService.class).putExtra("kind", kind).putExtra("id", id).putExtra("label", label)
                .putExtra("sound", sound).putExtra("vibrate", vibrate);
        c.startForegroundService(i);
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent i, int flags, int startId) {
        if (i == null) { stopSelf(); return START_NOT_STICKY; }
        String a = i.getAction();
        if (ACTION_DISMISS.equals(a)) { finish(false); return START_NOT_STICKY; }
        if (ACTION_SNOOZE.equals(a)) {
            if ("timer".equals(kind)) {   // for a timer, "snooze" adds a minute
                long end = System.currentTimeMillis() + 60000L;
                InkPrefs.sp(this).edit().putLong("timerEnd", end).putLong("timerTotal", 60000L).apply();
                Scheduler.setTimer(this, end);
            } else Scheduler.snooze(this, alarmId, InkPrefs.getInt(this, "snoozeMin", 10));
            finish(false);
            return START_NOT_STICKY;
        }
        if (running) return START_NOT_STICKY;     // already ringing; a second alarm at the same moment shares the sound
        running = true;
        kind = i.getStringExtra("kind") == null ? "alarm" : i.getStringExtra("kind");
        alarmId = i.getIntExtra("id", 0);
        String label = i.getStringExtra("label") == null ? "" : i.getStringExtra("label");

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Alarms and timers", NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(null, null);   // the service plays its own sound, on the alarm volume
        ch.setBypassDnd(true);
        nm.createNotificationChannel(ch);

        boolean timer = kind.equals("timer");
        String title = timer ? "Timer finished" : (label.isEmpty() ? "Alarm" : label);
        String when = DateFormat.getTimeFormat(this).format(new Date());
        Intent ring = new Intent(this, RingActivity.class).putExtra("kind", kind).putExtra("label", label).putExtra("title", title)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        PendingIntent full = PendingIntent.getActivity(this, 1, ring, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title).setContentText(when)
                .setCategory(Notification.CATEGORY_ALARM).setOngoing(true).setAutoCancel(false)
                .setFullScreenIntent(full, true).setContentIntent(full)
                .addAction(new Notification.Action.Builder(null, timer ? "+1 min" : "Snooze", self(ACTION_SNOOZE)).build())
                .addAction(new Notification.Action.Builder(null, "Dismiss", self(ACTION_DISMISS)).build())
                .build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(NOTE_ID, n);

        // Keep the CPU awake while it rings, but never for more than the silence limit.
        int limit = InkPrefs.getInt(this, "ringMin", 10);
        lock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "eink:ring");
        lock.acquire(limit * 60000L + 5000);
        playSound(i.getStringExtra("sound"));
        if (i.getBooleanExtra("vibrate", true)) vibrate();
        h.postDelayed(() -> finish(true), limit * 60000L);
        // The full-screen intent only fires on a locked screen; on an unlocked one the heads-up notification is shown.
        return START_NOT_STICKY;
    }

    private PendingIntent self(String action) {
        return PendingIntent.getService(this, action.hashCode(), new Intent(this, RingService.class).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void playSound(String custom) {
        Uri uri = custom != null && !custom.isEmpty() ? Uri.parse(custom) : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception e) {
            // A sound that is gone or unreadable: fall back to the default one, and if that fails too, vibrate only.
            try {
                if (player != null) player.release();
                player = new MediaPlayer();
                player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
                player.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
                player.setLooping(true);
                player.prepare();
                player.start();
            } catch (Exception e2) {
                player = null;
            }
        }
    }

    private void vibrate() {
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 700, 500}, 0),
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
    }

    /** Stops everything. `missed` is true when nobody answered and the silence limit ran out. */
    private void finish(boolean missed) {
        h.removeCallbacksAndMessages(null);
        if (player != null) { try { player.stop(); } catch (RuntimeException ignored) {} player.release(); player = null; }
        if (vibrator != null) vibrator.cancel();
        if (lock != null && lock.isHeld()) lock.release();
        running = false;
        sendBroadcast(new Intent(STOPPED).setPackage(getPackageName()));
        stopForeground(STOP_FOREGROUND_REMOVE);
        if (missed) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(NOTE_ID + 1, new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(kind.equals("timer") ? "Missed timer" : "Missed alarm").setAutoCancel(true).build());
        }
        stopSelf();
    }

    @Override public void onDestroy() {
        if (player != null) { try { player.stop(); } catch (RuntimeException ignored) {} player.release(); }
        if (vibrator != null) vibrator.cancel();
        if (lock != null && lock.isHeld()) lock.release();
        super.onDestroy();
    }
}
