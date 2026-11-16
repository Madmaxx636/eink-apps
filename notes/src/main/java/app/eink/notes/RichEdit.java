package app.eink.notes;

import android.content.Context;
import android.widget.EditText;

/** An EditText that tells the screen when the cursor or selection moves, so the toolbar can follow it. */
final class RichEdit extends EditText {
    interface Listener { void onSelection(int a, int b); }

    private Listener listener;

    RichEdit(Context c) { super(c); }

    void setSelectionListener(Listener l) { listener = l; }

    @Override protected void onSelectionChanged(int a, int b) {
        super.onSelectionChanged(a, b);
        if (listener != null) listener.onSelection(a, b);
    }
}
