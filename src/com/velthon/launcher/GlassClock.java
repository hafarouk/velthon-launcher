package com.velthon.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.View;
import java.util.Calendar;

/** The home clock: tall rounded digits of frosted glass, the picture showing blurred through them, with a bright rim. */
class GlassClock extends View {
    // each digit is drawn as round-capped strokes in a box 1 wide and 2.4 tall
    private static final String[] G = {
        "A .5 .5 .35 180 180;L .85 1.9;A .5 1.9 .35 0 180;Z",
        "M .22 .5;L .62 .15;L .62 2.25",
        "M .15 .92;L .15 .5;A .5 .5 .35 180 180;L .85 .95;L .15 2.25;L .85 2.25",
        "M .15 .9;L .15 .5;A .5 .5 .35 180 180;L .85 1.9;A .5 1.9 .35 0 180;L .15 1.5;M .5 1.2;L .85 1.2",
        "M .68 2.25;L .68 .15;L .15 1.6;L .85 1.6",
        "M .85 .15;L .15 .15;L .15 1.15;L .5 1.15;A .5 1.5 .35 270 90;L .85 1.9;A .5 1.9 .35 0 180;L .15 1.5",
        "M .85 .9;L .85 .5;A .5 .5 .35 0 -180;L .15 1.9;A .5 1.9 .35 180 -180;L .85 1.5;A .5 1.5 .35 0 -180",
        "M .15 .15;L .85 .15;L .4 2.25",
        "A .5 .47 .32 180 180;L .82 .83;A .5 .83 .32 0 180;Z;M .15 1.5;A .5 1.5 .35 180 180;L .85 1.9;A .5 1.9 .35 0 180;Z",
        "M .15 .9;L .15 .5;A .5 .5 .35 180 180;L .85 1.9;A .5 1.9 .35 0 180;L .15 1.5;M .15 .88;A .5 .88 .35 180 -180"
    };
    private static final float T = .21f, GAP = .24f, COLON = .46f, SX = .6f;   // the grey picture: very tall, narrow, rounded   // tall, narrow digits   // SX: wider digits

    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG),
            edge = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path glass = new Path();
    private final Handler h = new Handler(Looper.getMainLooper());
    private float digitH;
    private final float maxDigitH;
    boolean fit;
    boolean left;
    private final Paint fade = new Paint();
    private String shown = "";
    private int builtW = -1;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            update();
            long now = System.currentTimeMillis();
            h.postDelayed(this, 60000 - now % 60000 + 50);
        }
    };

    GlassClock(Context c, float digitHeightDp) {
        super(c);
        digitH = maxDigitH = U.dp(c, digitHeightDp);
        init(c);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);          // rounded ends, like the grey picture
        stroke.setStrokeJoin(Paint.Join.ROUND);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(U.dp(c, 1.6f));
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(U.dp(c, .8f));
        edge.setColor(0x47FFFFFF);
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); h.removeCallbacks(tick); tick.run(); }
    @Override protected void onDetachedFromWindow() { h.removeCallbacks(tick); super.onDetachedFromWindow(); }

    void update() {
        Calendar k = Calendar.getInstance();
        int hr = k.get(Calendar.HOUR_OF_DAY);
        if (!DateFormat.is24HourFormat(getContext())) { hr %= 12; if (hr == 0) hr = 12; }
        String s = (hr < 10 ? "0" : "") + hr + ":" + (k.get(Calendar.MINUTE) < 10 ? "0" : "") + k.get(Calendar.MINUTE);   // 09:41, as in the picture
        if (!s.equals(shown)) { boolean relayout = s.length() != shown.length(); shown = s; builtW = -1; if (relayout) requestLayout(); invalidate(); }
    }

    @Override protected void onMeasure(int wm, int hm) {
        if (fit) {
            int fw = MeasureSpec.getSize(wm);
            float screenH = getResources().getDisplayMetrics().heightPixels;
            digitH = Math.min(Math.min(maxDigitH, screenH * .155f), fw * .94f * 2.4f / width("12:00"));   // fills the width, leaves room for apps
            setMeasuredDimension(fw, (int) (digitH + U.dp(getContext(), 4)));
            return;
        }
        int w = left ? (int) (digitH / 2.4f * width(shown.isEmpty() ? "88:88" : shown) + U.dp(getContext(), 6)) : MeasureSpec.getSize(wm);   // only as wide as the time needs
        setMeasuredDimension(w, (int) (digitH + U.dp(getContext(), 4)));
    }

    // ---- the clock face: Sofia Sans Extra Condensed SemiBold (SIL OFL), like the pictures
    private static android.graphics.Typeface face;
    private static float zeroTop, zeroH;          // at text size 1000
    static void init(android.content.Context c) {
        if (face != null) return;
        try { face = android.graphics.Typeface.createFromAsset(c.getAssets(), "fonts/clock_sofia.ttf"); }
        catch (Throwable t) { face = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD); }
        Paint p = new Paint(); p.setTypeface(face); p.setTextSize(1000);
        android.graphics.Rect r = new android.graphics.Rect();
        p.getTextBounds("0", 0, 1, r);
        zeroTop = r.top; zeroH = r.height();
    }
    /** Digits sit a little closer than the font's own spacing. */
    static final float TRACK = -0.07f;
    /** The width of the time in "units": pixels = units * digitHeight / 2.4. */
    static float width(String t) {
        if (face == null) return t.length() * .9f;
        Paint p = new Paint(); p.setTypeface(face); p.setTextSize(1000); p.setLetterSpacing(TRACK);
        return p.measureText(t) / zeroH * 2.4f;
    }
    /** The time as one outline, top-left at (x, y), digits digitH tall. */
    static Path timePath(String t, float digitH, float x, float y) {
        Path out = new Path();
        if (face == null) return out;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setTypeface(face); p.setLetterSpacing(TRACK);
        float size = 1000 * digitH / zeroH;
        p.setTextSize(size);
        p.getTextPath(t, 0, t.length(), x, y - zeroTop * size / 1000, out);
        return out;
    }

    private void build(int w) {
        builtW = w;
        float total = width(shown) * digitH / 2.4f;
        float x = left ? U.dp(getContext(), 2) : (w - total) / 2f, y = U.dp(getContext(), 2);
        glass.reset();
        glass.set(timePath(shown, digitH, x, y));
        int hh = getHeight();
        fade.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
        fade.setShader(new LinearGradient(0, 0, 0, hh, new int[]{0xFFFFFFFF, 0xE6FFFFFF, 0x59FFFFFF, 0x00FFFFFF, 0x00FFFFFF},
                new float[]{0f, .3f, .6f, .84f, 1f}, Shader.TileMode.CLAMP));
        tint.setShader(new LinearGradient(0, 0, 0, hh, 0xD1FFFFFF, 0xB8FFFFFF, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(0, 0, 0, hh, 0xF2FFFFFF, 0x99FFFFFF, Shader.TileMode.CLAMP));
    }

    private static void glyph(Path p, String ops, float ox, float oy, float s, float sx) {
        RectF o = new RectF();
        boolean open = false;
        for (String op : ops.split(";")) {
            String[] a = op.trim().split("\\s+");
            char k = a[0].charAt(0);
            if (k == 'Z') { p.close(); open = false; continue; }
            float[] n = new float[a.length - 1];
            for (int i = 1; i < a.length; i++) n[i - 1] = Float.parseFloat(a[i]);
            if (k == 'M') { p.moveTo(ox + n[0] * s * sx, oy + n[1] * s); open = true; }
            else if (k == 'L') { p.lineTo(ox + n[0] * s * sx, oy + n[1] * s); }
            else if (k == 'A') {
                float cx = ox + n[0] * s * sx, cy = oy + n[1] * s, r = n[2] * s;
                o.set(cx - r * sx, cy - r, cx + r * sx, cy + r);
                p.arcTo(o, n[3], n[4], !open);
                open = true;
            }
        }
    }

    @Override protected void onDraw(Canvas c) {
        if (shown.isEmpty()) return;
        if (builtW != getWidth()) build(getWidth());
        int layer = c.saveLayer(0, 0, getWidth(), getHeight(), null);
        c.drawPath(glass, tint);                           // soft white, no outline
        c.drawRect(0, 0, getWidth(), getHeight(), fade);   // the digits melt away toward the bottom, like mist
        c.restoreToCount(layer);
    }
}
