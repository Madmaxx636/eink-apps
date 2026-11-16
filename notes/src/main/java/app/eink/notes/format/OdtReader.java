package app.eink.notes.format;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Reads the text and basic formatting of an OpenDocument text (.odt) file. Tables are flattened to their
 * paragraphs; pictures, text boxes and footnotes are skipped.
 */
public final class OdtReader {
    private OdtReader() {}

    private static final class Style {
        String parent;
        Boolean bold, italic, underline, strike;
        Float size;
        String family;
        Integer align;
    }

    public static Doc read(InputStream in) throws IOException {
        byte[] content = null, styles = null;
        try (ZipInputStream z = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (e.getName().equals("content.xml")) content = Xml.readAll(z);
                else if (e.getName().equals("styles.xml")) styles = Xml.readAll(z);
            }
        }
        if (content == null) throw new IOException("This is not an OpenDocument text file");
        Handler h = new Handler();
        if (styles != null) Xml.parse(styles, h);   // named styles first, then the document's own
        h.inBody = false;
        Xml.parse(content, h);
        if (h.doc.paras.isEmpty()) h.doc.newPara();
        return h.doc;
    }

    private static final class Handler extends DefaultHandler {
        final Doc doc = new Doc();
        boolean inBody;
        private final Map<String, Style> styles = new HashMap<>();
        private final Map<String, String> listKinds = new HashMap<>();

        private Style cur;
        private String curListStyle;
        private final List<String> listStack = new ArrayList<>();
        private final List<String> spanStack = new ArrayList<>();
        private Doc.Para para;
        private String paraStyle;
        private int skip;      // inside something whose text we ignore
        private boolean lineStart;

        private static Boolean on(String v, String yes) { return v == null ? null : v.equals(yes); }

        @Override public void startElement(String u, String l, String q, Attributes a) {
            if (q.equals("office:body") || q.equals("office:text")) inBody = true;
            if (!inBody) { style(q, a); return; }
            if (skip > 0 || q.equals("draw:frame") || q.equals("text:note") || q.equals("office:annotation")) { skip++; return; }
            switch (q) {
                case "text:list": {
                    String s = a.getValue("text:style-name");
                    listStack.add(s != null ? s : (listStack.isEmpty() ? null : listStack.get(listStack.size() - 1)));
                    break;
                }
                case "text:p": case "text:h": {
                    para = doc.newPara();
                    paraStyle = a.getValue("text:style-name");
                    lineStart = true;
                    if (q.equals("text:h")) {
                        try { para.heading = Math.max(1, Math.min(3, Integer.parseInt(a.getValue("text:outline-level")))); } catch (Exception e) { para.heading = 1; }
                    } else if (paraStyle != null) {
                        String n = paraStyle.replace("_20_", " ").toLowerCase();
                        if (n.startsWith("heading ") && n.length() > 8 && Character.isDigit(n.charAt(8))) para.heading = Math.min(3, n.charAt(8) - '0');
                        else if (n.equals("title")) para.heading = 1;
                    }
                    if (!listStack.isEmpty()) {
                        String k = listKinds.get(listStack.get(listStack.size() - 1));
                        para.list = k == null ? Doc.UL : k;
                    }
                    Style ps = resolve(paraStyle);
                    if (ps.align != null) para.align = ps.align;
                    break;
                }
                case "text:span": spanStack.add(a.getValue("text:style-name")); break;
                case "text:s": {
                    int n = 1;
                    try { n = Integer.parseInt(a.getValue("text:c")); } catch (Exception ignored) {}
                    add(spaces(Math.min(n, 200)));
                    break;
                }
                case "text:tab": add("\t"); break;
                case "text:line-break":
                    if (para != null) { Doc.Para p = para.copyAttrs(); doc.paras.add(p); para = p; lineStart = true; }
                    break;
                default: break;
            }
        }

        private static String spaces(int n) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < n; i++) b.append(' ');
            return b.toString();
        }

        /** Merges a style with the ones it is based on, nearest first. */
        private Style resolve(String name) {
            Style out = new Style();
            int guard = 0;
            for (String n = name; n != null && guard++ < 10; ) {
                Style s = styles.get(n);
                if (s == null) break;
                if (out.bold == null) out.bold = s.bold;
                if (out.italic == null) out.italic = s.italic;
                if (out.underline == null) out.underline = s.underline;
                if (out.strike == null) out.strike = s.strike;
                if (out.size == null) out.size = s.size;
                if (out.family == null) out.family = s.family;
                if (out.align == null) out.align = s.align;
                n = s.parent;
            }
            return out;
        }

        private void add(String t) {
            if (para == null || skip > 0 || t.isEmpty()) return;
            Doc.Run r = new Doc.Run(t);
            // Paragraph style first, then each enclosing span, inner ones winning.
            apply(r, resolve(paraStyle));
            for (String s : spanStack) apply(r, resolve(s));
            // A heading gets its size and weight from being a heading, so the style's own copy is dropped.
            if (para.heading > 0) { r.size = 1f; r.bold = false; }
            para.add(r);
        }

        private static void apply(Doc.Run r, Style s) {
            if (s.bold != null) r.bold = s.bold;
            if (s.italic != null) r.italic = s.italic;
            if (s.underline != null) r.underline = s.underline;
            if (s.strike != null) r.strike = s.strike;
            if (s.size != null) r.size = s.size;
            if (s.family != null) r.family = s.family.equals("default") ? null : s.family;
        }

        @Override public void characters(char[] ch, int s, int n) {
            if (!inBody || para == null || skip > 0) return;
            String t = new String(ch, s, n).replaceAll("[ \\t\\r\\n]+", " ");
            if (lineStart && t.startsWith(" ")) t = t.substring(1);   // ODF drops spaces at the start of a paragraph
            if (t.isEmpty()) return;
            lineStart = false;
            add(t);
        }

        @Override public void endElement(String u, String l, String q) {
            if (inBody && skip > 0) {
                if (q.equals("draw:frame") || q.equals("text:note") || q.equals("office:annotation") || skip > 1) skip--;
                else skip--;
                return;
            }
            if (!inBody) {
                if (q.equals("style:style") || q.equals("text:list-style")) { cur = null; curListStyle = null; }
                return;
            }
            switch (q) {
                case "text:list": if (!listStack.isEmpty()) listStack.remove(listStack.size() - 1); break;
                case "text:p": case "text:h": para = null; break;
                case "text:span": if (!spanStack.isEmpty()) spanStack.remove(spanStack.size() - 1); break;
                default: break;
            }
        }

        /** Style definitions, in styles.xml and in the document's automatic styles. */
        private void style(String q, Attributes a) {
            switch (q) {
                case "style:style": {
                    String n = a.getValue("style:name");
                    if (n == null) return;
                    cur = new Style();
                    cur.parent = a.getValue("style:parent-style-name");
                    styles.put(n, cur);
                    break;
                }
                case "style:text-properties": {
                    if (cur == null) return;
                    cur.bold = on(a.getValue("fo:font-weight"), "bold");
                    cur.italic = on(a.getValue("fo:font-style"), "italic");
                    String ul = a.getValue("style:text-underline-style");
                    if (ul != null) cur.underline = !ul.equals("none");
                    String st = a.getValue("style:text-line-through-style");
                    if (st != null) cur.strike = !st.equals("none");
                    String fs = a.getValue("fo:font-size");
                    if (fs != null) cur.size = size(fs);
                    String ff = a.getValue("fo:font-family");
                    if (ff == null) ff = a.getValue("style:font-name");
                    if (ff != null) cur.family = family(ff);
                    break;
                }
                case "style:paragraph-properties": {
                    if (cur == null) return;
                    String al = a.getValue("fo:text-align");
                    if (al != null) cur.align = al.equals("center") ? Doc.CENTER : (al.equals("end") || al.equals("right")) ? Doc.RIGHT : Doc.LEFT;
                    break;
                }
                case "text:list-style": {
                    curListStyle = a.getValue("style:name");
                    break;
                }
                case "text:list-level-style-number":
                    if (curListStyle != null && !listKinds.containsKey(curListStyle)) listKinds.put(curListStyle, Doc.OL);
                    break;
                case "text:list-level-style-bullet": case "text:list-level-style-image":
                    if (curListStyle != null && !listKinds.containsKey(curListStyle)) listKinds.put(curListStyle, Doc.UL);
                    break;
                default: break;
            }
        }

        private static Float size(String v) {
            try {
                if (v.endsWith("%")) return Doc.snapSize(Float.parseFloat(v.substring(0, v.length() - 1)) / 100f);
                if (v.endsWith("pt")) return Doc.snapSize(Float.parseFloat(v.substring(0, v.length() - 2)) / Doc.BODY_PT);
            } catch (NumberFormatException ignored) {}
            return null;
        }

        private static String family(String f) {
            String s = f.toLowerCase();
            if (s.contains("courier") || s.contains("mono") || s.contains("consolas")) return "monospace";
            if (!s.contains("sans") && (s.contains("times") || s.contains("serif") || s.contains("georgia") || s.contains("cambria")
                    || s.contains("garamond") || s.contains("palatino"))) return "serif";
            return "default";
        }
    }
}
