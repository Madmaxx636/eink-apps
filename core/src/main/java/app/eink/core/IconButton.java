package app.eink.core;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

/**
 * A button with an optional icon, drawn in the style chosen in Settings: round, box, underline or none.
 * "On" is how toggles such as Bold show they are active. Pressing always flips the look, so a tap is
 * visible on e-ink without any animation.
 */
public final class IconButton extends TextView {
    private CharSequence label = "";
    private boolean on, iconOnly, vertical, noIcon, ready;

    IconButton(Context c, CharSequence label) {
        super(c);
        ready = true;
        setText(label);
        boolean compact = Display.compact(c);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, Display.sp(c, compact ? 14 : 16));
        setGravity(Gravity.CENTER);
        setMinHeight(Display.btnH(c));
        setMinWidth(Display.btnH(c));
        int p = Display.dp(c, compact ? 10 : 12);
        setPadding(p, Display.dp(c, compact ? 3 : 8), p, Display.dp(c, compact ? 3 : 8));
        setClickable(true);
        setSingleLine(false);
        apply();
    }

    @Override public void setText(CharSequence t, BufferType type) {
        if (!ready) { super.setText(t, type); return; }
        label = t == null ? "" : t;
        apply();
    }

    @Override public void setTextSize(int unit, float size) {
        super.setTextSize(unit, size);
        if (ready) apply();   // the icon is sized from the text
    }

    public void setOn(boolean on) { this.on = on; apply(); }

    /** Icon above the text instead of beside it: used in the bottom bar. */
    public void setVertical(boolean v) { vertical = v; apply(); }

    /** Show the icon without the words, when the bar is too narrow for both. */
    public void setIconOnly(boolean v) { iconOnly = v; apply(); }

    /** For buttons that must stay plain text, such as calculator keys. */
    public void setNoIcon(boolean v) { noIcon = v; apply(); }

    public boolean hasIcon() { return iconName() != null; }

    private String iconName() {
        if (noIcon || !InkPrefs.getBool(getContext(), InkPrefs.BTN_ICONS, true)) return null;
        return Icons.forLabel(label);
    }

    private static GradientDrawable shape(Context c, int fill, int stroke, float radiusDp, int strokePx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(strokePx, stroke);
        g.setCornerRadius(Display.dp(c, radiusDp));
        return g;
    }

    private void apply() {
        Context c = getContext();
        String style = InkPrefs.getStr(c, InkPrefs.BTN_STYLE, "pill");
        int bg = Display.bg(c), fg = Display.fg(c);
        int sw = Display.line(c);

        // Which state is filled: the pressed one is always the opposite of the resting one.
        boolean filled = !style.equals("underline") && on;
        boolean pressedFilled = !filled;

        StateListDrawable d = new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_pressed}, background(c, style, pressedFilled, false, fg, bg, sw));
        d.addState(new int[]{}, background(c, style, filled, on, fg, bg, sw));
        setBackground(d);
        int normalText = filled ? bg : fg, pressedText = pressedFilled ? bg : fg;
        setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_pressed}, {}}, new int[]{pressedText, normalText}));

        String icon = iconName();
        boolean textShown = !(iconOnly && icon != null);
        super.setText(textShown ? label : "", BufferType.NORMAL);
        setContentDescription(label);
        if (icon == null) {
            setCompoundDrawablesRelative(null, null, null, null);
            return;
        }
        int px = Math.round(getTextSize() * (vertical ? 1.5f : 1.25f));
        Drawable g = Icons.glyph(icon, px, normalText, pressedText);
        if (vertical) setCompoundDrawablesRelative(null, g, null, null);
        else setCompoundDrawablesRelative(g, null, null, null);
        setCompoundDrawablePadding(textShown ? Display.dp(c, vertical ? 2 : 7) : 0);
    }

    private Drawable background(Context c, String style, boolean filled, boolean thick, int fg, int bg, int sw) {
        switch (style) {
            case "box": return shape(c, filled ? fg : bg, fg, 2, sw);
            case "underline": {
                // No outline: a line under the text. A filled (pressed) state is a plain block.
                if (filled) return shape(c, fg, fg, 0, 0);
                GradientDrawable clear = shape(c, 0x00000000, 0x00000000, 0, 0);
                GradientDrawable line = shape(c, fg, fg, 0, 0);
                LayerDrawable l = new LayerDrawable(new Drawable[]{clear, line});
                l.setLayerGravity(1, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
                l.setLayerHeight(1, sw * (thick ? 3 : 2));
                l.setLayerInset(1, Display.dp(c, 6), 0, Display.dp(c, 6), Display.dp(c, 3));
                return l;
            }
            case "none": return filled ? shape(c, fg, fg, 8, 0) : new GradientDrawable();
            default: return shape(c, filled ? fg : bg, fg, 100, sw);    // round, which is an oval on a wide button
        }
    }
}
