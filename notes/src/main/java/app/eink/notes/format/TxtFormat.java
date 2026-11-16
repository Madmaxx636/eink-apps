package app.eink.notes.format;

/** Plain text in and out. Lists keep their markers so nothing is lost. */
public final class TxtFormat {
    private TxtFormat() {}

    public static String write(Doc d) {
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (int i = 0; i < d.paras.size(); i++) {
            Doc.Para p = d.paras.get(i);
            if (i > 0) b.append('\n');
            if (Doc.OL.equals(p.list)) b.append(++n).append(". "); else n = 0;
            if (Doc.UL.equals(p.list)) b.append("• ");
            b.append(p.text());
        }
        return b.toString();
    }

    public static Doc read(String text) {
        Doc d = new Doc();
        for (String line : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            Doc.Para p = d.newPara();
            p.add(new Doc.Run(line));
        }
        return d;
    }
}
