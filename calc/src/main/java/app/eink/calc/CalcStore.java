package app.eink.calc;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

import app.eink.core.InkPrefs;

/** The calculator's memory and its history of past sums, kept in this app's private settings. */
final class CalcStore {
    private static final int MAX_HISTORY = 60;

    private CalcStore() {}

    static double memory(Context c) {
        try { return Double.parseDouble(InkPrefs.getStr(c, "memory", "0")); } catch (NumberFormatException e) { return 0; }
    }

    static void setMemory(Context c, double v) { InkPrefs.put(c, "memory", Double.toString(v)); }

    /** Newest first. Each entry is "expression = answer". */
    static List<String> history(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(InkPrefs.getStr(c, "history", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (JSONException ignored) {}
        return out;
    }

    static void addHistory(Context c, String entry) {
        List<String> l = history(c);
        if (!l.isEmpty() && l.get(0).equals(entry)) return;
        l.add(0, entry);
        while (l.size() > MAX_HISTORY) l.remove(l.size() - 1);
        InkPrefs.put(c, "history", new JSONArray(l).toString());
    }

    static void clearHistory(Context c) { InkPrefs.put(c, "history", "[]"); }
}
