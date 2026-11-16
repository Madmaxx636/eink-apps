package app.eink.core;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * A list of rows. On a normal screen it scrolls as usual.
 *
 * On e-ink it is a set of pages, and only the rows of the current page are on screen. Rows are measured first and
 * dealt out so that every page fits: nothing is cut off at the bottom, no row is split, there is no scrolling that
 * could show part of the next page, and a last page never holds only a divider line. The room left over on a full
 * page is spread between its rows. Swipe up for the next page and down for the previous one, or use the Prev and
 * Next strip, which appears only when there is more than one page (and can be a page number only, or hidden, in
 * Settings; it can sit at the top or the bottom).
 */
public class PagedScroll extends LinearLayout {
    /** The Prev/Next strip. It takes no space at all when it is off. */
    private static final class Bar extends LinearLayout {
        boolean off = true;

        Bar(Context c) { super(c); }

        @Override protected void onMeasure(int w, int h) {
            if (off) setMeasuredDimension(0, 0);
            else super.onMeasure(w, h);
        }
    }

    /** The page. A vertical swipe on it turns the page. */
    private final class Host extends LinearLayout {
        private final int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        private final int commit = Display.dp(getContext(), 36);
        private float downX, downY;
        private boolean swiping;

        Host(Context c) { super(c); setOrientation(VERTICAL); }

        private boolean vertical(MotionEvent e) {
            float dy = Math.abs(e.getY() - downY), dx = Math.abs(e.getX() - downX);
            return dy > slop * 2 && dy > dx * 1.5f;
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: downX = e.getX(); downY = e.getY(); swiping = false; break;
                case MotionEvent.ACTION_MOVE: if (!swiping && vertical(e)) swiping = true; break;   // take over from a row that was being pressed
                default: break;
            }
            return swiping;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: downX = e.getX(); downY = e.getY(); swiping = false; return true;
                case MotionEvent.ACTION_MOVE: if (!swiping && vertical(e)) swiping = true; return true;
                case MotionEvent.ACTION_UP:
                    if (swiping) {
                        float dy = e.getY() - downY;
                        if (Math.abs(dy) > commit) turn(dy < 0 ? 1 : -1);   // finger up means forward, like turning a page
                    }
                    swiping = false;
                    return true;
                case MotionEvent.ACTION_CANCEL: swiping = false; return true;
                default: return true;
            }
        }
    }

    private final boolean paged;
    private final List<View> rows = new ArrayList<>();
    private final int spacer, maxGap;

    // normal screens
    private final ScrollView scroll;
    private final LinearLayout column;

    // e-ink
    private final Host host;
    private final Bar pageBar;
    private final TextView label;
    private final View prev, next;
    private String mode;                       // full, number or hidden
    private List<Paging.Page> pages = new ArrayList<>();
    private int[] heights = new int[0];
    private int pageH, page, lastW = -1, lastH = -1;
    private boolean dirty = true, rebuildQueued;

    public PagedScroll(Context c) {
        super(c);
        setOrientation(VERTICAL);
        paged = Display.isEink(c);
        mode = InkPrefs.getStr(c, InkPrefs.PAGE_BAR, "full");
        spacer = Display.dp(c, 6);
        maxGap = Display.dp(c, 18);

        if (!paged) {
            scroll = new ScrollView(c);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setOverScrollMode(OVER_SCROLL_NEVER);
            scroll.setFillViewport(true);
            column = new LinearLayout(c);
            column.setOrientation(VERTICAL);
            scroll.addView(column, new ViewGroup.LayoutParams(-1, -2));
            addView(scroll, new LayoutParams(-1, 0, 1f));
            host = null; pageBar = null; label = null; prev = null; next = null;
            return;
        }
        scroll = null;
        column = null;

        label = Ui.text(c, "", 13, true);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, Display.dp(c, 6), 0, Display.dp(c, 6));
        label.setClickable(true);
        // Tap the page number to switch between the full strip and just the number.
        label.setOnClickListener(v -> {
            mode = mode.equals("full") ? "number" : "full";
            InkPrefs.put(getContext(), InkPrefs.PAGE_BAR, mode);
            dirty = true;
            refreshBar();
            requestLayout();
        });
        prev = Ui.button(c, "Prev", () -> turn(-1));
        next = Ui.button(c, "Next", () -> turn(1));
        pageBar = new Bar(c);
        pageBar.setOrientation(VERTICAL);
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Display.dp(c, 4);
        row.setPadding(pad, pad / 2, pad, pad / 2);
        row.addView(prev, new LayoutParams(Display.dp(c, 96), -2));
        row.addView(label, new LayoutParams(0, -2, 1f));
        row.addView(next, new LayoutParams(Display.dp(c, 96), -2));
        boolean top = "top".equals(InkPrefs.getStr(c, InkPrefs.PAGE_POS, "bottom"));
        if (top) { pageBar.addView(row); pageBar.addView(Ui.divider(c)); }
        else { pageBar.addView(Ui.divider(c)); pageBar.addView(row); }

        host = new Host(c);
        host.setClickable(true);
        if (top) addView(pageBar, new LayoutParams(-1, -2));
        addView(host, new LayoutParams(-1, 0, 1f));
        if (!top) addView(pageBar, new LayoutParams(-1, -2));
    }

    /** Replace everything with new rows and go back to the first page. */
    public void setRows(List<View> newRows) {
        rows.clear();
        rows.addAll(newRows);
        page = 0;
        if (!paged) {
            column.removeAllViews();
            for (View v : rows) {
                ViewGroup.LayoutParams own = v.getLayoutParams();
                column.addView(v, own instanceof LinearLayout.LayoutParams ? own : new LayoutParams(-1, -2));
            }
            scroll.scrollTo(0, 0);
            return;
        }
        host.removeAllViews();
        dirty = true;
        requestLayout();
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        // Rows are measured and dealt out to pages once there is a size, in one place, so there is no back and forth.
        if (paged && !rebuildQueued && (dirty || r - l != lastW || b - t != lastH)) {
            rebuildQueued = true;
            post(() -> { rebuildQueued = false; rebuild(); });
        }
    }

    private int rowHeight(View v, int width) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp != null && lp.height > 0) return lp.height;             // a divider has a fixed height
        v.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        return v.getMeasuredHeight();
    }

    private void rebuild() {
        int w = getWidth(), totalH = getHeight();
        if (w <= 0 || totalH <= 0) return;
        lastW = w;
        lastH = totalH;
        dirty = false;
        int n = rows.size();
        heights = new int[n];
        for (int i = 0; i < n; i++) heights[i] = rowHeight(rows.get(i), w);

        // Do the rows fit on one screen? If not, the strip takes room and the pages are planned for what is left.
        boolean needBar = Paging.plan(heights, totalH, spacer).size() > 1 && !mode.equals("hidden");
        int barH = 0;
        if (needBar) {
            pageBar.off = false;
            pageBar.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            barH = pageBar.getMeasuredHeight();
        }
        boolean wasOff = pageBar.off;
        pageBar.off = !needBar;
        pageH = totalH - barH;
        pages = Paging.plan(heights, pageH, spacer);
        page = Math.max(0, Math.min(page, Math.max(0, pages.size() - 1)));
        show();
        refreshBar();
        if (wasOff != pageBar.off) requestLayout();
    }

    /** Puts the rows of the current page on screen, with spare room spread between them. */
    private void show() {
        host.removeAllViews();
        if (pages.isEmpty()) return;
        Paging.Page p = pages.get(page);
        int gap = Paging.gap(heights, p, pageH, spacer, maxGap, page == pages.size() - 1);
        for (int i = p.from; i < p.to; i++) {
            View v = rows.get(i);
            boolean real = heights[i] > spacer;
            if (gap > 0 && i > p.from && real) host.addView(new View(getContext()), new LayoutParams(-1, gap));
            ViewGroup.LayoutParams own = v.getLayoutParams();
            if (own instanceof LinearLayout.LayoutParams) ((LinearLayout.LayoutParams) own).topMargin = 0;
            host.addView(v, own instanceof LinearLayout.LayoutParams ? own : new LayoutParams(-1, -2));
        }
    }

    private void refreshBar() {
        boolean buttons = mode.equals("full");
        label.setText((page + 1) + " / " + Math.max(1, pages.size()));
        prev.setVisibility(!buttons ? GONE : page > 0 ? VISIBLE : INVISIBLE);
        next.setVisibility(!buttons ? GONE : page < pages.size() - 1 ? VISIBLE : INVISIBLE);
    }

    /** Move one page. Returns false if already at that end. On a normal screen it scrolls by about a screen. */
    public boolean turn(int dir) {
        if (!paged) {
            int step = Math.max(1, scroll.getHeight() - Display.dp(getContext(), 48));
            int y = scroll.getScrollY();
            scroll.scrollBy(0, dir * step);
            return scroll.getScrollY() != y;
        }
        int n = page + dir;
        if (n < 0 || n >= pages.size()) return false;
        page = n;
        show();
        refreshBar();
        if (getContext() instanceof Activity) Eink.pageTurned((Activity) getContext());
        return true;
    }
}
