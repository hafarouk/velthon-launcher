package com.velthon.launcher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Thin line icons drawn on a 24 x 24 grid (in the style of the mockup). */
final class Ico extends Drawable {
    static final int HOME = 0, HEART = 1, GRID = 2, BELL = 3, SEARCH = 4, SHARE = 5, SOUND = 6, BOOK = 7,
            GEAR = 8, CLOSE = 9, HEART_FULL = 10, SHUFFLE = 11, BACK = 12, NEXT = 13,
            LEAF = 14, CLOUD = 15, MOON = 16, CROSS = 17;
    final int type;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    Ico(int type, int color) {
        this.type = type;
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override public void draw(Canvas c) {
        float s = Math.min(getBounds().width(), getBounds().height()) / 24f;
        c.save();
        c.translate(getBounds().left + (getBounds().width() - 24 * s) / 2f, getBounds().top + (getBounds().height() - 24 * s) / 2f);
        c.scale(s, s);
        p.setStrokeWidth(2f);
        p.setStyle(Paint.Style.STROKE);
        path.reset();
        switch (type) {
            case HOME:
                path.moveTo(3, 10); path.lineTo(12, 3); path.lineTo(21, 10); path.lineTo(21, 20);
                path.quadTo(21, 22, 19, 22); path.lineTo(5, 22); path.quadTo(3, 22, 3, 20); path.close();
                path.moveTo(9, 22); path.lineTo(9, 13); path.lineTo(15, 13); path.lineTo(15, 22);
                c.drawPath(path, p); break;
            case HEART: case HEART_FULL:
                path.moveTo(12, 21);
                path.cubicTo(5, 15.5f, 2, 12.5f, 2, 8.5f);
                path.cubicTo(2, 5.2f, 4.5f, 3, 7.3f, 3);
                path.cubicTo(9.3f, 3, 11, 4.2f, 12, 5.8f);
                path.cubicTo(13, 4.2f, 14.7f, 3, 16.7f, 3);
                path.cubicTo(19.5f, 3, 22, 5.2f, 22, 8.5f);
                path.cubicTo(22, 12.5f, 19, 15.5f, 12, 21);
                path.close();
                if (type == HEART_FULL) { p.setStyle(Paint.Style.FILL_AND_STROKE); }
                c.drawPath(path, p); break;
            case GRID:
                c.drawRoundRect(new RectF(3, 3, 10, 10), 2, 2, p); c.drawRoundRect(new RectF(14, 3, 21, 10), 2, 2, p);
                c.drawRoundRect(new RectF(3, 14, 10, 21), 2, 2, p); c.drawRoundRect(new RectF(14, 14, 21, 21), 2, 2, p);
                break;
            case BELL:
                path.moveTo(18, 8); path.cubicTo(18, 4.7f, 15.3f, 2, 12, 2); path.cubicTo(8.7f, 2, 6, 4.7f, 6, 8);
                path.cubicTo(6, 15, 3, 17, 3, 17); path.lineTo(21, 17); path.cubicTo(21, 17, 18, 15, 18, 8);
                path.moveTo(13.7f, 21); path.quadTo(12, 22.6f, 10.3f, 21);
                c.drawPath(path, p); break;
            case SEARCH:
                c.drawCircle(11, 11, 7, p); c.drawLine(16.2f, 16.2f, 21, 21, p); break;
            case SHARE:
                c.drawCircle(18, 5, 3, p); c.drawCircle(6, 12, 3, p); c.drawCircle(18, 19, 3, p);
                c.drawLine(8.6f, 13.5f, 15.4f, 17.5f, p); c.drawLine(15.4f, 6.5f, 8.6f, 10.5f, p); break;
            case SOUND:
                path.moveTo(11, 5); path.lineTo(6, 9); path.lineTo(2, 9); path.lineTo(2, 15); path.lineTo(6, 15); path.lineTo(11, 19); path.close();
                c.drawPath(path, p);
                c.drawArc(new RectF(10.5f, 7, 18.5f, 17), -50, 100, false, p);
                c.drawArc(new RectF(9, 3, 23, 21), -55, 110, false, p); break;
            case BOOK:
                path.moveTo(4, 19.5f); path.quadTo(4, 17, 6.5f, 17); path.lineTo(20, 17); path.lineTo(20, 2); path.lineTo(6.5f, 2);
                path.quadTo(4, 2, 4, 4.5f); path.close();
                path.moveTo(12, 6); path.lineTo(12, 13); path.moveTo(9, 9); path.lineTo(15, 9);
                c.drawPath(path, p); break;
            case GEAR:
                c.drawCircle(12, 12, 3, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4;
                    c.drawLine((float) (12 + 6.5 * Math.cos(a)), (float) (12 + 6.5 * Math.sin(a)),
                            (float) (12 + 9.5 * Math.cos(a)), (float) (12 + 9.5 * Math.sin(a)), p);
                }
                c.drawCircle(12, 12, 6.5f, p); break;
            case CLOSE:
                c.drawLine(6, 6, 18, 18, p); c.drawLine(18, 6, 6, 18, p); break;
            case SHUFFLE:
                path.moveTo(3, 7); path.lineTo(7, 7); path.cubicTo(12, 7, 12, 17, 17, 17); path.lineTo(21, 17);
                path.moveTo(3, 17); path.lineTo(7, 17); path.cubicTo(12, 17, 12, 7, 17, 7); path.lineTo(21, 7);
                path.moveTo(18, 4); path.lineTo(21, 7); path.lineTo(18, 10);
                path.moveTo(18, 14); path.lineTo(21, 17); path.lineTo(18, 20);
                c.drawPath(path, p); break;
            case BACK:
                path.moveTo(15, 5); path.lineTo(8, 12); path.lineTo(15, 19); c.drawPath(path, p); break;
            case LEAF:
                path.moveTo(5, 19); path.cubicTo(4, 10, 10, 4, 20, 4); path.cubicTo(20, 14, 14, 20, 5, 19); path.close();
                path.moveTo(5, 19); path.lineTo(14, 10);
                c.drawPath(path, p); break;
            case CLOUD:
                path.moveTo(7, 19); path.cubicTo(3.5f, 19, 2, 16.5f, 2, 14.5f); path.cubicTo(2, 12, 4, 10, 6.5f, 10.2f);
                path.cubicTo(7.3f, 6.8f, 10, 5, 13, 5); path.cubicTo(16.8f, 5, 19.5f, 8, 19.6f, 11.2f);
                path.cubicTo(21.4f, 11.6f, 22.5f, 13.2f, 22.5f, 15); path.cubicTo(22.5f, 17.4f, 20.6f, 19, 18.5f, 19); path.close();
                c.drawPath(path, p); break;
            case MOON:
                path.moveTo(20, 14.5f); path.cubicTo(18.8f, 15.2f, 17.4f, 15.6f, 15.9f, 15.6f);
                path.cubicTo(11.4f, 15.6f, 7.8f, 12, 7.8f, 7.5f); path.cubicTo(7.8f, 6, 8.2f, 4.6f, 8.9f, 3.4f);
                path.cubicTo(5.3f, 4.6f, 2.8f, 8, 2.8f, 12); path.cubicTo(2.8f, 17.1f, 6.9f, 21.2f, 12, 21.2f);
                path.cubicTo(15.6f, 21.2f, 18.7f, 19.1f, 20, 14.5f); path.close();
                c.drawPath(path, p); break;
            case CROSS:
                path.moveTo(12, 2.5f); path.lineTo(12, 21.5f); path.moveTo(6, 8); path.lineTo(18, 8);
                c.drawPath(path, p); break;
            case NEXT:
                path.moveTo(9, 5); path.lineTo(16, 12); path.lineTo(9, 19); c.drawPath(path, p); break;
        }
        c.restore();
    }

    @Override public void setAlpha(int a) { p.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
    void setColor(int c) { p.setColor(c); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
