package app.eink.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Deals rows out to pages. No Android classes, so it is tested on a plain JVM.
 * A "divider" is a row no taller than the spacer limit: it never starts a page and never ends one.
 */
final class Paging {
    private Paging() {}

    /** One page: rows from (inclusive) to to (exclusive). The first and last rows of a page are real rows. */
    static final class Page {
        final int from, to;

        Page(int from, int to) { this.from = from; this.to = to; }
    }

    /** Pages that each fit in pageH, in order. A row taller than a page gets a page of its own. */
    static List<Page> plan(int[] h, int pageH, int spacer) {
        List<Page> pages = new ArrayList<>();
        int n = h.length, i = 0;
        while (i < n && h[i] <= spacer) i++;
        while (i < n) {
            int from = i, acc = 0, lastReal = i, j = i;
            while (j < n) {
                if (h[j] > spacer) {
                    if (acc > 0 && acc + h[j] > pageH) break;
                    acc += h[j];
                    lastReal = j;
                } else acc += h[j];
                j++;
            }
            pages.add(new Page(from, lastReal + 1));
            i = lastReal + 1;
            while (i < n && h[i] <= spacer) i++;
        }
        return pages;
    }

    static int used(int[] h, Page p) {
        int s = 0;
        for (int i = p.from; i < p.to; i++) s += h[i];
        return s;
    }

    /** Space to put above each real row after the first on a page, so a full page is filled. Zero for the last page. */
    static int gap(int[] h, Page p, int pageH, int spacer, int maxGap, boolean lastPage) {
        if (lastPage) return 0;
        int rows = 0;
        for (int i = p.from + 1; i < p.to; i++) if (h[i] > spacer) rows++;
        int spare = pageH - used(h, p);
        return rows == 0 || spare <= 0 ? 0 : Math.min(maxGap, spare / rows);
    }
}
