package app.eink.calc;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.eink.core.Display;
import app.eink.core.InkActivity;
import app.eink.core.InkPrefs;
import app.eink.core.Ui;

/** Calculator, tip calculator, currency converter and unit converter. */
public class CalcActivity extends InkActivity {
    private static final String[] NAMES = {"Calc", "Tip", "Money", "Units"};

    private final Tab[] tabs = new Tab[4];
    private CalcTab calc;
    private LinearLayout tabRow;
    private FrameLayout body;
    private int tab;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        calc = new CalcTab(this);
        calc.restore(b);
        tabs[0] = calc;
        tabs[1] = new TipTab(this);
        tabs[2] = new MoneyTab(this);
        tabs[3] = new UnitsTab(this);
        tab = b != null ? b.getInt("tab", InkPrefs.getInt(this, "startTab", 0)) : InkPrefs.getInt(this, "startTab", 0);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        tabRow = new LinearLayout(this);
        int p = Display.dp(this, 4);
        tabRow.setPadding(p, p, p, p);
        body = new FrameLayout(this);
        boolean bottom = "bottom".equals(InkPrefs.getStr(this, "tabsPos", "top"));
        if (!bottom) { root.addView(tabRow, new LinearLayout.LayoutParams(-1, -2)); root.addView(Ui.divider(this)); }
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        if (bottom) { root.addView(Ui.divider(this)); root.addView(tabRow, new LinearLayout.LayoutParams(-1, -2)); }

        setScreen("Calculator", root);
        addBarButton("History", () -> calc.showHistory());
        addSettingsButton();
        showTab(tab);
    }

    @Override protected Class<? extends android.app.Activity> settingsClass() { return CalcSettings.class; }

    @Override protected void onSaveInstanceState(Bundle o) {
        super.onSaveInstanceState(o);
        o.putInt("tab", tab);
        calc.save(o);
    }

    void showTab(int t) {
        tab = t;
        InkPrefs.put(this, "startTab", t);
        tabRow.removeAllViews();
        for (int i = 0; i < NAMES.length; i++) {
            final int idx = i;
            TextView b = Ui.button(this, NAMES[i], () -> { if (tab != idx) showTab(idx); });
            b.setMinWidth(0);
            Ui.style(b, i == t);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(Display.dp(this, 2), 0, Display.dp(this, 2), 0);
            tabRow.addView(b, lp);
        }
        rebuildTab();
    }

    void rebuildTab() {
        body.removeAllViews();
        View v = tabs[tab].build();
        body.addView(v, new FrameLayout.LayoutParams(-1, -1));
        tabs[tab].onShow();
    }

    /** Swipe left or right to change between the four screens. */
    @Override protected boolean onSwipe(int dir) {
        int to = tab + dir;
        if (to < 0 || to >= NAMES.length) return false;
        showTab(to);
        return true;
    }

    @Override protected boolean asksScreenType() { return true; }
}
