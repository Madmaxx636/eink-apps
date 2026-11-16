package app.eink.core;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.util.TypedValue;

import java.util.Locale;

/** Which kind of screen we are on, and the colors and sizes that follow from it. */
public final class Display {
    /** Words in the device's name that mean e-ink. Auto mode is only a guess; the first-launch question and Settings decide. */
    private static final String[] EINK_WORDS = {"onyx", "boox", "bigme", "meebook", "mudita", "supernote", "ireader", "likebook",
            "boyue", "hanvon", "dasung", "palma", "eink", "e-ink", "e_ink", "epd", "ereader", "e-reader", "tolino", "inkpad",
            "inkpal", "inkbook", "inkcase", "inkphone", "minimal company", "moaan", "pocketbook"};

    /** Every name the phone gives itself, in one lowercase string. */
    static String identity() {
        return (Build.MANUFACTURER + " " + Build.BRAND + " " + Build.MODEL + " " + Build.DEVICE + " " + Build.PRODUCT + " "
                + Build.HARDWARE + " " + Build.BOARD + " " + Build.DISPLAY + " " + sys("ro.product.name") + " " + sys("ro.build.characteristics")
                + " " + sys("ro.product.system.brand") + " " + sys("ro.product.vendor.brand")).toLowerCase(Locale.ROOT);
    }

    /** A system property, or an empty string. Vendors of e-ink phones often leave a hint in here. */
    private static String sys(String key) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Object v = c.getMethod("get", String.class, String.class).invoke(null, key, "");
            return v == null ? "" : v.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /** What matched, or an empty string if nothing in the device's names says e-ink. */
    public static String detectReason() {
        String s = identity();
        for (String w : EINK_WORDS) if (s.contains(w)) return "\"" + w + "\" in the device name";
        // Hisense's e-ink phones are the A5, A7 and A9 series.
        if (Build.MANUFACTURER.toLowerCase(Locale.ROOT).contains("hisense") && Build.MODEL.toLowerCase(Locale.ROOT).matches("a[0-9].*")) return "Hisense A-series model";
        // Some e-ink builds expose a property with "eink" in its name; checking a few common ones.
        for (String k : new String[]{"ro.vendor.eink", "persist.vendor.eink", "sys.eink.mode", "ro.eink", "ro.hardware.eink", "persist.sys.eink"})
            if (!sys(k).isEmpty()) return "system property " + k;
        return "";
    }

    public static boolean detectedEink() { return !detectReason().isEmpty(); }

    /** One line for Settings: what the phone calls itself, so a wrong guess can be understood. */
    public static String describeDevice() {
        return Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ")";
    }

    public static boolean isEink(Context c) {
        String m = InkPrefs.getStr(c, InkPrefs.MODE, "auto");
        if (m.equals("eink")) return true;
        if (m.equals("normal")) return false;
        return detectedEink();
    }

    public static boolean isDark(Context c) {
        String t = InkPrefs.getStr(c, InkPrefs.THEME, isEink(c) ? "light" : "system");
        if (t.equals("dark")) return true;
        if (t.equals("light")) return false;
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    /** E-ink gets pure black and white: grays are dithered and slow to settle. */
    public static int bg(Context c) {
        boolean e = isEink(c);
        return isDark(c) ? (e ? 0xFF000000 : 0xFF121212) : 0xFFFFFFFF;
    }

    public static int fg(Context c) {
        boolean e = isEink(c);
        return isDark(c) ? (e ? 0xFFFFFFFF : 0xFFEDEDED) : (e ? 0xFF000000 : 0xFF111111);
    }

    /** Secondary text. On e-ink it stays full strength; only the size and weight tell it apart. */
    public static int dim(Context c) {
        if (isEink(c)) return fg(c);
        return isDark(c) ? 0xFF9A9A9A : 0xFF666666;
    }

    public static int accent(Context c) {
        if (isEink(c)) return fg(c);
        return isDark(c) ? 0xFF8AB4F8 : 0xFF1A5FB4;
    }

    public static float scale(Context c) {
        return Math.max(50, Math.min(250, InkPrefs.getInt(c, InkPrefs.SCALE, 100))) / 100f;
    }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    /** Text size in sp scaled by the user's setting. Pass it to TextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, ...). */
    public static float sp(Context c, float base) { return base * scale(c); }

    /** Compact is the default: smaller buttons, rows and gaps. Comfortable gives bigger touch targets. */
    public static boolean compact(Context c) { return !"comfortable".equals(InkPrefs.getStr(c, InkPrefs.UI_SIZE, "compact")); }

    /** Height of a button. */
    public static int btnH(Context c) { return dp(c, compact(c) ? 38 : 48); }

    /** Width of an outline or divider, in pixels. */
    public static int line(Context c) { return Math.max(1, dp(c, compact(c) ? 1 : 2)); }

    /** Screen width in dp, for layouts that change on small screens (a 3.5 inch phone is about 320dp). */
    public static int widthDp(Context c) {
        return Math.round(c.getResources().getDisplayMetrics().widthPixels / c.getResources().getDisplayMetrics().density);
    }
}
