package app.eink.calc;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import app.eink.core.Display;
import app.eink.core.IconButton;
import app.eink.core.InkPrefs;
import app.eink.core.Ui;

/** The keypad: basic and scientific, with memory, history, sign change, squares and reciprocals. */
final class CalcTab extends Tab {
    private final StringBuilder input = new StringBuilder();
    private String answer = "";
    private String lastAnswer = "0";
    private boolean showingAnswer;
    private boolean degrees = true;
    private boolean sci;
    private TextView exprView, resultView, modeBtn, memView, sciBtn;

    CalcTab(CalcActivity a) { super(a); }

    void restore(Bundle b) {
        if (b == null) return;
        input.append(b.getString("input", ""));
        answer = b.getString("answer", "");
        showingAnswer = b.getBoolean("showing");
        lastAnswer = b.getString("last", "0");
    }

    void save(Bundle o) {
        o.putString("input", input.toString());
        o.putString("answer", answer);
        o.putBoolean("showing", showingAnswer);
        o.putString("last", lastAnswer);
    }

    @Override View build() {
        degrees = InkPrefs.getBool(a, "degrees", true);
        sci = InkPrefs.getBool(a, "sci", false);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = Display.dp(a, 6);
        root.setPadding(p, 0, p, p);

        // top line: Basic or Scientific, and an M when something is in memory
        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        sciBtn = Ui.button(a, sci ? "Basic" : "Scientific", () -> {
            sci = !sci;
            InkPrefs.put(a, "sci", sci);
            a.rebuildTab();
        });
        ((IconButton) sciBtn).setNoIcon(true);
        sciBtn.setMinHeight(0);
        sciBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, Display.sp(a, 12));
        top.addView(sciBtn, new LinearLayout.LayoutParams(-2, Display.dp(a, 28)));
        memView = Ui.dimText(a, "", 13);
        memView.setPadding(Display.dp(a, 10), 0, 0, 0);
        top.addView(memView, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout disp = new LinearLayout(a);
        disp.setOrientation(LinearLayout.VERTICAL);
        disp.setGravity(Gravity.BOTTOM | Gravity.END);
        disp.setPadding(Display.dp(a, 8), 0, Display.dp(a, 8), Display.dp(a, 4));
        exprView = Ui.dimText(a, "", 16);
        exprView.setGravity(Gravity.END);
        exprView.setMaxLines(2);
        resultView = Ui.text(a, "0", 40, true);
        resultView.setGravity(Gravity.END);
        resultView.setMaxLines(1);
        resultView.setAutoSizeTextTypeUniformWithConfiguration(14, Math.round(Display.sp(a, 44)), 2, TypedValue.COMPLEX_UNIT_SP);
        disp.addView(exprView, new LinearLayout.LayoutParams(-1, -2));
        disp.addView(resultView, new LinearLayout.LayoutParams(-1, 0, 1f));
        disp.setOnLongClickListener(v -> { copy(); return true; });
        root.addView(disp, new LinearLayout.LayoutParams(-1, 0, 1.3f));

        if (sci) {
            row(root, "sin", "cos", "tan", degrees ? "Deg" : "Rad");
            row(root, "ln", "log", "√", "x²");
            row(root, "(", ")", "^", "π");
            row(root, "!", "1/x", "e", "Ans");
        }
        row(root, "MC", "MR", "M+", "M−");
        row(root, "C", "⌫", "%", "÷");
        row(root, "7", "8", "9", "×");
        row(root, "4", "5", "6", "−");
        row(root, "1", "2", "3", "+");
        row(root, "±", "0", ".", "=");
        render();
        return root;
    }

    private void row(LinearLayout parent, String... keys) {
        LinearLayout r = new LinearLayout(a);
        for (final String k : keys) {
            TextView t = Ui.button(a, k, () -> press(k));
            ((IconButton) t).setNoIcon(true);
            t.setMinHeight(0);
            t.setMinWidth(0);
            boolean memory = k.startsWith("M") && k.length() == 2;
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, Display.sp(a, memory || k.length() > 2 ? 14 : 20));
            if (k.equals("Deg") || k.equals("Rad")) modeBtn = t;
            if (k.equals("=")) Ui.style(t, true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            int m = Display.dp(a, 2);
            lp.setMargins(m, m, m, m);
            r.addView(t, lp);
        }
        parent.addView(r, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private static boolean isOp(char c) { return "+−×÷^".indexOf(c) >= 0; }

    /** What the display shows now as a number: the answer, or what is typed so far if it adds up. */
    private double value() {
        try {
            if (showingAnswer) return Double.parseDouble(answer);
            if (input.length() == 0) return 0;
            return Expr.evalValue(closed(input.toString()), degrees);
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    private static String closed(String s) {
        int open = 0;
        for (int i = 0; i < s.length(); i++) { if (s.charAt(i) == '(') open++; else if (s.charAt(i) == ')') open--; }
        StringBuilder b = new StringBuilder(s);
        while (open-- > 0) b.append(')');
        return b.toString();
    }

    private void press(String k) {
        // After "=" a digit starts fresh; an operator or a postfix key carries on from the answer.
        if (showingAnswer) {
            boolean carry = k.equals("+") || k.equals("−") || k.equals("×") || k.equals("÷") || k.equals("^") || k.equals("%") || k.equals("!")
                    || k.equals("x²") || k.equals("1/x") || k.equals("±");
            boolean memory = k.equals("MC") || k.equals("M+") || k.equals("M−") || k.equals("Deg") || k.equals("Rad");
            if (!memory) {
                input.setLength(0);
                if (carry) input.append(answer.startsWith("-") ? "(−" + answer.substring(1) + ")" : answer);
                showingAnswer = false;
                if (k.equals("⌫")) { render(); return; }
            }
        }
        switch (k) {
            case "C": input.setLength(0); answer = ""; break;
            case "⌫": if (input.length() > 0) input.setLength(input.length() - Edit.backLen(input.toString())); break;
            case "=": equals(); return;
            case "Deg": case "Rad":
                degrees = !degrees;
                InkPrefs.put(a, "degrees", degrees);
                if (modeBtn != null) modeBtn.setText(degrees ? "Deg" : "Rad");
                break;
            case "Ans": input.append(lastAnswer.startsWith("-") ? "(−" + lastAnswer.substring(1) + ")" : lastAnswer); break;
            case "MC": CalcStore.setMemory(a, 0); break;
            case "MR": {
                double m = CalcStore.memory(a);
                if (m != 0) input.append(m < 0 ? "(−" + Expr.format(-m) + ")" : Expr.format(m));
                break;
            }
            case "M+": case "M−": {
                double v = value();
                if (Double.isNaN(v)) { toast("Nothing to store"); break; }
                CalcStore.setMemory(a, CalcStore.memory(a) + (k.equals("M+") ? v : -v));
                break;
            }
            case "±":
                if (input.length() > 0) { String s = Edit.negateLast(input.toString()); input.setLength(0); input.append(s); }
                break;
            case "x²": if (input.length() > 0) input.append("^2"); break;
            case "1/x": {
                String s = input.length() == 0 ? lastAnswer : input.toString();
                input.setLength(0);
                input.append(Edit.reciprocal(s));
                break;
            }
            case "π": input.append('π'); break;
            case "sin": case "cos": case "tan": case "ln": case "log": input.append(k).append('('); break;
            case "√": input.append("√("); break;
            case "+": case "−": case "×": case "÷": case "^":
                // A second operator replaces the first, except a minus after × ÷ ^ which makes a negative number.
                if (input.length() > 0 && isOp(input.charAt(input.length() - 1))) {
                    boolean negAfter = k.equals("−") && input.charAt(input.length() - 1) != '−' && input.charAt(input.length() - 1) != '+';
                    if (!negAfter) input.setLength(input.length() - 1);
                }
                if (input.length() == 0 && !k.equals("−")) input.append(lastAnswer);
                input.append(k);
                break;
            default: input.append(k);
        }
        render();
    }

    private void equals() {
        if (input.length() == 0) return;
        try {
            String full = closed(input.toString());
            answer = Expr.eval(full, degrees);
            lastAnswer = answer;
            showingAnswer = true;
            exprView.setText(full + " =");
            resultView.setText(answer);
            CalcStore.addHistory(a, full + " = " + answer);
        } catch (Expr.Error e) {
            showingAnswer = false;
            exprView.setText(input);
            resultView.setText(e.getMessage().isEmpty() ? "0" : e.getMessage());
        }
        memView.setText(memoryText());
    }

    private String memoryText() {
        double m = CalcStore.memory(a);
        return m == 0 ? "" : "M = " + Expr.format(m);
    }

    private void render() {
        memView.setText(memoryText());
        if (showingAnswer) return;
        exprView.setText("");
        resultView.setText(input.length() == 0 ? "0" : input);
    }

    private void toast(String s) { Toast.makeText(a, s, Toast.LENGTH_SHORT).show(); }

    private void copy() {
        String t = showingAnswer ? answer : input.toString();
        if (t.isEmpty()) return;
        ((ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("result", t));
        toast("Copied " + t);
    }

    /** Past sums: pick one to carry on from its answer. */
    void showHistory() {
        final List<String> h = CalcStore.history(a);
        if (h.isEmpty()) { toast("No history yet"); return; }
        String[] names = new String[h.size() + 1];
        for (int i = 0; i < h.size(); i++) names[i] = h.get(i);
        names[h.size()] = "Clear history";
        Ui.choose(a, "History", names, -1, i -> {
            if (i == h.size()) { CalcStore.clearHistory(a); return; }
            String entry = h.get(i);
            String ans = entry.substring(entry.lastIndexOf('=') + 1).trim();
            input.setLength(0);
            input.append(ans.startsWith("-") ? "(−" + ans.substring(1) + ")" : ans);
            showingAnswer = false;
            render();
        });
    }
}
