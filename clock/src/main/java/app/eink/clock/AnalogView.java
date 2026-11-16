package app.eink.clock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.util.Calendar;

import app.eink.core.Display;

/** A plain analog face: ring, twelve ticks, two thick hands and an optional thin seconds hand. */
final class AnalogView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean seconds;

    AnalogView(Context c) {
        super(c);
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    void setShowSeconds(boolean s) { seconds = s; }

    @Override protected void onMeasure(int w, int h) {
        int size = Math.min(MeasureSpec.getSize(w), MeasureSpec.getSize(h));
        if (MeasureSpec.getMode(h) == MeasureSpec.UNSPECIFIED) size = MeasureSpec.getSize(w);
        setMeasuredDimension(size, size);
    }

    @Override protected void onDraw(Canvas cv) {
        float r = Math.min(getWidth(), getHeight()) / 2f, cx = getWidth() / 2f, cy = getHeight() / 2f;
        int fg = Display.fg(getContext());
        p.setColor(fg);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(r * 0.04f);
        cv.drawCircle(cx, cy, r - p.getStrokeWidth(), p);
        for (int i = 0; i < 12; i++) {
            double a = Math.toRadians(i * 30);
            float len = i % 3 == 0 ? 0.16f : 0.08f;
            p.setStrokeWidth(r * (i % 3 == 0 ? 0.045f : 0.025f));
            cv.drawLine(cx + (float) Math.sin(a) * r * (0.9f - len), cy - (float) Math.cos(a) * r * (0.9f - len),
                    cx + (float) Math.sin(a) * r * 0.9f, cy - (float) Math.cos(a) * r * 0.9f, p);
        }
        Calendar c = Calendar.getInstance();
        int m = c.get(Calendar.MINUTE), s = c.get(Calendar.SECOND);
        float hour = (c.get(Calendar.HOUR) + m / 60f) * 30, min = (m + s / 60f) * 6;
        hand(cv, cx, cy, hour, r * 0.5f, r * 0.07f);
        hand(cv, cx, cy, min, r * 0.75f, r * 0.045f);
        if (seconds) hand(cv, cx, cy, s * 6, r * 0.8f, r * 0.015f);
        p.setStyle(Paint.Style.FILL);
        cv.drawCircle(cx, cy, r * 0.05f, p);
    }

    private void hand(Canvas cv, float cx, float cy, float deg, float len, float width) {
        double a = Math.toRadians(deg);
        p.setStrokeWidth(width);
        cv.drawLine(cx, cy, cx + (float) Math.sin(a) * len, cy - (float) Math.cos(a) * len, p);
    }
}
