package app.eink.weather;

import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import app.eink.core.Display;
import app.eink.core.InkActivity;
import app.eink.core.InkPrefs;
import app.eink.core.PagedScroll;
import app.eink.core.Ui;

/** Everything the forecast knows about one day: the figures for the whole day, then every hour. Swipe for the next or previous day. */
public class DayActivity extends InkActivity {
    private Forecast f;
    private int day;
    private PagedScroll pages;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        day = getIntent().getIntExtra("day", 0);
        try {
            f = Forecast.parse(InkPrefs.getStr(this, "cache", ""), InkPrefs.getStr(this, "cacheAq", null), InkPrefs.getStr(this, "cacheAlerts", null));
        } catch (JSONException e) {
            finish();
            return;
        }
        day = Math.max(0, Math.min(day, f.dn - 1));
        pages = new PagedScroll(this);
        setScreen(title(), pages);
        addBarButton("Back", this::finish);
        addBarButton("Next day", () -> go(1));
        addBarButton("Prev", () -> go(-1));
        pages.setRows(rows());
    }

    private String title() {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(f.dDate[day]);
            return day == 0 ? "Today" : new SimpleDateFormat("EEEE d MMM", Locale.getDefault()).format(d);
        } catch (ParseException e) {
            return f.dDate[day];
        }
    }

    private void go(int dir) {
        int to = day + dir;
        if (to < 0 || to >= f.dn) return;
        getIntent().putExtra("day", to);
        recreate();
    }

    @Override protected boolean onSwipe(int dir) {
        if (day + dir < 0 || day + dir >= f.dn) return false;
        go(dir);
        return true;
    }

    @Override protected boolean onPage(int dir) { return pages.turn(dir); }

    // ---- small helpers ----

    private boolean imp() {
        String u = InkPrefs.getStr(this, "units", "auto");
        if (u.equals("auto")) { String c = Locale.getDefault().getCountry(); return c.equals("US") || c.equals("LR") || c.equals("MM"); }
        return u.equals("imperial");
    }

    private static String f0(double v) { return Double.isNaN(v) ? "–" : String.valueOf(Math.round(v)); }

    private static String deg(double v) { return Double.isNaN(v) ? "–" : Math.round(v) + "°"; }

    private static String hm(double seconds) {
        if (Double.isNaN(seconds)) return "–";
        long m = Math.round(seconds / 60);
        return (m / 60) + " h " + (m % 60) + " min";
    }

    private String clock(String iso) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.ROOT).parse(iso);
            return new SimpleDateFormat(DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm a", Locale.getDefault()).format(d);
        } catch (ParseException e) {
            return "–";
        }
    }

    private View pad(View v, int top, int bottom) {
        v.setPadding(Display.dp(this, 14), Display.dp(this, top), Display.dp(this, 14), Display.dp(this, bottom));
        return v;
    }

    private View section(String title) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(this, title.toUpperCase(Locale.getDefault()), 12, true);
        t.setLetterSpacing(0.08f);
        pad(t, 14, 3);
        l.addView(t);
        l.addView(Ui.divider(this));
        return l;
    }

    private View pair(String[] a, String[] b) {
        LinearLayout r = new LinearLayout(this);
        pad(r, 3, 3);
        r.addView(cell(a), new LinearLayout.LayoutParams(0, -2, 1f));
        View second = b == null ? new View(this) : cell(b);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.leftMargin = Display.dp(this, 14);
        r.addView(second, lp);
        return r;
    }

    private View cell(String[] kv) {
        LinearLayout c = new LinearLayout(this);
        c.addView(Ui.dimText(this, kv[0], 13), new LinearLayout.LayoutParams(-2, -2));
        TextView v = Ui.text(this, kv[1], 14, true);
        v.setGravity(Gravity.END);
        v.setSingleLine(true);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        c.addView(v, new LinearLayout.LayoutParams(0, -2, 1f));
        return c;
    }

    // ---- the screen ----

    private List<View> rows() {
        List<View> rows = new ArrayList<>();
        boolean imp = imp();
        int i = day;
        String wu = f.windUnit.isEmpty() ? (imp ? "mph" : "km/h") : f.windUnit;
        String pu = f.precipUnit.isEmpty() ? (imp ? "in" : "mm") : f.precipUnit;

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.addView(Ui.text(this, Api.describe(f.dCode[i]), 22, true));
        head.addView(Ui.text(this, "High " + deg(f.dMax[i]) + "   Low " + deg(f.dMin[i]), 18, false));
        pad(head, 8, 6);
        rows.add(head);

        String[][] facts = {
                {"Feels like (high)", deg(f.dAppMax[i])}, {"Feels like (low)", deg(f.dAppMin[i])},
                {"Chance of rain", Double.isNaN(f.dProb[i]) ? "–" : f0(f.dProb[i]) + "%"},
                {"Rain total", Double.isNaN(f.dSum[i]) ? "–" : String.format(Locale.ROOT, imp ? "%.2f %s" : "%.1f %s", f.dSum[i], pu)},
                {"Hours of rain", f0(f.dPrecipHours[i])},
                {"Snow", Double.isNaN(f.dSnow[i]) ? "–" : String.format(Locale.ROOT, "%.1f %s", f.dSnow[i], imp ? "in" : "cm")},
                {"Wind (top)", f0(f.dWind[i]) + " " + wu + " " + WeatherActivity.compass(f.dDir[i])}, {"Gusts (top)", f0(f.dGust[i]) + " " + wu},
                {"UV index (top)", f.dUv[i] == 0 && Double.isNaN(f.dUv[i]) ? "–" : f0(f.dUv[i]) + " " + WeatherActivity.uvLevel(f.dUv[i])},
                {"UV, clear sky", Double.isNaN(f.dUvClear[i]) ? "–" : f0(f.dUvClear[i])},
                {"Humidity (avg)", Double.isNaN(f.dHumMean[i]) ? "–" : f0(f.dHumMean[i]) + "%"}, {"Dew point (avg)", deg(f.dDewMean[i])},
                {"Pressure (avg)", Double.isNaN(f.dPressMean[i]) ? "–" : imp ? String.format(Locale.ROOT, "%.2f inHg", f.dPressMean[i] * 0.02953) : f0(f.dPressMean[i]) + " hPa"},
                {"Cloud cover (avg)", Double.isNaN(f.dCloudMean[i]) ? "–" : f0(f.dCloudMean[i]) + "%"},
                {"Visibility (avg)", Double.isNaN(f.dVisMean[i]) ? "–" : imp ? String.format(Locale.ROOT, "%.1f mi", f.dVisMean[i] / 1609.34) : String.format(Locale.ROOT, "%.1f km", f.dVisMean[i] / 1000)},
                {"", ""},
                {"Sunrise", clock(f.sunrise[i])}, {"Sunset", clock(f.sunset[i])},
                {"Daylight", hm(f.dDaylight[i])}, {"Sunshine", hm(f.dSunshine[i])},
        };
        double[] aq = imp ? f.dAqiUs : f.dAqiEu;
        rows.add(section("The day"));
        for (int k = 0; k < facts.length; k += 2) {
            String[] left = facts[k], right = k + 1 < facts.length ? facts[k + 1] : null;
            if (left[0].isEmpty()) { k--; continue; }              // spacer entry: skip it so pairs stay in step
            if (right != null && right[0].isEmpty()) right = null;
            rows.add(pair(left, right));
        }
        if (i < aq.length && !Double.isNaN(aq[i])) rows.add(pair(new String[]{"Air quality (top)", f0(aq[i]) + " " + WeatherActivity.aqName(aq[i], imp)}, null));

        // every hour of the day
        int[] hrs = f.hoursOf(i);
        rows.add(section("Hour by hour"));
        rows.add(hourLine(new String[]{"Time", "Temp", "Sky", "Rain", "Amt", "Wind"}, true));
        boolean h24 = DateFormat.is24HourFormat(this);
        SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.ROOT), out = new SimpleDateFormat(h24 ? "HH:mm" : "h a", Locale.getDefault());
        for (int h : hrs) {
            String time;
            try { time = out.format(in.parse(f.aTime[h])); } catch (ParseException e) { time = f.aTime[h]; }
            rows.add(hourLine(new String[]{time, deg(f.aTemp[h]), Api.shortName(f.aCode[h]),
                    Double.isNaN(f.aProb[h]) ? "–" : f0(f.aProb[h]) + "%",
                    Double.isNaN(f.aPrecip[h]) || f.aPrecip[h] <= 0 ? "–" : String.format(Locale.ROOT, imp ? "%.2f" : "%.1f", f.aPrecip[h]),
                    f0(f.aWind[h]) + " " + WeatherActivity.compass(f.aDir[h])}, false));
        }
        TextView hint = Ui.dimText(this, "Swipe left or right for the next or previous day.", 11);
        pad(hint, 10, 14);
        rows.add(hint);
        return rows;
    }

    private View hourLine(String[] c, boolean header) {
        float[] w = {1.2f, 0.8f, 1.4f, 0.8f, 0.8f, 1.2f};
        LinearLayout r = new LinearLayout(this);
        pad(r, 3, 3);
        for (int k = 0; k < c.length; k++) {
            TextView t = header ? Ui.dimText(this, c[k], 12) : Ui.text(this, c[k], 13, k == 0);
            t.setSingleLine(true);
            t.setGravity(k == 0 || k == 2 ? Gravity.START : Gravity.END);
            r.addView(t, new LinearLayout.LayoutParams(0, -2, w[k]));
        }
        return r;
    }
}
