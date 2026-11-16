package app.eink.files;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import app.eink.core.InkPrefs;

/** Favorites, per-folder sort order, and the optional PIN. All of it stays in this app's private settings. */
final class FilesPrefs {
    private FilesPrefs() {}

    // ---- favorites ----

    static List<String> favorites(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(InkPrefs.getStr(c, "favorites", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (JSONException ignored) {}
        return out;
    }

    private static void saveFavorites(Context c, List<String> l) { InkPrefs.put(c, "favorites", new JSONArray(l).toString()); }

    static boolean isFavorite(Context c, File f) { return favorites(c).contains(f.getAbsolutePath()); }

    static void setFavorite(Context c, File f, boolean on) {
        List<String> l = favorites(c);
        l.remove(f.getAbsolutePath());
        if (on) l.add(f.getAbsolutePath());
        saveFavorites(c, l);
    }

    // ---- sort order: one default, and optional exceptions for single folders ----

    /** {key, "asc" or "desc"} for this folder. */
    static String[] sortFor(Context c, File dir) {
        try {
            JSONObject o = new JSONObject(InkPrefs.getStr(c, "folderSort", "{}"));
            String v = o.optString(dir.getAbsolutePath(), "");
            if (!v.isEmpty()) { String[] p = v.split(":"); return new String[]{p[0], p.length > 1 ? p[1] : "asc"}; }
        } catch (JSONException ignored) {}
        return new String[]{InkPrefs.getStr(c, "sort", FileOps.SORT_NAME), InkPrefs.getBool(c, "sortAsc", true) ? "asc" : "desc"};
    }

    static boolean hasFolderSort(Context c, File dir) {
        try { return new JSONObject(InkPrefs.getStr(c, "folderSort", "{}")).has(dir.getAbsolutePath()); } catch (JSONException e) { return false; }
    }

    /** dir null sets the default for every folder without its own setting. */
    static void setSort(Context c, File dir, String key, boolean asc) {
        if (dir == null) {
            InkPrefs.put(c, "sort", key);
            InkPrefs.put(c, "sortAsc", asc);
            return;
        }
        try {
            JSONObject o = new JSONObject(InkPrefs.getStr(c, "folderSort", "{}"));
            o.put(dir.getAbsolutePath(), key + ":" + (asc ? "asc" : "desc"));
            InkPrefs.put(c, "folderSort", o.toString());
        } catch (JSONException ignored) {}
    }

    static void clearFolderSort(Context c, File dir) {
        try {
            JSONObject o = new JSONObject(InkPrefs.getStr(c, "folderSort", "{}"));
            o.remove(dir.getAbsolutePath());
            InkPrefs.put(c, "folderSort", o.toString());
        } catch (JSONException ignored) {}
    }

    // ---- PIN ----

    static boolean hasPin(Context c) { return !InkPrefs.getStr(c, "pinHash", "").isEmpty(); }

    private static String hash(String salt, String pin) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest((salt + ":" + pin).getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static void setPin(Context c, String pin) {
        byte[] s = new byte[12];
        new SecureRandom().nextBytes(s);
        StringBuilder salt = new StringBuilder();
        for (byte x : s) salt.append(String.format("%02x", x));
        InkPrefs.put(c, "pinSalt", salt.toString());
        InkPrefs.put(c, "pinHash", hash(salt.toString(), pin));
    }

    static boolean checkPin(Context c, String pin) {
        String want = InkPrefs.getStr(c, "pinHash", "");
        String got = hash(InkPrefs.getStr(c, "pinSalt", ""), pin);
        // compare every character, so the time taken does not hint at how much matched
        int diff = want.length() ^ got.length();
        for (int i = 0; i < Math.min(want.length(), got.length()); i++) diff |= want.charAt(i) ^ got.charAt(i);
        return diff == 0;
    }

    static void clearPin(Context c) {
        InkPrefs.put(c, "pinHash", "");
        InkPrefs.put(c, "pinSalt", "");
    }
}
