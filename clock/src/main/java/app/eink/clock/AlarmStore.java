package app.eink.clock;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import app.eink.core.InkPrefs;

/** All alarms, kept as JSON in the app's private settings. */
final class AlarmStore {
    private AlarmStore() {}

    static List<Alarm> all(Context c) {
        List<Alarm> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(InkPrefs.getStr(c, "alarms", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Alarm x = new Alarm();
                x.id = o.getInt("id");
                x.hour = o.getInt("h");
                x.minute = o.getInt("m");
                x.days = o.optInt("d", 0);
                x.enabled = o.optBoolean("on", true);
                x.vibrate = o.optBoolean("v", true);
                x.label = o.optString("l", "");
                x.sound = o.optString("s", "");
                out.add(x);
            }
        } catch (JSONException ignored) {}
        java.util.Collections.sort(out, (p, q) -> p.hour != q.hour ? Integer.compare(p.hour, q.hour) : Integer.compare(p.minute, q.minute));
        return out;
    }

    static Alarm get(Context c, int id) {
        for (Alarm a : all(c)) if (a.id == id) return a;
        return null;
    }

    private static void write(Context c, List<Alarm> list) {
        JSONArray a = new JSONArray();
        try {
            for (Alarm x : list) a.put(new JSONObject().put("id", x.id).put("h", x.hour).put("m", x.minute).put("d", x.days)
                    .put("on", x.enabled).put("v", x.vibrate).put("l", x.label).put("s", x.sound));
        } catch (JSONException ignored) {}
        InkPrefs.put(c, "alarms", a.toString());
    }

    /** Adds or replaces by id. A new alarm (id 0) gets the next free id. */
    static Alarm save(Context c, Alarm alarm) {
        List<Alarm> list = all(c);
        if (alarm.id == 0) {
            int max = InkPrefs.getInt(c, "alarmSeq", 0);
            for (Alarm x : list) max = Math.max(max, x.id);
            alarm.id = max + 1;
            InkPrefs.put(c, "alarmSeq", alarm.id);
        }
        boolean found = false;
        for (int i = 0; i < list.size(); i++) if (list.get(i).id == alarm.id) { list.set(i, alarm); found = true; }
        if (!found) list.add(alarm);
        write(c, list);
        return alarm;
    }

    static void delete(Context c, int id) {
        List<Alarm> list = all(c);
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id == id) list.remove(i);
        write(c, list);
    }
}
