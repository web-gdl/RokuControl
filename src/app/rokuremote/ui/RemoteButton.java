package app.rokuremote.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

/** Botón del control: icono dibujado, respuesta inmediata al tocar y repetición opcional. */
final class RemoteButton extends View {
    private final Icons.Kind kind;
    private final String label;
    private final boolean repeatable;
    private final HoldRepeater.Action action;
    private final HoldRepeater repeater = new HoldRepeater();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String lockedReason;
    private int iconColor = Theme.TEXT;

    /**
     * @param label      texto junto al icono (null = solo icono); también es la descripción para accesibilidad
     * @param description descripción para lectores de pantalla
     */
    RemoteButton(Context ctx, Icons.Kind kind, String label, String description, boolean repeatable,
                 HoldRepeater.Action action) {
        super(ctx);
        this.kind = kind;
        this.label = label;
        this.repeatable = repeatable;
        this.action = action;
        setContentDescription(description);
        setBackground(Theme.pressable(Theme.SURFACE, Theme.SURFACE_PRESSED, Theme.dp(ctx, 999)));
        setClickable(true);
        paint.setTextSize(Theme.dp(ctx, 15));
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    }

    void setIconColor(int color) {
        iconColor = color;
        invalidate();
    }

    /** Marca el botón como no disponible en este Roku; al tocarlo se explica por qué. */
    void setLocked(String reason) {
        lockedReason = reason;
        setAlpha(reason == null ? 1f : 0.38f);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                setPressed(true);
                if (lockedReason != null) {
                    Toast.makeText(getContext(), lockedReason, Toast.LENGTH_SHORT).show();
                    return true;
                }
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                repeater.start(action, repeatable);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                repeater.stop();
                return true;
            default:
                return true;
        }
    }

    /** Activación por accesibilidad (TalkBack, teclado). */
    @Override
    public boolean performClick() {
        if (lockedReason != null) {
            Toast.makeText(getContext(), lockedReason, Toast.LENGTH_SHORT).show();
        } else {
            action.run(false);
        }
        return super.performClick();
    }

    @Override
    protected void onDetachedFromWindow() {
        repeater.stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float icon = Math.min(h * 0.46f, Theme.dp(getContext(), 26));
        paint.setColor(iconColor);
        if (label == null) {
            Icons.draw(c, kind, w / 2f, h / 2f, icon, paint);
        } else {
            paint.setStyle(Paint.Style.FILL);
            float textW = paint.measureText(label);
            float gap = Theme.dp(getContext(), 10);
            float total = icon + gap + textW;
            float x0 = (w - total) / 2f;
            Icons.draw(c, kind, x0 + icon / 2f, h / 2f, icon, paint);
            paint.setStyle(Paint.Style.FILL);
            Paint.FontMetrics fm = paint.getFontMetrics();
            c.drawText(label, x0 + icon + gap, h / 2f - (fm.ascent + fm.descent) / 2f, paint);
        }
    }
}
