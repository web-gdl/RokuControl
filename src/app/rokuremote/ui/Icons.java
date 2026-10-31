package app.rokuremote.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Iconos vectoriales dibujados con Canvas: sin imágenes ni bibliotecas. */
final class Icons {
    enum Kind { POWER, HOME, BACK, PLAY, REV, FWD, REPLAY, OPTIONS, VOL_UP, VOL_DOWN, MUTE, KEYBOARD, APPS, CHEVRON, CLOSE }

    private Icons() {}

    /** Dibuja el icono centrado en (cx, cy) dentro de un cuadrado de lado {@code s}. */
    static void draw(Canvas c, Kind k, float cx, float cy, float s, Paint p) {
        float h = s / 2f;
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(s * 0.1f);
        Path path = new Path();
        switch (k) {
            case POWER:
                p.setStyle(Paint.Style.STROKE);
                c.drawArc(new RectF(cx - h * .7f, cy - h * .7f, cx + h * .7f, cy + h * .7f), -60, 300, false, p);
                c.drawLine(cx, cy - h * .85f, cx, cy - h * .1f, p);
                break;
            case HOME:
                p.setStyle(Paint.Style.STROKE);
                path.moveTo(cx - h * .8f, cy); path.lineTo(cx, cy - h * .75f); path.lineTo(cx + h * .8f, cy);
                path.moveTo(cx - h * .5f, cy - h * .2f); path.lineTo(cx - h * .5f, cy + h * .7f);
                path.lineTo(cx + h * .5f, cy + h * .7f); path.lineTo(cx + h * .5f, cy - h * .2f);
                c.drawPath(path, p);
                break;
            case BACK:
                p.setStyle(Paint.Style.STROKE);
                path.moveTo(cx - h * .3f, cy - h * .55f); path.lineTo(cx - h * .75f, cy - h * .1f); path.lineTo(cx - h * .3f, cy + h * .35f);
                path.moveTo(cx - h * .75f, cy - h * .1f); path.lineTo(cx + h * .2f, cy - h * .1f);
                path.quadTo(cx + h * .75f, cy - h * .1f, cx + h * .75f, cy + h * .4f);
                c.drawPath(path, p);
                break;
            case PLAY:
                p.setStyle(Paint.Style.FILL);
                path.moveTo(cx - h * .8f, cy - h * .6f); path.lineTo(cx - h * .8f, cy + h * .6f); path.lineTo(cx - h * .1f, cy);
                path.close();
                c.drawPath(path, p);
                c.drawRoundRect(new RectF(cx + h * .2f, cy - h * .6f, cx + h * .42f, cy + h * .6f), h * .08f, h * .08f, p);
                c.drawRoundRect(new RectF(cx + h * .6f, cy - h * .6f, cx + h * .82f, cy + h * .6f), h * .08f, h * .08f, p);
                break;
            case REV:
            case FWD: {
                p.setStyle(Paint.Style.FILL);
                float dir = k == Kind.FWD ? 1f : -1f;
                for (int i = 0; i < 2; i++) {
                    float baseX = cx + dir * (-.55f + i * .65f) * h;
                    float apexX = baseX + dir * .65f * h;
                    Path t = new Path();
                    t.moveTo(baseX, cy - h * .55f); t.lineTo(baseX, cy + h * .55f); t.lineTo(apexX, cy);
                    t.close();
                    c.drawPath(t, p);
                }
                break;
            }
            case REPLAY: {
                p.setStyle(Paint.Style.STROKE);
                float r = h * .7f;
                c.drawArc(new RectF(cx - r, cy - r, cx + r, cy + r), -40, -280, false, p);
                double end = Math.toRadians(40);
                float ex = cx + r * (float) Math.cos(end), ey = cy + r * (float) Math.sin(end);
                float dx = (float) Math.sin(end), dy = (float) -Math.cos(end); // sentido antihorario
                p.setStyle(Paint.Style.FILL);
                Path t = new Path();
                t.moveTo(ex + dx * h * .45f, ey + dy * h * .45f);
                t.lineTo(ex - dy * h * .35f - dx * h * .1f, ey + dx * h * .35f - dy * h * .1f);
                t.lineTo(ex + dy * h * .35f - dx * h * .1f, ey - dx * h * .35f - dy * h * .1f);
                t.close();
                c.drawPath(t, p);
                break;
            }
            case OPTIONS:
                p.setStyle(Paint.Style.STROKE);
                for (int a : new int[]{90, 30, 150}) {
                    float dx = (float) Math.cos(Math.toRadians(a)) * h * .8f;
                    float dy = (float) Math.sin(Math.toRadians(a)) * h * .8f;
                    c.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, p);
                }
                break;
            case VOL_UP:
            case VOL_DOWN:
            case MUTE:
                p.setStyle(Paint.Style.FILL);
                path.moveTo(cx - h * .85f, cy - h * .25f); path.lineTo(cx - h * .45f, cy - h * .25f);
                path.lineTo(cx, cy - h * .65f); path.lineTo(cx, cy + h * .65f);
                path.lineTo(cx - h * .45f, cy + h * .25f); path.lineTo(cx - h * .85f, cy + h * .25f);
                path.close();
                c.drawPath(path, p);
                p.setStyle(Paint.Style.STROKE);
                if (k == Kind.VOL_UP) {
                    c.drawLine(cx + h * .35f, cy, cx + h * .9f, cy, p);
                    c.drawLine(cx + h * .625f, cy - h * .275f, cx + h * .625f, cy + h * .275f, p);
                } else if (k == Kind.VOL_DOWN) {
                    c.drawLine(cx + h * .35f, cy, cx + h * .9f, cy, p);
                } else {
                    c.drawLine(cx + h * .3f, cy - h * .3f, cx + h * .85f, cy + h * .3f, p);
                    c.drawLine(cx + h * .3f, cy + h * .3f, cx + h * .85f, cy - h * .3f, p);
                }
                break;
            case KEYBOARD:
                p.setStyle(Paint.Style.STROKE);
                c.drawRoundRect(new RectF(cx - h * .9f, cy - h * .55f, cx + h * .9f, cy + h * .55f), h * .15f, h * .15f, p);
                p.setStyle(Paint.Style.FILL);
                for (int i = -1; i <= 1; i++) c.drawCircle(cx + i * h * .5f, cy - h * .2f, h * .08f, p);
                p.setStyle(Paint.Style.STROKE);
                c.drawLine(cx - h * .45f, cy + h * .25f, cx + h * .45f, cy + h * .25f, p);
                break;
            case APPS:
                p.setStyle(Paint.Style.FILL);
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sy = -1; sy <= 1; sy += 2) {
                        float x = cx + sx * h * .4f, y = cy + sy * h * .4f;
                        c.drawRoundRect(new RectF(x - h * .3f, y - h * .3f, x + h * .3f, y + h * .3f), h * .1f, h * .1f, p);
                    }
                }
                break;
            case CHEVRON:
                p.setStyle(Paint.Style.STROKE);
                path.moveTo(cx - h * .5f, cy - h * .2f); path.lineTo(cx, cy + h * .3f); path.lineTo(cx + h * .5f, cy - h * .2f);
                c.drawPath(path, p);
                break;
            case CLOSE:
                p.setStyle(Paint.Style.STROKE);
                c.drawLine(cx - h * .5f, cy - h * .5f, cx + h * .5f, cy + h * .5f, p);
                c.drawLine(cx - h * .5f, cy + h * .5f, cx + h * .5f, cy - h * .5f, p);
                break;
        }
    }
}
