package app.eink.calc;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

import app.eink.core.Display;
import app.eink.core.IconButton;
import app.eink.core.InkPrefs;
import app.eink.core.Ui;

/** Currency converter on the European Central Bank's reference rates. Rates are saved, so it works offline. */
final class MoneyTab extends Tab {
    private Rates rates;
    private EditText amount;
    private TextView fromBtn, toBtn, result, rateLine, status, updateBtn;
    private String from, to;
    private boolean loading;

    MoneyTab(CalcActivity a) { super(a); }

    @Override View build() {
        rates = Rates.load(a);
        String local = "USD";
        try { local = Currency.getInstance(Locale.getDefault()).getCurrencyCode(); } catch (RuntimeException ignored) {}
        from = InkPrefs.getStr(a, "moneyFrom", local);
        to = InkPrefs.getStr(a, "moneyTo", local.equals("EUR") ? "USD" : "EUR");

        ScrollView sv = new ScrollView(a);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = Display.dp(a, 12);
        col.setPadding(p, p / 2, p, p);
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));

        col.addView(Ui.dimText(a, "Amount", 13));
        amount = new EditText(a);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setText(InkPrefs.getStr(a, "moneyAmount", "1"));
        amount.setTextSize(TypedValue.COMPLEX_UNIT_SP, Display.sp(a, 26));
        amount.setTextColor(Display.fg(a));
        amount.setSingleLine(true);
        amount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            @Override public void onTextChanged(CharSequence s, int x, int y, int z) {}
            @Override public void afterTextChanged(Editable s) { InkPrefs.put(a, "moneyAmount", s.toString()); compute(); }
        });
        col.addView(amount, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout pick = new LinearLayout(a);
        pick.setGravity(Gravity.CENTER_VERTICAL);
        fromBtn = button(() -> choose(true));
        toBtn = button(() -> choose(false));
        TextView swap = Ui.button(a, "⇄", () -> { String t = from; from = to; to = t; saveCurrencies(); draw(); });
        ((IconButton) swap).setNoIcon(true);
        swap.setMinWidth(Display.dp(a, 44));
        pick.addView(fromBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        pick.addView(swap, new LinearLayout.LayoutParams(Display.dp(a, 52), -2));
        pick.addView(toBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, -2);
        pl.topMargin = Display.dp(a, 8);
        col.addView(pick, pl);

        col.addView(Ui.divider(a), top(12));
        result = Ui.text(a, "", 32, true);
        result.setGravity(Gravity.END);
        result.setSingleLine(true);
        result.setAutoSizeTextTypeUniformWithConfiguration(14, Math.round(Display.sp(a, 34)), 2, TypedValue.COMPLEX_UNIT_SP);
        result.setPadding(0, Display.dp(a, 10), 0, Display.dp(a, 4));
        col.addView(result, new LinearLayout.LayoutParams(-1, -2));
        rateLine = Ui.dimText(a, "", 13);
        rateLine.setGravity(Gravity.END);
        col.addView(rateLine, new LinearLayout.LayoutParams(-1, -2));

        updateBtn = Ui.button(a, "Update rates", this::update);
        ((IconButton) updateBtn).setNoIcon(true);
        col.addView(updateBtn, top(14));
        status = Ui.dimText(a, "", 12);
        status.setPadding(0, Display.dp(a, 6), 0, 0);
        col.addView(status, new LinearLayout.LayoutParams(-1, -2));

        draw();
        // Open with nothing saved: fetch once, since the converter cannot work without rates.
        if (rates.isEmpty()) update();
        return sv;
    }

    private LinearLayout.LayoutParams top(int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Display.dp(a, dp);
        return lp;
    }

    private TextView button(Runnable r) {
        TextView t = Ui.button(a, "", r);
        ((IconButton) t).setNoIcon(true);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    private String label(String code) {
        String n = rates.names.get(code);
        return n == null ? code : code + "  " + n;
    }

    private void saveCurrencies() { InkPrefs.put(a, "moneyFrom", from); InkPrefs.put(a, "moneyTo", to); }

    private void choose(final boolean isFrom) {
        if (rates.isEmpty()) { Toast.makeText(a, "Update the rates first", Toast.LENGTH_SHORT).show(); return; }
        final List<String> codes = new ArrayList<>(rates.rates.keySet());
        codes.add("EUR");
        java.util.Collections.sort(codes);
        String[] names = new String[codes.size()];
        int sel = -1;
        String cur = isFrom ? from : to;
        for (int i = 0; i < names.length; i++) { names[i] = label(codes.get(i)); if (codes.get(i).equals(cur)) sel = i; }
        Ui.choose(a, isFrom ? "Convert from" : "Convert to", names, sel, i -> {
            if (isFrom) from = codes.get(i); else to = codes.get(i);
            saveCurrencies();
            draw();
        });
    }

    private void update() {
        if (loading) return;
        loading = true;
        status.setText("Updating…");
        new Thread(() -> {
            String err = null;
            Rates fresh = null;
            try { fresh = Rates.fetch(a); } catch (Exception e) { err = e.getMessage() == null ? "No connection" : e.getMessage(); }
            final Rates r = fresh;
            final String er = err;
            a.runOnUiThread(() -> {
                loading = false;
                if (a.isDestroyed()) return;
                if (r != null) rates = r;
                draw();
                if (er != null) status.setText("Could not update: " + er + (rates.isEmpty() ? "" : ". Using the saved rates."));
            });
        }).start();
    }

    private void draw() {
        fromBtn.setText(from + " ▾");
        toBtn.setText(to + " ▾");
        if (rates.isEmpty()) status.setText("No exchange rates are saved yet. Tap Update rates (needs internet).");
        else {
            String when = rates.fetched == 0 ? "" : "  Updated " + DateUtils.getRelativeTimeSpanString(rates.fetched, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
            status.setText("European Central Bank reference rates for " + rates.date + ", via frankfurter.dev." + when
                    + "\nRates are for information only and are not what a bank or card will charge.");
        }
        compute();
    }

    private void compute() {
        if (result == null) return;
        double amt = 0;
        try { amt = Double.parseDouble(amount.getText().toString().trim().replace(',', '.')); } catch (NumberFormatException ignored) {}
        double out = RateMath.convert(amt, from, to, rates.rates);
        if (Double.isNaN(out)) { result.setText("–"); rateLine.setText(""); return; }
        result.setText(money(out, to));
        double one = RateMath.convert(1, from, to, rates.rates);
        rateLine.setText("1 " + from + " = " + String.format(Locale.getDefault(), one >= 100 ? "%.2f" : "%.4f", one) + " " + to);
    }

    private static String money(double v, String code) {
        NumberFormat f = NumberFormat.getNumberInstance();
        try {
            int digits = Currency.getInstance(code).getDefaultFractionDigits();
            f.setMinimumFractionDigits(Math.max(0, digits));
            f.setMaximumFractionDigits(Math.max(0, digits));
        } catch (RuntimeException e) {
            f.setMaximumFractionDigits(2);
        }
        return f.format(v) + " " + code;
    }
}
