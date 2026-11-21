package com.velthon.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import java.util.HashMap;

/** App icons in one shape: the phone's own icon as a rounded square with a thin light edge. */
final class Shape {
    private Shape() { }
    private static final HashMap<String, Bitmap> CACHE = new HashMap<>();

    static void clear() { synchronized (CACHE) { CACHE.clear(); } }

    static Bitmap icon(Context c, String key, Drawable d, int px) {
        String k = key + "@" + px;
        synchronized (CACHE) { Bitmap b = CACHE.get(k); if (b != null) return b; }
        Bitmap b = render(c, d, px);
        synchronized (CACHE) { CACHE.put(k, b); }
        return b;
    }

    /** The dock's look: a thin light outline, clear inside, with the app's symbol in the middle.
     *  Uses the phone's single-colour (themed) symbol in white when the app has one, else its own symbol layer. */
    static Bitmap outline(Context c, String key, Drawable d, int px) { return glyph(c, key, d, px, true); }
    /** The app list's look: the same symbol on a faint frosted tile, no border. */
    static Bitmap glass(Context c, String key, Drawable d, int px) { return glyph(c, key, d, px, false, false); }
    /** The home screen's look: the same symbol on a deeper, smoky tile, so it stands out on a bright sky. */
    static Bitmap glassDark(Context c, String key, Drawable d, int px) { return glyph(c, key, d, px, false, true); }
    private static Bitmap glyph(Context c, String key, Drawable d, int px, boolean rimmed) { return glyph(c, key, d, px, rimmed, false); }
    private static Bitmap glyph(Context c, String key, Drawable d, int px, boolean rimmed, boolean dark) {
        String k = key + (rimmed ? "#o@" : dark ? "#d@" : "#g@") + px;
        synchronized (CACHE) { Bitmap b = CACHE.get(k); if (b != null) return b; }
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        if (d != null) {
            Drawable s = d.getConstantState() != null ? d.getConstantState().newDrawable(c.getResources()).mutate() : d;
            Canvas cv = new Canvas(b);
            float edge = U.dp(c, 1.5f), in = edge / 2f + .5f, rad = px * .27f;
            RectF r = new RectF(in, in, px - in, px - in);
            Path shape = new Path(); shape.addRoundRect(r, rad, rad, Path.Direction.CW);
            if (!rimmed) {   // a faint frosted tile, lighter at the top
                Paint tile = new Paint(Paint.ANTI_ALIAS_FLAG);
                tile.setShader(new android.graphics.LinearGradient(0, 0, 0, px, dark ? 0x80243248 : 0x33FFFFFF, dark ? 0x99131C2C : 0x14FFFFFF, android.graphics.Shader.TileMode.CLAMP));
                cv.drawPath(shape, tile);
            }
            cv.save(); cv.clipPath(shape);
            Drawable glyph = null; boolean mono = false;
            if (Build.VERSION.SDK_INT >= 26 && s instanceof android.graphics.drawable.AdaptiveIconDrawable) {
                android.graphics.drawable.AdaptiveIconDrawable a = (android.graphics.drawable.AdaptiveIconDrawable) s;
                if (Build.VERSION.SDK_INT >= 33) { try { glyph = (Drawable) a.getClass().getMethod("getMonochrome").invoke(a); mono = glyph != null; } catch (Throwable ignored) { } }
                if (glyph == null) glyph = a.getForeground();
                if (glyph != null) {
                    int ext = Math.round(px * .25f);
                    glyph.setBounds(-ext, -ext, px + ext, px + ext);
                    if (mono) glyph.setColorFilter(new android.graphics.PorterDuffColorFilter(0xFFFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN));
                    glyph.draw(cv);
                }
                if (coverage(b) < .04f) {
                    // some apps keep their symbol in the back layer (Chrome): show the whole icon, smaller, as a round badge
                    b.eraseColor(0);
                    if (!rimmed) {
                        cv.restore(); 
                        Paint tile = new Paint(Paint.ANTI_ALIAS_FLAG);
                        tile.setShader(new android.graphics.LinearGradient(0, 0, 0, px, dark ? 0x80243248 : 0x33FFFFFF, dark ? 0x99131C2C : 0x14FFFFFF, android.graphics.Shader.TileMode.CLAMP));
                        cv.drawPath(shape, tile);
                        cv.save(); cv.clipPath(shape);
                    }
                    int m = Math.round(px * .2f);
                    Path round = new Path(); round.addCircle(px / 2f, px / 2f, (px - 2 * m) / 2f, Path.Direction.CW);
                    cv.save(); cv.clipPath(round);
                    int e2 = Math.round((px - 2 * m) * .25f);
                    Drawable bg = a.getBackground(), fg = a.getForeground();
                    if (bg != null) { bg.setBounds(m - e2, m - e2, px - m + e2, px - m + e2); bg.draw(cv); }
                    if (fg != null) { fg.setBounds(m - e2, m - e2, px - m + e2, px - m + e2); fg.draw(cv); }
                    cv.restore();
                }
            } else {
                int pad = Math.round(px * .2f);
                s.setBounds(pad, pad, px - pad, px - pad);
                s.draw(cv);
            }
            cv.restore();
            if (rimmed) {
                Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
                rim.setStyle(Paint.Style.STROKE); rim.setStrokeWidth(edge); rim.setColor(0xD9FFFFFF);
                cv.drawPath(shape, rim);
            }
        }
        synchronized (CACHE) { CACHE.put(k, b); }
        return b;
    }

    /** How much of the picture is painted (0..1). */
    private static float coverage(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight(), n = 0, t = 0;
        for (int y = 0; y < h; y += 3) for (int x = 0; x < w; x += 3) { t++; if ((b.getPixel(x, y) >>> 24) > 60) n++; }
        return t == 0 ? 0 : n / (float) t;
    }
    static Bitmap render(Context c, Drawable src, int px) {
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        if (src == null) return b;
        Drawable d = src.getConstantState() != null ? src.getConstantState().newDrawable(c.getResources()).mutate() : src;
        Canvas cv = new Canvas(b);
        float edge = U.dp(c, 1.6f), in = edge / 2f + .5f;
        RectF r = new RectF(in, in, px - in, px - in);
        float rad = px * .27f;
        Path shape = new Path();
        shape.addRoundRect(r, rad, rad, Path.Direction.CW);
        cv.save();
        cv.clipPath(shape);
        if (Build.VERSION.SDK_INT >= 26 && d instanceof android.graphics.drawable.AdaptiveIconDrawable) {
            // the phone's own layers, filling the shape (adaptive icons are drawn larger than what shows)
            android.graphics.drawable.AdaptiveIconDrawable a = (android.graphics.drawable.AdaptiveIconDrawable) d;
            int ext = Math.round(px * .25f);
            Drawable bg = a.getBackground(), fg = a.getForeground();
            if (bg != null) { bg.setBounds(-ext, -ext, px + ext, px + ext); bg.draw(cv); }
            if (fg != null) { fg.setBounds(-ext, -ext, px + ext, px + ext); fg.draw(cv); }
        } else {
            // older icons: on a soft white tile, a little inside the edge
            cv.drawColor(0xFFFBF8F2);
            int pad = Math.round(px * .14f);
            d.setBounds(pad, pad, px - pad, px - pad);
            d.draw(cv);
        }
        cv.restore();
        return b;
    }
}
