package app.eink.notes;

import android.text.Html;
import android.text.Layout;
import android.text.Spanned;
import android.text.style.AlignmentSpan;
import android.text.style.BulletSpan;
import android.text.style.RelativeSizeSpan;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import app.eink.notes.format.Doc;
import app.eink.notes.format.DocxReader;
import app.eink.notes.format.OdtReader;
import app.eink.notes.format.TxtFormat;

/** Opens a file as a document, chosen by its name and type. Slow: call from a background thread. */
final class Importer {
    private static final int MAX = 40 * 1024 * 1024;

    private Importer() {}

    static Doc read(String name, String mime, InputStream in) throws IOException {
        byte[] all = readAll(in);
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String m = mime == null ? "" : mime;
        if (n.endsWith(".docx") || m.contains("wordprocessingml")) return DocxReader.read(new ByteArrayInputStream(all));
        if (n.endsWith(".odt") || m.equals("application/vnd.oasis.opendocument.text")) return OdtReader.read(new ByteArrayInputStream(all));
        if (n.endsWith(".doc") || n.endsWith(".rtf") || n.endsWith(".pdf")) throw new IOException("Only .docx, .odt, .html and text files can be opened. Save the file as .docx or .odt first.");
        String text = new String(all, StandardCharsets.UTF_8);
        if (n.endsWith(".html") || n.endsWith(".htm") || m.equals("text/html")) return html(text);
        return TxtFormat.read(text);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > MAX) throw new IOException("That file is too large to open here");
        }
        return out.toByteArray();
    }

    /** Android's own HTML parser handles the basics: bold, italic, underline, strike, bullets and alignment. */
    private static Doc html(String html) {
        Spanned s = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        // Reuse the editor's conversion for character formatting, then read paragraph settings Android's way.
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(s);
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') sb.delete(sb.length() - 1, sb.length());
        Doc d = Spans.toDoc(sb);
        java.util.List<Spans.PA> paras = Spans.paragraphs(sb);
        for (int i = 0; i < paras.size() && i < d.paras.size(); i++) {
            Spans.PA a = paras.get(i);
            Doc.Para p = d.paras.get(i);
            if (sb.getSpans(a.s, a.e, BulletSpan.class).length > 0) p.list = Doc.UL;
            for (AlignmentSpan.Standard al : sb.getSpans(a.s, a.e, AlignmentSpan.Standard.class))
                p.align = al.getAlignment() == Layout.Alignment.ALIGN_CENTER ? Doc.CENTER
                        : al.getAlignment() == Layout.Alignment.ALIGN_OPPOSITE ? Doc.RIGHT : Doc.LEFT;
            // Headings come out of Android as a larger, bold paragraph.
            float big = 1f;
            for (RelativeSizeSpan r : sb.getSpans(a.s, a.e, RelativeSizeSpan.class)) big = Math.max(big, r.getSizeChange());
            if (big >= 1.4f) { p.heading = 1; for (Doc.Run r : p.runs) r.size = 1f; }
            else if (big >= 1.1f) { p.heading = 2; for (Doc.Run r : p.runs) r.size = 1f; }
        }
        return d;
    }
}
