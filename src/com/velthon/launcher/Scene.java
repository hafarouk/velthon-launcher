package com.velthon.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;
import android.widget.FrameLayout;

/**
 * The window: the picture, the branch in the corner (in the season's colours, darker in the evening) and a
 * soft shade at the top and bottom. Drawn only when something changes - nothing animates on the home screen.
 * It also keeps a small blurred copy that the glass panels draw behind themselves.
 */
class Scene extends FrameLayout {
    private final Paint pic = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint branchPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint();
    private final Matrix picM = new Matrix(), branchM = new Matrix();
    private Bitmap picture, branch, small;
    private float smallScale;
    private int lastW, lastH;
    private float evening;
    private int season;
    private Bitmap shownPic; private boolean leavesOn = true;
    private int focusY = -1;
    private Bitmap sill;
    private final Matrix sillM = new Matrix();
    private final Paint sillPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    /** Puts the sky's sun behind this point (y on screen). */
    void setFocus(int y) {
        if (Math.abs(y - focusY) < 2) return;
        focusY = y; lastW = -1; small = null; invalidate();
        refresh();
    }

    Scene(Context c) {
        super(c);
        setWillNotDraw(false);
        picture = U.background(c);
        shownPic = picture;
        leavesOn = U.cornerLeaves(c);
        branch = U.bitmap(c, "window_branch.png");
        sill = U.bitmap(c, "sill.webp");
    }

    /** Re-reads the season and the time of day (called when the screen comes back). */
    void refresh() {
        // weather motion: on the live sky every kind; on a picture only what falls (rain, snow, storm), which suits any window
        String k = U.anims(getContext()) && U.prefs(getContext()).getString("wx_fx", "on").equals("on") ? U.skyName(getContext()) : "";
        if (!U.isSky(getContext()) && !(k.contains("rain") || k.contains("snow") || k.contains("storm") || k.contains("drizzle"))) k = "";
        fxKind = k;
        float ev = U.evening(getContext());
        int s = U.season(getContext());
        Bitmap pic = U.background(getContext());
        boolean lv = U.cornerLeaves(getContext());
        if (pic != shownPic || lv != leavesOn) { picture = shownPic = pic; leavesOn = lv; lastW = -1; small = null; }
        if (Math.abs(ev - evening) > .03f || s != season || small == null) {
            evening = ev; season = s;
            small = null;
            invalidate();
            invalidateGlass(this);
        }
    }
    private static void invalidateGlass(View v) {
        v.invalidate();
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) invalidateGlass(g.getChildAt(i));
        }
    }

    private void layoutScene(int w, int h) {
        if (picture != null) {
            float k = Math.max(w / (float) picture.getWidth(), h / (float) picture.getHeight());
            boolean window = U.isWindow(getContext());
            if (U.isSky(getContext()) && focusY > 0) {
                // the sky: move it so the sun (or moon) sits right behind the verse, zooming a little if needed
                float ph = picture.getHeight(), fy = U.SKY_FY;
                k = Math.max(k, Math.max(focusY / (fy * ph), (h - focusY) / ((1 - fy) * ph)));
                k *= 1.12f;                                   // extra all round, so the sky can move with the phone
                picM.setScale(k, k);
                // keep the sun where it should be, but never let the picture's top edge come into view
                float top = Math.min(focusY - fy * ph * k, 0);
                picM.postTranslate(w / 2f - U.SKY_FX * picture.getWidth() * k, top);
            } else {
                picM.setScale(k, k);
                picM.postTranslate((w - picture.getWidth() * k) / 2f, window ? h - picture.getHeight() * k   // the sill stays at the bottom
                        : (h - picture.getHeight() * k) / 2f);
            }
        }
        if (sill != null) {
            float ks = w * 1.03f / sill.getWidth();
            sillM.setScale(ks, ks);
            sillM.postTranslate(-w * .015f, h - sill.getHeight() * ks);      // on the bottom of the screen, under the sky
        }
        if (branch != null) {
            float k = w * .74f / branch.getWidth();
            branchM.setScale(k, k);
            branchM.postTranslate(-w * .1f, -h * .006f);
        }
        float dim = U.isSky(getContext()) ? 1f : 1f - .38f * evening;   // the sky pictures carry their own night
        float sd = 1f - .5f * evening;                                   // the sill darkens with the evening
        sillPaint.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[] {sd, 0, 0, 0, 0, 0, sd, 0, 0, 0, 0, 0, sd * 1.04f, 0, 0, 0, 0, 0, 1, 0})));
        ColorMatrix m = new ColorMatrix(new float[] {dim, 0, 0, 0, 0, 0, dim, 0, 0, 0, 0, 0, dim * 1.02f, 0, 0, 0, 0, 0, 1, 0});
        pic.setColorFilter(new ColorMatrixColorFilter(m));
        ColorMatrix bm = U.seasonMatrix(season);
        float bd = 1f - .62f * evening;
        bm.postConcat(new ColorMatrix(new float[] {bd, 0, 0, 0, 0, 0, bd, 0, 0, 0, 0, 0, bd, 0, 0, 0, 0, 0, 1, 0}));
        branchPaint.setColorFilter(new ColorMatrixColorFilter(bm));
        shade.setShader(new LinearGradient(0, 0, 0, h,
                new int[] {0x00060A18, 0x00060A18, 0x00060A18, 0x40060A18},
                new float[] {0f, .1f, .8f, 1f}, Shader.TileMode.CLAMP));   // the sky runs to the very top; a light shade at the bottom only
    }

    // tilting the phone: the far sky moves more than the near sill, so the window has depth
    private float tiltX, tiltY;
    /** The living sky: the clouds drift and the light breathes, very slowly (Settings › Atmosphere › Animations).
     *  About 15 frames a second, and only while the home screen is showing. */
    private boolean living;
    void setLiving(boolean on) { if (living != on) { living = on; invalidate(); } }
    private WeatherFx fx;
    private String fxKind = "";
    /** A tap on the sky: the weather answers (a ring of light, a splash, a puff of snow, a burst of stars). */
    void poke(float x, float y) { if (living && fx != null) { fx.poke(x, y); invalidate(); } }
    void setTilt(float x, float y) {
        if (Math.abs(x - tiltX) < .4f && Math.abs(y - tiltY) < .4f) return;
        tiltX = x; tiltY = y; invalidate();
    }
    private boolean live;
    private int sillObjTop = -1;
    /** Where the window sill begins (y on screen), or the bottom when there is none. */
    /** How bright the window is behind a part of the screen (0 dark .. 1 bright): the sky picture and the sill,
     *  sampled where they are drawn. Text over it picks its colour from this. */
    /** Was the last part measured a mix of light and dark? */
    boolean lastMixed;
    float brightness(View v) {
        if (picture == null || getWidth() == 0 || v.getWidth() == 0) return .3f;
        if (lastW != getWidth() || lastH != getHeight()) { layoutScene(getWidth(), getHeight()); lastW = getWidth(); lastH = getHeight(); small = null; }
        int[] me = new int[2], it = new int[2];
        getLocationOnScreen(me); v.getLocationOnScreen(it);
        float x0 = it[0] - me[0] + v.getPaddingLeft(), y0 = it[1] - me[1] + v.getPaddingTop();
        float vw = v.getWidth() - v.getPaddingLeft() - v.getPaddingRight(), vh = v.getHeight() - v.getPaddingTop() - v.getPaddingBottom();
        android.graphics.Matrix inv = new android.graphics.Matrix(), sinv = new android.graphics.Matrix();
        android.graphics.Matrix m = new android.graphics.Matrix(picM);
        if (U.isSky(getContext())) m.postTranslate(tiltX, tiltY);
        m.invert(inv);
        boolean hasSill = sill != null && U.isSky(getContext());
        if (hasSill) { android.graphics.Matrix sm = new android.graphics.Matrix(sillM); sm.postTranslate(tiltX * .3f, tiltY * .12f); sm.invert(sinv); }
        float sum = 0; int n = 0, darkN = 0, lightN = 0;
        float[] pt = new float[2];
        for (int gy = 0; gy < 4; gy++) for (int gx = 0; gx < 7; gx++) {
            float sx = x0 + vw * (gx + .5f) / 7f, sy = y0 + vh * (gy + .5f) / 4f;
            int col = 0; boolean got = false;
            if (hasSill) {
                pt[0] = sx; pt[1] = sy; sinv.mapPoints(pt);
                int px = (int) pt[0], py = (int) pt[1];
                if (px >= 0 && py >= 0 && px < sill.getWidth() && py < sill.getHeight()) {
                    int c = sill.getPixel(px, py);
                    if ((c >>> 24) > 140) { col = c; got = true; }
                }
            }
            if (!got) {
                pt[0] = sx; pt[1] = sy; inv.mapPoints(pt);
                int px = Math.max(0, Math.min(picture.getWidth() - 1, (int) pt[0])), py = Math.max(0, Math.min(picture.getHeight() - 1, (int) pt[1]));
                col = picture.getPixel(px, py);
            }
            float l = (.299f * ((col >> 16) & 255) + .587f * ((col >> 8) & 255) + .114f * (col & 255)) / 255f;
            sum += l; n++;
            if (l < .36f) darkN++; else if (l > .6f) lightN++;
        }
        float b = n == 0 ? .3f : sum / n;
        lastMixed = n > 0 && darkN > n / 7 && lightN > n / 7;   // part light, part dark (the moon's edge, a sunset)
        if (!U.isSky(getContext())) b *= 1f - .38f * evening;      // the dimming the other pictures get in the evening
        return b;
    }

    float sillTop() {
        if (sill == null || !U.isSky(getContext()) || getWidth() == 0) return Float.MAX_VALUE;
        if (sillObjTop < 0) {   // the first row of the sill picture with something solid in it (the top of the mug or the Bible)
            sillObjTop = sill.getHeight() / 3;
            outer:
            for (int y = 0; y < sill.getHeight(); y += 3)
                for (int x = 0; x < sill.getWidth(); x += 6)
                    if ((sill.getPixel(x, y) >>> 24) > 200) { sillObjTop = y; break outer; }
        }
        float[] pt = {0, sillObjTop};
        sillM.mapPoints(pt);                       // exactly where it is drawn
        int[] me = new int[2]; getLocationOnScreen(me);
        float top = me[1] + pt[1] - getResources().getDisplayMetrics().density * 4;
        return top;
    }
    private final android.graphics.RectF bounds = new android.graphics.RectF();
    private void paintScene(Canvas c, int w, int h) {
        boolean sky = U.isSky(getContext());
        if (picture != null) {
            if (sky) {
                c.save();
                float mx = tiltX, my = tiltY;
                if (living) {
                    double t = android.os.SystemClock.uptimeMillis() / 1000.0;
                    float d = getResources().getDisplayMetrics().density;
                    mx += (float) Math.sin(t * 2 * Math.PI / 48) * 10 * d; my += (float) Math.sin(t * 2 * Math.PI / 67) * 4 * d;
                }
                // however the phone moves, the picture always covers the whole screen: no edge, no dark band
                bounds.set(0, 0, picture.getWidth(), picture.getHeight());
                picM.mapRect(bounds);
                mx = Math.max(w - bounds.right, Math.min(-bounds.left, mx));
                my = Math.max(h - bounds.bottom, Math.min(-bounds.top, my));
                c.translate(mx, my);
                if (living) {
                    double t = android.os.SystemClock.uptimeMillis() / 1000.0;
                    float k = 1f + .014f * (float) (.5 + .5 * Math.sin(t * 2 * Math.PI / 37));
                    c.scale(k, k, w / 2f, h * .4f);
                }
                c.drawBitmap(picture, picM, pic);
                c.restore();
            }
            else c.drawBitmap(picture, picM, pic);
        }
        else c.drawColor(0xFF1A2440);
        if (living && live && fx != null && (sky || !fxKind.isEmpty())) {   // rain, snow, sun rays, stars: in the sky, behind the sill
            float sx = w / 2f + tiltX, sy = (focusY > 0 ? focusY : h * .4f) + tiltY;
            fx.draw(c, sx, sy, tiltX / Math.max(1f, 26 * getResources().getDisplayMetrics().density));
        }
        if (sill != null && sky) {      // the window sill with the Bible and the mug
            c.save(); c.translate(tiltX * .3f, tiltY * .12f); c.drawBitmap(sill, sillM, sillPaint); c.restore();
        }
        if (branch != null && leavesOn && !U.isSky(getContext())) c.drawBitmap(branch, branchM, branchPaint);
        c.drawRect(0, 0, w, h, shade);
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w != lastW || h != lastH || small == null) {
            lastW = w; lastH = h;
            layoutScene(w, h);
            // the blurred copy for the glass: one eighth of the size, blurred once
            smallScale = 1f / 8f;
            int sw = Math.max(1, Math.round(w * smallScale)), sh = Math.max(1, Math.round(h * smallScale));
            Bitmap b = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            Canvas sc = new Canvas(b);
            sc.scale(smallScale, smallScale);
            live = false;
            paintScene(sc, w, h);
            small = U.blur(b, 3);
            b.recycle();
        }
        if (living && (U.isSky(getContext()) || !fxKind.isEmpty())) {
            if (fx == null) fx = new WeatherFx(getResources().getDisplayMetrics().density);
            fx.setKind(fxKind, w, h);
        }
        live = true;
        paintScene(c, w, h);
        // battery: 25 frames a second only while rain, snow or a touch is moving; the slow drift needs no more than 5
        if (living && (U.isSky(getContext()) || !fxKind.isEmpty())) postInvalidateDelayed(fx != null && fx.busy() ? 40 : 200);
    }

    private final int[] me = new int[2], it = new int[2];
    private final Paint blurPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Matrix blurM = new Matrix();

    /** Draws the blurred window behind {@code v} (in v's own coordinates). */
    void drawBlurBehind(Canvas c, View v) {
        if (small == null) return;
        getLocationInWindow(me);
        v.getLocationInWindow(it);
        blurM.setScale(getWidth() / (float) small.getWidth(), getHeight() / (float) small.getHeight());
        blurM.postTranslate(me[0] - it[0], me[1] - it[1]);
        c.drawBitmap(small, blurM, blurPaint);
    }

    static Scene of(View v) {
        android.view.ViewParent p = v.getParent();
        while (p != null && !(p instanceof Scene)) p = p.getParent();
        return (Scene) p;
    }
}
