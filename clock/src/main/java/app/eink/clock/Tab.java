package app.eink.clock;

import android.view.View;

/** One screen of the clock app. The host calls tick() on the schedule the tab asks for. */
abstract class Tab {
    protected final ClockActivity a;

    Tab(ClockActivity a) { this.a = a; }

    abstract View build();

    /** The tab came on screen or the app came back. */
    void onShow() {}

    /** Redraw what changes with time. */
    void tick() {}

    /** Milliseconds until tick() should run again, or -1 for never. */
    long nextTick() { return -1; }

    boolean onPage(int dir) { return false; }
}
