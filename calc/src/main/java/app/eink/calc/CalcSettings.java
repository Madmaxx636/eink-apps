package app.eink.calc;

import android.widget.LinearLayout;

import app.eink.core.InkPrefs;
import app.eink.core.SettingsActivity;
import app.eink.core.Ui;

public class CalcSettings extends SettingsActivity {
    @Override protected void extraRows(LinearLayout col) {
        final String[] pos = {"top", "bottom"};
        final String[] posNames = {"Top", "Bottom"};
        int tp = "bottom".equals(InkPrefs.getStr(this, "tabsPos", "top")) ? 1 : 0;
        add(Ui.row(this, "Tab buttons (Calc, Tip, Money, Units)", posNames[tp], () ->
                Ui.choose(this, "Tab buttons", posNames, tp, i -> { InkPrefs.put(this, "tabsPos", pos[i]); recreate(); })));

        boolean deg = InkPrefs.getBool(this, "degrees", true);
        add(Ui.row(this, "Angles in the scientific keypad", deg ? "Degrees" : "Radians", () -> {
            InkPrefs.put(this, "degrees", !InkPrefs.getBool(this, "degrees", true));
            recreate();
        }));
    }
}
