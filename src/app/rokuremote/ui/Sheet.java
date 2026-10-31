package app.rokuremote.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Hoja inferior oscura con título y contenido desplazable. */
class Sheet {
    final Context ctx;
    final Dialog dialog;
    final LinearLayout body;
    private final LinearLayout root;

    /** ScrollView que no crece más del 55 % de la pantalla. */
    private static final class CappedScroll extends ScrollView {
        CappedScroll(Context c) { super(c); }
        @Override protected void onMeasure(int w, int h) {
            int cap = (int) (getResources().getDisplayMetrics().heightPixels * 0.55f);
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST));
        }
    }

    @SuppressWarnings("deprecation") // ADJUST_RESIZE sigue siendo la forma de subir el diálogo con el teclado
    Sheet(Context ctx, String title) {
        this.ctx = ctx;
        dialog = new Dialog(ctx);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        float r = Theme.dp(ctx, 24);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.SHEET);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        root.setBackground(bg);
        int pad = Theme.dp(ctx, 20);
        root.setPadding(pad, Theme.dp(ctx, 18), pad, Theme.dp(ctx, 28));

        TextView t = text(ctx, title, 20, Theme.TEXT, true);
        root.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new CappedScroll(ctx);
        body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = Theme.dp(ctx, 10);
        root.addView(scroll, sp);

        dialog.setContentView(root);
        Window w = dialog.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        w.setGravity(Gravity.BOTTOM);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    /** Añade una vista fija bajo el contenido desplazable. */
    void addFooter(View v) {
        root.addView(v);
    }

    /** Muestra el teclado del teléfono al abrir la hoja (y la sube con él). */
    @SuppressWarnings("deprecation")
    void showKeyboardOnOpen(View target) {
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.setOnShowListener(d -> target.requestFocus());
    }

    /** Añade una vista entre el título y el contenido desplazable. */
    void addHeader(View v, ViewGroup.LayoutParams lp) {
        root.addView(v, 1, lp);
    }

    void show() { dialog.show(); }

    void dismiss() { dialog.dismiss(); }

    boolean isShowing() { return dialog.isShowing(); }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }

    static TextView pill(Context c, String label, int bgColor, int textColor, View.OnClickListener l) {
        TextView t = text(c, label, 15, textColor, true);
        t.setGravity(Gravity.CENTER);
        int ph = Theme.dp(c, 18);
        t.setPadding(ph, Theme.dp(c, 12), ph, Theme.dp(c, 12));
        t.setBackground(Theme.pressable(bgColor, Theme.SURFACE_PRESSED, Theme.dp(c, 999)));
        t.setOnClickListener(l);
        return t;
    }
}
