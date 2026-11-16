package app.eink.notes.format;

/** A self-contained HTML page: opens in any browser and pastes into most editors with formatting. */
public final class HtmlWriter {
    private HtmlWriter() {}

    public static String write(Doc d, String title) {
        StringBuilder b = new StringBuilder("<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\"><title>")
                .append(Doc.esc(title)).append("</title>\n<style>body{max-width:42em;margin:2em auto;padding:0 1em;font-family:sans-serif;line-height:1.5}</style></head>\n<body>\n");
        String open = null;
        for (Doc.Para p : d.paras) {
            String want = p.list;
            if (open != null && !open.equals(want)) { b.append("</").append(open).append(">\n"); open = null; }
            if (want != null && open == null) { b.append('<').append(want).append(">\n"); open = want; }
            String tag = want != null ? "li" : p.heading > 0 ? "h" + p.heading : "p";
            b.append('<').append(tag);
            if (p.align != Doc.LEFT) b.append(" style=\"text-align:").append(p.align == Doc.CENTER ? "center" : "right").append('"');
            b.append('>');
            if (p.runs.isEmpty()) b.append("<br>");
            for (Doc.Run r : p.runs) {
                StringBuilder css = new StringBuilder();
                if (r.size != 1f) css.append("font-size:").append(r.size).append("em;");
                if (r.family != null) css.append("font-family:").append(r.family.equals("serif") ? "serif" : "monospace").append(';');
                String t = Doc.esc(r.text).replace("\t", "&emsp;");
                if (css.length() > 0) t = "<span style=\"" + css + "\">" + t + "</span>";
                if (r.strike) t = "<s>" + t + "</s>";
                if (r.underline) t = "<u>" + t + "</u>";
                if (r.italic) t = "<i>" + t + "</i>";
                if (r.bold) t = "<b>" + t + "</b>";
                b.append(t);
            }
            b.append("</").append(tag).append(">\n");
        }
        if (open != null) b.append("</").append(open).append(">\n");
        return b.append("</body></html>\n").toString();
    }
}
