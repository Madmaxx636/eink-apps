package app.eink.weather;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** The forecast as plain arrays, parsed once from the saved JSON. Missing numbers are NaN. */
final class Forecast {
    static final class Alert {
        String event, headline, description, instruction, sender, severity;
        long onset, ends;
    }

    // current
    double temp, feels, humidity, dew, gust, wind, windDir, precipNow, pressure, visibility, cloud;
    int code;
    boolean isDay;
    String time;                       // "2026-11-12T14:15" at the place
    String windUnit = "", precipUnit = "", pressureUnit = "hPa";

    // daily
    int dn;
    String[] dDate, sunrise, sunset;
    int[] dCode;
    double[] dMax, dMin, dAppMax, dAppMin, dProb, dSum, dWind, dGust, dDir, dUv;

    // more per-day figures
    double[] dPrecipHours, dRain, dSnow, dDaylight, dSunshine, dHumMean, dDewMean, dPressMean, dCloudMean, dVisMean, dUvClear;

    // every hour of every day, for the day screens (index 0 is midnight on the first day)
    int an;
    String[] aTime;
    int[] aCode;
    double[] aTemp, aApp, aProb, aPrecip, aWind, aGust, aDir, aUv, aHum, aDew, aPress, aCloud, aVis, aSnow;

    // hourly (from the current hour on)
    int hn;
    String[] hTime;
    int[] hCode;
    boolean[] hDay;
    double[] hTemp, hApp, hProb, hPrecip, hWind, hGust, hDir, hUv;

    /** Minutes until rain starts within the next two hours, or -1 if none. */
    int rainInMinutes = -1;
    boolean haveMinutely;

    // air quality
    double aqiUs = Double.NaN, aqiEu = Double.NaN, pm25 = Double.NaN, pm10 = Double.NaN, ozone = Double.NaN, no2 = Double.NaN;
    final String[] pollenNames = {"Alder", "Birch", "Grass", "Mugwort", "Olive", "Ragweed"};
    final double[] pollen = {Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN};
    double[] dAqiUs = new double[0], dAqiEu = new double[0];   // daily maximum, aligned with dDate; NaN after the data ends

    final List<Alert> alerts = new ArrayList<>();

    static Forecast parse(String json, String aqJson, String alertJson) throws JSONException {
        Forecast f = new Forecast();
        JSONObject o = new JSONObject(json);
        JSONObject c = o.getJSONObject("current"), cu = o.optJSONObject("current_units");
        f.time = c.getString("time");
        f.temp = num(c, "temperature_2m");
        f.feels = num(c, "apparent_temperature");
        f.humidity = num(c, "relative_humidity_2m");
        f.dew = num(c, "dew_point_2m");
        f.code = (int) num(c, "weather_code");
        f.isDay = c.optInt("is_day", 1) == 1;
        f.wind = num(c, "wind_speed_10m");
        f.gust = num(c, "wind_gusts_10m");
        f.windDir = num(c, "wind_direction_10m");
        f.precipNow = num(c, "precipitation");
        f.pressure = num(c, "pressure_msl");
        f.visibility = num(c, "visibility");
        f.cloud = num(c, "cloud_cover");
        if (cu != null) {
            f.windUnit = cu.optString("wind_speed_10m", "");
            f.precipUnit = cu.optString("precipitation", "");
            f.pressureUnit = cu.optString("pressure_msl", "hPa");
        }

        JSONObject d = o.getJSONObject("daily");
        JSONArray dt = d.getJSONArray("time");
        f.dn = dt.length();
        f.dDate = strs(dt);
        f.sunrise = strs(d.getJSONArray("sunrise"));
        f.sunset = strs(d.getJSONArray("sunset"));
        f.dCode = ints(d, "weather_code");
        f.dMax = nums(d, "temperature_2m_max"); f.dMin = nums(d, "temperature_2m_min");
        f.dAppMax = nums(d, "apparent_temperature_max"); f.dAppMin = nums(d, "apparent_temperature_min");
        f.dProb = nums(d, "precipitation_probability_max"); f.dSum = nums(d, "precipitation_sum");
        f.dPrecipHours = nums(d, "precipitation_hours"); f.dRain = nums(d, "rain_sum"); f.dSnow = nums(d, "snowfall_sum");
        f.dDaylight = nums(d, "daylight_duration"); f.dSunshine = nums(d, "sunshine_duration");
        f.dHumMean = nums(d, "relative_humidity_2m_mean"); f.dDewMean = nums(d, "dew_point_2m_mean");
        f.dPressMean = nums(d, "pressure_msl_mean"); f.dCloudMean = nums(d, "cloud_cover_mean");
        f.dVisMean = nums(d, "visibility_mean"); f.dUvClear = nums(d, "uv_index_clear_sky_max");
        f.dWind = nums(d, "wind_speed_10m_max"); f.dGust = nums(d, "wind_gusts_10m_max");
        f.dDir = nums(d, "wind_direction_10m_dominant"); f.dUv = nums(d, "uv_index_max");

        JSONObject h = o.getJSONObject("hourly");
        JSONArray ht = h.getJSONArray("time");
        f.an = ht.length();
        f.aTime = strs(ht);
        f.aCode = ints(h, "weather_code");
        f.aTemp = nums(h, "temperature_2m"); f.aApp = nums(h, "apparent_temperature"); f.aProb = nums(h, "precipitation_probability");
        f.aPrecip = nums(h, "precipitation"); f.aWind = nums(h, "wind_speed_10m"); f.aGust = nums(h, "wind_gusts_10m");
        f.aDir = nums(h, "wind_direction_10m"); f.aUv = nums(h, "uv_index"); f.aHum = nums(h, "relative_humidity_2m");
        f.aDew = nums(h, "dew_point_2m"); f.aPress = nums(h, "pressure_msl"); f.aCloud = nums(h, "cloud_cover");
        f.aVis = nums(h, "visibility"); f.aSnow = nums(h, "snowfall");
        String nowHour = f.time.substring(0, 13) + ":00";
        int start = 0;
        while (start < ht.length() && ht.getString(start).compareTo(nowHour) < 0) start++;
        int n = Math.min(36, ht.length() - start);
        f.hn = Math.max(0, n);
        f.hTime = new String[f.hn];
        for (int i = 0; i < f.hn; i++) f.hTime[i] = ht.getString(start + i);
        f.hCode = slice(ints(h, "weather_code"), start, f.hn);
        int[] day = ints(h, "is_day");
        f.hDay = new boolean[f.hn];
        for (int i = 0; i < f.hn; i++) f.hDay[i] = day[start + i] == 1;
        f.hTemp = slice(nums(h, "temperature_2m"), start, f.hn);
        f.hApp = slice(nums(h, "apparent_temperature"), start, f.hn);
        f.hProb = slice(nums(h, "precipitation_probability"), start, f.hn);
        f.hPrecip = slice(nums(h, "precipitation"), start, f.hn);
        f.hWind = slice(nums(h, "wind_speed_10m"), start, f.hn);
        f.hGust = slice(nums(h, "wind_gusts_10m"), start, f.hn);
        f.hDir = slice(nums(h, "wind_direction_10m"), start, f.hn);
        f.hUv = slice(nums(h, "uv_index"), start, f.hn);

        JSONObject mm = o.optJSONObject("minutely_15");
        if (mm != null && mm.optJSONArray("precipitation") != null) {
            JSONArray p = mm.getJSONArray("precipitation");
            f.haveMinutely = p.length() > 0;
            for (int i = 0; i < p.length(); i++) if (p.optDouble(i, 0) > 0.0) { f.rainInMinutes = i * 15; break; }
        }

        if (aqJson != null) {
            try {
                JSONObject a = new JSONObject(aqJson), ac = a.optJSONObject("current");
                if (ac != null) {
                    f.aqiUs = num(ac, "us_aqi");
                    f.aqiEu = num(ac, "european_aqi");
                    f.pm25 = num(ac, "pm2_5"); f.pm10 = num(ac, "pm10"); f.ozone = num(ac, "ozone"); f.no2 = num(ac, "nitrogen_dioxide");
                    String[] keys = {"alder_pollen", "birch_pollen", "grass_pollen", "mugwort_pollen", "olive_pollen", "ragweed_pollen"};
                    for (int i = 0; i < keys.length; i++) f.pollen[i] = num(ac, keys[i]);
                }
                JSONObject ah = a.optJSONObject("hourly");
                if (ah != null) {
                    f.dAqiUs = dailyMax(f.dDate, ah.getJSONArray("time"), ah.optJSONArray("us_aqi"));
                    f.dAqiEu = dailyMax(f.dDate, ah.getJSONArray("time"), ah.optJSONArray("european_aqi"));
                }
            } catch (JSONException ignored) {}
        }

        if (alertJson != null) {
            try {
                JSONArray feats = new JSONObject(alertJson).optJSONArray("features");
                for (int i = 0; feats != null && i < feats.length(); i++) {
                    JSONObject p = feats.getJSONObject(i).getJSONObject("properties");
                    Alert al = new Alert();
                    al.event = p.optString("event");
                    al.headline = p.optString("headline");
                    al.description = p.optString("description");
                    al.instruction = p.optString("instruction", "").equals("null") ? "" : p.optString("instruction", "");
                    al.sender = p.optString("senderName");
                    al.severity = p.optString("severity");
                    al.onset = iso(p.optString("onset", p.optString("effective")));
                    al.ends = iso(p.optString("ends", p.optString("expires")));
                    if (!al.event.isEmpty()) f.alerts.add(al);
                }
            } catch (JSONException ignored) {}
        }
        return f;
    }

    private static long iso(String s) {
        if (s == null || s.isEmpty() || s.equals("null")) return 0;
        try {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT).parse(s).getTime();
        } catch (ParseException e) {
            return 0;
        }
    }

    private static double[] dailyMax(String[] dates, JSONArray times, JSONArray vals) throws JSONException {
        double[] out = new double[dates.length];
        java.util.Arrays.fill(out, Double.NaN);
        if (vals == null) return out;
        for (int i = 0; i < times.length() && i < vals.length(); i++) {
            String day = times.getString(i).substring(0, 10);
            for (int k = 0; k < dates.length; k++) {
                if (dates[k].equals(day) && !vals.isNull(i)) { double v = vals.getDouble(i); if (Double.isNaN(out[k]) || v > out[k]) out[k] = v; }
            }
        }
        return out;
    }

    private static double num(JSONObject o, String k) { return o.isNull(k) ? Double.NaN : o.optDouble(k, Double.NaN); }

    private static String[] strs(JSONArray a) throws JSONException {
        String[] r = new String[a.length()];
        for (int i = 0; i < r.length; i++) r[i] = a.optString(i, "");
        return r;
    }

    private static double[] nums(JSONObject o, String k) {
        JSONArray a = o.optJSONArray(k);
        int n = a == null ? 0 : a.length();
        double[] r = new double[n];
        for (int i = 0; i < n; i++) r[i] = a.isNull(i) ? Double.NaN : a.optDouble(i, Double.NaN);
        return r;
    }

    private static int[] ints(JSONObject o, String k) {
        double[] d = nums(o, k);
        int[] r = new int[d.length];
        for (int i = 0; i < r.length; i++) r[i] = Double.isNaN(d[i]) ? 0 : (int) d[i];
        return r;
    }

    private static double[] slice(double[] a, int from, int n) {
        double[] r = new double[n];
        for (int i = 0; i < n; i++) r[i] = from + i < a.length ? a[from + i] : Double.NaN;
        return r;
    }

    private static int[] slice(int[] a, int from, int n) {
        int[] r = new int[n];
        for (int i = 0; i < n; i++) r[i] = from + i < a.length ? a[from + i] : 0;
        return r;
    }

    /** Indexes into the a* arrays for the hours of one day. */
    int[] hoursOf(int day) {
        List<Integer> l = new ArrayList<>();
        String d = dDate[day];
        for (int i = 0; i < an; i++) if (aTime[i].startsWith(d)) l.add(i);
        int[] r = new int[l.size()];
        for (int i = 0; i < r.length; i++) r[i] = l.get(i);
        return r;
    }

    /** A sentence or two about the week ahead, worked out from the numbers. */
    String summary() {
        StringBuilder b = new StringBuilder();
        String[] names = new java.text.DateFormatSymbols(Locale.getDefault()).getWeekdays();
        int wet = -1;
        for (int i = 0; i < Math.min(dn, 5); i++) if (!Double.isNaN(dProb[i]) && dProb[i] >= 50) { wet = i; break; }
        if (wet == 0) b.append("Rain is likely today. ");
        else if (wet > 0) b.append("Rain is likely ").append(dayName(wet, names)).append(". ");
        else b.append("Mostly dry for the next few days. ");
        int cold = 0, warm = 0;
        for (int i = 1; i < Math.min(dn, 7); i++) {
            if (dMax[i] < dMax[cold]) cold = i;
            if (dMax[i] > dMax[warm]) warm = i;
        }
        if (dn > 2 && dMax[warm] - dMax[cold] >= (isFahrenheitScale() ? 8 : 4)) {
            b.append(cold < warm ? "Coolest " + dayName(cold, names) + ", warming to " : "Warmest " + dayName(warm, names) + ", then cooling to ");
            b.append(Math.round(cold < warm ? dMax[warm] : dMax[cold])).append("°.");
        }
        return b.toString().trim();
    }

    private boolean isFahrenheitScale() { return dMax.length > 0 && dMax[0] > 45; }

    private String dayName(int i, String[] names) {
        if (i == 0) return "today";
        if (i == 1) return "tomorrow";
        try {
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(dDate[i]));
            return names[c.get(java.util.Calendar.DAY_OF_WEEK)];
        } catch (ParseException e) {
            return "later";
        }
    }
}
