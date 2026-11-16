package app.eink.notes.format;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Writes an OpenDocument text (.odt) file, the format LibreOffice and OpenOffice use. */
public final class OdtWriter {
    private static final String NS = "xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" "
            + "xmlns:style=\"urn:oasis:names:tc:opendocument:xmlns:style:1.0\" "
            + "xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\" "
            + "xmlns:fo=\"urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0\" ";

    private OdtWriter() {}

    public static void write(Doc d, OutputStream out) throws IOException {
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            // The mimetype entry must come first and must not be compressed, or other programs will not recognise the file.
            byte[] mt = "application/vnd.oasis.opendocument.text".getBytes(StandardCharsets.US_ASCII);
            ZipEntry e = new ZipEntry("mimetype");
            e.setMethod(ZipEntry.STORED);
            e.setSize(mt.length);
            e.setCompressedSize(mt.length);
            CRC32 crc = new CRC32();
            crc.update(mt);
            e.setCrc(crc.getValue());
            z.putNextEntry(e);
            z.write(mt);
            z.closeEntry();

            put(z, "META-INF/manifest.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<manifest:manifest xmlns:manifest=\"urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\" manifest:version=\"1.3\">"
                    + "<manifest:file-entry manifest:full-path=\"/\" manifest:version=\"1.3\" manifest:media-type=\"application/vnd.oasis.opendocument.text\"/>"
                    + "<manifest:file-entry manifest:full-path=\"content.xml\" manifest:media-type=\"text/xml\"/>"
                    + "<manifest:file-entry manifest:full-path=\"styles.xml\" manifest:media-type=\"text/xml\"/>"
                    + "</manifest:manifest>");
            put(z, "styles.xml", styles());
            put(z, "content.xml", content(d));
        }
    }

    private static String styles() {
        StringBuilder s = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><office:document-styles " + NS + "office:version=\"1.3\"><office:styles>"
                + "<style:default-style style:family=\"paragraph\"><style:paragraph-properties fo:margin-bottom=\"0.2cm\" fo:line-height=\"115%\"/>"
                + "<style:text-properties fo:font-size=\"12pt\" fo:font-family=\"Liberation Sans\"/></style:default-style>"
                + "<style:style style:name=\"Standard\" style:family=\"paragraph\" style:class=\"text\"/>");
        for (int h = 1; h <= 3; h++) {
            s.append("<style:style style:name=\"Heading_20_").append(h).append("\" style:display-name=\"Heading ").append(h)
                    .append("\" style:family=\"paragraph\" style:parent-style-name=\"Standard\" style:next-style-name=\"Standard\" style:default-outline-level=\"")
                    .append(h).append("\" style:class=\"text\"><style:paragraph-properties fo:margin-top=\"0.4cm\" fo:keep-with-next=\"always\"/>"
                            + "<style:text-properties fo:font-size=\"").append(Math.round(Doc.BODY_PT * Doc.HEADING[h] * 10) / 10f)
                    .append("pt\" fo:font-weight=\"bold\"/></style:style>");
        }
        return s.append("</office:styles></office:document-styles>").toString();
    }

    private static String content(Doc d) {
        Map<String, String> textStyles = new LinkedHashMap<>(); // style body -> name
        StringBuilder body = new StringBuilder();
        boolean inList = false;
        String listKind = null;
        for (Doc.Para p : d.paras) {
            if (inList && !p.list2eq(listKind)) { body.append("</text:list>"); inList = false; }
            if (p.list != null && !inList) {
                body.append("<text:list text:style-name=\"").append(Doc.UL.equals(p.list) ? "LUL" : "LOL").append("\">");
                inList = true;
                listKind = p.list;
            }
            if (p.list != null) body.append("<text:list-item>");
            String ps = "P" + p.heading + p.align;
            String tag = p.heading > 0 && p.list == null ? "text:h" : "text:p";
            body.append('<').append(tag).append(" text:style-name=\"").append(ps).append('"');
            if (tag.equals("text:h")) body.append(" text:outline-level=\"").append(p.heading).append('"');
            body.append('>');
            for (Doc.Run r : p.runs) {
                String t = text(r.text);
                String css = runStyle(r, p.heading > 0);
                if (css.isEmpty()) body.append(t);
                else {
                    String name = textStyles.get(css);
                    if (name == null) { name = "T" + (textStyles.size() + 1); textStyles.put(css, name); }
                    body.append("<text:span text:style-name=\"").append(name).append("\">").append(t).append("</text:span>");
                }
            }
            body.append("</").append(tag).append('>');
            if (p.list != null) body.append("</text:list-item>");
        }
        if (inList) body.append("</text:list>");

        StringBuilder a = new StringBuilder();
        for (Map.Entry<String, String> e : textStyles.entrySet())
            a.append("<style:style style:name=\"").append(e.getValue()).append("\" style:family=\"text\"><style:text-properties ").append(e.getKey()).append("/></style:style>");
        for (int h = 0; h <= 3; h++) for (int al = 0; al <= 2; al++) {
            a.append("<style:style style:name=\"P").append(h).append(al).append("\" style:family=\"paragraph\" style:parent-style-name=\"")
                    .append(h == 0 ? "Standard" : "Heading_20_" + h).append("\">");
            if (al != Doc.LEFT) a.append("<style:paragraph-properties fo:text-align=\"").append(al == Doc.CENTER ? "center" : "end").append("\"/>");
            a.append("</style:style>");
        }
        a.append("<text:list-style style:name=\"LUL\"><text:list-level-style-bullet text:level=\"1\" text:bullet-char=\"•\"><style:list-level-properties text:list-level-position-and-space-mode=\"label-alignment\"><style:list-level-label-alignment text:label-followed-by=\"listtab\" text:list-tab-stop-position=\"1.27cm\" fo:text-indent=\"-0.635cm\" fo:margin-left=\"1.27cm\"/></style:list-level-properties></text:list-level-style-bullet></text:list-style>");
        a.append("<text:list-style style:name=\"LOL\"><text:list-level-style-number text:level=\"1\" style:num-suffix=\".\" style:num-format=\"1\"><style:list-level-properties text:list-level-position-and-space-mode=\"label-alignment\"><style:list-level-label-alignment text:label-followed-by=\"listtab\" text:list-tab-stop-position=\"1.27cm\" fo:text-indent=\"-0.635cm\" fo:margin-left=\"1.27cm\"/></style:list-level-properties></text:list-level-style-number></text:list-style>");

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><office:document-content " + NS + "office:version=\"1.3\"><office:automatic-styles>"
                + a + "</office:automatic-styles><office:body><office:text>" + body + "</office:text></office:body></office:document-content>";
    }

    private static String runStyle(Doc.Run r, boolean inHeading) {
        StringBuilder s = new StringBuilder();
        if (r.bold) s.append("fo:font-weight=\"bold\" ");
        if (r.italic) s.append("fo:font-style=\"italic\" ");
        if (r.underline) s.append("style:text-underline-style=\"solid\" style:text-underline-width=\"auto\" style:text-underline-color=\"font-color\" ");
        if (r.strike) s.append("style:text-line-through-style=\"solid\" ");
        if (r.size != 1f && !inHeading) s.append("fo:font-size=\"").append(Math.round(Doc.BODY_PT * r.size * 10) / 10f).append("pt\" ");
        if (r.family != null) s.append("fo:font-family=\"").append(r.family.equals("serif") ? "Liberation Serif" : "Liberation Mono").append("\" ");
        return s.toString().trim();
    }

    /** ODF collapses runs of spaces, so extra spaces and tabs are written as elements. */
    private static String text(String s) {
        StringBuilder b = new StringBuilder();
        String e = Doc.esc(s);
        int spaces = 0;
        for (int i = 0; i < e.length(); i++) {
            char c = e.charAt(i);
            if (c == ' ') { spaces++; continue; }
            flushSpaces(b, spaces, false);
            spaces = 0;
            if (c == '\t') b.append("<text:tab/>"); else b.append(c);
        }
        flushSpaces(b, spaces, true);
        return b.toString();
    }

    private static void flushSpaces(StringBuilder b, int n, boolean last) {
        if (n == 0) return;
        b.append(' ');
        if (n > 1) b.append("<text:s text:c=\"").append(n - 1).append("\"/>");
    }

    private static void put(ZipOutputStream z, String name, String content) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(content.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }
}
