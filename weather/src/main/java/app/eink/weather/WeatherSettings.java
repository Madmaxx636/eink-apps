package app.eink.weather;

import android.widget.LinearLayout;

import app.eink.core.InkPrefs;
import app.eink.core.SettingsActivity;
import app.eink.core.Ui;

public class WeatherSettings extends SettingsActivity {
    @Override protected void extraRows(LinearLayout col) {
        final String[] ids = {"auto", "metric", "imperial"};
        final String[] names = {"Automatic (from your language)", "Metric: °C, km/h, mm", "Imperial: °F, mph, inches"};
        int idx = Math.max(0, java.util.Arrays.asList(ids).indexOf(InkPrefs.getStr(this, "units", "auto")));
        final int sel = idx;
        add(Ui.row(this, "Units", names[idx], () ->
                Ui.choose(this, "Units", names, sel, i -> { InkPrefs.put(this, "units", ids[i]); recreate(); })));

        boolean aq = InkPrefs.getBool(this, "airQuality", true);
        add(Ui.row(this, "Air quality and pollen", (aq ? "On" : "Off") + ". Asks air-quality-api.open-meteo.com for the city's coordinates.", () -> {
            InkPrefs.put(this, "airQuality", !InkPrefs.getBool(this, "airQuality", true));
            InkPrefs.put(this, "cacheKey", "");
            recreate();
        }));
        boolean al = InkPrefs.getBool(this, "alerts", true);
        add(Ui.row(this, "Weather warnings (US only)", (al ? "On" : "Off") + ". Asks api.weather.gov, the National Weather Service, for the city's coordinates.", () -> {
            InkPrefs.put(this, "alerts", !InkPrefs.getBool(this, "alerts", true));
            InkPrefs.put(this, "cacheKey", "");
            recreate();
        }));

        final int[] mins = {15, 30, 60, 180};
        final String[] minNames = {"15 minutes", "30 minutes", "1 hour", "3 hours"};
        int cur = InkPrefs.getInt(this, "maxAge", 30), mi = 1;
        for (int i = 0; i < mins.length; i++) if (mins[i] == cur) mi = i;
        final int msel = mi;
        add(Ui.row(this, "Refresh when older than", minNames[mi] + ". Only happens when you open the app.", () ->
                Ui.choose(this, "Refresh when older than", minNames, msel, i -> { InkPrefs.put(this, "maxAge", mins[i]); recreate(); })));
    }
}
