package app.eink.core;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Small line icons drawn in code, so there are no image files. One colour, with a second for the pressed state. */
public final class Icons {
    private static final Map<String, String> BY_LABEL = new HashMap<>();

    static {
        String[][] m = {
                {"new", "plus"}, {"add", "plus"}, {"settings", "gear"}, {"back", "back"}, {"cancel", "x"}, {"close", "x"},
                {"done", "check"}, {"save", "save"}, {"menu", "menu"}, {"more", "dots"}, {"find", "search"}, {"search", "search"},
                {"delete", "trash"}, {"share", "share"}, {"refresh", "refresh"}, {"reset", "refresh"}, {"up", "up"},
                {"prev", "left"}, {"next", "right"}, {"undo", "undo"}, {"redo", "redo"}, {"agenda", "list"}, {"month", "grid"},
                {"start", "play"}, {"resume", "play"}, {"play", "play"}, {"stop", "pause"}, {"pause", "pause"}, {"lap", "flag"},
                {"clock", "clock"}, {"alarms", "bell"}, {"timer", "hourglass"}, {"watch", "stopwatch"}, {"world", "globe"},
                {"snooze", "bell"}, {"dismiss", "x"}, {"allow access", "check"},
                {"calc", "grid"}, {"tip", "percent"}, {"money", "coin"}, {"units", "ruler"}, {"history", "clock"}, {"files", "folder"}, {"recents", "clock"}, {"storage", "drive"}, {"sort", "list"}, {"unlock", "check"}, {"exit", "x"},
        };
        for (String[] e : m) BY_LABEL.put(e[0], e[1]);
    }

    private Icons() {}

    /** The icon name for a button label such as "New" or "Settings", or null if there is none. */
    public static String forLabel(CharSequence label) {
        if (label == null) return null;
        return BY_LABEL.get(label.toString().trim().toLowerCase(Locale.ROOT));
    }

    public static Drawable glyph(String name, int sizePx, int color, int pressedColor) {
        return new Glyph(name, sizePx, color, pressedColor);
    }

    private static final class Glyph extends Drawable {
        private final String name;
        private final int size, normal, pressed;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int current;

        Glyph(String name, int size, int normal, int pressed) {
            this.name = name;
            this.size = size;
            this.normal = normal;
            this.pressed = pressed;
            this.current = normal;
            setBounds(0, 0, size, size);
        }

        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
        @Override public boolean isStateful() { return true; }

        @Override protected boolean onStateChange(int[] state) {
            int c = normal;
            for (int s : state) if (s == android.R.attr.state_pressed) c = pressed;
            boolean changed = c != current;
            current = c;
            return changed;
        }

        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

        // Everything is drawn on a 24 by 24 grid and scaled to fit.
        @Override public void draw(Canvas c) {
            float s = Math.min(getBounds().width(), getBounds().height()) / 24f;
            c.save();
            c.translate(getBounds().left, getBounds().top);
            c.scale(s, s);
            p.setColor(current);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            switch (name) {
                case "plus": line(c, 12, 5, 12, 19); line(c, 5, 12, 19, 12); break;
                case "x": line(c, 6, 6, 18, 18); line(c, 18, 6, 6, 18); break;
                case "check": poly(c, 5, 13, 10, 18, 19, 7); break;
                case "back": line(c, 19, 12, 5, 12); poly(c, 11, 6, 5, 12, 11, 18); break;
                case "up": line(c, 12, 19, 12, 5); poly(c, 6, 11, 12, 5, 18, 11); break;
                case "left": poly(c, 15, 5, 8, 12, 15, 19); break;
                case "right": poly(c, 9, 5, 16, 12, 9, 19); break;
                case "menu": line(c, 5, 7, 19, 7); line(c, 5, 12, 19, 12); line(c, 5, 17, 19, 17); break;
                case "dots": dot(c, 6, 12); dot(c, 12, 12); dot(c, 18, 12); break;
                case "search": c.drawCircle(10.5f, 10.5f, 5.5f, p); line(c, 14.5f, 14.5f, 19.5f, 19.5f); break;
                case "gear":
                    c.drawCircle(12, 12, 6.2f, p);
                    c.drawCircle(12, 12, 2.2f, p);
                    for (int i = 0; i < 8; i++) {
                        double a = Math.toRadians(i * 45);
                        line(c, 12 + (float) Math.sin(a) * 6.2f, 12 - (float) Math.cos(a) * 6.2f, 12 + (float) Math.sin(a) * 9.2f, 12 - (float) Math.cos(a) * 9.2f);
                    }
                    break;
                case "trash":
                    line(c, 5, 7, 19, 7); line(c, 9.5f, 7, 9.5f, 4.5f); line(c, 14.5f, 7, 14.5f, 4.5f); line(c, 9.5f, 4.5f, 14.5f, 4.5f);
                    poly(c, 7, 7, 8, 19.5f, 16, 19.5f, 17, 7);
                    line(c, 10.5f, 10.5f, 10.5f, 16); line(c, 13.5f, 10.5f, 13.5f, 16);
                    break;
                case "share":
                    line(c, 7, 12, 17, 6); line(c, 7, 12, 17, 18);
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(6.5f, 12, 2.6f, p); c.drawCircle(17.5f, 6, 2.6f, p); c.drawCircle(17.5f, 18, 2.6f, p);
                    break;
                case "save": line(c, 12, 4, 12, 14.5f); poly(c, 7, 10, 12, 15, 17, 10); poly(c, 5, 18, 5, 20, 19, 20, 19, 18); break;
                case "refresh": {
                    RectF r = new RectF(5.5f, 5.5f, 18.5f, 18.5f);
                    c.drawArc(r, -60, 280, false, p);
                    p.setStyle(Paint.Style.FILL);
                    Path h = new Path();
                    h.moveTo(9.1f, 5.4f); h.lineTo(9.3f, 9.7f); h.lineTo(4.7f, 5.9f); h.close();   // arrow head at the end of the arc
                    c.drawPath(h, p);
                    break;
                }
                case "undo": case "redo": {
                    boolean redo = name.equals("redo");
                    if (redo) { c.save(); c.scale(-1, 1, 12, 12); }
                    poly(c, 9, 6, 5, 10, 9, 14);
                    Path q = new Path();
                    q.moveTo(5, 10); q.lineTo(13, 10); q.cubicTo(17.5f, 10, 19, 13, 19, 15); q.cubicTo(19, 17.5f, 17, 19, 14, 19);
                    c.drawPath(q, p);
                    if (redo) c.restore();
                    break;
                }
                case "list": dot(c, 5.5f, 7); dot(c, 5.5f, 12); dot(c, 5.5f, 17); line(c, 10, 7, 19, 7); line(c, 10, 12, 19, 12); line(c, 10, 17, 19, 17); break;
                case "grid": c.drawRoundRect(new RectF(5, 5, 19, 19), 2, 2, p); line(c, 12, 5, 12, 19); line(c, 5, 12, 19, 12); break;
                case "play": { p.setStyle(Paint.Style.FILL); Path t = new Path(); t.moveTo(8, 5.5f); t.lineTo(19, 12); t.lineTo(8, 18.5f); t.close(); c.drawPath(t, p); break; }
                case "pause": p.setStyle(Paint.Style.FILL); c.drawRoundRect(new RectF(6.5f, 5.5f, 10.5f, 18.5f), 1, 1, p); c.drawRoundRect(new RectF(13.5f, 5.5f, 17.5f, 18.5f), 1, 1, p); break;
                case "flag": line(c, 6, 20, 6, 4); p.setStyle(Paint.Style.FILL); { Path f = new Path(); f.moveTo(6, 5); f.lineTo(18, 5); f.lineTo(14.5f, 9); f.lineTo(18, 13); f.lineTo(6, 13); f.close(); c.drawPath(f, p); } break;
                case "clock": c.drawCircle(12, 12, 8, p); poly(c, 12, 7, 12, 12, 15.5f, 14); break;
                case "percent": c.drawCircle(8, 8, 2.6f, p); c.drawCircle(16, 16, 2.6f, p); line(c, 18, 5, 6, 19); break;
                case "coin": c.drawCircle(12, 12, 8, p); poly(c, 14.5f, 9, 11, 9, 9.5f, 10.5f, 9.5f, 11.8f, 11, 13, 13, 13, 14.5f, 14.2f, 14.5f, 15.5f, 13, 17, 9.5f, 17); line(c, 12, 7.5f, 12, 9); line(c, 12, 17, 12, 18.5f); break;
                case "ruler": c.drawRoundRect(new RectF(3.5f, 8, 20.5f, 16), 1.5f, 1.5f, p); for (int i = 0; i < 6; i++) line(c, 6.5f + i * 2.6f, 8, 6.5f + i * 2.6f, i % 2 == 0 ? 12.5f : 11); break;
                case "folder": poly(c, 4, 7, 4, 19, 20, 19, 20, 9, 11, 9, 9, 6, 4, 6, 4, 7); break;
                case "drive": c.drawRoundRect(new RectF(4, 8, 20, 17), 2, 2, p); line(c, 4, 13, 20, 13); dot(c, 16.5f, 15.2f); break;
                case "bell": {
                    Path b = new Path();
                    b.moveTo(6, 17); b.lineTo(6, 11); b.cubicTo(6, 7, 8.5f, 5, 12, 5); b.cubicTo(15.5f, 5, 18, 7, 18, 11); b.lineTo(18, 17); b.lineTo(6, 17);
                    c.drawPath(b, p);
                    line(c, 4.5f, 17, 19.5f, 17);
                    c.drawArc(new RectF(9.5f, 17.5f, 14.5f, 21.5f), 0, 180, false, p);
                    break;
                }
                case "stopwatch": c.drawCircle(12, 13.5f, 7, p); line(c, 12, 13.5f, 14.5f, 10.5f); line(c, 10, 3.5f, 14, 3.5f); line(c, 12, 3.5f, 12, 6.5f); break;
                case "hourglass": poly(c, 7, 4.5f, 17, 4.5f, 12, 12, 17, 19.5f, 7, 19.5f, 12, 12, 7, 4.5f); break;
                case "globe": c.drawCircle(12, 12, 8, p); line(c, 4, 12, 20, 12); c.drawOval(new RectF(8.5f, 4, 15.5f, 20), p); break;
                default: break;
            }
            c.restore();
        }

        private void line(Canvas c, float x1, float y1, float x2, float y2) { c.drawLine(x1, y1, x2, y2, p); }

        private void poly(Canvas c, float... v) {
            Path path = new Path();
            path.moveTo(v[0], v[1]);
            for (int i = 2; i + 1 < v.length; i += 2) path.lineTo(v[i], v[i + 1]);
            c.drawPath(path, p);
        }

        private void dot(Canvas c, float x, float y) {
            Paint.Style old = p.getStyle();
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(x, y, 1.9f, p);
            p.setStyle(old);
        }
    }
}
