package app.eink.notes;

import android.widget.LinearLayout;

import app.eink.core.InkPrefs;
import app.eink.core.SettingsActivity;
import app.eink.core.Ui;

public class NotesSettings extends SettingsActivity {
    @Override protected void extraRows(LinearLayout col) {
        addLockRows();
        final String[] pos = {"top", "bottom"};
        final String[] posNames = {"Top, above the text", "Bottom, below the text"};
        int tp = "bottom".equals(InkPrefs.getStr(this, "toolbarPos", "top")) ? 1 : 0;
        add(Ui.row(this, "Formatting toolbar", posNames[tp], () ->
                Ui.choose(this, "Formatting toolbar", posNames, tp, i -> { InkPrefs.put(this, "toolbarPos", pos[i]); recreate(); })));
    }
}
