package app.eink.files;

import android.os.Build;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.Arrays;

import app.eink.core.Display;
import app.eink.core.InkPrefs;
import app.eink.core.SettingsActivity;
import app.eink.core.Ui;

public class FilesSettings extends SettingsActivity {
    @Override protected void extraRows(LinearLayout col) {
        final String[] keys = {FileOps.SORT_NAME, FileOps.SORT_SIZE, FileOps.SORT_DATE, FileOps.SORT_EXT};
        final String[] names = {"Name", "Size", "Last modified", "Extension"};
        int ki = Math.max(0, Arrays.asList(keys).indexOf(InkPrefs.getStr(this, "sort", FileOps.SORT_NAME)));
        boolean asc = InkPrefs.getBool(this, "sortAsc", true);
        add(Ui.row(this, "Sort files by", names[ki] + ", " + (asc ? "ascending" : "descending") + ". Folders come first. In a folder, More → Sort can set its own order.", () ->
                Ui.choose(this, "Sort by", names, ki, i -> {
                    InkPrefs.put(this, "sort", keys[i]);
                    Ui.choose(this, "Order", new String[]{"Ascending", "Descending"}, asc ? 0 : 1, j -> { InkPrefs.put(this, "sortAsc", j == 0); recreate(); });
                })));

        boolean compact = "compact".equals(InkPrefs.getStr(this, "view", "detailed"));
        add(Ui.row(this, "List style", compact ? "Compact: one line per file" : "Detailed: size and date under each name", () -> {
            InkPrefs.put(this, "view", compact ? "detailed" : "compact");
            recreate();
        }));

        boolean h = InkPrefs.getBool(this, "hidden", false);
        add(Ui.row(this, "Show hidden files", h ? "On" : "Off", () -> {
            InkPrefs.put(this, "hidden", !InkPrefs.getBool(this, "hidden", false));
            recreate();
        }));

        final String[] starts = {"last", "root", "downloads"};
        final String[] startNames = {"The folder you were last in", "Internal storage", "Downloads"};
        int si = Math.max(0, Arrays.asList(starts).indexOf(InkPrefs.getStr(this, "startIn", "last")));
        add(Ui.row(this, "Open in", startNames[si], () ->
                Ui.choose(this, "Open in", startNames, si, i -> { InkPrefs.put(this, "startIn", starts[i]); recreate(); })));

        final String[] pos = {"top", "bottom"};
        final String[] posNames = {"Top", "Bottom"};
        int tp = "bottom".equals(InkPrefs.getStr(this, "tabsPos", "top")) ? 1 : 0;
        add(Ui.row(this, "Tab buttons (Files, Recents, Storage)", posNames[tp], () ->
                Ui.choose(this, "Tab buttons", posNames, tp, i -> { InkPrefs.put(this, "tabsPos", pos[i]); recreate(); })));

        boolean c = InkPrefs.getBool(this, "confirmDelete", true);
        add(Ui.row(this, "Ask before deleting", c ? "On" : "Off", () -> {
            InkPrefs.put(this, "confirmDelete", !InkPrefs.getBool(this, "confirmDelete", true));
            recreate();
        }));

        boolean pin = FilesPrefs.hasPin(this);
        add(Ui.row(this, "PIN lock", pin ? "On. Asked when the app is opened or has been away for a while. Tap to change or remove it." : "Off. Tap to set a PIN.", () -> {
            if (!pin) askNewPin();
            else Ui.choose(this, "PIN lock", new String[]{"Change PIN", "Remove PIN"}, -1, i -> {
                if (i == 0) askNewPin();
                else askOldPin(() -> { FilesPrefs.clearPin(this); recreate(); });
            });
        }));

        // Fingerprint or face: only offered when a PIN is set (the PIN is the fallback) and the phone can do it.
        if (pin && Biometrics.available(this)) {
            boolean bio = InkPrefs.getBool(this, "biometric", false);
            add(Ui.row(this, "Unlock with fingerprint or face", bio ? "On. The PIN is still there as a fallback." : "Off", () -> {
                InkPrefs.put(this, "biometric", !InkPrefs.getBool(this, "biometric", false));
                recreate();
            }));
        } else if (pin) {
            add(Ui.row(this, "Unlock with fingerprint or face", Build.VERSION.SDK_INT < 29
                    ? "Needs Android 10 or later." : "Set up a fingerprint or face in Android Settings first.", null));
        }
    }

    private EditText pinField(String hint) {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setHint(hint);
        return e;
    }

    private void askNewPin() {
        final EditText e = pinField("4 to 8 digits");
        LinearLayout box = new LinearLayout(this);
        int p = Display.dp(this, 16);
        box.setPadding(p, p / 2, p, 0);
        box.addView(e, new LinearLayout.LayoutParams(-1, -2));
        Ui.dialog(this).setTitle("New PIN").setView(box).setPositiveButton("Set", (d, w) -> {
            String v = e.getText().toString();
            if (v.length() < 4 || v.length() > 8) { Toast.makeText(this, "Use 4 to 8 digits", Toast.LENGTH_LONG).show(); return; }
            FilesPrefs.setPin(this, v);
            recreate();
        }).setNegativeButton("Cancel", null).show();
    }

    private void askOldPin(final Runnable ok) {
        final EditText e = pinField("Current PIN");
        LinearLayout box = new LinearLayout(this);
        int p = Display.dp(this, 16);
        box.setPadding(p, p / 2, p, 0);
        box.addView(e, new LinearLayout.LayoutParams(-1, -2));
        Ui.dialog(this).setTitle("Enter your PIN").setView(box).setPositiveButton("OK", (d, w) -> {
            if (FilesPrefs.checkPin(this, e.getText().toString())) ok.run();
            else Toast.makeText(this, "Wrong PIN", Toast.LENGTH_LONG).show();
        }).setNegativeButton("Cancel", null).show();
    }
}
