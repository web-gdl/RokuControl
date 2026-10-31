package app.rokuremote.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Vista que dibuja un único icono. */
final class IconView extends View {
    private final Icons.Kind kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    IconView(Context ctx, Icons.Kind kind, int color) {
        super(ctx);
        this.kind = kind;
        paint.setColor(color);
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight()) * 0.7f;
        Icons.draw(c, kind, getWidth() / 2f, getHeight() / 2f, s, paint);
    }
}
