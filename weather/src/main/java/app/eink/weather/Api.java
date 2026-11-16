package app.eink.weather;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Open-Meteo: free, no key, no account. Only a city name or coordinates are sent. */
final class Api {
    static final class Place {
        final String name, region, country, cc;
        final double lat, lon;

        Place(String name, String region, String country, String cc, double lat, double lon) {
            this.name = name; this.region = region; this.country = country; this.cc = cc; this.lat = lat; this.lon = lon;
        }

        boolean isUs() { return "US".equalsIgnoreCase(cc) || (country != null && country.startsWith("United States")); }

        String label() {
            StringBuilder b = new StringBuilder(name);
            if (region != null && !region.isEmpty() && !region.equals(name)) b.append(", ").append(region);
            if (country != null && !country.isEmpty()) b.append(", ").append(country);
            return b.toString();
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("n", name).put("r", region).put("c", country).put("cc", cc).put("la", lat).put("lo", lon);
        }

        static Place fromJson(JSONObject o) {
            return new Place(o.optString("n"), o.optString("r"), o.optString("c"), o.optString("cc"), o.optDouble("la"), o.optDouble("lo"));
        }
    }

    private Api() {}

    static List<Place> search(String query) throws IOException, JSONException {
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=8&format=json&language="
                + Locale.getDefault().getLanguage() + "&name=" + URLEncoder.encode(query, "UTF-8");
        JSONObject o = new JSONObject(get(url));
        JSONArray r = o.optJSONArray("results");
        List<Place> out = new ArrayList<>();
        if (r == null) return out;
        for (int i = 0; i < r.length(); i++) {
            JSONObject p = r.getJSONObject(i);
            out.add(new Place(p.optString("name"), p.optString("admin1"), p.optString("country"), p.optString("country_code"), p.getDouble("latitude"), p.getDouble("longitude")));
        }
        return out;
    }

    /** Returns the raw forecast JSON, so it can be cached and shown again offline. */
    static String forecast(Place p, boolean imperial) throws IOException {
        return get("https://api.open-meteo.com/v1/forecast?latitude=" + p.lat + "&longitude=" + p.lon
                + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,dew_point_2m,is_day,weather_code,wind_speed_10m,wind_gusts_10m,wind_direction_10m,precipitation,pressure_msl,visibility,cloud_cover"
                + "&minutely_15=precipitation&forecast_minutely_15=8"
                + "&hourly=temperature_2m,apparent_temperature,weather_code,is_day,precipitation_probability,precipitation,wind_speed_10m,wind_gusts_10m,wind_direction_10m,uv_index,relative_humidity_2m,dew_point_2m,pressure_msl,cloud_cover,visibility,snowfall"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min,precipitation_probability_max,precipitation_sum,wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant,uv_index_max,uv_index_clear_sky_max,sunrise,sunset,precipitation_hours,rain_sum,snowfall_sum,daylight_duration,sunshine_duration,relative_humidity_2m_mean,dew_point_2m_mean,pressure_msl_mean,cloud_cover_mean,visibility_mean"
                + "&forecast_days=8&timezone=auto"
                + "&temperature_unit=" + (imperial ? "fahrenheit" : "celsius")
                + "&wind_speed_unit=" + (imperial ? "mph" : "kmh")
                + "&precipitation_unit=" + (imperial ? "inch" : "mm"));
    }

    /** Air quality and, in Europe, pollen. Optional: callers treat a failure as "no data". */
    static String airQuality(Place p) throws IOException {
        return get("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=" + p.lat + "&longitude=" + p.lon
                + "&current=us_aqi,european_aqi,pm2_5,pm10,ozone,nitrogen_dioxide,alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen"
                + "&hourly=us_aqi,european_aqi&forecast_days=5&timezone=auto");
    }

    /** Official weather warnings from the US National Weather Service. Only asked for places in the United States. */
    static String alerts(Place p) throws IOException {
        return get("https://api.weather.gov/alerts/active?point=" + String.format(Locale.ROOT, "%.4f,%.4f", p.lat, p.lon));
    }

    private static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "eink-apps-weather");
        try {
            if (c.getResponseCode() != 200) throw new IOException("Server said " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }

    /** The same codes in one short word, for narrow table cells. */
    static String shortName(int code) {
        switch (code) {
            case 0: return "Clear";
            case 1: return "Clear";
            case 2: return "Partly";
            case 3: return "Cloudy";
            case 45: case 48: return "Fog";
            case 51: case 53: case 55: case 56: case 57: return "Drizzle";
            case 61: case 63: case 66: case 67: return "Rain";
            case 65: return "Hvy rain";
            case 71: case 73: case 77: return "Snow";
            case 75: return "Hvy snow";
            case 80: case 81: return "Showers";
            case 82: return "Hvy show.";
            case 85: case 86: return "Snow shr.";
            case 95: case 96: case 99: return "Storm";
            default: return "–";
        }
    }

    /** WMO weather codes as short words. */
    static String describe(int code) {
        switch (code) {
            case 0: return "Clear";
            case 1: return "Mostly clear";
            case 2: return "Partly cloudy";
            case 3: return "Overcast";
            case 45: case 48: return "Fog";
            case 51: case 53: case 55: return "Drizzle";
            case 56: case 57: return "Freezing drizzle";
            case 61: return "Light rain";
            case 63: return "Rain";
            case 65: return "Heavy rain";
            case 66: case 67: return "Freezing rain";
            case 71: return "Light snow";
            case 73: return "Snow";
            case 75: return "Heavy snow";
            case 77: return "Snow grains";
            case 80: return "Light showers";
            case 81: return "Showers";
            case 82: return "Heavy showers";
            case 85: case 86: return "Snow showers";
            case 95: return "Thunderstorm";
            case 96: case 99: return "Thunderstorm, hail";
            default: return "Unknown";
        }
    }
}
