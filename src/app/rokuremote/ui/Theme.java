package app.rokuremote.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;

/** Colores y medidas del tema oscuro (único tema). */
final class Theme {
    static final int BG = 0xFF0E1014;
    static final int SURFACE = 0xFF1A1D25;
    static final int SURFACE_PRESSED = 0xFF2C3140;
    static final int SHEET = 0xFF161920;
    static final int ACCENT = 0xFF8B6CFF;
    static final int TEXT = 0xFFEDEFF5;
    static final int TEXT_DIM = 0xFF8D93A5;
    static final int OK = 0xFF3DDC84;
    static final int WARN = 0xFFFFB020;
    static final int OFF = 0xFF5A5F70;
    static final int DANGER = 0xFFFF6B6B;

    private Theme() {}

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static GradientDrawable rect(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    /** Fondo con color distinto al pulsar. */
    static StateListDrawable pressable(int normal, int pressed, float radiusPx) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, rect(pressed, radiusPx));
        s.addState(new int[]{}, rect(normal, radiusPx));
        return s;
    }
}
