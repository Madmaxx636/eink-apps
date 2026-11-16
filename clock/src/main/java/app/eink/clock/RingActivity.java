package app.eink.clock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import app.eink.core.Display;
import app.eink.core.InkActivity;
import app.eink.core.InkPrefs;
import app.eink.core.Ui;

/** The ringing screen: shows over the lock screen and turns the display on. Big buttons, nothing else. */
public class RingActivity extends InkActivity {
    private final BroadcastReceiver stopped = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { finish(); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        boolean timer = "timer".equals(getIntent().getStringExtra("kind"));
        String label = getIntent().getStringExtra("label");
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        int p = Display.dp(this, 24);
        col.setPadding(p, p, p, p);

        boolean h24 = InkPrefs.getStr(this, "hours", "system").equals("24")
                || (InkPrefs.getStr(this, "hours", "system").equals("system") && android.text.format.DateFormat.is24HourFormat(this));
        TextView time = Ui.text(this, new SimpleDateFormat(h24 ? "HH:mm" : "h:mm a", Locale.getDefault()).format(new Date()), 64, true);
        time.setGravity(Gravity.CENTER);
        col.addView(time, new LinearLayout.LayoutParams(-1, -2));
        TextView what = Ui.text(this, timer ? "Timer finished" : (label == null || label.isEmpty() ? "Alarm" : label), 26, false);
        what.setGravity(Gravity.CENTER);
        what.setPadding(0, p, 0, p * 2);
        col.addView(what, new LinearLayout.LayoutParams(-1, -2));

        TextView snooze = Ui.button(this, timer ? "+1 minute" : "Snooze " + InkPrefs.getInt(this, "snoozeMin", 10) + " min", () -> send(RingService.ACTION_SNOOZE));
        snooze.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, Display.sp(this, 22));
        snooze.setMinHeight(Display.dp(this, 72));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = p / 2;
        col.addView(snooze, lp);
        TextView dismiss = Ui.button(this, "Dismiss", () -> send(RingService.ACTION_DISMISS));
        dismiss.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, Display.sp(this, 22));
        dismiss.setMinHeight(Display.dp(this, 72));
        Ui.style(dismiss, true);
        col.addView(dismiss, new LinearLayout.LayoutParams(-1, -2));

        setScreen(timer ? "Timer" : "Alarm", col);
        IntentFilter f = new IntentFilter(RingService.STOPPED);
        register(f);
    }

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")   // before Android 13 there is no flag to give
    private void register(IntentFilter f) {
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(stopped, f, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(stopped, f);
    }

    private void send(String action) {
        startService(new Intent(this, RingService.class).setAction(action));
        finish();
    }

    @Override public void onBackPressed() { /* leaving by Back would leave it ringing unseen; use the buttons */ }

    @Override protected void onDestroy() {
        try { unregisterReceiver(stopped); } catch (RuntimeException ignored) {}
        super.onDestroy();
    }

    @Override protected void onLookChanged() { /* keep the ringing screen as it is */ }
}
