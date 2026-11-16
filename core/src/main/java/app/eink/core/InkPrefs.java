package app.eink.core;

import android.content.Context;
import android.content.SharedPreferences;

/** Settings every app in the suite shares. Each app keeps its own copy, in its own storage. */
public final class InkPrefs {
    /** "auto", "eink" or "normal". */
    public static final String MODE = "mode";
    /** "light", "dark" or "system". */
    public static final String THEME = "theme";
    /** Text size in percent. */
    public static final String SCALE = "scale";
    /** Flash the screen every N page turns; 0 is never. */
    public static final String FLASH_PAGES = "flashPages";
    /** One of Eink.STYLE_IDS. */
    public static final String FLASH_STYLE = "flashStyle";
    public static final String VOLKEYS = "volKeys";
    /** Set once the first-launch screen-type question has been shown. */
    public static final String ASKED_SCREEN = "askedScreen";
    /** Button look: "pill" (round or oval), "box", "underline" or "none". */
    public static final String BTN_STYLE = "btnStyle";
    /** Where the action buttons sit: "top" or "bottom". */
    public static final String BAR_POS = "barPos";
    /** Draw an icon beside the label on buttons that have one. */
    public static final String BTN_ICONS = "btnIcons";
    /** "compact" (default) or "comfortable". */
    public static final String UI_SIZE = "uiSize";
    /** Where the Prev and Next page buttons sit on e-ink: "bottom" (default) or "top". */
    public static final String PAGE_POS = "pagePos";
    /** The page strip: "full" (buttons and page number), "number" (page number only) or "hidden". */
    public static final String PAGE_BAR = "pageBar";

    private InkPrefs() {}

    public static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("ink", Context.MODE_PRIVATE);
    }

    public static String getStr(Context c, String k, String d) { return sp(c).getString(k, d); }

    public static int getInt(Context c, String k, int d) { return sp(c).getInt(k, d); }

    public static boolean getBool(Context c, String k, boolean d) { return sp(c).getBoolean(k, d); }

    public static void put(Context c, String k, String v) { sp(c).edit().putString(k, v).apply(); }

    public static void put(Context c, String k, int v) { sp(c).edit().putInt(k, v).apply(); }

    public static void put(Context c, String k, boolean v) { sp(c).edit().putBoolean(k, v).apply(); }
}
