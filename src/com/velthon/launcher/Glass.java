package com.velthon.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.widget.LinearLayout;

/** A frosted glass panel: the blurred window behind it, a light tint, a bright top edge and a thin border. */
class Glass extends LinearLayout {
    static final int CLEAR = 0, GREEN = 1, BLUE = 2, AMBER = 3, DARK = 4, GOLD = 5, GOLD_EDGE = 6, WARM = 7, NONE = 8, SHEET = 9, FROST = 10, BAND = 11;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG),
            glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final RectF r = new RectF();
    float radius;
    int style;
    boolean blur = true;
    /** A round notch cut out of the top edge (for the dock's raised button): centre x as a fraction, radius in px. */
    float notchX = -1, notchR, notchCy = Float.NaN;
    void setNotch(float x) { notchX = x; ls = -1; invalidate(); }
    private int lw = -1, lh = -1, ls = -1;

    Glass(Context c, float radiusDp, int style) {
        super(c);
        this.radius = U.dp(c, radiusDp);
        this.style = style;
        setWillNotDraw(false);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(U.dp(c, 1));
    }

    void setStyle(int s) { if (s != style) { style = s; ls = -1; invalidate(); } }

    private void build(int w, int h) {
        lw = w; lh = h; ls = style;
        r.set(0, 0, w, h);
        shape.reset();
        float rad = Math.min(radius, h / 2f);
        shape.addRoundRect(r, rad, rad, Path.Direction.CW);
        if (notchX >= 0) {
            float cx = w * notchX, cy = Float.isNaN(notchCy) ? -notchR * .15f : notchCy;
            Path cut = new Path();
            cut.addCircle(cx, cy, notchR, Path.Direction.CW);
            // smooth shoulders: small circles tangent to the notch and to the top edge round off the corners
            float f = notchR * .3f, dy = f - cy, dx = (float) Math.sqrt(Math.max(0, (notchR + f) * (notchR + f) - dy * dy));
            Path band = new Path();
            band.addRect(cx - dx, -notchR * 3, cx + dx, f, Path.Direction.CW);
            Path fil = new Path();
            fil.addCircle(cx - dx, f, f, Path.Direction.CW);
            fil.addCircle(cx + dx, f, f, Path.Direction.CW);
            band.op(fil, Path.Op.DIFFERENCE);
            cut.op(band, Path.Op.UNION);
            shape.op(cut, Path.Op.DIFFERENCE);
        }
        int top, bottom, line;
        switch (style) {
            case GREEN: top = 0x8C50BE96; bottom = 0x73288264; line = 0x73A0FFD7; break;
            case BLUE: top = 0x8C6E96FF; bottom = 0x803C5FC8; line = 0x8CAAC8FF; break;
            case AMBER: top = 0x8CD6AA78; bottom = 0x80966941; line = 0x73FFE6C8; break;
            case WARM: top = 0x52140C06; bottom = 0x70100804; line = 0; break;     // dimmed dock, no border
            case FROST: top = 0x40FFFFFF; bottom = 0x33FFF8F0; line = 0; break;   // light frosted dock, no border
            case SHEET: top = 0xF2201812; bottom = 0xFA14100C; line = 0; break;   // the settings picker
            case DARK: top = 0x8C141A2C; bottom = 0xB80E1220; line = 0x38FFFFFF; break;
            case GOLD: top = 0xF2FFD54A; bottom = 0xE6E0A800; line = 0xCCFFE699; break;          // Velthon gold
            case GOLD_EDGE: top = 0x33FFFFFF; bottom = 0x14FFFFFF; line = 0xD9FFC400; break;    // clear glass, gold rim
            default: top = 0x29FFFFFF; bottom = 0x0DFFFFFF; line = 0x38FFFFFF;
        }
        fill.setShader(new LinearGradient(0, 0, w * .3f, h, top, bottom, Shader.TileMode.CLAMP));
        edge.setShader(new LinearGradient(0, 0, 0, h, (line & 0x00FFFFFF) | 0xA0000000, line, Shader.TileMode.CLAMP));
        if (style == GREEN || style == BLUE || style == GOLD || style == GOLD_EDGE) {
            glow.setColor(style == GREEN ? 0x5546DCA0 : style == BLUE ? 0x556E96FF : 0x55FFC400);
            glow.setMaskFilter(new android.graphics.BlurMaskFilter(U.dp(getContext(), 10), android.graphics.BlurMaskFilter.Blur.OUTER));
        } else glow.setMaskFilter(null);
    }

    private android.graphics.Paint bandFill, bandFade, holePaint;
    /** BAND only: clear windows cut in the band (the dock's icon boxes), so the sky shows through them. */
    final java.util.ArrayList<android.graphics.RectF> holes = new java.util.ArrayList<>();
    float holeR;
    @Override protected void onDraw(Canvas c) {
        if (style == NONE) { super.onDraw(c); return; }
        if (style == BAND) {
            // the dock from the sketch: a light milky band, straight across, its ends fading out
            int w = getWidth(), h = getHeight();
            if (bandFill == null || w != lw) {
                lw = w; lh = h;
                bandFill = new android.graphics.Paint();
                bandFill.setColor(0x75E6E0D6);          // more see-through
                bandFade = new android.graphics.Paint();
                bandFade.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
                bandFade.setShader(new android.graphics.LinearGradient(0, 0, w, 0,
                        new int[] {0x00000000, 0xFF000000, 0xFF000000, 0x00000000}, new float[] {0f, .3f, .7f, 1f},   // fades out under the first and last icons
                        android.graphics.Shader.TileMode.CLAMP));
            }
            int layer = c.saveLayer(0, 0, w, h, null);
            if (blur) { Scene s = Scene.of(this); if (s != null) s.drawBlurBehind(c, this); }
            c.drawRect(0, 0, w, h, bandFill);
            c.drawRect(0, 0, w, h, bandFade);
            if (!holes.isEmpty()) {
                if (holePaint == null) { holePaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                    holePaint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)); }
                for (android.graphics.RectF r : holes) c.drawRoundRect(r, holeR, holeR, holePaint);
            }
            c.restoreToCount(layer);
            super.onDraw(c);
            return;
        }
        int w = getWidth(), h = getHeight();
        if (w != lw || h != lh || style != ls) build(w, h);
        int sc = c.save();
        c.clipPath(shape);
        if (blur) { Scene s = Scene.of(this); if (s != null) s.drawBlurBehind(c, this); }
        c.drawPath(shape, fill);
        c.restoreToCount(sc);
        super.onDraw(c);   // no borders anywhere: the dimmed tint is enough
    }
}
