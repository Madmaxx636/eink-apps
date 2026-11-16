package app.eink.notes.format;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Writes a Word (.docx) file: styles for headings, real list numbering, direct formatting for the rest. */
public final class DocxWriter {
    private static final String NS = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"";

    private DocxWriter() {}

    public static void write(Doc d, OutputStream out) throws IOException {
        StringBuilder nums = new StringBuilder();
        String body = body(d, nums);
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            put(z, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                    + "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>"
                    + "<Override PartName=\"/word/numbering.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml\"/>"
                    + "</Types>");
            put(z, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                    + "</Relationships>");
            put(z, "word/_rels/document.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                    + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering\" Target=\"numbering.xml\"/>"
                    + "</Relationships>");
            put(z, "word/document.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document " + NS + "><w:body>"
                    + body + "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/></w:sectPr></w:body></w:document>");
            put(z, "word/styles.xml", styles());
            put(z, "word/numbering.xml", numbering(nums.toString()));
        }
    }

    private static String body(Doc d, StringBuilder numInstances) {
        StringBuilder b = new StringBuilder();
        int nextNum = 3;       // numId 1 is bullets, 2 is the first numbered list; later numbered lists restart at 1
        boolean inOl = false;
        int olId = 2;
        boolean firstOl = true;
        for (Doc.Para p : d.paras) {
            b.append("<w:p><w:pPr>");
            if (p.heading > 0) b.append("<w:pStyle w:val=\"Heading").append(p.heading).append("\"/>");
            if (p.list != null) {
                int id;
                if (Doc.UL.equals(p.list)) { id = 1; inOl = false; }
                else {
                    if (!inOl) {
                        if (firstOl) firstOl = false;
                        else {
                            olId = nextNum++;
                            numInstances.append("<w:num w:numId=\"").append(olId).append("\"><w:abstractNumId w:val=\"1\"/><w:lvlOverride w:ilvl=\"0\"><w:startOverride w:val=\"1\"/></w:lvlOverride></w:num>");
                        }
                        inOl = true;
                    }
                    id = olId;
                }
                b.append("<w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"").append(id).append("\"/></w:numPr>");
            } else inOl = false;
            if (p.align != Doc.LEFT) b.append("<w:jc w:val=\"").append(p.align == Doc.CENTER ? "center" : "right").append("\"/>");
            b.append("</w:pPr>");
            for (Doc.Run r : p.runs) run(b, r, p.heading > 0);
            b.append("</w:p>");
        }
        return b.toString();
    }

    private static void run(StringBuilder b, Doc.Run r, boolean inHeading) {
        b.append("<w:r><w:rPr>");
        if (r.family != null) {
            String f = r.family.equals("serif") ? "Times New Roman" : "Courier New";
            b.append("<w:rFonts w:ascii=\"").append(f).append("\" w:hAnsi=\"").append(f).append("\" w:cs=\"").append(f).append("\"/>");
        }
        if (r.bold) b.append("<w:b/>");
        if (r.italic) b.append("<w:i/>");
        if (r.strike) b.append("<w:strike/>");
        if (r.size != 1f && !inHeading) b.append("<w:sz w:val=\"").append(Math.round(Doc.BODY_PT * r.size * 2)).append("\"/>");
        if (r.underline) b.append("<w:u w:val=\"single\"/>");
        b.append("</w:rPr>");
        // Tabs become real tab elements; text goes in w:t with spaces kept.
        String[] parts = r.text.split("\t", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) b.append("<w:tab/>");
            if (!parts[i].isEmpty()) b.append("<w:t xml:space=\"preserve\">").append(Doc.esc(parts[i])).append("</w:t>");
        }
        b.append("</w:r>");
    }

    private static String styles() {
        StringBuilder s = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:styles " + NS + ">"
                + "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:cs=\"Calibri\"/><w:sz w:val=\"24\"/><w:szCs w:val=\"24\"/></w:rPr></w:rPrDefault>"
                + "<w:pPrDefault><w:pPr><w:spacing w:after=\"120\" w:line=\"276\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>"
                + "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:qFormat/></w:style>");
        for (int h = 1; h <= 3; h++) {
            s.append("<w:style w:type=\"paragraph\" w:styleId=\"Heading").append(h).append("\"><w:name w:val=\"heading ").append(h)
                    .append("\"/><w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:qFormat/><w:pPr><w:keepNext/><w:spacing w:before=\"240\" w:after=\"120\"/><w:outlineLvl w:val=\"")
                    .append(h - 1).append("\"/></w:pPr><w:rPr><w:b/><w:sz w:val=\"").append(Math.round(Doc.BODY_PT * Doc.HEADING[h] * 2)).append("\"/></w:rPr></w:style>");
        }
        return s.append("</w:styles>").toString();
    }

    private static String numbering(String extraNums) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:numbering " + NS + ">"
                + "<w:abstractNum w:abstractNumId=\"0\"><w:multiLevelType w:val=\"hybridMultilevel\"/><w:lvl w:ilvl=\"0\"><w:start w:val=\"1\"/><w:numFmt w:val=\"bullet\"/><w:lvlText w:val=\"•\"/><w:lvlJc w:val=\"left\"/><w:pPr><w:ind w:left=\"720\" w:hanging=\"360\"/></w:pPr></w:lvl></w:abstractNum>"
                + "<w:abstractNum w:abstractNumId=\"1\"><w:multiLevelType w:val=\"hybridMultilevel\"/><w:lvl w:ilvl=\"0\"><w:start w:val=\"1\"/><w:numFmt w:val=\"decimal\"/><w:lvlText w:val=\"%1.\"/><w:lvlJc w:val=\"left\"/><w:pPr><w:ind w:left=\"720\" w:hanging=\"360\"/></w:pPr></w:lvl></w:abstractNum>"
                + "<w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num>"
                + "<w:num w:numId=\"2\"><w:abstractNumId w:val=\"1\"/></w:num>"
                + extraNums + "</w:numbering>";
    }

    private static void put(ZipOutputStream z, String name, String content) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(content.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }
}
