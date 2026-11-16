package app.eink.files;

import android.content.Context;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;

/** Fingerprint or face unlock through Android's own prompt. Needs Android 10 or later; the PIN always remains as the fallback. */
@android.annotation.TargetApi(29)
final class Biometrics {
    interface Result {
        void ok();

        /** The user chose the PIN, cancelled, or the sensor gave up. */
        void usePin(String why);

        /** The prompt went away without an answer (cancelled by the system, or by back). The lock screen keeps its buttons. */
        void dismissed();
    }

    private Biometrics() {}

    /** True if this phone can do it and has at least one fingerprint or face enrolled. */
    static boolean available(Context c) {
        if (Build.VERSION.SDK_INT < 29) return false;
        BiometricManager m = c.getSystemService(BiometricManager.class);
        if (m == null) return false;
        int r = Build.VERSION.SDK_INT >= 30 ? m.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) : m.canAuthenticate();
        return r == BiometricManager.BIOMETRIC_SUCCESS;
    }

    static CancellationSignal prompt(Context c, String title, String useAlternative, final Result result) {
        final CancellationSignal cancel = new CancellationSignal();
        BiometricPrompt p = new BiometricPrompt.Builder(c)
                .setTitle(title)
                .setNegativeButton(useAlternative, c.getMainExecutor(), (d, w) -> result.usePin("PIN"))
                .build();
        p.authenticate(cancel, c.getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
            @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) { result.ok(); }

            @Override public void onAuthenticationError(int code, CharSequence message) {
                // A cancel by the system (screen off, another app in front) is not a choice of PIN; any other error is the sensor giving up.
                if (code == BiometricPrompt.BIOMETRIC_ERROR_CANCELED || code == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED) result.dismissed();
                else result.usePin(String.valueOf(message));
            }
            // A finger that did not match: the prompt lets the person try again by itself.
        });
        return cancel;
    }
}
