package app.eink.calc;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.NumberFormat;

import app.eink.core.Display;
import app.eink.core.IconButton;
import app.eink.core.InkPrefs;
import app.eink.core.Ui;

/** Tip calculator: bill, tip percentage, how many people, and whether to round. */
final class TipTab extends Tab {
    private static final int[] PCTS = {10, 15, 18, 20, 25};

    private EditText bill;
    private TextView[] pctBtns = new TextView[PCTS.length];
    private TextView otherBtn, peopleView;
    private TextView[] roundBtns = new TextView[3];
    private TextView tipOut, totalOut, eachOut, tipEachOut;
    private double pct;
    private int people, rounding;

    TipTab(CalcActivity a) { super(a); }

    @Override View build() {
        pct = Double.parseDouble(InkPrefs.getStr(a, "tipPct", "18"));
        people = InkPrefs.getInt(a, "tipPeople", 1);
        rounding = InkPrefs.getInt(a, "tipRound", 0);

        ScrollView sv = new ScrollView(a);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = Display.dp(a, 12);
        col.setPadding(p, p / 2, p, p);
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));

        col.addView(Ui.dimText(a, "Bill", 13));
        bill = new EditText(a);
        bill.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        bill.setHint("0.00");
        bill.setTextSize(TypedValue.COMPLEX_UNIT_SP, Display.sp(a, 26));
        bill.setTextColor(Display.fg(a));
        bill.setHintTextColor(Display.dim(a));
        bill.setSingleLine(true);
        String saved = InkPrefs.getStr(a, "tipBill", "");
        bill.setText(saved);
        bill.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            @Override public void onTextChanged(CharSequence s, int x, int y, int z) {}
            @Override public void afterTextChanged(Editable s) { InkPrefs.put(a, "tipBill", s.toString()); compute(); }
        });
        col.addView(bill, new LinearLayout.LayoutParams(-1, -2));

        col.addView(Ui.dimText(a, "Tip", 13), top(10));
        LinearLayout pr = new LinearLayout(a);
        for (int i = 0; i < PCTS.length; i++) {
            final int v = PCTS[i];
            pctBtns[i] = chip(v + "%", () -> { pct = v; save(); styles(); compute(); });
            pr.addView(pctBtns[i], weight());
        }
        col.addView(pr, new LinearLayout.LayoutParams(-1, -2));
        otherBtn = chip("Other tip %", () -> Ui.prompt(a, "Tip percentage", fmtPct(), "OK", s -> {
            try { pct = Math.max(0, Math.min(100, Double.parseDouble(s.trim().replace(',', '.')))); save(); styles(); compute(); } catch (NumberFormatException ignored) {}
        }));
        LinearLayout.LayoutParams lo = new LinearLayout.LayoutParams(-1, -2);
        lo.topMargin = Display.dp(a, 6);
        col.addView(otherBtn, lo);

        col.addView(Ui.dimText(a, "People", 13), top(10));
        LinearLayout pp = new LinearLayout(a);
        pp.setGravity(Gravity.CENTER_VERTICAL);
        TextView minus = chip("−", () -> { if (people > 1) { people--; save(); compute(); } });
        TextView plus = chip("+", () -> { if (people < 99) { people++; save(); compute(); } });
        peopleView = Ui.text(a, "1", 24, true);
        peopleView.setGravity(Gravity.CENTER);
        pp.addView(minus, new LinearLayout.LayoutParams(Display.dp(a, 64), -2));
        pp.addView(peopleView, new LinearLayout.LayoutParams(0, -2, 1f));
        pp.addView(plus, new LinearLayout.LayoutParams(Display.dp(a, 64), -2));
        col.addView(pp, new LinearLayout.LayoutParams(-1, -2));

        col.addView(Ui.dimText(a, "Round up", 13), top(10));
        LinearLayout rr = new LinearLayout(a);
        String[] rn = {"No", "The total", "Each share"};
        for (int i = 0; i < 3; i++) {
            final int v = i;
            roundBtns[i] = chip(rn[i], () -> { rounding = v; save(); styles(); compute(); });
            rr.addView(roundBtns[i], weight());
        }
        col.addView(rr, new LinearLayout.LayoutParams(-1, -2));

        col.addView(Ui.divider(a), top(14));
        tipOut = line(col, "Tip");
        totalOut = line(col, "Total");
        eachOut = line(col, "Each person pays");
        tipEachOut = line(col, "Tip per person");
        styles();
        compute();
        return sv;
    }

    private LinearLayout.LayoutParams top(int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Display.dp(a, dp);
        return lp;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.rightMargin = Display.dp(a, 4);
        return lp;
    }

    private TextView chip(String label, Runnable r) {
        TextView t = Ui.button(a, label, r);
        ((IconButton) t).setNoIcon(true);
        t.setMinWidth(0);
        return t;
    }

    private TextView line(LinearLayout col, String label) {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Display.dp(a, 6), 0, Display.dp(a, 6));
        r.addView(Ui.text(a, label, 16, false), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView v = Ui.text(a, "", 22, true);
        r.addView(v);
        col.addView(r, new LinearLayout.LayoutParams(-1, -2));
        return v;
    }

    private String fmtPct() { return pct == Math.floor(pct) ? String.valueOf((int) pct) : String.valueOf(pct); }

    private void save() {
        InkPrefs.put(a, "tipPct", String.valueOf(pct));
        InkPrefs.put(a, "tipPeople", people);
        InkPrefs.put(a, "tipRound", rounding);
    }

    private void styles() {
        boolean preset = false;
        for (int i = 0; i < PCTS.length; i++) { boolean on = pct == PCTS[i]; preset |= on; Ui.style(pctBtns[i], on); }
        Ui.style(otherBtn, !preset);
        otherBtn.setText(preset ? "Other tip %" : "Tip " + fmtPct() + "%  (change)");
        for (int i = 0; i < 3; i++) Ui.style(roundBtns[i], i == rounding);
    }

    private void compute() {
        double b = 0;
        try { b = Double.parseDouble(bill.getText().toString().trim().replace(',', '.')); } catch (NumberFormatException ignored) {}
        TipMath t = TipMath.compute(b, pct, people, rounding);
        NumberFormat money = NumberFormat.getCurrencyInstance();
        peopleView.setText(String.valueOf(people));
        tipOut.setText(money.format(t.tip));
        totalOut.setText(money.format(t.total));
        eachOut.setText(people > 1 ? money.format(t.perPerson) : "—");
        tipEachOut.setText(people > 1 ? money.format(t.tipPerPerson) : "—");
    }
}
