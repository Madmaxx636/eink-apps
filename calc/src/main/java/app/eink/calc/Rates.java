package app.eink.calc;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import app.eink.core.InkPrefs;

/** Exchange rates: the European Central Bank's daily reference rates, from the free Frankfurter service, kept for offline use. */
final class Rates {
    final Map<String, Double> rates = new TreeMap<>();     // how many of each currency make one euro
    final Map<String, String> names = new TreeMap<>();
    String date = "";
    long fetched;

    private Rates() {}

    boolean isEmpty() { return rates.isEmpty(); }

    static Rates load(Context c) {
        Rates r = new Rates();
        try {
            JSONObject o = new JSONObject(InkPrefs.getStr(c, "rates", "{}"));
            r.date = o.optString("date");
            r.fetched = o.optLong("fetched");
            JSONObject m = o.optJSONObject("rates");
            if (m != null) for (Iterator<String> it = m.keys(); it.hasNext(); ) { String k = it.next(); r.rates.put(k, m.getDouble(k)); }
            JSONObject n = o.optJSONObject("names");
            if (n != null) for (Iterator<String> it = n.keys(); it.hasNext(); ) { String k = it.next(); r.names.put(k, n.getString(k)); }
        } catch (JSONException ignored) {}
        return r;
    }

    private static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "eink-apps-calculator");
        try {
            if (c.getResponseCode() != 200) throw new IOException("Server said " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) { out.write(buf, 0, n); if (out.size() > 200000) throw new IOException("Unexpected reply"); }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }

    /** Downloads the latest rates and saves them. Slow: call from a background thread. */
    static Rates fetch(Context c) throws IOException {
        try {
            JSONObject latest = new JSONObject(get("https://api.frankfurter.dev/v1/latest?base=EUR"));
            JSONObject names = null;
            try { names = new JSONObject(get("https://api.frankfurter.dev/v1/currencies")); } catch (IOException | JSONException ignored) {}
            JSONObject save = new JSONObject().put("date", latest.optString("date")).put("fetched", System.currentTimeMillis())
                    .put("rates", latest.getJSONObject("rates"));
            if (names != null) save.put("names", names);
            InkPrefs.put(c, "rates", save.toString());
            return load(c);
        } catch (JSONException e) {
            throw new IOException("Unexpected reply");
        }
    }
}
