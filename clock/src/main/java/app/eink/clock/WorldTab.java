package app.eink.clock;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import app.eink.core.InkPrefs;
import app.eink.core.PagedScroll;
import app.eink.core.Ui;

/** Current time in cities you pick, from the phone's own time-zone database. No network. */
final class WorldTab extends Tab {
    private PagedScroll list;
    private final List<String> shownZones = new ArrayList<>();
    private final List<View> shownRows = new ArrayList<>();

    WorldTab(ClockActivity a) { super(a); }

    @Override View build() {
        list = new PagedScroll(a);
        refresh();
        return list;
    }

    @Override void onShow() { refresh(); }

    /** Updates the times in place, so the list keeps its place and a per-second tick stays cheap. */
    @Override void tick() {
        Date now = new Date();
        for (int i = 0; i < shownZones.size(); i++) fill((LinearLayout) shownRows.get(i), shownZones.get(i), now);
    }

    private boolean seconds() { return a.live() && InkPrefs.getBool(a, "seconds", true); }

    @Override long nextTick() {
        long step = seconds() ? 1000 : 60000;
        return step - System.currentTimeMillis() % step + 50;
    }

    private void fill(LinearLayout row, String id, Date now) {
        TimeZone tz = TimeZone.getTimeZone(id), here = TimeZone.getDefault();
        SimpleDateFormat t = new SimpleDateFormat(a.is24() ? (seconds() ? "HH:mm:ss" : "HH:mm") : (seconds() ? "h:mm:ss a" : "h:mm a"), Locale.getDefault());
        t.setTimeZone(tz);
        int diffMin = (tz.getOffset(now.getTime()) - here.getOffset(now.getTime())) / 60000;
        String rel = diffMin == 0 ? "Same time as here" : (diffMin > 0 ? "+" : "−") + hm(Math.abs(diffMin)) + " from here";
        SimpleDateFormat day = new SimpleDateFormat("EEE", Locale.getDefault()), here1 = new SimpleDateFormat("EEE", Locale.getDefault());
        day.setTimeZone(tz);
        String when = day.format(now).equals(here1.format(now)) ? "Today" : (diffMin > 0 ? "Tomorrow" : "Yesterday");
        ((TextView) row.getChildAt(0)).setText(city(id) + "     " + t.format(now));
        if (row.getChildCount() > 1) ((TextView) row.getChildAt(1)).setText(when + "  ·  " + rel);
    }

    @Override boolean onPage(int dir) { return list != null && list.turn(dir); }

    private List<String> zones() {
        List<String> out = new ArrayList<>();
        try {
            JSONArray j = new JSONArray(InkPrefs.getStr(a, "zones", "[]"));
            for (int i = 0; i < j.length(); i++) out.add(j.getString(i));
        } catch (JSONException ignored) {}
        return out;
    }

    private void save(List<String> z) { InkPrefs.put(a, "zones", new JSONArray(z).toString()); }

    /** "America/New_York" shown as "New York". */
    static String city(String id) {
        String n = id.substring(id.lastIndexOf('/') + 1).replace('_', ' ');
        return n;
    }

    private void refresh() {
        List<View> rows = new ArrayList<>();
        rows.add(Ui.button(a, "+  Add a city", this::add));
        Date now = new Date();
        List<String> z = zones();
        shownZones.clear();
        shownRows.clear();
        if (z.isEmpty()) rows.add(Ui.row(a, "No cities yet", "Add one to see its time next to yours", null));
        for (final String id : z) {
            rows.add(Ui.divider(a));
            LinearLayout r = (LinearLayout) Ui.row(a, " ", " ", null);
            fill(r, id, now);
            r.setOnLongClickListener(v -> {
                Ui.confirm(a, "Remove " + city(id) + "?", "It will no longer be listed.", "Remove", () -> {
                    List<String> l = zones();
                    l.remove(id);
                    save(l);
                    refresh();
                });
                return true;
            });
            rows.add(r);
            shownZones.add(id);
            shownRows.add(r);
        }
        if (!z.isEmpty()) rows.add(Ui.dimText(a, "  Press and hold a city to remove it", 12));
        list.setRows(rows);
    }

    private static String hm(int m) { return m % 60 == 0 ? (m / 60) + "h" : (m / 60) + "h " + (m % 60) + "m"; }

    private void add() {
        Ui.prompt(a, "Add a city", "", "Search", q -> {
            if (q.isEmpty()) return;
            String needle = q.toLowerCase(Locale.ROOT).replace(' ', '_');
            final List<String> hits = new ArrayList<>();
            String[] ids = TimeZone.getAvailableIDs();
            Arrays.sort(ids);
            for (String id : ids) {
                if (!id.contains("/") || id.startsWith("Etc/") || id.startsWith("SystemV/")) continue;
                if (id.toLowerCase(Locale.ROOT).contains(needle)) hits.add(id);
                if (hits.size() >= 30) break;
            }
            if (hits.isEmpty()) { android.widget.Toast.makeText(a, "No time zone matches that. Try a big nearby city, such as Tokyo.", android.widget.Toast.LENGTH_LONG).show(); return; }
            String[] names = new String[hits.size()];
            for (int i = 0; i < names.length; i++) names[i] = city(hits.get(i)) + "  (" + hits.get(i).substring(0, hits.get(i).indexOf('/')) + ")";
            Ui.choose(a, "Which one?", names, -1, i -> {
                List<String> l = zones();
                if (!l.contains(hits.get(i))) l.add(hits.get(i));
                save(l);
                refresh();
            });
        });
    }
}
