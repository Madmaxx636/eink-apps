package app.eink.core;

import android.content.Context;

import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * An optional lock on an app: a PIN or a password, with fingerprint or face as a shortcut. The secret is never stored,
 * only a salted, deliberately slow hash of it. An app opts in by returning true from InkActivity.usesAppLock().
 */
public final class AppLock {
    public static final String PIN = "pin", PASSWORD = "password";

    private static long lastActive;
    private static boolean unlocked;

    private AppLock() {}

    public static boolean enabled(Context c) { return !InkPrefs.getStr(c, "lockHash", "").isEmpty(); }

    public static String kind(Context c) { return InkPrefs.getStr(c, "lockKind", PIN); }

    /** Seconds the app may be away before it asks again. 0 asks every time. */
    public static int timeout(Context c) { return InkPrefs.getInt(c, "lockTimeout", 30); }

    public static boolean needed(Context c) {
        return enabled(c) && (!unlocked || System.currentTimeMillis() - lastActive > timeout(c) * 1000L);
    }

    public static void markUnlocked() { unlocked = true; lastActive = System.currentTimeMillis(); }

    /** Call when leaving a locked screen, so moving between the app's own screens does not ask again. */
    public static void touch() { lastActive = System.currentTimeMillis(); }

    public static boolean biometricOn(Context c) { return InkPrefs.getBool(c, "lockBio", false); }

    private static String hash(String salt, String secret) {
        try {
            KeySpec spec = new PBEKeySpec(secret.toCharArray(), salt.getBytes("UTF-8"), 60000, 256);
            byte[] d = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static void set(Context c, String kind, String secret) {
        byte[] s = new byte[16];
        new SecureRandom().nextBytes(s);
        StringBuilder salt = new StringBuilder();
        for (byte x : s) salt.append(String.format("%02x", x));
        InkPrefs.put(c, "lockSalt", salt.toString());
        InkPrefs.put(c, "lockHash", hash(salt.toString(), secret));
        InkPrefs.put(c, "lockKind", kind);
        markUnlocked();
    }

    public static boolean check(Context c, String secret) {
        String want = InkPrefs.getStr(c, "lockHash", "");
        String got = hash(InkPrefs.getStr(c, "lockSalt", ""), secret);
        int diff = want.length() ^ got.length();    // look at every character so the time taken says nothing about how much matched
        for (int i = 0; i < Math.min(want.length(), got.length()); i++) diff |= want.charAt(i) ^ got.charAt(i);
        return diff == 0;
    }

    public static void clear(Context c) {
        InkPrefs.put(c, "lockHash", "");
        InkPrefs.put(c, "lockSalt", "");
        InkPrefs.put(c, "lockBio", false);
        unlocked = false;
    }
}
