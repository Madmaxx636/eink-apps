package app.eink.weather;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import app.eink.core.Display;
import app.eink.core.InkActivity;
import app.eink.core.InkPrefs;
import app.eink.core.Ui;

/**
 * A still picture from NOAA's GOES weather satellites. It is one image, updated when you ask for a new one: it does not
 * animate. Only the picture's address is requested, so no location is sent; the region is chosen here on the phone.
 */
public class SatelliteActivity extends InkActivity {
    static final class Region {
        final String id, name, sat, sector;      // sector null means the whole disk
        final double lat, lon;

        Region(String id, String name, String sat, String sector, double lat, double lon) {
            this.id = id; this.name = name; this.sat = sat; this.sector = sector; this.lat = lat; this.lon = lon;
        }

        String satName() { return sat.equals("GOES19") ? "GOES-East (GOES-19)" : "GOES-West (GOES-18)"; }
    }

    /** Regions NOAA publishes as stills. Centres are rough: they only decide which one is nearest to a city. */
    static final Region[] REGIONS = {
            new Region("ne", "Northeast US", "GOES19", "ne", 41.5, -74), new Region("se", "Southeast US", "GOES19", "se", 32, -82),
            new Region("cgl", "Great Lakes", "GOES19", "cgl", 44, -84), new Region("umv", "Upper Mississippi Valley", "GOES19", "umv", 44, -92),
            new Region("smv", "Southern Mississippi Valley", "GOES19", "smv", 34, -90), new Region("sp", "Southern Plains", "GOES19", "sp", 33, -98),
            new Region("sr", "Southern Rockies", "GOES19", "sr", 37, -107), new Region("nr", "Northern Rockies", "GOES19", "nr", 45, -110),
            new Region("can", "Canada", "GOES19", "can", 55, -100), new Region("ga", "Gulf of America", "GOES19", "ga", 25, -90),
            new Region("car", "Caribbean", "GOES19", "car", 18, -70), new Region("pr", "Puerto Rico", "GOES19", "pr", 18.2, -66.5),
            new Region("mex", "Mexico", "GOES19", "mex", 23, -102), new Region("cam", "Central America", "GOES19", "cam", 14, -87),
            new Region("nsa", "Northern South America", "GOES19", "nsa", 0, -62), new Region("ssa", "Southern South America", "GOES19", "ssa", -30, -62),
            new Region("na", "North Atlantic", "GOES19", "na", 45, -40), new Region("taw", "Tropical Atlantic", "GOES19", "taw", 15, -45),
            new Region("pnw", "Pacific Northwest", "GOES18", "pnw", 46, -122), new Region("psw", "Pacific Southwest", "GOES18", "psw", 35, -120),
            new Region("wus", "Western US", "GOES18", "wus", 40, -115), new Region("np", "Northern Pacific", "GOES18", "np", 45, -150),
            new Region("ak", "Alaska", "GOES18", "ak", 62, -150), new Region("sea", "Southeast Alaska", "GOES18", "sea", 57, -135),
            new Region("hi", "Hawaii", "GOES18", "hi", 20, -157), new Region("tsp", "South Pacific", "GOES18", "tsp", -15, -140),
            new Region("fde", "Full disk: Americas and Atlantic (GOES-East)", "GOES19", null, 0, -75.2),
            new Region("fdw", "Full disk: Pacific (GOES-West)", "GOES18", null, 0, -137),
    };
    static final String[][] PRODUCTS = {
            {"GEOCOLOR", "Natural colour by day, infrared at night"}, {"13", "Infrared: cloud tops, day and night"}, {"09", "Water vapour: moisture in the air"}};

    private double lat, lon;
    private ImageView image;
    private TextView info, regionBtn, productBtn;
    private boolean loading;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        lat = getIntent().getDoubleExtra("lat", 0);
        lon = getIntent().getDoubleExtra("lon", 0);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        info = Ui.dimText(this, "", 12);
        info.setPadding(Display.dp(this, 12), Display.dp(this, 6), Display.dp(this, 12), Display.dp(this, 6));
        col.addView(info, new LinearLayout.LayoutParams(-1, -2));
        image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        col.addView(image, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout row = new LinearLayout(this);
        int p = Display.dp(this, 6);
        row.setPadding(p, p, p, p);
        regionBtn = Ui.button(this, "Region", this::chooseRegion);
        productBtn = Ui.button(this, "Image", this::chooseProduct);
        for (TextView t : new TextView[]{regionBtn, productBtn}) {
            ((app.eink.core.IconButton) t).setNoIcon(true);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(p / 2, 0, p / 2, 0);
            row.addView(t, lp);
        }
        col.addView(row, new LinearLayout.LayoutParams(-1, -2));
        setScreen("Satellite", col);
        addBarButton("Back", this::finish);
        addBarButton("Refresh", () -> fetch(true));
        draw();
        showCached();
        long age = System.currentTimeMillis() - InkPrefs.sp(this).getLong("satTime", 0);
        if (age > 10 * 60000L || !cacheMatches()) fetch(false);
    }

    // ---- which picture ----

    private static double km(double la1, double lo1, double la2, double lo2) {
        double r = 6371, dLa = Math.toRadians(la2 - la1), dLo = Math.toRadians(lo2 - lo1);
        double a = Math.sin(dLa / 2) * Math.sin(dLa / 2) + Math.cos(Math.toRadians(la1)) * Math.cos(Math.toRadians(la2)) * Math.sin(dLo / 2) * Math.sin(dLo / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    private static double lonDiff(double a, double b) {
        double d = (a - b) % 360;
        if (d > 180) d -= 360;
        if (d < -180) d += 360;
        return Math.abs(d);
    }

    /** The nearest regional picture, the whole disk if the city is far from all of them, or null if no GOES satellite sees it. */
    static Region autoRegion(double lat, double lon) {
        double east = lonDiff(lon, -75.2), west = lonDiff(lon, -137);
        Region best = null;
        double bestKm = 1e9;
        for (Region r : REGIONS) {
            if (r.sector == null) continue;
            double sub = r.sat.equals("GOES19") ? east : west;
            if (sub > 75) continue;
            double d = km(lat, lon, r.lat, r.lon);
            if (d < bestKm) { bestKm = d; best = r; }
        }
        if (best != null && bestKm <= 1800) return best;
        if (east <= 78 || west <= 78) return REGIONS[east <= west ? REGIONS.length - 2 : REGIONS.length - 1];
        return null;
    }

    private Region region() {
        String id = InkPrefs.getStr(this, "satRegion", "auto");
        if (!id.equals("auto")) for (Region r : REGIONS) if (r.id.equals(id)) return r;
        return autoRegion(lat, lon);
    }

    private String product() { return InkPrefs.getStr(this, "satProduct", "GEOCOLOR"); }

    /** NOAA publishes each regional sector in its own set of sizes: a large one first, a small one to fall back on. */
    private static String[] sizes(String sector) {
        switch (sector) {
            case "ga": case "car": case "mex": case "cam": case "wus": case "ak": return new String[]{"1000x1000", "500x500"};
            case "nsa": case "ssa": case "na": case "taw": case "np": case "tsp": return new String[]{"1800x1080", "900x540"};
            case "can": return new String[]{"2250x1125", "1125x560"};
            default: return new String[]{"1200x1200", "600x600"};
        }
    }

    private String[] urls(Region r) {
        String base = "https://cdn.star.nesdis.noaa.gov/" + r.sat + "/ABI/" + (r.sector == null ? "FD" : "SECTOR/" + r.sector) + "/" + product() + "/";
        if (r.sector == null) return new String[]{base + "1808x1808.jpg", base + "678x678.jpg"};
        String[] sz = sizes(r.sector);
        return new String[]{base + sz[0] + ".jpg", base + sz[1] + ".jpg"};
    }

    private String key(Region r) { return r.sat + "/" + r.sector + "/" + product(); }

    private boolean cacheMatches() {
        Region r = region();
        return r != null && key(r).equals(InkPrefs.getStr(this, "satKey", "")) && file().exists();
    }

    private File file() { return new File(getCacheDir(), "satellite.jpg"); }

    private void draw() {
        Region r = region();
        boolean auto = InkPrefs.getStr(this, "satRegion", "auto").equals("auto");
        regionBtn.setText(r == null ? "Region: none" : (auto ? "Region: auto" : "Region: " + r.name));
        String pname = "";
        for (String[] p : PRODUCTS) if (p[0].equals(product())) pname = p[1].split(":")[0].split(" by ")[0];
        productBtn.setText("Image: " + pname);
    }

    // ---- showing and fetching ----

    private void showCached() {
        Region r = region();
        if (r == null) {
            info.setText("No GOES satellite looks at this part of the world, so there is no still picture for it. Use Region to look at one anyway.");
            return;
        }
        if (!cacheMatches()) { info.setText("Loading…"); return; }
        image.setImageBitmap(decode(file()));
        applyLook();
        long t = InkPrefs.sp(this).getLong("satTime", 0);
        SimpleDateFormat f = new SimpleDateFormat(DateFormat.is24HourFormat(this) ? "MMM d, HH:mm" : "MMM d, h:mm a", Locale.getDefault());
        long mins = Math.max(0, (System.currentTimeMillis() - t) / 60000);
        info.setText(r.satName() + " · " + r.name + "\nTaken " + f.format(new Date(t)) + " (" + (mins < 90 ? mins + " min" : (mins / 60) + " h") + " ago). A still picture: Refresh for a newer one.");
    }

    /** E-ink shows colour as muddy grey, so turn it to grey on purpose and add a little contrast. */
    private void applyLook() {
        if (!Display.isEink(this)) { image.clearColorFilter(); return; }
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        cm.postConcat(new ColorMatrix(new float[]{1.25f, 0, 0, 0, -28, 0, 1.25f, 0, 0, -28, 0, 0, 1.25f, 0, -28, 0, 0, 0, 1, 0}));
        image.setColorFilter(new ColorMatrixColorFilter(cm));
    }

    private Bitmap decode(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        int target = Math.max(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= target) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    private void fetch(boolean force) {
        final Region r = region();
        if (r == null || loading) return;
        loading = true;
        info.setText("Loading…");
        final String key = key(r);
        final String[] urls = urls(r);
        new Thread(() -> {
            String err = null;
            long taken = 0;
            for (String u : urls) {
                try {
                    taken = download(u, file());
                    err = null;
                    break;
                } catch (IOException e) {
                    err = e.getMessage() == null ? "No connection" : e.getMessage();
                }
            }
            final String er = err;
            final long t = taken;
            runOnUiThread(() -> {
                loading = false;
                if (isDestroyed()) return;
                if (er != null) { info.setText("Could not load the picture: " + er + (cacheMatches() ? "\nShowing the last one." : "")); if (cacheMatches()) showCachedImageOnly(); return; }
                InkPrefs.sp(this).edit().putString("satKey", key).putLong("satTime", t > 0 ? t : System.currentTimeMillis()).apply();
                showCached();
            });
        }).start();
    }

    private void showCachedImageOnly() { image.setImageBitmap(decode(file())); applyLook(); }

    /** Saves the picture to a file and returns when it was taken, from the server's Last-Modified time. */
    private static long download(String url, File to) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "eink-apps-weather");
        try {
            if (c.getResponseCode() != 200) throw new IOException("Server said " + c.getResponseCode());
            long len = c.getContentLengthLong();
            if (len > 12_000_000L) throw new IOException("The picture is too large");
            File tmp = new File(to.getPath() + ".part");
            long total = 0;
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > 12_000_000L) throw new IOException("The picture is too large");
                    out.write(buf, 0, n);
                }
            }
            if (!tmp.renameTo(to)) { to.delete(); tmp.renameTo(to); }
            return c.getLastModified();
        } finally {
            c.disconnect();
        }
    }

    private void chooseRegion() {
        String[] names = new String[REGIONS.length + 1];
        names[0] = "Automatic: the nearest to your city";
        int sel = 0;
        String id = InkPrefs.getStr(this, "satRegion", "auto");
        for (int i = 0; i < REGIONS.length; i++) { names[i + 1] = REGIONS[i].name + "  (" + (REGIONS[i].sat.equals("GOES19") ? "East" : "West") + ")"; if (REGIONS[i].id.equals(id)) sel = i + 1; }
        Ui.choose(this, "Region", names, sel, i -> {
            InkPrefs.put(this, "satRegion", i == 0 ? "auto" : REGIONS[i - 1].id);
            draw();
            showCached();
            if (!cacheMatches()) fetch(true);
        });
    }

    private void chooseProduct() {
        String[] names = new String[PRODUCTS.length];
        int sel = 0;
        for (int i = 0; i < names.length; i++) { names[i] = PRODUCTS[i][1]; if (PRODUCTS[i][0].equals(product())) sel = i; }
        Ui.choose(this, "Image type", names, sel, i -> {
            InkPrefs.put(this, "satProduct", PRODUCTS[i][0]);
            draw();
            showCached();
            if (!cacheMatches()) fetch(true);
        });
    }

    @Override protected void onLookChanged() { recreate(); }
}
