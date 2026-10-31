package app.rokuremote.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import app.rokuremote.ecp.Key;

/** Cruceta circular con OK central. Las flechas se repiten al mantener pulsado. */
final class DPadView extends View {
    interface Listener {
        void onKey(String key, boolean repeat);
    }

    private static final float INNER = 0.34f; // radio del OK respecto al radio total

    private final Listener listener;
    private final HoldRepeater repeater = new HoldRepeater();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private String pressedKey;

    DPadView(Context ctx, Listener listener) {
        super(ctx);
        this.listener = listener;
        setClickable(true);
        setContentDescription("Cruceta de navegación");
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    }

    private float cx() { return getWidth() / 2f; }
    private float cy() { return getHeight() / 2f; }
    private float radius() { return Math.min(getWidth(), getHeight()) / 2f - Theme.dp(getContext(), 4); }

    /** Tecla bajo el punto tocado, o null si cae fuera del círculo. */
    private String keyAt(float x, float y) {
        float dx = x - cx(), dy = y - cy();
        float r = (float) Math.hypot(dx, dy);
        float R = radius();
        if (r > R * 1.08f) return null;
        if (r < R * INNER) return Key.SELECT;
        if (Math.abs(dx) > Math.abs(dy)) return dx < 0 ? Key.LEFT : Key.RIGHT;
        return dy < 0 ? Key.UP : Key.DOWN;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                String k = keyAt(e.getX(), e.getY());
                if (k == null) return false;
                pressedKey = k;
                invalidate();
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                repeater.start(rep -> listener.onKey(k, rep), !k.equals(Key.SELECT));
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                repeater.stop();
                pressedKey = null;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        listener.onKey(Key.SELECT, false);
        return super.performClick();
    }

    @Override
    protected void onDetachedFromWindow() {
        repeater.stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = cx(), cy = cy(), R = radius();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Theme.SURFACE);
        c.drawCircle(cx, cy, R, paint);

        if (pressedKey != null && !pressedKey.equals(Key.SELECT)) {
            paint.setColor(Theme.SURFACE_PRESSED);
            oval.set(cx - R, cy - R, cx + R, cy + R);
            float start = pressedKey.equals(Key.UP) ? 225 : pressedKey.equals(Key.RIGHT) ? 315
                    : pressedKey.equals(Key.DOWN) ? 45 : 135;
            c.drawArc(oval, start, 90, true, paint);
        }

        // Anillo interior del OK
        float ri = R * INNER;
        paint.setColor(Theme.BG);
        c.drawCircle(cx, cy, ri + Theme.dp(getContext(), 3), paint);
        paint.setColor(Key.SELECT.equals(pressedKey) ? Theme.ACCENT : Theme.SURFACE);
        c.drawCircle(cx, cy, ri, paint);
        paint.setColor(Theme.TEXT);
        paint.setTextSize(ri * 0.62f);
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = paint.getFontMetrics();
        c.drawText("OK", cx, cy - (fm.ascent + fm.descent) / 2f, paint);

        // Flechas
        paint.setColor(Theme.TEXT);
        float d = R * 0.67f, a = R * 0.09f;
        arrow(c, cx, cy - d, 0, -1, a);
        arrow(c, cx, cy + d, 0, 1, a);
        arrow(c, cx - d, cy, -1, 0, a);
        arrow(c, cx + d, cy, 1, 0, a);
    }

    /** Triángulo con vértice en la dirección (dx, dy) centrado en (x, y). */
    private void arrow(Canvas c, float x, float y, int dx, int dy, float a) {
        Path p = new Path();
        p.moveTo(x + dx * a * 1.4f, y + dy * a * 1.4f);
        p.lineTo(x - dx * a + (-dy) * a * 1.5f, y - dy * a + dx * a * 1.5f);
        p.lineTo(x - dx * a - (-dy) * a * 1.5f, y - dy * a - dx * a * 1.5f);
        p.close();
        c.drawPath(p, paint);
    }
}
