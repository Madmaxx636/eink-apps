package app.eink.notes;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.AlignmentSpan;
import android.text.style.CharacterStyle;
import android.text.style.LeadingMarginSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.ParagraphStyle;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import app.eink.notes.format.Doc;

/**
 * Everything about formatted text in the editor: the custom spans, turning a Doc into editable text and
 * back, and the operations the toolbar runs. Paragraph settings (heading, list, alignment) are spans over
 * whole paragraphs, rewritten after any edit that adds or removes a line break.
 */
final class Spans {
    private Spans() {}

    /** A heading paragraph: bigger and bold. Affects measuring, so lines get taller. */
    static final class HeadingSpan extends MetricAffectingSpan implements ParagraphStyle {
        final int level;

        HeadingSpan(int level) { this.level = level; }

        private void apply(TextPaint p) {
            p.setTextSize(p.getTextSize() * Doc.HEADING[level]);
            p.setFakeBoldText(true);
        }

        @Override public void updateMeasureState(TextPaint p) { apply(p); }
        @Override public void updateDrawState(TextPaint p) { apply(p); }
    }

    /** A bullet or a number in the left margin of a list paragraph. Numbers count the list items just above. */
    static final class ListSpan implements LeadingMarginSpan {
        final String kind;
        private final int margin;

        ListSpan(String kind, int margin) { this.kind = kind; this.margin = margin; }

        @Override public int getLeadingMargin(boolean first) { return margin; }

        @Override public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline, int bottom,
                                                CharSequence text, int start, int end, boolean first, Layout layout) {
            if (!first) return;
            String label = "•";
            if (Doc.OL.equals(kind)) {
                int n = 1;
                if (text instanceof Spanned) {
                    Spanned s = (Spanned) text;
                    int pos = start;
                    while (pos > 0) {
                        int prevStart = s.toString().lastIndexOf('\n', pos - 2) + 1;
                        ListSpan[] prev = s.getSpans(prevStart, pos, ListSpan.class);
                        boolean ol = false;
                        for (ListSpan l : prev) if (Doc.OL.equals(l.kind)) ol = true;
                        if (!ol) break;
                        n++;
                        pos = prevStart;
                    }
                }
                label = n + ".";
            }
            Paint.Style old = p.getStyle();
            p.setStyle(Paint.Style.FILL);
            float w = p.measureText(label), gap = p.getTextSize() * 0.4f;
            c.drawText(label, dir > 0 ? x + margin - w - gap : x - margin + gap, baseline, p);
            p.setStyle(old);
        }
    }

    // ---- Doc <-> editable text ----

    static SpannableStringBuilder toEditable(Doc d, int listMargin) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        List<PA> paras = new ArrayList<>();
        for (int i = 0; i < d.paras.size(); i++) {
            Doc.Para p = d.paras.get(i);
            PA a = new PA();
            a.s = sb.length();
            for (Doc.Run r : p.runs) {
                int rs = sb.length();
                sb.append(r.text.replace('\n', ' '));
                int re = sb.length();
                if (re == rs) continue;
                if (r.bold) sb.setSpan(new StyleSpan(Typeface.BOLD), rs, re, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
                if (r.italic) sb.setSpan(new StyleSpan(Typeface.ITALIC), rs, re, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
                if (r.underline) sb.setSpan(new UnderlineSpan(), rs, re, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
                if (r.strike) sb.setSpan(new StrikethroughSpan(), rs, re, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
                if (r.size != 1f) sb.setSpan(new RelativeSizeSpan(r.size), rs, re, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
                if (r.family != null) sb.setSpan(new TypefaceSpan(r.family), rs, re, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
            }
            if (i < d.paras.size() - 1) sb.append('\n');
            a.e = sb.length();
            a.heading = p.heading;
            a.list = p.list;
            a.align = p.align;
            paras.add(a);
        }
        writeParas(sb, paras, listMargin);
        return sb;
    }

    static Doc toDoc(Spanned s) {
        Doc d = new Doc();
        for (PA a : paragraphs(s)) {
            Doc.Para p = d.newPara();
            p.heading = a.heading;
            p.list = a.list;
            p.align = a.align;
            int end = a.e > a.s && s.charAt(a.e - 1) == '\n' ? a.e - 1 : a.e;
            int pos = a.s;
            while (pos < end) {
                int next = Math.min(end, s.nextSpanTransition(pos, end, CharacterStyle.class));
                if (next <= pos) next = end;
                Doc.Run r = new Doc.Run(s.subSequence(pos, next).toString());
                for (StyleSpan sp : s.getSpans(pos, next, StyleSpan.class)) {
                    if ((sp.getStyle() & Typeface.BOLD) != 0) r.bold = true;
                    if ((sp.getStyle() & Typeface.ITALIC) != 0) r.italic = true;
                }
                if (hasPlain(s, pos, next, UnderlineSpan.class)) r.underline = true;
                if (hasPlain(s, pos, next, StrikethroughSpan.class)) r.strike = true;
                for (RelativeSizeSpan sp : s.getSpans(pos, next, RelativeSizeSpan.class)) r.size = Doc.snapSize(sp.getSizeChange());
                for (TypefaceSpan sp : s.getSpans(pos, next, TypefaceSpan.class)) {
                    String f = sp.getFamily();
                    r.family = "serif".equals(f) || "monospace".equals(f) ? f : null;
                }
                p.add(r);
                pos = next;
            }
        }
        if (d.paras.isEmpty()) d.newPara();
        return d;
    }

    /** True if a span of this type covers the stretch, ignoring the IME's own composing-text underline. */
    private static boolean hasPlain(Spanned s, int a, int b, Class<?> cls) {
        for (Object sp : s.getSpans(a, b, cls)) if ((s.getSpanFlags(sp) & Spanned.SPAN_COMPOSING) == 0) return true;
        return false;
    }

    // ---- character formatting ----

    /** kind: b, i, u, s (value Boolean), z (value Float size), f (value String family or null). */
    static void setChar(Editable e, int a, int b, String kind, Object value) {
        if (a >= b) return;
        Class<?> cls = classFor(kind);
        for (Object sp : e.getSpans(a, b, cls)) {
            if (!matches(e, sp, kind)) continue;
            int s = e.getSpanStart(sp), en = e.getSpanEnd(sp), fl = e.getSpanFlags(sp);
            e.removeSpan(sp);
            if (s < a) e.setSpan(copy(sp), s, a, fl);
            if (en > b) e.setSpan(copy(sp), b, en, fl);
        }
        Object add = null;
        switch (kind) {
            case "b": if (Boolean.TRUE.equals(value)) add = new StyleSpan(Typeface.BOLD); break;
            case "i": if (Boolean.TRUE.equals(value)) add = new StyleSpan(Typeface.ITALIC); break;
            case "u": if (Boolean.TRUE.equals(value)) add = new UnderlineSpan(); break;
            case "s": if (Boolean.TRUE.equals(value)) add = new StrikethroughSpan(); break;
            case "z": if (value != null && (Float) value != 1f) add = new RelativeSizeSpan((Float) value); break;
            case "f": if (value != null) add = new TypefaceSpan((String) value); break;
            default: break;
        }
        if (add != null) e.setSpan(add, a, b, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
    }

    private static Class<?> classFor(String kind) {
        switch (kind) {
            case "b": case "i": return StyleSpan.class;
            case "u": return UnderlineSpan.class;
            case "s": return StrikethroughSpan.class;
            case "z": return RelativeSizeSpan.class;
            default: return TypefaceSpan.class;
        }
    }

    private static boolean matches(Spanned s, Object sp, String kind) {
        if (kind.equals("b")) return (((StyleSpan) sp).getStyle() & Typeface.BOLD) != 0;
        if (kind.equals("i")) return (((StyleSpan) sp).getStyle() & Typeface.ITALIC) != 0;
        if (kind.equals("u")) return (s.getSpanFlags(sp) & Spanned.SPAN_COMPOSING) == 0;
        return true;
    }

    private static Object copy(Object sp) {
        if (sp instanceof StyleSpan) return new StyleSpan(((StyleSpan) sp).getStyle());
        if (sp instanceof UnderlineSpan) return new UnderlineSpan();
        if (sp instanceof StrikethroughSpan) return new StrikethroughSpan();
        if (sp instanceof RelativeSizeSpan) return new RelativeSizeSpan(((RelativeSizeSpan) sp).getSizeChange());
        return new TypefaceSpan(((TypefaceSpan) sp).getFamily());
    }

    /** Is the format on for the whole selection, or at the cursor (judged by the character before it)? */
    static boolean isOn(Spanned s, int a, int b, String kind) {
        if (a == b) { if (a == 0) return false; a--; }
        int covered = 0;
        for (Object sp : s.getSpans(a, b, classFor(kind))) {
            if (!matches(s, sp, kind)) continue;
            covered += Math.min(b, s.getSpanEnd(sp)) - Math.max(a, s.getSpanStart(sp));
        }
        return covered >= b - a;
    }

    static float sizeAt(Spanned s, int a) {
        int p = a > 0 ? a - 1 : a;
        for (RelativeSizeSpan sp : s.getSpans(p, Math.min(s.length(), p + 1), RelativeSizeSpan.class)) return Doc.snapSize(sp.getSizeChange());
        return 1f;
    }

    static String familyAt(Spanned s, int a) {
        int p = a > 0 ? a - 1 : a;
        for (TypefaceSpan sp : s.getSpans(p, Math.min(s.length(), p + 1), TypefaceSpan.class)) {
            String f = sp.getFamily();
            return "serif".equals(f) || "monospace".equals(f) ? f : null;
        }
        return null;
    }

    // ---- paragraphs ----

    static final class PA {
        int s, e;           // e includes the paragraph's line break, if it has one
        int heading;
        String list;
        int align;
    }

    static List<PA> paragraphs(Spanned s) {
        List<PA> out = new ArrayList<>();
        String t = s.toString();
        int pos = 0;
        while (true) {
            int nl = t.indexOf('\n', pos);
            PA a = new PA();
            a.s = pos;
            a.e = nl < 0 ? t.length() : nl + 1;
            read(s, a);
            out.add(a);
            if (nl < 0 || nl + 1 > t.length()) break;
            pos = nl + 1;
            if (pos == t.length()) {   // text ends with a line break: one more, empty, paragraph
                PA last = new PA();
                last.s = last.e = pos;
                read(s, last);
                out.add(last);
                break;
            }
        }
        return out;
    }

    /** Settings come from the first span that touches the paragraph. */
    private static void read(Spanned s, PA a) {
        int hi = a.e, lo = a.s;
        HeadingSpan[] h = first(s, lo, hi, HeadingSpan.class);
        if (h.length > 0) a.heading = h[0].level;
        ListSpan[] l = first(s, lo, hi, ListSpan.class);
        if (l.length > 0) a.list = l[0].kind;
        AlignmentSpan.Standard[] al = first(s, lo, hi, AlignmentSpan.Standard.class);
        if (al.length > 0) a.align = al[0].getAlignment() == Layout.Alignment.ALIGN_CENTER ? Doc.CENTER
                : al[0].getAlignment() == Layout.Alignment.ALIGN_OPPOSITE ? Doc.RIGHT : Doc.LEFT;
    }

    private static <T> T[] first(final Spanned s, int a, int b, Class<T> cls) {
        T[] arr = s.getSpans(a, b, cls);
        List<T> keep = new ArrayList<>();
        for (T t : arr) {
            int st = s.getSpanStart(t), en = s.getSpanEnd(t);
            if (a == b) {
                // An empty paragraph at the very end takes the settings of a span that grows into it (the paragraph above, while
                // you are typing), but not one that merely stops there.
                int pm = s.getSpanFlags(t) & Spanned.SPAN_POINT_MARK_MASK;
                boolean endInclusive = pm == (Spanned.SPAN_INCLUSIVE_INCLUSIVE & Spanned.SPAN_POINT_MARK_MASK)
                        || pm == (Spanned.SPAN_EXCLUSIVE_INCLUSIVE & Spanned.SPAN_POINT_MARK_MASK);
                if (st <= a && en >= a && (en > a || endInclusive)) keep.add(t);
            } else if (en > a && st < b) keep.add(t);
        }
        Collections_sort(keep, s);
        @SuppressWarnings("unchecked") T[] res = (T[]) java.lang.reflect.Array.newInstance(cls, keep.size());
        return keep.toArray(res);
    }

    private static void Collections_sort(List<?> l, final Spanned s) {
        @SuppressWarnings("unchecked") List<Object> lo = (List<Object>) l;
        java.util.Collections.sort(lo, new Comparator<Object>() {
            @Override public int compare(Object x, Object y) { return Integer.compare(s.getSpanStart(x), s.getSpanStart(y)); }
        });
    }

    /** Replaces every paragraph span with exactly one set per paragraph. */
    static void writeParas(Editable e, List<PA> paras, int listMargin) {
        for (HeadingSpan sp : e.getSpans(0, e.length(), HeadingSpan.class)) e.removeSpan(sp);
        for (ListSpan sp : e.getSpans(0, e.length(), ListSpan.class)) e.removeSpan(sp);
        for (AlignmentSpan.Standard sp : e.getSpans(0, e.length(), AlignmentSpan.Standard.class)) e.removeSpan(sp);
        for (int i = 0; i < paras.size(); i++) {
            PA a = paras.get(i);
            // Typing at the start of a paragraph joins it; typing at the start of the next one does not. The last one grows at its end.
            int flags = i == paras.size() - 1 ? Spanned.SPAN_INCLUSIVE_INCLUSIVE : Spanned.SPAN_INCLUSIVE_EXCLUSIVE;
            if (a.heading > 0) e.setSpan(new HeadingSpan(a.heading), a.s, a.e, flags);
            if (a.list != null) e.setSpan(new ListSpan(a.list, listMargin), a.s, a.e, flags);
            if (a.align != Doc.LEFT) e.setSpan(new AlignmentSpan.Standard(a.align == Doc.CENTER ? Layout.Alignment.ALIGN_CENTER : Layout.Alignment.ALIGN_OPPOSITE), a.s, a.e, flags);
        }
    }

    /** Indexes of the paragraphs the selection touches. */
    static int[] touched(List<PA> paras, int selA, int selB) {
        int first = 0, last = paras.size() - 1;
        for (int i = 0; i < paras.size(); i++) {
            PA p = paras.get(i);
            if (selA >= p.s && (selA < p.e || i == paras.size() - 1)) first = i;
            if (selB >= p.s && (selB < p.e || i == paras.size() - 1)) last = i;
        }
        // A selection that ends right at the start of a paragraph does not include that paragraph.
        if (selB > selA && last > first && selB == paras.get(last).s) last--;
        return new int[]{first, Math.max(first, last)};
    }
}
