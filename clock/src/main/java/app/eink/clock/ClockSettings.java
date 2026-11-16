package app.eink.clock;

import android.widget.LinearLayout;

import app.eink.core.Display;
import app.eink.core.InkPrefs;
import app.eink.core.SettingsActivity;
import app.eink.core.Ui;

public class ClockSettings extends SettingsActivity {
    @Override protected void extraRows(LinearLayout col) {
        final String[] styles = {"digital", "analog", "both"};
        final String[] styleNames = {"Digital", "Analog", "Analog and digital"};
        final int si = Math.max(0, java.util.Arrays.asList(styles).indexOf(InkPrefs.getStr(this, "style", "digital")));
        add(Ui.row(this, "Clock face", styleNames[si], () ->
                Ui.choose(this, "Clock face", styleNames, si, i -> { InkPrefs.put(this, "style", styles[i]); recreate(); })));

        final String[] hours = {"system", "12", "24"};
        final String[] hourNames = {"Follow the system", "12-hour", "24-hour"};
        final int hi = Math.max(0, java.util.Arrays.asList(hours).indexOf(InkPrefs.getStr(this, "hours", "system")));
        add(Ui.row(this, "Hours", hourNames[hi], () ->
                Ui.choose(this, "Hours", hourNames, hi, i -> { InkPrefs.put(this, "hours", hours[i]); recreate(); })));

        final String[] pos = {"top", "bottom"};
        final String[] posNames = {"Top", "Bottom"};
        int tp = "bottom".equals(InkPrefs.getStr(this, "tabsPos", "top")) ? 1 : 0;
        add(Ui.row(this, "Tab buttons (Clock, Alarms, Timer, Watch, World)", posNames[tp], () ->
                Ui.choose(this, "Tab buttons", posNames, tp, i -> { InkPrefs.put(this, "tabsPos", pos[i]); recreate(); })));

        boolean live = InkPrefs.getBool(this, "live", true);
        add(Ui.row(this, "Live time", (live ? "On" : "Off") + ". Seconds on the clock, and the stopwatch and timer count on screen."
                + (Display.isEink(this) ? " On e-ink this redraws the panel every second: it looks live but uses more battery and can ghost." : ""), () -> {
            InkPrefs.put(this, "live", !InkPrefs.getBool(this, "live", true));
            recreate();
        }));
        boolean sec = InkPrefs.getBool(this, "seconds", true);
        add(Ui.row(this, "Show seconds", sec ? "On, on the clock, analog hand and world clock" : "Off", () -> {
            InkPrefs.put(this, "seconds", !InkPrefs.getBool(this, "seconds", true));
            recreate();
        }));
        final String[] precNames = {"Whole seconds", "Tenths of a second", "Hundredths of a second"};
        int pr = Math.max(0, Math.min(2, InkPrefs.getInt(this, "swPrec", Display.isEink(this) ? 0 : 1)));
        add(Ui.row(this, "Stopwatch precision", precNames[pr], () ->
                Ui.choose(this, "Stopwatch precision", precNames, pr, i -> { InkPrefs.put(this, "swPrec", i); recreate(); })));

        final int[] snooze = {5, 10, 15, 20, 30};
        int cs = InkPrefs.getInt(this, "snoozeMin", 10), sn = 1;
        for (int i = 0; i < snooze.length; i++) if (snooze[i] == cs) sn = i;
        final int snSel = sn;
        final String[] snNames = {"5 minutes", "10 minutes", "15 minutes", "20 minutes", "30 minutes"};
        add(Ui.row(this, "Snooze for", snNames[sn], () ->
                Ui.choose(this, "Snooze for", snNames, snSel, i -> { InkPrefs.put(this, "snoozeMin", snooze[i]); recreate(); })));

        final int[] ring = {1, 5, 10, 15, 30};
        int cr = InkPrefs.getInt(this, "ringMin", 10), ri = 2;
        for (int i = 0; i < ring.length; i++) if (ring[i] == cr) ri = i;
        final int riSel = ri;
        final String[] riNames = {"1 minute", "5 minutes", "10 minutes", "15 minutes", "30 minutes"};
        add(Ui.row(this, "Stop ringing after", riNames[ri] + " if you do nothing", () ->
                Ui.choose(this, "Stop ringing after", riNames, riSel, i -> { InkPrefs.put(this, "ringMin", ring[i]); recreate(); })));
    }
}
