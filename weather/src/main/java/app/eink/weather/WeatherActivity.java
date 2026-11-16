package app.eink.weather;

import android.os.Bundle;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
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

public class WeatherActivity extends InkActivity {
    private PagedScroll page;
    private final List<Api.Place> places = new ArrayList<>();
    private int idx;
    private boolean loading;
    private String error;
    private TextView refreshBtn;
    private Forecast fc;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        page = new PagedScroll(this);
        setScreen("Weather", page);
        refreshBtn = addBarButton("Refresh", () -> fetch(true));
        addSettingsButton();
        loadPlaces();
    }

    @Override protected Class<? extends android.app.Activity> settingsClass() { return WeatherSettings.class; }

    @Override protected boolean onPage(int dir) { return page.turn(dir); }

    @Override protected void onResume() {
        super.onResume();
        if (places.isEmpty()) { render(); return; }
        long age = System.currentTimeMillis() - InkPrefs.sp(this).getLong("cacheTime", 0);
        boolean stale = age > InkPrefs.getInt(this, "maxAge", 30) * 60000L || !InkPrefs.getStr(this, "cacheKey", "").equals(cacheKey());
        if (stale) fetch(false); else render();
    }

    private boolean imperial() {
        String u = InkPrefs.getStr(this, "units", "auto");
        if (u.equals("auto")) {
            String c = Locale.getDefault().getCountry();
            return c.equals("US") || c.equals("LR") || c.equals("MM");
        }
        return u.equals("imperial");
    }

    private String cacheKey() {
        if (places.isEmpty()) return "";
        Api.Place p = places.get(idx);
        return p.lat + "," + p.lon + "," + imperial() + InkPrefs.getBool(this, "airQuality", true) + InkPrefs.getBool(this, "alerts", true);
    }

    // ---- places ----

    private void loadPlaces() {
        places.clear();
        try {
            JSONArray a = new JSONArray(InkPrefs.getStr(this, "places", "[]"));
            for (int i = 0; i < a.length(); i++) places.add(Api.Place.fromJson(a.getJSONObject(i)));
        } catch (JSONException ignored) {}
        idx = Math.max(0, Math.min(InkPrefs.getInt(this, "placeIdx", 0), places.size() - 1));
    }

    private void savePlaces() {
        JSONArray a = new JSONArray();
        try { for (Api.Place p : places) a.put(p.toJson()); } catch (JSONException ignored) {}
        InkPrefs.put(this, "places", a.toString());
        InkPrefs.put(this, "placeIdx", idx);
    }

    private void addCity() {
        Ui.prompt(this, "Add a city", "", "Search", q -> {
            if (q.isEmpty()) return;
            error = null;
            new Thread(() -> {
                try {
                    final List<Api.Place> found = Api.search(q);
                    runOnUiThread(() -> { if (!isDestroyed()) pickResult(found); });
                } catch (Exception e) {
                    runOnUiThread(() -> { if (!isDestroyed()) { error = "Could not search: " + e.getMessage(); render(); } });
                }
            }).start();
        });
    }

    private void pickResult(final List<Api.Place> found) {
        if (found.isEmpty()) { error = "No city found with that name"; render(); return; }
        String[] names = new String[found.size()];
        for (int i = 0; i < names.length; i++) names[i] = found.get(i).label();
        Ui.choose(this, "Which one?", names, -1, i -> {
            places.add(found.get(i));
            idx = places.size() - 1;
            savePlaces();
            fetch(true);
        });
    }

    private void placeMenu() {
        List<String> names = new ArrayList<>();
        for (Api.Place p : places) names.add(p.label());
        names.add("+ Add a city");
        if (!places.isEmpty()) names.add("− Remove this city");
        Ui.choose(this, "City", names.toArray(new String[0]), idx, i -> {
            if (i < places.size()) { idx = i; savePlaces(); fetch(false); }
            else if (i == places.size()) addCity();
            else {
                places.remove(idx);
                idx = Math.max(0, idx - 1);
                savePlaces();
                if (places.isEmpty()) render(); else fetch(false);
            }
        });
    }

    // ---- fetching ----

    private void fetch(final boolean force) {
        if (places.isEmpty() || loading) return;
        if (!force && InkPrefs.getStr(this, "cacheKey", "").equals(cacheKey())
                && System.currentTimeMillis() - InkPrefs.sp(this).getLong("cacheTime", 0) < 60000) { render(); return; }
        loading = true;
        error = null;
        refreshBtn.setText("Loading…");
        final Api.Place p = places.get(idx);
        final boolean imp = imperial();
        final String key = cacheKey();
        final boolean wantAq = InkPrefs.getBool(this, "airQuality", true), wantAlerts = InkPrefs.getBool(this, "alerts", true) && p.isUs();
        new Thread(() -> {
            String json = null, aq = null, alerts = null, err = null;
            try { json = Api.forecast(p, imp); } catch (Exception e) { err = e.getMessage() == null ? "No connection" : e.getMessage(); }
            // These two are extras: if they fail the forecast still shows.
            if (json != null && wantAq) try { aq = Api.airQuality(p); } catch (Exception ignored) {}
            if (json != null && wantAlerts) try { alerts = Api.alerts(p); } catch (Exception ignored) {}
            final String j = json, a = aq, w = alerts, er = err;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                loading = false;
                refreshBtn.setText("Refresh");
                if (j != null) {
                    InkPrefs.sp(this).edit().putString("cache", j).putString("cacheAq", a).putString("cacheAlerts", w)
                            .putString("cacheKey", key).putLong("cacheTime", System.currentTimeMillis()).apply();
                } else error = "Could not update: " + er;
                render();
            });
        }).start();
    }

    // ---- building blocks ----

    private static final int SIDE = 14;

    private View pad(View v, int top, int bottom) {
        v.setPadding(Display.dp(this, SIDE), Display.dp(this, top), Display.dp(this, SIDE), Display.dp(this, bottom));
        return v;
    }

    private TextView text(String s, float sp, boolean bold) { return Ui.text(this, s, sp, bold); }

    private TextView dim(String s, float sp) { return Ui.dimText(this, s, sp); }

    private View section(String title) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(title.toUpperCase(Locale.getDefault()), 12, true);
        t.setLetterSpacing(0.08f);
        pad(t, 14, 3);
        l.addView(t);
        l.addView(Ui.divider(this));
        return l;
    }

    private String f0(double v) { return Double.isNaN(v) ? "–" : String.valueOf(Math.round(v)); }

    private String deg(double v) { return Double.isNaN(v) ? "–" : Math.round(v) + "°"; }

    private String windText(double speed, double dir, String unit) {
        return f0(speed) + " " + unit + (Double.isNaN(dir) ? "" : " " + compass(dir));
    }

    // ---- the screen ----

    private void render() {
        List<View> rows = new ArrayList<>();
        if (places.isEmpty()) {
            rows.add(pad(Ui.row(this, "No city yet", "Tap here to add one. Only the city you pick is sent to the weather service.", this::addCity), 0, 0));
            if (error != null) rows.add(Ui.row(this, error, null, null));
            page.setRows(rows);
            return;
        }
        Api.Place place = places.get(idx);

        TextView city = text(place.name + "  ▾", 22, true);
        pad(city, 8, 0);
        city.setClickable(true);
        city.setOnClickListener(v -> placeMenu());
        city.setSingleLine(true);
        city.setEllipsize(android.text.TextUtils.TruncateAt.END);
        rows.add(city);
        TextView where = dim(place.label(), 12);
        pad(where, 0, 6);
        where.setSingleLine(true);
        where.setEllipsize(android.text.TextUtils.TruncateAt.END);
        rows.add(where);

        if (error != null) rows.add(Ui.row(this, error, "Showing the last saved forecast", null));

        String json = InkPrefs.getStr(this, "cache", null);
        if (json == null || !InkPrefs.getStr(this, "cacheKey", "").equals(cacheKey())) {
            rows.add(Ui.row(this, loading ? "Loading…" : "No forecast yet", loading ? null : "Tap Refresh", null));
            page.setRows(rows);
            return;
        }
        try {
            fc = Forecast.parse(json, InkPrefs.getStr(this, "cacheAq", null), InkPrefs.getStr(this, "cacheAlerts", null));
        } catch (JSONException e) {
            rows.add(Ui.row(this, "The saved forecast could not be read", "Tap Refresh to load it again", null));
            page.setRows(rows);
            return;
        }
        final Forecast f = fc;
        boolean imp = imperial();

        // Now
        LinearLayout now = new LinearLayout(this);
        now.setGravity(Gravity.BOTTOM);
        TextView t = text(deg(f.temp), 44, true);
        now.addView(t, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(Display.dp(this, 12), 0, 0, Display.dp(this, 4));
        side.addView(text(Api.describe(f.code), 18, true));
        side.addView(dim("Feels like " + deg(f.feels) + "   Day " + deg(f.dMax[0]) + " / Night " + deg(f.dMin[0]), 13));
        now.addView(side, new LinearLayout.LayoutParams(0, -2, 1f));
        rows.add(pad(now, 2, 6));

        for (Forecast.Alert a : f.alerts) rows.add(alertRow(a));

        String sum = f.summary();
        String rain = !f.haveMinutely ? "" : f.rainInMinutes < 0 ? "No precipitation for at least 120 min."
                : f.rainInMinutes == 0 ? "Precipitation now." : "Precipitation starting in " + f.rainInMinutes + " min.";
        TextView summary = text((sum + " " + rain).trim(), 15, false);
        pad(summary, 4, 6);
        rows.add(summary);

        // Now: details in two columns
        rows.add(section("Right now"));
        String wu = f.windUnit.isEmpty() ? (imp ? "mph" : "km/h") : f.windUnit;
        String[][] pairs = {
                {"Wind", windText(f.wind, f.windDir, wu)}, {"Gusts", f0(f.gust) + " " + wu},
                {"Humidity", f0(f.humidity) + "%"}, {"Dew point", deg(f.dew)},
                {"Pressure", Double.isNaN(f.pressure) ? "–" : imp ? String.format(Locale.ROOT, "%.2f inHg", f.pressure * 0.02953) : f0(f.pressure) + " hPa"},
                {"Visibility", Double.isNaN(f.visibility) ? "–" : imp ? String.format(Locale.ROOT, "%.1f mi", f.visibility / 1609.34) : String.format(Locale.ROOT, "%.1f km", f.visibility / 1000)},
                {"Cloud cover", f0(f.cloud) + "%"}, {"UV index", (f.hUv.length > 0 && !Double.isNaN(f.hUv[0]) ? f0(f.hUv[0]) + " " + uvLevel(f.hUv[0]) : "–")},
                {"Rain today", Double.isNaN(f.dSum[0]) ? "–" : String.format(Locale.ROOT, imp ? "%.2f %s" : "%.1f %s", f.dSum[0], f.precipUnit.isEmpty() ? (imp ? "in" : "mm") : f.precipUnit)},
                {"Chance of rain", Double.isNaN(f.dProb[0]) ? "–" : f0(f.dProb[0]) + "%"},
                {"Sunrise", clock(f.sunrise[0])}, {"Sunset", clock(f.sunset[0])},
        };
        for (int i = 0; i < pairs.length; i += 2) rows.add(pairRow(pairs[i], pairs[i + 1]));

        // Hourly
        rows.add(section(Display.isEink(this) ? "Next 24 hours  (swipe sideways)" : "Next 24 hours"));
        rows.add(hourly(f));

        // Daily
        rows.add(section("Daily forecast  (tap a day for more)"));
        for (int i = 0; i < f.dn; i++) { rows.add(dayRow(f, i)); rows.add(Ui.divider(this)); }

        rows.add(section("Satellite"));
        rows.add(Ui.row(this, "Satellite image  ›", "A still picture from the NOAA GOES weather satellites. It does not move.",
                () -> startActivity(new android.content.Intent(this, SatelliteActivity.class)
                        .putExtra("lat", place.lat).putExtra("lon", place.lon).putExtra("name", place.name))));

        // Air and pollen
        boolean aqUs = aqUs();
        double aqi = aqUs ? f.aqiUs : f.aqiEu;
        boolean anyPollen = false;
        for (double p : f.pollen) if (!Double.isNaN(p)) anyPollen = true;
        if (!Double.isNaN(aqi) || anyPollen) {
            rows.add(section("Air and pollen"));
            if (!Double.isNaN(aqi)) {
                rows.add(pad(text("Air quality  " + f0(aqi) + "  " + aqName(aqi, aqUs) + (aqUs ? "  (US scale)" : "  (European scale)"), 15, true), 6, 0));
                rows.add(pad(dim("PM2.5 " + f0(f.pm25) + "   PM10 " + f0(f.pm10) + "   Ozone " + f0(f.ozone) + "   NO₂ " + f0(f.no2) + "   (µg/m³)", 13), 0, 6));
            }
            if (anyPollen) {
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < f.pollen.length; i++) {
                    if (Double.isNaN(f.pollen[i])) continue;
                    if (b.length() > 0) b.append("   ");
                    b.append(f.pollenNames[i]).append(' ').append(f.pollen[i] <= 0 ? "none" : pollenLevel(f.pollen[i]).toLowerCase(Locale.ROOT));
                }
                rows.add(pad(text("Pollen  " + b, 14, false), 2, 6));
            }
        }

        long up = InkPrefs.sp(this).getLong("cacheTime", 0);
        TextView foot = dim("Updated " + DateUtils.formatDateTime(this, up, DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_ALL)
                + "\nForecast and air quality: Open-Meteo.com" + (place.isUs() ? "   Warnings: weather.gov" : ""), 11);
        pad(foot, 12, 14);
        rows.add(foot);
        page.setRows(rows);
    }

    private View pairRow(String[] a, String[] b) {
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
        TextView k = dim(kv[0], 13);
        TextView v = text(kv[1], 14, true);
        v.setGravity(Gravity.END);
        v.setSingleLine(true);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        c.addView(k, new LinearLayout.LayoutParams(-2, -2));
        c.addView(v, new LinearLayout.LayoutParams(0, -2, 1f));
        return c;
    }

    // ---- hourly: a small table, labels fixed on the left ----

    private View hourly(Forecast f) {
        int n = Math.min(f.hn, 24);
        boolean h24 = DateFormat.is24HourFormat(this);
        SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.ROOT), out = new SimpleDateFormat(h24 ? "HH:mm" : "h a", Locale.getDefault());
        boolean imp = imperial();
        String wu = f.windUnit.isEmpty() ? (imp ? "mph" : "km/h") : f.windUnit;
        String[] labels = {"Time", "Temp", "Feels", "Sky", "Rain", "Amount", "Wind (" + wu + ")", "Gusts", "UV"};

        // On e-ink, show as many whole columns as fit and move a page of them at a time, so none is ever cut off.
        int labelW = Display.dp(this, 74), minCol = Display.dp(this, 54);
        int avail = getResources().getDisplayMetrics().widthPixels - labelW - 2 * Display.dp(this, SIDE);
        int perPage = Math.max(3, avail / minCol);
        int colW = Display.isEink(this) ? avail / perPage : Display.dp(this, 58);

        LinearLayout row = new LinearLayout(this);
        LinearLayout left = column(labels, true, labelW, true);
        row.addView(left, new LinearLayout.LayoutParams(-2, -2));
        app.eink.core.SnapHScroll hs = new app.eink.core.SnapHScroll(this);
        if (Display.isEink(this)) hs.setPageWidth(colW * perPage);
        LinearLayout cols = new LinearLayout(this);
        for (int i = 0; i < n; i++) {
            String time;
            try { time = out.format(in.parse(f.hTime[i])); } catch (ParseException e) { time = f.hTime[i]; }
            String[] v = {time, deg(f.hTemp[i]), deg(f.hApp[i]), Api.shortName(f.hCode[i]),
                    Double.isNaN(f.hProb[i]) ? "–" : f0(f.hProb[i]) + "%",
                    Double.isNaN(f.hPrecip[i]) || f.hPrecip[i] <= 0 ? "–" : String.format(Locale.ROOT, imp ? "%.2f" : "%.1f", f.hPrecip[i]),
                    f0(f.hWind[i]) + (Double.isNaN(f.hDir[i]) ? "" : " " + compass(f.hDir[i])), f0(f.hGust[i]),
                    Double.isNaN(f.hUv[i]) ? "–" : f0(f.hUv[i])};
            cols.addView(column(v, i == 0, colW, false));
        }
        hs.addView(cols);
        row.addView(hs, new LinearLayout.LayoutParams(0, -2, 1f));
        pad(row, 4, 4);
        return row;
    }

    /** One column of the table. The first row and the left labels are bold. */
    private LinearLayout column(String[] cells, boolean boldAll, int width, boolean isLabel) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < cells.length; i++) {
            TextView t = i == 0 || boldAll ? text(cells[i], 13, true) : text(cells[i], 13, false);
            if (boldAll && i > 0) t.setTypeface(null, android.graphics.Typeface.NORMAL);
            if (isLabel) t.setTextColor(Display.dim(this));
            t.setSingleLine(true);
            t.setGravity(isLabel ? Gravity.START : Gravity.CENTER_HORIZONTAL);
            t.setPadding(0, Display.dp(this, 2), 0, Display.dp(this, 2));
            c.addView(t, new LinearLayout.LayoutParams(width, -2));
        }
        return c;
    }

    // ---- daily: two lines per day ----

    private View dayRow(final Forecast f, final int i) {
        boolean imp = imperial();
        String wu = f.windUnit.isEmpty() ? (imp ? "mph" : "km/h") : f.windUnit;
        String label, md = "";
        try {
            Date dt = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(f.dDate[i]);
            md = new SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "Md"), Locale.getDefault()).format(dt);
            label = i == 0 ? "Today" : new SimpleDateFormat("EEE", Locale.getDefault()).format(dt);
        } catch (ParseException e) { label = f.dDate[i]; }

        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        pad(r, 6, 6);

        // line 1: the day, and high and low
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text(label, 16, true), new LinearLayout.LayoutParams(-2, -2));
        TextView date = dim("  " + md, 13);
        top.addView(date, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(text(deg(f.dMax[i]), 17, true), new LinearLayout.LayoutParams(-2, -2));
        top.addView(dim("  " + deg(f.dMin[i]), 15), new LinearLayout.LayoutParams(-2, -2));
        top.addView(dim("   ›", 16), new LinearLayout.LayoutParams(-2, -2));
        r.addView(top);

        // line 2: the weather, and how it feels
        LinearLayout mid = new LinearLayout(this);
        TextView sky = text(Api.describe(f.dCode[i]), 14, false);
        sky.setSingleLine(true);
        sky.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mid.addView(sky, new LinearLayout.LayoutParams(0, -2, 1f));
        mid.addView(dim("Feels " + deg(f.dAppMax[i]) + " / " + deg(f.dAppMin[i]), 13));
        r.addView(mid);

        // line 3: three small figures in even columns
        String rain = (Double.isNaN(f.dProb[i]) ? "–" : f0(f.dProb[i]) + "%")
                + (!Double.isNaN(f.dSum[i]) && f.dSum[i] > 0 ? "  " + String.format(Locale.ROOT, imp ? "%.2f" : "%.1f", f.dSum[i]) + (imp ? " in" : " mm") : "");
        String wind = f0(f.dWind[i]) + " " + compass(f.dDir[i]) + (Double.isNaN(f.dGust[i]) ? "" : " (" + f0(f.dGust[i]) + ")");
        double[] aq = aqUs() ? f.dAqiUs : f.dAqiEu;
        String last = !Double.isNaN(f.dUv[i]) ? "UV " + f0(f.dUv[i]) : "";
        if (i < aq.length && !Double.isNaN(aq[i])) last += (last.isEmpty() ? "" : "  ") + "AQI " + f0(aq[i]);
        LinearLayout facts = new LinearLayout(this);
        facts.setPadding(0, Display.dp(this, 3), 0, 0);
        String[] cells = {"Rain " + rain, "Wind " + wind, last};
        for (String c : cells) {
            TextView t = dim(c, 12);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            facts.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        r.addView(facts);

        r.setClickable(true);
        r.setOnClickListener(v -> startActivity(new android.content.Intent(this, DayActivity.class).putExtra("day", i)));
        return r;
    }

    // ---- warnings: plain text, boxed ----

    private View alertRow(final Forecast.Alert a) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Display.dp(this, 10);
        box.setPadding(p, p - 2, p, p - 2);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        boolean eink = Display.isEink(this), dark = Display.isDark(this);
        g.setCornerRadius(Display.dp(this, eink ? 2 : 8));
        if (eink) { g.setColor(Display.bg(this)); g.setStroke(Display.dp(this, 3), Display.fg(this)); }
        else g.setColor(dark ? 0xFF5C2420 : 0xFFFFDAD6);
        box.setBackground(g);
        int ink = eink ? Display.fg(this) : dark ? 0xFFFFDAD6 : 0xFF410002;
        TextView t1 = text("WARNING  " + a.event, 15, true);
        t1.setTextColor(ink);
        box.addView(t1);
        if (a.onset > 0 || a.ends > 0) {
            TextView t2 = text(alertTimes(a), 13, false);
            t2.setTextColor(ink);
            box.addView(t2);
        }
        box.setClickable(true);
        box.setOnClickListener(v -> {
            StringBuilder m = new StringBuilder();
            if (!a.headline.isEmpty()) m.append(a.headline).append("\n\n");
            if (!a.description.isEmpty()) m.append(a.description.replace("\n\n", "\u0000").replace("\n", " ").replace("\u0000", "\n\n")).append("\n\n");
            if (!a.instruction.isEmpty()) m.append(a.instruction.replace("\n", " ")).append("\n\n");
            if (!a.sender.isEmpty()) m.append(a.sender);
            Ui.dialog(this).setTitle(a.event).setMessage(m.toString().trim()).setPositiveButton("Close", null).show();
        });
        LinearLayout wrap = new LinearLayout(this);
        pad(wrap, 4, 4);
        wrap.addView(box, new LinearLayout.LayoutParams(-1, -2));
        return wrap;
    }

    private String alertTimes(Forecast.Alert a) {
        boolean h24 = DateFormat.is24HourFormat(this);
        String time = h24 ? "HH:mm" : "h:mm a";
        SimpleDateFormat full = new SimpleDateFormat("MMM d, " + time, Locale.getDefault()), only = new SimpleDateFormat(time, Locale.getDefault());
        if (a.onset > 0 && a.ends > 0)
            return full.format(new Date(a.onset)) + " — " + (sameDay(a.onset, a.ends) ? only : full).format(new Date(a.ends));
        return a.ends > 0 ? "Until " + full.format(new Date(a.ends)) : "From " + full.format(new Date(a.onset));
    }

    private static boolean sameDay(long a, long b) {
        java.util.Calendar x = java.util.Calendar.getInstance(), y = java.util.Calendar.getInstance();
        x.setTimeInMillis(a);
        y.setTimeInMillis(b);
        return x.get(java.util.Calendar.DAY_OF_YEAR) == y.get(java.util.Calendar.DAY_OF_YEAR) && x.get(java.util.Calendar.YEAR) == y.get(java.util.Calendar.YEAR);
    }

    private boolean aqUs() { return imperial(); }

    private String clock(String iso) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.ROOT).parse(iso);
            return new SimpleDateFormat(DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm a", Locale.getDefault()).format(d);
        } catch (ParseException e) {
            return "–";
        }
    }

    static String compass(double deg) {
        if (Double.isNaN(deg)) return "";
        String[] n = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        return n[(int) Math.round(((deg % 360) + 360) % 360 / 22.5) % 16];
    }

    static String aqName(double v, boolean us) {
        if (us) return v <= 50 ? "Good" : v <= 100 ? "Moderate" : v <= 150 ? "Unhealthy for some" : v <= 200 ? "Unhealthy" : v <= 300 ? "Very unhealthy" : "Hazardous";
        return v <= 20 ? "Good" : v <= 40 ? "Fair" : v <= 60 ? "Moderate" : v <= 80 ? "Poor" : v <= 100 ? "Very poor" : "Extremely poor";
    }

    /** Rough bands for grains per cubic metre. */
    static String pollenLevel(double v) { return v < 20 ? "Low" : v < 50 ? "Moderate" : v < 200 ? "High" : "Very high"; }

    static String uvLevel(double v) { return v < 3 ? "Low" : v < 6 ? "Moderate" : v < 8 ? "High" : v < 11 ? "Very high" : "Extreme"; }

    /** Swipe left for the next city, right for the one before. */
    @Override protected boolean onSwipe(int dir) {
        if (places.size() < 2) return false;
        int to = idx + dir;
        if (to < 0 || to >= places.size()) return false;
        idx = to;
        savePlaces();
        fetch(false);
        return true;
    }

    @Override protected boolean asksScreenType() { return true; }
}
