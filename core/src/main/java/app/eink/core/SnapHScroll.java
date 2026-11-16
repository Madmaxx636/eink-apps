package app.eink.core;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.HorizontalScrollView;

/**
 * A sideways scroller for e-ink: it moves a whole page of columns at a time and never stops between them,
 * so no column is cut off. On a normal screen, or with no page width set, it scrolls freely.
 */
public class SnapHScroll extends HorizontalScrollView {
    private int pageWidth;
    private float downX, downY;
    private boolean dragging;
    private final int slop;

    public SnapHScroll(Context c) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    /** Pixels in one page of columns; 0 turns snapping off. */
    public void setPageWidth(int px) { pageWidth = px; }

    private boolean snapping() { return pageWidth > 0; }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        if (!snapping()) return super.onInterceptTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: downX = e.getX(); downY = e.getY(); dragging = false; break;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && Math.abs(e.getX() - downX) > slop * 2 && Math.abs(e.getX() - downX) > Math.abs(e.getY() - downY) * 1.5f) dragging = true;
                break;
            default: break;
        }
        return dragging;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!snapping()) return super.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: downX = e.getX(); downY = e.getY(); dragging = false; return true;
            case MotionEvent.ACTION_MOVE: return true;     // the content stays put while the finger moves; it jumps on release
            case MotionEvent.ACTION_UP: {
                if (dragging) {
                    float dx = e.getX() - downX;
                    int pages = Math.max(1, (int) Math.ceil(getChildAt(0).getWidth() / (double) pageWidth));
                    int cur = Math.round(getScrollX() / (float) pageWidth);
                    if (Math.abs(dx) > Display.dp(getContext(), 36)) cur += dx < 0 ? 1 : -1;
                    cur = Math.max(0, Math.min(pages - 1, cur));
                    scrollTo(cur * pageWidth, 0);
                }
                dragging = false;
                return true;
            }
            default: dragging = false; return true;
        }
    }

    @Override public void fling(int velocity) { if (!snapping()) super.fling(velocity); }
}
