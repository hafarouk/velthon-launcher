package com.velthon.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Random;

/**
 * The screensaver: the same window as the TV. The verse is written, stays a while, then leaves (petals in
 * spring, snow in winter) fall outside the glass and wipe it away where they pass. Then the next verse.
 * Runs at 30 frames a second only while it is on screen.
 */
class SaverView extends View {
    private static final int WRITE = 0, HOLD = 1, WIPE = 2, PAUSE = 3;
    private float GLASS_BOTTOM = 2167f;   // in picture pixels (picture is 1080 x 2340)
    private static final float FG_TOP = 2063f;
    private float EXT = 480f;              // extra sill below the picture (for the home screen dock)
    private final boolean leavesOn; private final float wind; private final int nightFrom, nightTo;
    private final Paint night = new Paint();
    private Bitmap sill; private final Matrix sillM = new Matrix(); private boolean sky;
    private final Paint scrimP = new Paint(Paint.ANTI_ALIAS_FLAG);
    { scrimP.setShader(new android.graphics.RadialGradient(0, 0, 1, new int[] {0x66000000, 0x40000000, 0x00000000},
            new float[] {0f, .55f, 1f}, android.graphics.Shader.TileMode.CLAMP)); }
    private float clockH, clockShadeH; private String clockKey; private android.graphics.Path clockPath;
    private final Paint clockFill = new Paint(Paint.ANTI_ALIAS_FLAG), clockShadow = new Paint(Paint.ANTI_ALIAS_FLAG), clockRim = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Bitmap picture, fg, branch;
    private final ArrayList<Bitmap> sprites = new ArrayList<>();
    private final Paint pic = new Paint(Paint.FILTER_BITMAP_FLAG), fgPaint = new Paint(Paint.FILTER_BITMAP_FLAG),
            branchPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG),
            spritePaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG),
            textBlit = new Paint(Paint.FILTER_BITMAP_FLAG), erase = new Paint(Paint.ANTI_ALIAS_FLAG),
            shade = new Paint(), snow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint clockP = new TextPaint(Paint.ANTI_ALIAS_FLAG), dateP = new TextPaint(Paint.ANTI_ALIAS_FLAG),
            enP = new TextPaint(Paint.ANTI_ALIAS_FLAG), arP = new TextPaint(Paint.ANTI_ALIAS_FLAG), refP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix picM = new Matrix();
    private float k, ox, oy;                    // picture -> screen
    private final float[] mesh = new float[15 * 11 * 2];
    private final Random rnd = new Random();
    private int season;
    private float evening;
    private long shownAt;

    // the verse
    private U.Verse verse;
    private StaticLayout enL, enRefL, arL, arRefL;
    private Bitmap text;
    private Canvas textC;
    private float textTop;
    private int phase;
    private long phaseAt;
    private long writeMs;
    private float textAlpha = 1f;
    private long shownSlot = -1;
    /** Room kept free at the bottom (the lock screen's unlock button). */
    float reserveBottom;
    /** Room under the date for something placed over the view (the glucose widget on the lock screen). */
    float reserveTop;
    /** The lock screen's battery and network row, drawn right under the verse (null elsewhere). */
    StatusLine status;
    /** Where the date (and feast) lines end: the room under them is free. */
    float infoBottom() { return getHeight() * .07f + clockH + dateP.getTextSize() * (feastNow() != null ? 3.0f : 1.9f); }
    private final TextPaint evP = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private static final class Leaf { float x, y, vy, size, rot, vr, sway, phase, flip; int sprite; boolean snow; }
    private final ArrayList<Leaf> leaves = new ArrayList<>();
    private int spawned, toSpawn;
    private long lastSpawn;

    /** Lock screen: redraw once a second only (the clock), to spare the battery. */
    boolean calm;
    // performance: the feast of the day is worked out once a minute, not on every frame
    private Church.Feast feastC; private long feastAt = -1;
    private Church.Feast feastNow() {
        long m = System.currentTimeMillis() / 60000;
        if (m != feastAt) { feastAt = m; feastC = Church.today(getContext()); }
        return feastC;
    }

    SaverView(Context c) {
        super(c);
        picture = U.background(c);
        boolean window = U.isWindow(c);
        fg = window ? U.bitmap(c, "scene_fg.png") : null;
        if (!window && picture != null) {
            // another picture: centred, no sill objects; the leaves fall to the bottom of the screen
            EXT = 0; GLASS_BOTTOM = picture.getHeight() * 1.2f;
        }
        leavesOn = U.cornerLeaves(c);
        sky = U.isSky(c);
        if (sky) sill = U.bitmap(c, "sill.webp");
        String wd = U.prefs(c).getString("wind", "normal");
        wind = wd.equals("calm") ? .45f : wd.equals("strong") ? 1.7f : 1f;
        String nm = U.prefs(c).getString("night_mode", "off");
        nightFrom = nm.equals("22") ? 22 : nm.equals("21") ? 21 : -1;
        nightTo = nm.equals("22") ? 6 : 7;
        night.setColor(0x9E000000);
        clockFill.setColor(0xF7FFFAF0);
        GlassClock.init(c);
        clockShadow.setColor(0x26000000);
        clockRim.setStyle(Paint.Style.STROKE); clockRim.setStrokeWidth(U.dp(c, 2)); clockRim.setColor(0xF2FFFFFF);
        clockShadow.setMaskFilter(new android.graphics.BlurMaskFilter(U.dp(c, 10), android.graphics.BlurMaskFilter.Blur.NORMAL));
        branch = U.bitmap(c, "window_branch.png");
        season = U.season(c);
        evening = U.evening(c);
        String set = season == U.SPRING ? "spring" : "fall";
        int n = season == U.SPRING ? 6 : 10;
        if (season != U.WINTER) for (int i = 0; i < n; i++) { Bitmap b = U.bitmap(c, "season/" + set + i + ".png"); if (b != null) sprites.add(b); }

        float dim = 1f - .4f * evening;
        pic.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[] {dim, 0, 0, 0, 0, 0, dim, 0, 0, 0, 0, 0, dim, 0, 0, 0, 0, 0, 1, 0})));
        fgPaint.setColorFilter(pic.getColorFilter());
        ColorMatrix bm = U.seasonMatrix(season);
        float bd = 1f - .62f * evening;
        bm.postConcat(new ColorMatrix(new float[] {bd, 0, 0, 0, 0, 0, bd, 0, 0, 0, 0, 0, bd, 0, 0, 0, 0, 0, 1, 0}));
        branchPaint.setColorFilter(new ColorMatrixColorFilter(bm));
        if (season != U.SPRING) spritePaint.setColorFilter(branchPaint.getColorFilter());   // fallen leaves match the branch
        else spritePaint.setColorFilter(pic.getColorFilter());
        erase.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        snow.setColor(Color.WHITE);

        clockP.setColor(Color.WHITE); clockP.setTypeface(U.sansBold(c)); clockP.setTextAlign(Paint.Align.CENTER);
        clockP.setShadowLayer(U.dp(c, 8), 0, U.dp(c, 2), 0x99000000);
        dateP.setColor(0xFFFFFFFF); dateP.setTypeface(U.sans(c)); dateP.setTextAlign(Paint.Align.CENTER);
        dateP.setShadowLayer(U.dp(c, 2.5f), U.dp(c, .8f), U.dp(c, 2), 0xE6000000);
        enP.setColor(Color.WHITE); enP.setTypeface(U.sans(c)); enP.setShadowLayer(U.dp(c, 6), 0, U.dp(c, 2), 0xAA000000);
        arP.setColor(Color.WHITE); arP.setTypeface(U.arabic(c)); arP.setShadowLayer(U.dp(c, 6), 0, U.dp(c, 2), 0xAA000000);
        refP.setColor(0xFFF0B43C); refP.setTypeface(U.sansBold(c)); refP.setShadowLayer(U.dp(c, 4), 0, U.dp(c, 1), 0x99000000);
        verse = U.verseOfHour(c);
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); shownAt = SystemClock.uptimeMillis(); }
    @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); if (text != null) { text.recycle(); text = null; } }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        if (picture != null) {
            k = Math.max(w / (float) picture.getWidth(), h / (float) picture.getHeight());
            ox = (w - picture.getWidth() * k) / 2f;
            oy = EXT > 0 ? h - (picture.getHeight() - EXT) * k : (h - picture.getHeight() * k) / 2f;
            picM.setScale(k, k); picM.postTranslate(ox, oy);
        }
        shade.setShader(new LinearGradient(0, 0, 0, h, new int[] {0x00060A18, 0x00060A18, 0x00060A18, 0x40060A18},
                new float[] {0f, .1f, .8f, 1f}, Shader.TileMode.CLAMP));
        clockP.setTextSize(w * .22f);
        // the big clock: as large as fits, about a fifth of the screen tall
        clockH = Math.min(h * .22f, w * .86f / (GlassClock.width("12:00") / 2.4f));
        clockKey = null;
        dateP.setTextSize(w * .045f);
        prepareVerse();
    }

    private void prepareVerse() {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        String langs = U.langs(getContext());
        int tw = Math.round(w * .86f);
        int len = (langs.equals("ar") ? 0 : verse.en.length()) + (!langs.equals("ar") ? 0 : verse.ar.length());
        float size = w * (len > 260 ? .052f : len > 180 ? .058f : .064f);
        size *= 1.35f;                                  // big and easy to read on the lock screen
        int evCount = Events.next(getContext(), 2).size();
        if (evCount > 0) size *= len > 120 ? .74f : .84f;   // room for the "Up next" card under it
        String ts = U.prefs(getContext()).getString("text_scale", "auto");
        if (ts.equals("small")) size *= .88f; else if (ts.equals("large")) size *= 1.12f;
        enP.setTextSize(size); arP.setTextSize(size * 1.02f); refP.setTextSize(size * .62f);
        boolean serifFont = U.prefs(getContext()).getString("verse_font", "system").equals("serif");
        enP.setTypeface(serifFont ? U.serif(getContext()) : U.sans(getContext()));
        refP.setTypeface(serifFont ? U.serif(getContext()) : U.sansBold(getContext()));
        // the same clear colours as the home screen: deep navy on a bright sky, white on a dark one
        boolean bright = U.skyIsBright(getContext());
        enP.setColor(bright ? 0xFF23334A : Color.WHITE); arP.setColor(bright ? 0xFF23334A : Color.WHITE);
        refP.setColor(bright ? 0xFF874C0C : 0xFFF2BC4E);
        dateP.setColor(bright ? 0xFF23334A : Color.WHITE);
        evP.setColor(bright ? 0xFF23334A : Color.WHITE);
        evP.setTextSize(w * .042f);
        evP.setTextAlign(Paint.Align.CENTER);
        evP.setTypeface(U.sans(getContext()));
        int sh = 0xCC000000;
        float sr = U.dp(getContext(), 1.4f), sy = U.dp(getContext(), bright ? .6f : 1.2f);
        int sc = bright ? 0xB3FFFFFF : 0xB3000000;
        enP.setShadowLayer(sr, 0, sy, sc); arP.setShadowLayer(sr, 0, sy, sc);
        refP.setShadowLayer(sr, 0, sy, sc); dateP.setShadowLayer(sr, 0, sy, sc); evP.setShadowLayer(sr, 0, sy, sc);

        enL = enRefL = arL = arRefL = null;
        if (!langs.equals("ar")) {
            enL = layout(verse.en, enP, tw, 1.2f);
            enRefL = layout(verse.refEn, refP, tw, 1f);
        }
        if (!langs.equals("en")) {
            arL = layout(verse.ar, arP, tw, 1.3f);
            arRefL = layout(verse.refAr, refP, tw, 1f);
        }
        int th = blockHeight();
        if (text != null) text.recycle();
        text = Bitmap.createBitmap(w, Math.max(1, th + Math.round(size)), Bitmap.Config.ARGB_8888);
        textC = new Canvas(text);
        float clockBottom = h * .07f + clockH + dateP.getTextSize() * (feastNow() != null ? 3.6f : 2.4f) + reserveTop;
        float glassBottom = oy + GLASS_BOTTOM * k;
        float bottomLimit = Math.min(glassBottom, h * .9f);
        float rowRoom = status != null ? status.rowHeight() * 1.25f : 0;   // the battery and network row under the verse
        textTop = Math.max(clockBottom + h * .02f, (clockBottom + bottomLimit) / 2f - (th + rowRoom) / 2f);   // centred in the space under the clock
        if (U.isSky(getContext()) && picture != null) {
            // the verse sits in the heart of the sun (or the moon): the sky is placed around it
            float sillTop = sill != null ? h - sill.getHeight() * (w / (float) sill.getWidth()) : h * .9f;
            float fy = (clockBottom + sillTop) / 2f;                 // between the clock and the sill
            textTop = Math.max(clockBottom + h * .02f, fy - th / 2f);
            float ph = picture.getHeight();
            k = Math.max(Math.max(w / (float) picture.getWidth(), h / ph), Math.max(fy / (U.SKY_FY * ph), (h - fy) / ((1 - U.SKY_FY) * ph)));
            ox = w / 2f - U.SKY_FX * picture.getWidth() * k;
            oy = fy - U.SKY_FY * ph * k;
            picM.setScale(k, k); picM.postTranslate(ox, oy);
            if (sill != null) {
                float ks = w / (float) sill.getWidth();
                sillM.setScale(ks, ks); sillM.postTranslate(0, h - sill.getHeight() * ks);
                // leaves land on the sill
                GLASS_BOTTOM = (h - sill.getHeight() * ks + 30 * ks - oy) / k;
            }
        }
        // room for the calendar events under the verse: lift the verse by half their height
        if (evCount > 0) {   // the verse right under the date, the card right under the verse
            float clockB = h * .07f + clockH + dateP.getTextSize() * (feastNow() != null ? 3.6f : 2.4f) + reserveTop;
            textTop = clockB + h * .01f;
        }
        // colours by what is behind each part: dark letters on the bright sun or moon, white on a dark sky
        boolean vl = lum(w * .1f, textTop, w * .9f, textTop + th) > Launcher.LIGHT;
        enP.setColor(vl ? Launcher.INK : Color.WHITE); arP.setColor(vl ? Launcher.INK : Color.WHITE);
        refP.setColor(vl ? 0xFF874C0C : 0xFFF2BC4E);
        float sr2 = U.dp(getContext(), 1.4f);
        int sc2 = vl ? 0xB3FFFFFF : 0xB3000000;
        if (mixed) { sr2 = U.dp(getContext(), 3.5f); sc2 = vl ? 0xF2FFFFFF : 0xF2000000; }   // a halo in the opposite tone
        enP.setShadowLayer(sr2, 0, U.dp(getContext(), vl ? .6f : 1.2f), sc2); arP.setShadowLayer(sr2, 0, U.dp(getContext(), vl ? .6f : 1.2f), sc2);
        refP.setShadowLayer(sr2, 0, U.dp(getContext(), vl ? .6f : 1.2f), sc2);
        float dy = h * .07f + clockH + dateP.getTextSize() * 1.9f;
        boolean dl = lum(w * .2f, dy - dateP.getTextSize(), w * .8f, dy) > Launcher.LIGHT;
        dateP.setColor(dl ? Launcher.INK : Color.WHITE);
        dateP.setShadowLayer(sr2, 0, U.dp(getContext(), dl ? .6f : 1.2f), dl ? 0xB3FFFFFF : 0xB3000000);
        textAlpha = 1f;
        shownSlot = U.verseSlot(getContext());
        phase = WRITE;
        phaseAt = SystemClock.uptimeMillis();
        int chars = (enL != null ? verse.en.length() : 0) + (arL != null ? verse.ar.length() : 0);
        writeMs = Math.max(2800, Math.min(8000, chars * 48L));
        leaves.clear();
        spawned = 0;
    }
    private static StaticLayout layout(String s, TextPaint p, int w, float spacing) {
        return new StaticLayout(s, p, w, Layout.Alignment.ALIGN_CENTER, spacing, 0, false);
    }
    private int gap() { return Math.round(getWidth() * .025f); }
    private int blockHeight() {
        int h = 0;
        if (enL != null) h += enL.getHeight() + gap() / 2 + enRefL.getHeight();
        if (arL != null) h += (h > 0 ? gap() * 2 : 0) + arL.getHeight() + gap() / 2 + arRefL.getHeight();
        return h;
    }

    /** Draws the verse into the text layer, written up to {@code f} (0..1). */
    private void drawText(float f) {
        text.eraseColor(0);
        int x = Math.round(getWidth() * .07f);
        float y = Math.round(enP.getTextSize() * .4f);
        int total = (enL != null ? verse.en.length() : 0) + (arL != null ? verse.ar.length() : 0);
        float budget = f * total;
        if (enL != null) {
            budget = drawPart(enL, x, y, budget, false); y += enL.getHeight() + gap() / 2f;
            if (budget >= 0 || f >= 1) drawFade(enRefL, x, y, f); y += enRefL.getHeight() + gap() * 2;
        }
        if (arL != null) {
            budget = drawPart(arL, x, y, budget, true); y += arL.getHeight() + gap() / 2f;
            if (f >= 1 || budget > 0) drawFade(arRefL, x, y, f);
        }
    }
    private float drawPart(StaticLayout l, int x, float y, float budget, boolean rtl) {
        int n = l.getText().length();
        textC.save();
        textC.translate(x, y);
        if (budget >= n) { l.draw(textC); textC.restore(); return budget - n; }
        if (budget > 0) {
            int upTo = (int) budget;
            int line = l.getLineForOffset(upTo);
            for (int i = 0; i < line; i++) {
                textC.save(); textC.clipRect(0, l.getLineTop(i), l.getWidth(), l.getLineBottom(i)); l.draw(textC); textC.restore();
            }
            float px = l.getPrimaryHorizontal(upTo);
            textC.save();
            if (rtl) textC.clipRect(px, l.getLineTop(line), l.getWidth(), l.getLineBottom(line));
            else textC.clipRect(0, l.getLineTop(line), px, l.getLineBottom(line));
            l.draw(textC);
            textC.restore();
        }
        textC.restore();
        return budget - n;
    }
    private void drawFade(StaticLayout l, int x, float y, float f) {
        textC.save(); textC.translate(x, y); l.draw(textC); textC.restore();
    }

    // ------------------------------------------------------------------ frame
    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        int w = getWidth(), h = getHeight();
        if (picture != null) c.drawBitmap(picture, picM, pic); else c.drawColor(0xFF101828);
        if (sky && sill != null) c.drawBitmap(sill, sillM, null);
        if (!sky) drawBranch(c, now / 1000.0);   // the sky has no branch, as on the home screen
        c.drawRect(0, 0, w, h, shade);

        if (text != null) step(now);
        // falling leaves are outside the glass: behind the verse and behind the things on the sill
        for (Leaf l : leaves) drawLeaf(c, l);
        if (fg != null) {
            c.save(); c.translate(ox, oy + FG_TOP * k); c.scale(k, k); c.drawBitmap(fg, 0, 0, fgPaint); c.restore();
        }
        if (text != null) {
            // a soft shade with no edges behind the verse, so white reads on the sun and the moon
            float cxs = w / 2f, cys = textTop + text.getHeight() / 2f;
            c.save(); c.translate(cxs, cys); c.scale(w * .55f, text.getHeight() * .85f);
            // no shade behind the verse: the letters carry a drop shadow
            c.restore();
            textBlit.setAlpha(Math.round(255 * textAlpha));
            c.drawBitmap(text, 0, textTop, textBlit);
            // the next two calendar events, under the verse, on a light frosted card
            float under = textTop + text.getHeight();
            if (status != null) {
                status.drawRow(c, w / 2f, under + status.rowHeight() * .55f, textAlpha, Color.red(dateP.getColor()) < 128);
                under += status.rowHeight() * 1.25f;
            }
            drawEvents(c, w, under);
        }

        // the clock moves a few pixels every minute so nothing burns into the screen
        Calendar cal = Calendar.getInstance();
        int m = cal.get(Calendar.MINUTE);
        float drift = (m % 7 - 3) * U.dp(getContext(), 1.5f), driftY = (m % 5 - 2) * U.dp(getContext(), 1.5f);
        String t = DateFormat.format(DateFormat.is24HourFormat(getContext()) ? "HH:mm" : "hh:mm", cal).toString();
        float top = h * .07f + driftY;
        String key = t + "@" + w;
        if (!key.equals(clockKey)) {
            clockKey = key;
            float cw = GlassClock.width(t) * clockH / 2.4f;
            clockPath = GlassClock.timePath(t, clockH, (w - cw) / 2f, 0);
        }
        c.save();
        c.translate(drift, top);
        if (clockFill.getShader() == null || clockShadeH != clockH) {
            clockShadeH = clockH;
            // see-through white that melts away toward the bottom: no hard edge over the sun or clouds
            clockFill.setShader(new LinearGradient(0, 0, 0, clockH, new int[] {0xD9FFFFFF, 0xB3FFFFFF, 0x40FFFFFF, 0x00FFFFFF, 0x00FFFFFF},
                    new float[] {0f, .3f, .6f, .84f, 1f}, android.graphics.Shader.TileMode.CLAMP));
        }
        c.drawPath(clockPath, clockFill);
        c.restore();
        String d = DateFormat.format("EEE, MMM d", cal).toString();
        if (Weather.has(getContext())) d += "  ·  " + Weather.temp(getContext()) + " " + Weather.condition(getContext());
        Church.Feast feast = feastNow();
        float dy = top + clockH + dateP.getTextSize() * 1.9f;
        c.drawText(d, w / 2f + drift, dy, dateP);
        if (feast != null) {   // a feast day gets its own line, fitted to the width, never cut at the edges
            String f = "✦ " + feast.in(U.langs(getContext()));
            float sz = dateP.getTextSize(), max = w * .9f;
            while (dateP.measureText(f) > max && dateP.getTextSize() > sz * .7f) dateP.setTextSize(dateP.getTextSize() * .94f);
            int col = dateP.getColor(); dateP.setColor(Color.red(col) < 128 ? 0xFF8A5212 : 0xFFF2B640);   // deep amber on a bright sky
            c.drawText(f, w / 2f + drift, dy + sz * 1.35f, dateP);
            dateP.setColor(col); dateP.setTextSize(sz);
        }
        // night mode: the whole window dims late at night
        if (nightFrom >= 0 && !calm) {   // the lock screen is seen for a moment: keep it clear and readable
            int hr = cal.get(Calendar.HOUR_OF_DAY);
            if (hr >= nightFrom || hr < nightTo) c.drawRect(0, 0, w, h, night);
        }

        // battery: on the lock screen nothing moves faster than the clock, so once a second is enough
        if (isShown()) postInvalidateDelayed(sky || calm ? 1000 : Math.max(8, 33 - (SystemClock.uptimeMillis() - now)));
    }

    private void step(long now) {
        long t = now - phaseAt;
        switch (phase) {
            case WRITE:
                // the verse appears whole, no writing and no leaves: it simply stays until the next one is due
                drawText(1f);
                textAlpha = 1f;
                phase = HOLD; phaseAt = now;
                break;
            case HOLD:
                if (U.verseSlot(getContext()) != shownSlot) {
                    verse = U.verseOfHour(getContext());
                    prepareVerse();
                }
                break;
            case WIPE: {
                if (spawned < toSpawn && now - lastSpawn > 380 + rnd.nextInt(260)) { spawn(); lastSpawn = now; }
                float dt = 1 / 30f;
                for (int i = leaves.size() - 1; i >= 0; i--) {
                    Leaf l = leaves.get(i);
                    l.phase += dt;
                    l.y += l.vy * dt;
                    l.x += (float) Math.sin(l.phase * 1.6 + l.sway) * l.size * .9f * dt;
                    l.rot += l.vr * dt;
                    // wipe the verse where it passes
                    float ty = l.y - textTop;
                    if (ty > -l.size && ty < text.getHeight() + l.size) {
                        float r = l.size * .62f;   // wipes just where the leaf passes
                        erase.setShader(new RadialGradient(l.x, ty, r, new int[] {0xFF000000, 0xFF000000, 0x00000000}, new float[] {0f, .6f, 1f}, Shader.TileMode.CLAMP));
                        textC.drawCircle(l.x, ty, r, erase);
                    }
                    if (l.y > oy + GLASS_BOTTOM * k + l.size) leaves.remove(i);
                }
                boolean crossing = false;
                for (Leaf l : leaves) if (l.y < textTop + text.getHeight()) crossing = true;
                if (spawned >= toSpawn && !crossing) {   // the last one has passed: clear whatever is left
                    textAlpha = Math.max(0f, textAlpha - dt / 1.2f);
                    if (leaves.isEmpty() && textAlpha <= 0) { phase = PAUSE; phaseAt = now; }
                }
                break;
            }
            case PAUSE:
                if (t >= 2500) {
                    java.util.ArrayList<U.Verse> all = U.verses(getContext());
                    verse = all.get((verse.index + 1 + rnd.nextInt(all.size() - 1)) % all.size());
                    prepareVerse();
                }
                break;
        }
    }

    private final Paint cardP = new Paint(Paint.ANTI_ALIAS_FLAG), markP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint evTitle = new TextPaint(Paint.ANTI_ALIAS_FLAG), evWhen = new TextPaint(Paint.ANTI_ALIAS_FLAG), evHead = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    /** "UP NEXT", then each event: an amber marker, its title in bold and when it is, on a light frosted card. */
    private void drawEvents(Canvas c, int w, float top) {
        java.util.ArrayList<String[]> ev = Events.next(getContext(), 2);
        if (ev.isEmpty()) return;
        float d = getResources().getDisplayMetrics().density;
        float ts = w * .038f;
        evTitle.setTextSize(ts); evTitle.setTypeface(U.sansBold(getContext()));
        evWhen.setTextSize(ts * .8f); evWhen.setTypeface(U.sans(getContext()));
        evHead.setTextSize(ts * .62f); evHead.setTypeface(U.sansBold(getContext())); evHead.setLetterSpacing(.18f);
        float pad = 12 * d, rowH = ts * 1.2f + ts * .8f * 1.25f + 7 * d;
        float cw = w * .84f, cx = (w - cw) / 2f;
        float ch = pad + evHead.getTextSize() * 1.6f + rowH * ev.size() + pad * .5f;
        // on the clear wood below the Bible and the mug (or just under the verse when there is no sill)
        // on the lock screen (the unlock button below): right under the verse; elsewhere on the clear wood under the sill
        float y0 = reserveBottom > 0 || !(sky && sill != null) ? top + 6 * d : Math.max(top + 14 * d, getHeight() - ch - 44 * d);
        boolean bright = lum(cx, y0, cx + cw, y0 + ch) > Launcher.LIGHT;
        int ink2 = bright ? Launcher.INK : Color.WHITE, soft2 = bright ? 0xCC23334A : 0xD9FFFFFF;
        evTitle.setColor(ink2); evWhen.setColor(soft2); evHead.setColor(bright ? 0xFF874C0C : 0xFFF2BC4E);
        android.graphics.RectF card = new android.graphics.RectF(cx, y0, cx + cw, y0 + ch);
        cardP.setColor(bright ? 0x4DFFFFFF : 0x29FFFFFF);
        c.drawRoundRect(card, 22 * d, 22 * d, cardP);
        cardP.setStyle(Paint.Style.STROKE); cardP.setStrokeWidth(d); cardP.setColor(bright ? 0x59FFFFFF : 0x33FFFFFF);
        c.drawRoundRect(card, 22 * d, 22 * d, cardP);
        cardP.setStyle(Paint.Style.FILL);
        float x = cx + pad, y = y0 + pad + evHead.getTextSize();
        c.drawText(L.t("Up next").toUpperCase(), x, y, evHead);
        y += evHead.getTextSize() * .6f;
        for (int i = 0; i < ev.size(); i++) {
            float ry = y + i * rowH + 6 * d;
            boolean live = ev.get(i).length > 2;          // happening now: a green marker
            markP.setColor(live ? 0xFF4CC38A : 0xFFF0A93A);
            c.drawRoundRect(new android.graphics.RectF(x, ry + 2 * d, x + 4 * d, ry + rowH - 8 * d), 2 * d, 2 * d, markP);
            float tx = x + 14 * d, avail = cw - pad * 2 - 14 * d;
            c.drawText(TextUtils.ellipsize(ev.get(i)[0], evTitle, avail, TextUtils.TruncateAt.END).toString(), tx, ry + ts, evTitle);
            c.drawText(ev.get(i)[1], tx, ry + ts + evWhen.getTextSize() * 1.35f, evWhen);
            if (i < ev.size() - 1) {
                markP.setColor(bright ? 0x262B3D55 : 0x26FFFFFF);
                c.drawRect(tx, ry + rowH - 3 * d, cx + cw - pad, ry + rowH - 3 * d + Math.max(1, d * .6f), markP);
            }
        }
    }

    /** How bright the window is in a part of the screen (0..1): the sky picture, or the sill where it is drawn. */
    private boolean mixed;
    float lum(float x0, float y0, float x1, float y1) {
        if (picture == null) return .3f;
        android.graphics.Matrix inv = new android.graphics.Matrix(), sinv = new android.graphics.Matrix();
        picM.invert(inv);
        boolean hs = sky && sill != null; if (hs) sillM.invert(sinv);
        float sum = 0; int n = 0, dk = 0, lt = 0; float[] pt = new float[2];
        for (int gy = 0; gy < 4; gy++) for (int gx = 0; gx < 7; gx++) {
            float sx = x0 + (x1 - x0) * (gx + .5f) / 7f, sy = y0 + (y1 - y0) * (gy + .5f) / 4f;
            int col = 0; boolean got = false;
            if (hs) {
                pt[0] = sx; pt[1] = sy; sinv.mapPoints(pt);
                int px = (int) pt[0], py = (int) pt[1];
                if (px >= 0 && py >= 0 && px < sill.getWidth() && py < sill.getHeight()) { int c = sill.getPixel(px, py); if ((c >>> 24) > 140) { col = c; got = true; } }
            }
            if (!got) {
                pt[0] = sx; pt[1] = sy; inv.mapPoints(pt);
                col = picture.getPixel(Math.max(0, Math.min(picture.getWidth() - 1, (int) pt[0])), Math.max(0, Math.min(picture.getHeight() - 1, (int) pt[1])));
            }
            float l = (.299f * ((col >> 16) & 255) + .587f * ((col >> 8) & 255) + .114f * (col & 255)) / 255f;
            sum += l; n++; if (l < .36f) dk++; else if (l > .6f) lt++;
        }
        float b = sum / Math.max(1, n);
        mixed = dk > n / 7 && lt > n / 7;      // part light, part dark (the moon's edge, a sunset)
        return b;
    }

    private void spawn() {
        Leaf l = new Leaf();
        int w = getWidth();
        l.snow = season == U.WINTER || sprites.isEmpty();
        l.size = l.snow ? w * (.012f + rnd.nextFloat() * .012f) : w * (.085f + rnd.nextFloat() * .045f);
        // spread the leaves over the whole width, so between them they cross all of the verse
        l.x = w * (.06f + .88f * (((spawned * 7) % toSpawn + rnd.nextFloat()) / toSpawn));
        l.y = -l.size;
        l.vy = getHeight() * (l.snow ? .09f : .13f + rnd.nextFloat() * .06f);
        l.rot = rnd.nextFloat() * 360; l.vr = (rnd.nextFloat() - .5f) * 140;
        l.sway = rnd.nextFloat() * 6.28f;
        l.flip = rnd.nextBoolean() ? 1 : -1;
        l.sprite = sprites.isEmpty() ? 0 : rnd.nextInt(sprites.size());
        leaves.add(l);
        spawned++;
    }

    private void drawLeaf(Canvas c, Leaf l) {
        if (l.snow) { snow.setAlpha(Math.round(230 * (1 - .4f * evening))); c.drawCircle(l.x, l.y, l.size, snow); return; }
        Bitmap b = sprites.get(l.sprite);
        float s = l.size / Math.max(b.getWidth(), b.getHeight());
        c.save();
        c.translate(l.x, l.y);
        c.rotate(l.rot);
        c.scale(s * (float) Math.cos(l.phase * 2.2 + l.sway) * l.flip, s);   // turning over as it falls
        c.drawBitmap(b, -b.getWidth() / 2f, -b.getHeight() / 2f, spritePaint);
        c.restore();
    }

    /** The branch in the top corner, moving in a gentle gusty breeze (as on the TV). */
    private void drawBranch(Canvas c, double t) {
        if (branch == null || !leavesOn) return;
        int w = getWidth();
        float s = w * .74f / branch.getWidth();
        float bx = -w * .1f, by = -getHeight() * .006f;
        float bw = branch.getWidth() * s, bh = branch.getHeight() * s;
        double gust = .58 + .22 * Math.sin(t * .37) + .14 * Math.sin(t * .83 + 1.9) + .06 * Math.sin(t * 1.71 + .4);
        gust = Math.max(.25, Math.min(1, gust));
        double amp = bw * .026 * gust * wind, diag = Math.hypot(bw, bh);
        int n = 0;
        for (int j = 0; j <= 10; j++) for (int i = 0; i <= 14; i++) {
            float u = i / 14f, v = j / 10f, dx0 = bw * u, dy0 = bh * v;
            double r = Math.hypot(dx0, dy0) / diag, b = Math.pow(r, 1.6), lag = 1.1 * r;
            double sw = Math.sin(2 * Math.PI * .42 * t - lag) + .35 * Math.sin(2 * Math.PI * .77 * t - 1.5 * lag + .6);
            double lift = Math.sin(2 * Math.PI * .42 * t - lag + 1.3);
            double ph = (i * 12.9898 + j * 78.233) % 6.283, fq = 1.7 + .9 * ((i * 7 + j * 13) % 10) / 10.0;
            double fl = bw * .003 * (1.2 + 1.6 * gust) * b * Math.sin(2 * Math.PI * fq * t + ph);
            float pinX = Math.min(1f, u * 7f), pinY = Math.min(1f, v * 7f);
            mesh[n++] = (float) (bx + dx0 + (amp * b * sw * .9 + fl * .6) * pinX);
            mesh[n++] = (float) (by + dy0 + (amp * b * (.45 * lift + .15 * gust) + fl) * pinY);
        }
        c.drawBitmapMesh(branch, 14, 10, mesh, 0, null, 0, branchPaint);
    }
}
