package app.eink.core;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Software "clear ghosting": flash the whole screen, then back to the theme. Most e-ink panels do a
 * full refresh on a change that large. Does nothing on a normal screen.
 */
public final class Eink {
    public static final String[] STYLE_IDS = {"bw", "wb", "w", "b"};
    public static final String[] STYLE_NAMES = {"Black, then white (strongest)", "White, then black", "White only (gentle)", "Black only"};

    private static int pages;

    private Eink() {}

    public static void flash(Activity a) {
        if (!Display.isEink(a)) return;
        final ViewGroup content = a.findViewById(android.R.id.content);
        if (content == null) return;
        int[] one;
        switch (InkPrefs.getStr(a, InkPrefs.FLASH_STYLE, "bw")) {
            case "wb": one = new int[]{0xFFFFFFFF, 0xFF000000}; break;
            case "w": one = new int[]{0xFFFFFFFF}; break;
            case "b": one = new int[]{0xFF000000}; break;
            default: one = new int[]{0xFF000000, 0xFFFFFFFF};
        }
        final List<Integer> colors = new ArrayList<>();
        for (int col : one) colors.add(col);

        final View v = new View(a);
        v.setBackgroundColor(colors.get(0));
        v.setClickable(true); // swallow touches while flashing
        content.addView(v, new FrameLayout.LayoutParams(-1, -1));
        final Handler h = new Handler(Looper.getMainLooper());
        final int step = 220;
        for (int i = 1; i < colors.size(); i++) {
            final int color = colors.get(i);
            h.postDelayed(() -> v.setBackgroundColor(color), (long) i * step);
        }
        h.postDelayed(() -> content.removeView(v), (long) colors.size() * step);
    }

    /** Counts page turns and refreshes every Nth one, if that setting is on. */
    public static void pageTurned(final Activity a) {
        int n = InkPrefs.getInt(a, InkPrefs.FLASH_PAGES, 5);
        if (n <= 0 || ++pages % n != 0) return;
        new Handler(Looper.getMainLooper()).postDelayed(() -> flash(a), 150); // let the new page draw first
    }
}
