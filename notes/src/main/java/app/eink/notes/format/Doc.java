package app.eink.notes.format;

import java.util.ArrayList;
import java.util.List;

/**
 * The document as the exporters and importers see it: paragraphs of formatted runs. It has no Android
 * classes in it, so the file formats can be tested on a plain JVM.
 */
public final class Doc {
    /** Text sizes you can pick, as multiples of the body size. */
    public static final float[] SIZES = {0.8f, 1f, 1.25f, 1.5f, 2f};
    /** Body size on paper, in points. */
    public static final float BODY_PT = 12f;
    /** Heading sizes as multiples of the body size. Index 1..3. */
    public static final float[] HEADING = {1f, 1.8f, 1.4f, 1.15f};

    public static final int LEFT = 0, CENTER = 1, RIGHT = 2;
    public static final String UL = "ul", OL = "ol";

    public static final class Run {
        public String text;
        public boolean bold, italic, underline, strike;
        public float size = 1f;
        public String family;   // null for the default, else "serif" or "monospace"

        public Run(String text) { this.text = text; }

        public boolean sameLook(Run o) {
            return bold == o.bold && italic == o.italic && underline == o.underline && strike == o.strike
                    && size == o.size && (family == null ? o.family == null : family.equals(o.family));
        }

        public Run copyLook(String t) {
            Run r = new Run(t);
            r.bold = bold; r.italic = italic; r.underline = underline; r.strike = strike; r.size = size; r.family = family;
            return r;
        }
    }

    public static final class Para {
        public int heading;     // 0 is body text, 1 to 3 are headings
        public String list;     // null, UL or OL
        public int align;
        public final List<Run> runs = new ArrayList<>();

        public String text() {
            StringBuilder b = new StringBuilder();
            for (Run r : runs) b.append(r.text);
            return b.toString();
        }

        public void add(Run r) {
            if (r.text.isEmpty()) return;
            // Neighbouring runs that look the same are one run.
            if (!runs.isEmpty() && runs.get(runs.size() - 1).sameLook(r)) runs.get(runs.size() - 1).text += r.text;
            else runs.add(r);
        }

        /** True if this paragraph is in a list of the given kind (null means not in a list). */
        public boolean list2eq(String kind) { return list == null ? kind == null : list.equals(kind); }

        public Para copyAttrs() {
            Para p = new Para();
            p.heading = heading; p.list = list; p.align = align;
            return p;
        }
    }

    public final List<Para> paras = new ArrayList<>();

    public Para newPara() {
        Para p = new Para();
        paras.add(p);
        return p;
    }

    public String plainText() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < paras.size(); i++) {
            if (i > 0) b.append('\n');
            b.append(paras.get(i).text());
        }
        return b.toString();
    }

    public static float snapSize(float ratio) {
        float best = 1f, d = Float.MAX_VALUE;
        for (float s : SIZES) if (Math.abs(s - ratio) < d) { d = Math.abs(s - ratio); best = s; }
        return best;
    }

    /** XML text with the characters XML forbids removed and the five special ones escaped. */
    public static String esc(String s) {
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&') b.append("&amp;");
            else if (c == '<') b.append("&lt;");
            else if (c == '>') b.append("&gt;");
            else if (c == '"') b.append("&quot;");
            else if (c == '\t' || c == '\n' || c == '\r' || (c >= 0x20 && c <= 0xD7FF) || (c >= 0xE000 && c <= 0xFFFD)
                    || Character.isSurrogate(c)) b.append(c);
        }
        return b.toString();
    }
}
