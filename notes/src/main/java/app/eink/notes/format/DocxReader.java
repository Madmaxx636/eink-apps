package app.eink.notes.format;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Reads the text and basic formatting of a Word (.docx) file: paragraphs, headings, bold, italic, underline,
 * strike, sizes, fonts, alignment and lists. Tables are flattened to their paragraphs; pictures are skipped.
 */
public final class DocxReader {
    private DocxReader() {}

    public static Doc read(InputStream in) throws IOException {
        byte[] document = null, numbering = null;
        try (ZipInputStream z = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (e.getName().equals("word/document.xml")) document = Xml.readAll(z);
                else if (e.getName().equals("word/numbering.xml")) numbering = Xml.readAll(z);
            }
        }
        if (document == null) throw new IOException("This is not a Word document");
        final Map<String, String> numFmt = new HashMap<>(); // numId -> "ul" or "ol"
        if (numbering != null) Xml.parse(numbering, new Numbering(numFmt));
        Body h = new Body(numFmt);
        Xml.parse(document, h);
        if (h.doc.paras.isEmpty()) h.doc.newPara();
        return h.doc;
    }

    private static final class Numbering extends DefaultHandler {
        private final Map<String, String> out;
        private final Map<String, String> abstractFmt = new HashMap<>();
        private final Map<String, String> numToAbstract = new HashMap<>();
        private String abs, num;
        private boolean firstLevel;

        Numbering(Map<String, String> out) { this.out = out; }

        @Override public void startElement(String u, String l, String q, Attributes a) {
            if (q.equals("w:abstractNum")) { abs = a.getValue("w:abstractNumId"); }
            else if (q.equals("w:lvl") && abs != null) firstLevel = "0".equals(a.getValue("w:ilvl"));
            else if (q.equals("w:numFmt") && abs != null && firstLevel) {
                String v = a.getValue("w:val");
                abstractFmt.put(abs, "bullet".equals(v) || "none".equals(v) ? Doc.UL : Doc.OL);
            }
            else if (q.equals("w:num")) num = a.getValue("w:numId");
            else if (q.equals("w:abstractNumId") && num != null) numToAbstract.put(num, a.getValue("w:val"));
        }

        @Override public void endElement(String u, String l, String q) {
            if (q.equals("w:abstractNum")) abs = null;
            else if (q.equals("w:num")) num = null;
        }

        @Override public void endDocument() {
            for (Map.Entry<String, String> e : numToAbstract.entrySet()) {
                String f = abstractFmt.get(e.getValue());
                out.put(e.getKey(), f == null ? Doc.UL : f);
            }
        }
    }

    private static final class Body extends DefaultHandler {
        private static final Pattern HEADING = Pattern.compile("(?i)heading\\s*([1-9])");
        final Doc doc = new Doc();
        private final Map<String, String> numFmt;
        private Doc.Para para;
        private Doc.Run look = new Doc.Run("");
        private boolean inRun, inPPr, inT;
        private final StringBuilder text = new StringBuilder();

        Body(Map<String, String> numFmt) { this.numFmt = numFmt; }

        private static boolean on(Attributes a) {
            String v = a.getValue("w:val");
            return v == null || !(v.equals("0") || v.equals("false") || v.equals("none") || v.equals("off"));
        }

        @Override public void startElement(String u, String l, String q, Attributes a) {
            switch (q) {
                case "w:p": para = doc.newPara(); break;
                case "w:pPr": inPPr = true; break;
                case "w:pStyle": {
                    String v = a.getValue("w:val");
                    if (para != null && v != null) {
                        Matcher m = HEADING.matcher(v);
                        if (m.find()) para.heading = Math.min(3, Integer.parseInt(m.group(1)));
                        else if (v.equalsIgnoreCase("Title")) para.heading = 1;
                        else if (v.toLowerCase().contains("listbullet")) para.list = Doc.UL;
                        else if (v.toLowerCase().contains("listnumber")) para.list = Doc.OL;
                    }
                    break;
                }
                case "w:numId": {
                    String v = a.getValue("w:val");
                    if (para != null && v != null && !v.equals("0")) para.list = numFmt.containsKey(v) ? numFmt.get(v) : Doc.UL;
                    break;
                }
                case "w:jc": {
                    String v = a.getValue("w:val");
                    if (para != null && inPPr && v != null) para.align = v.equals("center") ? Doc.CENTER : (v.equals("right") || v.equals("end")) ? Doc.RIGHT : Doc.LEFT;
                    break;
                }
                case "w:r": inRun = true; look = new Doc.Run(""); break;
                case "w:b": if (inRun && !inPPr) look.bold = on(a); break;
                case "w:i": if (inRun && !inPPr) look.italic = on(a); break;
                case "w:strike": if (inRun && !inPPr) look.strike = on(a); break;
                case "w:u": if (inRun && !inPPr) look.underline = on(a); break;
                case "w:sz": {
                    String v = a.getValue("w:val");
                    if (inRun && !inPPr && v != null) try { look.size = Doc.snapSize(Integer.parseInt(v) / 2f / Doc.BODY_PT); } catch (NumberFormatException ignored) {}
                    break;
                }
                case "w:rFonts": {
                    String f = a.getValue("w:ascii");
                    if (inRun && !inPPr && f != null) look.family = family(f);
                    break;
                }
                case "w:t": inT = true; text.setLength(0); break;
                case "w:tab": if (inRun && para != null && !inPPr) add("\t"); break;
                case "w:br": case "w:cr":
                    // A line break starts a new paragraph with the same settings.
                    if (inRun && para != null && !"page".equals(a.getValue("w:type"))) para = doc.paras.get(doc.paras.size() - 1) == para ? addPara(para) : para;
                    break;
                default: break;
            }
        }

        private Doc.Para addPara(Doc.Para like) {
            Doc.Para p = like.copyAttrs();
            doc.paras.add(p);
            return p;
        }

        private static String family(String f) {
            String s = f.toLowerCase();
            if (s.contains("courier") || s.contains("mono") || s.contains("consolas")) return "monospace";
            if (!s.contains("sans") && (s.contains("times") || s.contains("serif") || s.contains("georgia") || s.contains("cambria")
                    || s.contains("garamond") || s.contains("palatino") || s.contains("book"))) return "serif";
            return null;
        }

        private void add(String t) {
            if (para == null || t.isEmpty()) return;
            para.add(look.copyLook(t));
        }

        @Override public void characters(char[] ch, int s, int n) { if (inT) text.append(ch, s, n); }

        @Override public void endElement(String u, String l, String q) {
            switch (q) {
                case "w:t": inT = false; if (inRun) add(text.toString()); break;
                case "w:r": inRun = false; break;
                case "w:pPr": inPPr = false; break;
                case "w:p": para = null; break;
                default: break;
            }
        }
    }
}
