package app.eink.calc;

import android.view.View;

/** One screen of the calculator app. */
abstract class Tab {
    protected final CalcActivity a;

    Tab(CalcActivity a) { this.a = a; }

    abstract View build();

    void onShow() {}
}
