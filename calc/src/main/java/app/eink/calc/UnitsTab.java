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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.eink.core.Display;
import app.eink.core.IconButton;
import app.eink.core.InkPrefs;
import app.eink.core.Ui;

/** Unit converter: length, mass, volume, area, temperature, speed, time, data and energy. Works offline. */
final class UnitsTab extends Tab {
    private Units.Kind kind;
    private Units.Unit from, to;
    private EditText amount;
    private TextView kindBtn, fromBtn, toBtn, result;
    private LinearLayout all;

    UnitsTab(CalcActivity a) { super(a); }

    @Override View build() {
        int ki = Math.max(0, Math.min(Units.KINDS.size() - 1, InkPrefs.getInt(a, "unitKind", 0)));
        kind = Units.KINDS.get(ki);
        pickUnits(InkPrefs.getInt(a, "unitFrom", 0), InkPrefs.getInt(a, "unitTo", 1));

        ScrollView sv = new ScrollView(a);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = Display.dp(a, 12);
        col.setPadding(p, p / 2, p, p);
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));

        kindBtn = plain(() -> {
            String[] names = new String[Units.KINDS.size()];
            for (int i = 0; i < names.length; i++) names[i] = Units.KINDS.get(i).name;
            Ui.choose(a, "What to convert", names, Units.KINDS.indexOf(kind), i -> {
                kind = Units.KINDS.get(i);
                InkPrefs.put(a, "unitKind", i);
                pickUnits(0, 1);
                draw();
            });
        });
        col.addView(kindBtn, new LinearLayout.LayoutParams(-1, -2));

        col.addView(Ui.dimText(a, "Amount", 13), top(8));
        amount = new EditText(a);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        amount.setText(InkPrefs.getStr(a, "unitAmount", "1"));
        amount.setTextSize(TypedValue.COMPLEX_UNIT_SP, Display.sp(a, 26));
        amount.setTextColor(Display.fg(a));
        amount.setSingleLine(true);
        amount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            @Override public void onTextChanged(CharSequence s, int x, int y, int z) {}
            @Override public void afterTextChanged(Editable s) { InkPrefs.put(a, "unitAmount", s.toString()); compute(); }
        });
        col.addView(amount, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout pick = new LinearLayout(a);
        pick.setGravity(Gravity.CENTER_VERTICAL);
        fromBtn = plain(() -> chooseUnit(true));
        toBtn = plain(() -> chooseUnit(false));
        TextView swap = Ui.button(a, "⇄", () -> { Units.Unit t = from; from = to; to = t; save(); draw(); });
        ((IconButton) swap).setNoIcon(true);
        swap.setMinWidth(Display.dp(a, 44));
        pick.addView(fromBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        pick.addView(swap, new LinearLayout.LayoutParams(Display.dp(a, 52), -2));
        pick.addView(toBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(pick, top(8));

        col.addView(Ui.divider(a), top(12));
        result = Ui.text(a, "", 30, true);
        result.setGravity(Gravity.END);
        result.setSingleLine(true);
        result.setAutoSizeTextTypeUniformWithConfiguration(14, Math.round(Display.sp(a, 32)), 2, TypedValue.COMPLEX_UNIT_SP);
        result.setPadding(0, Display.dp(a, 10), 0, Display.dp(a, 8));
        col.addView(result, new LinearLayout.LayoutParams(-1, -2));

        col.addView(Ui.dimText(a, "The same amount in every unit", 12));
        all = new LinearLayout(a);
        all.setOrientation(LinearLayout.VERTICAL);
        col.addView(all, new LinearLayout.LayoutParams(-1, -2));
        draw();
        return sv;
    }

    private LinearLayout.LayoutParams top(int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Display.dp(a, dp);
        return lp;
    }

    private TextView plain(Runnable r) {
        TextView t = Ui.button(a, "", r);
        ((IconButton) t).setNoIcon(true);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    private void pickUnits(int f, int t) {
        from = kind.units.get(Math.min(f, kind.units.size() - 1));
        to = kind.units.get(Math.min(t, kind.units.size() - 1));
    }

    private void save() {
        InkPrefs.put(a, "unitFrom", kind.units.indexOf(from));
        InkPrefs.put(a, "unitTo", kind.units.indexOf(to));
    }

    private void chooseUnit(final boolean isFrom) {
        String[] names = new String[kind.units.size()];
        for (int i = 0; i < names.length; i++) names[i] = kind.units.get(i).toString();
        Ui.choose(a, isFrom ? "Convert from" : "Convert to", names, kind.units.indexOf(isFrom ? from : to), i -> {
            if (isFrom) from = kind.units.get(i); else to = kind.units.get(i);
            save();
            draw();
        });
    }

    private double amt() {
        try { return Double.parseDouble(amount.getText().toString().trim().replace(',', '.')); } catch (NumberFormatException e) { return 0; }
    }

    private static String num(double v) {
        if (v == 0) return "0";
        double a = Math.abs(v);
        if (a >= 1e12 || a < 1e-6) return String.format(Locale.ROOT, "%.6g", v);
        String s = String.format(Locale.ROOT, "%.8f", v);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    private void draw() {
        kindBtn.setText(kind.name + " ▾");
        fromBtn.setText(from.symbol + " ▾");
        toBtn.setText(to.symbol + " ▾");
        compute();
    }

    private void compute() {
        if (result == null) return;
        double v = amt();
        result.setText(num(Units.convert(kind, from, to, v)) + " " + to.symbol);
        all.removeAllViews();
        for (Units.Unit u : kind.units) {
            LinearLayout r = new LinearLayout(a);
            r.setPadding(0, Display.dp(a, 3), 0, Display.dp(a, 3));
            boolean sel = u == to;
            TextView n = Ui.text(a, u.name, 14, sel);
            r.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
            r.addView(Ui.text(a, num(Units.convert(kind, from, u, v)) + " " + u.symbol, 14, sel));
            all.addView(r);
        }
    }
}
