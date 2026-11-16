package app.eink.notes;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import app.eink.notes.format.Doc;
import app.eink.notes.format.DocxWriter;
import app.eink.notes.format.HtmlWriter;
import app.eink.notes.format.OdtWriter;
import app.eink.notes.format.TxtFormat;

/** The formats a document can be saved as. Slow: call from a background thread. */
final class Exporter {
    enum Kind {
        DOCX("Word document (.docx)", ".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        ODT("OpenDocument text (.odt)", ".odt", "application/vnd.oasis.opendocument.text"),
        PDF("PDF", ".pdf", "application/pdf"),
        HTML("Web page (.html)", ".html", "text/html"),
        TXT("Plain text (.txt)", ".txt", "text/plain");

        final String label, ext, mime;

        Kind(String label, String ext, String mime) { this.label = label; this.ext = ext; this.mime = mime; }
    }

    private Exporter() {}

    static void write(Kind k, Doc d, String title, OutputStream out) throws IOException {
        switch (k) {
            case DOCX: DocxWriter.write(d, out); break;
            case ODT: OdtWriter.write(d, out); break;
            case HTML: out.write(HtmlWriter.write(d, title).getBytes(StandardCharsets.UTF_8)); break;
            case TXT: out.write(TxtFormat.write(d).getBytes(StandardCharsets.UTF_8)); break;
            default: pdf(d, out);
        }
        out.flush();
    }

    /** A4, 2 cm margins, 12 pt body text, page numbers at the bottom. */
    private static void pdf(Doc d, OutputStream out) throws IOException {
        final int pw = 595, ph = 842, margin = 57;
        int cw = pw - 2 * margin, chh = ph - 2 * margin;
        SpannableStringBuilder text = Spans.toEditable(d, 24);
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(0xFF000000);
        tp.setTextSize(Doc.BODY_PT);
        StaticLayout layout = StaticLayout.Builder.obtain(text, 0, text.length(), tp, cw)
                .setLineSpacing(0f, 1.15f).setIncludePad(false).build();
        PdfDocument pdf = new PdfDocument();
        try {
            Paint num = new Paint(Paint.ANTI_ALIAS_FLAG);
            num.setTextSize(9);
            num.setTextAlign(Paint.Align.CENTER);
            int line = 0, count = layout.getLineCount(), pageNo = 1;
            do {
                int top = layout.getLineTop(line), end = line;
                while (end < count && layout.getLineBottom(end) - top <= chh) end++;
                if (end == line) end = line + 1;       // a line taller than the page still gets a page
                int bottom = layout.getLineBottom(end - 1);
                PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(pw, ph, pageNo).create());
                Canvas c = page.getCanvas();
                c.save();
                c.translate(margin, margin - top);
                c.clipRect(0, top, cw, bottom);
                layout.draw(c);
                c.restore();
                c.drawText(String.valueOf(pageNo), pw / 2f, ph - margin / 2f, num);
                pdf.finishPage(page);
                line = end;
                pageNo++;
            } while (line < count);
            pdf.writeTo(out);
        } finally {
            pdf.close();
        }
    }
}
