package app.eink.core;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small view builders so every app looks and behaves the same. Large targets, hard edges, no animation. */
public final class Ui {
    public interface IntCb { void run(int v); }
    public interface StrCb { void run(String v); }

    private Ui() {}

    public static TextView text(Context c, CharSequence s, float baseSp, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, Display.sp(c, baseSp));
        t.setTextColor(Display.fg(c));
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    public static TextView dimText(Context c, CharSequence s, float baseSp) {
        TextView t = text(c, s, baseSp, false);
        t.setTextColor(Display.dim(c));
        return t;
    }

    private static GradientDrawable box(Context c, int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(Display.dp(c, Display.isEink(c) ? 2 : 1), stroke);
        g.setCornerRadius(Display.dp(c, Display.isEink(c) ? 4 : 10));
        return g;
    }

    /** A button in the look chosen in Settings, with an icon if its label has one (New, Settings, Back and so on). */
    public static TextView button(Context c, CharSequence label, final Runnable onClick) {
        IconButton t = new IconButton(c, label);
        if (onClick != null) t.setOnClickListener(v -> onClick.run());
        return t;
    }

    /** Draws a button as switched on or off. Used for toolbar toggles such as Bold. */
    public static void style(TextView t, boolean on) {
        if (t instanceof IconButton) ((IconButton) t).setOn(on);
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Display.isEink(c) ? Display.fg(c) : Display.dim(c));
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, Display.line(c)));
        return v;
    }

    /** A tappable row: label on the left, current value underneath. Used by settings and lists. */
    public static View row(Context c, CharSequence title, CharSequence sub, final Runnable onClick) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.VERTICAL);
        boolean compact = Display.compact(c);
        r.setMinimumHeight(Display.dp(c, compact ? 42 : 56));
        r.setGravity(Gravity.CENTER_VERTICAL);
        int p = Display.dp(c, compact ? 12 : 14);
        r.setPadding(p, Display.dp(c, compact ? 5 : 8), p, Display.dp(c, compact ? 5 : 8));
        r.addView(text(c, title, compact ? 16 : 17, false));
        if (sub != null && sub.length() > 0) r.addView(dimText(c, sub, compact ? 12 : 13));
        if (onClick != null) {
            r.setClickable(true);
            r.setOnClickListener(v -> onClick.run());
        }
        return r;
    }

    /** The dialog theme to pass to pickers such as TimePickerDialog. */
    public static int dialogTheme() { return R.style.InkDialog; }

    public static AlertDialog.Builder dialog(Context c) {
        return new AlertDialog.Builder(c, R.style.InkDialog);
    }

    public static void choose(Context c, String title, String[] names, int selected, final IntCb cb) {
        dialog(c).setTitle(title).setSingleChoiceItems(names, selected, (d, which) -> {
            d.dismiss();
            cb.run(which);
        }).setNegativeButton("Cancel", null).show();
    }

    public static void confirm(Context c, String title, String message, String ok, final Runnable yes) {
        dialog(c).setTitle(title).setMessage(message)
                .setPositiveButton(ok, (d, w) -> yes.run())
                .setNegativeButton("Cancel", null).show();
    }

    public static void prompt(Context c, String title, String initial, String ok, final StrCb cb) {
        final EditText e = new EditText(c);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setText(initial);
        e.setSelection(e.getText().length());
        int p = Display.dp(c, 16);
        LinearLayout box = new LinearLayout(c);
        box.setPadding(p, p / 2, p, 0);
        box.addView(e, new LinearLayout.LayoutParams(-1, -2));
        dialog(c).setTitle(title).setView(box)
                .setPositiveButton(ok, (d, w) -> cb.run(e.getText().toString().trim()))
                .setNegativeButton("Cancel", null).show();
    }
}
