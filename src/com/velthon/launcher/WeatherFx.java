package com.velthon.launcher;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.Random;

/**
 * Light, living weather drawn into the sky, matched to the sky picture: the sun's rays turn and its glow
 * breathes, rain falls (and lightning flashes in a storm), snow drifts, fog rolls, stars twinkle at night.
 * A tap on the sky answers: a ring of light, a splash, a puff of snow, a burst of stars.
 * Few particles, simple shapes; it only runs while the home screen is showing and animations are on.
 */
final class WeatherFx {
    private final Random rnd = new Random();
    private final float d;                 // density
    private String kind = "";
    private int w, h;
    private float[] px = new float[0], py = new float[0], pv = new float[0], ps = new float[0];
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long last;
    private final Path ray = new Path();
    private long nextFlash, flashAt = -1, nextShoot, shootAt = -1;
    private float shootX, shootY;

    /** Something a tap left behind: a ring or a burst, fading out. */
    private static final class Poke { float x, y; long t; float[] bx, by, bvx, bvy; }
    private final ArrayList<Poke> pokes = new ArrayList<>();

    WeatherFx(float density) { d = density; }

    /** sunny, partly, cloudy, rain, snow, fog, storm, night, night_cloudy */
    void setKind(String k, int width, int height) {
        if (k == null) k = "";
        if (k.equals(kind) && width == w && height == h) return;
        kind = k; w = width; h = height;
        int n = k.equals("rain") ? 70 : k.equals("storm") ? 90 : k.equals("snow") ? 55 : k.startsWith("night") ? 46
                : k.equals("sunny") || k.equals("partly") || k.equals("cloudy") ? 16 : k.equals("fog") ? 3 : 0;
        px = new float[n]; py = new float[n]; pv = new float[n]; ps = new float[n];
        for (int i = 0; i < n; i++) spawn(i, true);
        long now = SystemClock.uptimeMillis();
        nextFlash = now + 6000 + rnd.nextInt(9000);
        nextShoot = now + 9000 + rnd.nextInt(14000);
    }

    private void spawn(int i, boolean anywhere) {
        px[i] = rnd.nextFloat() * w;
        switch (kind) {
            case "rain": case "storm":
                py[i] = anywhere ? rnd.nextFloat() * h : -rnd.nextFloat() * h * .2f;
                pv[i] = (kind.equals("storm") ? 1.25f : 1f) * (900 + rnd.nextFloat() * 500) * d / 2.6f;
                ps[i] = (14 + rnd.nextFloat() * 18) * d;
                break;
            case "snow":
                py[i] = anywhere ? rnd.nextFloat() * h : -10 * d;
                pv[i] = (28 + rnd.nextFloat() * 42) * d;
                ps[i] = (1.4f + rnd.nextFloat() * 2.4f) * d;
                break;
            case "night": case "night_cloudy":
                py[i] = rnd.nextFloat() * h * .55f;            // the stars stay in the upper sky
                pv[i] = rnd.nextFloat() * 6.28f;               // twinkle phase
                ps[i] = (.8f + rnd.nextFloat() * 1.5f) * d;
                break;
            case "fog":
                py[i] = h * (.25f + .22f * i);
                pv[i] = (8 + rnd.nextFloat() * 10) * d * (i % 2 == 0 ? 1 : -1);
                ps[i] = h * (.12f + rnd.nextFloat() * .08f);
                break;
            default:                                            // motes of light floating in the sun
                py[i] = anywhere ? rnd.nextFloat() * h * .8f : h * .8f;
                pv[i] = (6 + rnd.nextFloat() * 10) * d;
                ps[i] = (1.2f + rnd.nextFloat() * 2f) * d;
        }
    }

    boolean busy() { return !kind.isEmpty() || !pokes.isEmpty(); }

    void poke(float x, float y) {
        Poke k = new Poke(); k.x = x; k.y = y; k.t = SystemClock.uptimeMillis();
        int n = kind.equals("snow") ? 14 : kind.startsWith("night") ? 12 : 0;
        if (n > 0) {
            k.bx = new float[n]; k.by = new float[n]; k.bvx = new float[n]; k.bvy = new float[n];
            for (int i = 0; i < n; i++) {
                double a = i * 2 * Math.PI / n + rnd.nextFloat() * .4f;
                float sp = (60 + rnd.nextFloat() * 70) * d;
                k.bx[i] = x; k.by[i] = y; k.bvx[i] = (float) Math.cos(a) * sp; k.bvy[i] = (float) Math.sin(a) * sp;
            }
        }
        pokes.add(k);
        if (pokes.size() > 6) pokes.remove(0);
    }

    /** sunX/sunY: where the sun or moon is on screen; wind: the phone's tilt, -1..1. */
    void draw(Canvas c, float sunX, float sunY, float wind) {
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0 : Math.min(.05f, (now - last) / 1000f);
        last = now;
        double t = now / 1000.0;
        switch (kind) {
            case "sunny": case "partly": drawSun(c, sunX, sunY, t, kind.equals("sunny") ? 1f : .6f); drawMotes(c, dt, t, wind); break;
            case "cloudy": drawMotes(c, dt, t, wind); break;
            case "rain": case "storm": drawRain(c, dt, wind, now); break;
            case "snow": drawSnow(c, dt, t, wind); break;
            case "fog": drawFog(c, dt); break;
            case "night": case "night_cloudy": drawStars(c, t, now); break;
        }
        drawPokes(c, now, dt);
    }

    private void drawSun(Canvas c, float x, float y, double t, float s) {
        // slow turning rays and a breathing glow around the sun
        float R = Math.min(w, h) * .62f;
        float pulse = (float) (.5 + .5 * Math.sin(t * 2 * Math.PI / 6));
        glow.setShader(new RadialGradient(x, y, R * (.55f + .06f * pulse), new int[] {
                a(0xFFF4D0, (int) ((46 + 26 * pulse) * s)), a(0xFFE2A0, (int) (18 * s)), 0}, new float[] {0f, .5f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, R, glow);
        glow.setShader(null);
        Path ray = this.ray;
        float rot = (float) (t * 4 % 360);
        c.save();
        c.rotate(rot, x, y);
        p.setStyle(Paint.Style.FILL);
        int ra = (int) ((34 + 18 * pulse) * s);
        Shader longRay = new RadialGradient(x, y, R, new int[] {a(0xFFF6DA, ra), 0}, null, Shader.TileMode.CLAMP);
        Shader shortRay = new RadialGradient(x, y, R * .78f, new int[] {a(0xFFF6DA, ra), 0}, null, Shader.TileMode.CLAMP);
        for (int i = 0; i < 12; i++) {
            float len = R * (i % 2 == 0 ? 1f : .78f), wdt = (float) Math.toRadians(i % 2 == 0 ? 2.6 : 1.8);
            double a0 = i * Math.PI / 6;
            ray.reset();
            ray.moveTo(x, y);
            ray.lineTo(x + (float) Math.cos(a0 - wdt) * len, y + (float) Math.sin(a0 - wdt) * len);
            ray.lineTo(x + (float) Math.cos(a0 + wdt) * len, y + (float) Math.sin(a0 + wdt) * len);
            ray.close();
            p.setShader(i % 2 == 0 ? longRay : shortRay);
            c.drawPath(ray, p);
        }
        p.setShader(null);
        c.restore();
    }

    private void drawMotes(Canvas c, float dt, double t, float wind) {
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < px.length; i++) {
            py[i] -= pv[i] * dt;
            px[i] += ((float) Math.sin(t * .7 + i) * 6 + wind * 20) * d * dt;
            if (py[i] < h * .05f) spawn(i, false);
            float life = Math.min(1f, (h * .8f - py[i]) / (h * .15f)) * Math.min(1f, (py[i] - h * .05f) / (h * .15f));
            p.setColor(a(0xFFF0C8, (int) (120 * Math.max(0, life))));
            c.drawCircle(px[i], py[i], ps[i], p);
        }
    }

    private void drawRain(Canvas c, float dt, float wind, long now) {
        float slant = .18f + wind * .35f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < px.length; i++) {
            py[i] += pv[i] * dt; px[i] += pv[i] * slant * dt;
            if (py[i] > h * .82f || px[i] > w + 20 * d || px[i] < -20 * d) spawn(i, false);
            p.setStrokeWidth((i % 3 == 0 ? 1.4f : 1f) * d);
            p.setColor(a(0xDCE6F2, i % 3 == 0 ? 120 : 80));
            float l = ps[i];
            c.drawLine(px[i], py[i], px[i] - l * slant, py[i] - l, p);
        }
        if (kind.equals("storm")) {
            if (flashAt < 0 && now > nextFlash) { flashAt = now; nextFlash = now + 7000 + rnd.nextInt(12000); }
            if (flashAt >= 0) {
                long e = now - flashAt;
                // two quick flickers, then fade
                float f = e < 90 ? 1f : e < 160 ? .25f : e < 260 ? .8f : Math.max(0, 1f - (e - 260) / 500f) * .6f;
                if (e > 760) flashAt = -1;
                p.setStyle(Paint.Style.FILL);
                p.setColor(a(0xEEF2FF, (int) (90 * f)));
                c.drawRect(0, 0, w, h, p);
            }
        }
    }

    private void drawSnow(Canvas c, float dt, double t, float wind) {
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < px.length; i++) {
            py[i] += pv[i] * dt;
            px[i] += ((float) Math.sin(t * 1.3 + i * 1.7) * 14 + wind * 40) * d * dt;
            if (py[i] > h * .84f) spawn(i, false);
            if (px[i] < -10 * d) px[i] += w + 20 * d; else if (px[i] > w + 10 * d) px[i] -= w + 20 * d;
            p.setColor(a(0xFFFFFF, ps[i] > 2.6f * d ? 220 : 160));
            c.drawCircle(px[i], py[i], ps[i], p);
        }
    }

    private void drawFog(Canvas c, float dt) {
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < px.length; i++) {
            px[i] += pv[i] * dt;
            if (px[i] > w * 1.5f) px[i] = -w * .5f; else if (px[i] < -w * .5f) px[i] = w * 1.5f;
            p.setShader(new RadialGradient(px[i], py[i], ps[i] * 3f, new int[] {0x3DE8ECF0, 0x14E8ECF0, 0}, new float[] {0, .5f, 1}, Shader.TileMode.CLAMP));
            c.save(); c.scale(2.2f, 1f, px[i], py[i]);
            c.drawCircle(px[i], py[i], ps[i] * 3f, p);
            c.restore();
        }
        p.setShader(null);
    }

    private void drawStars(Canvas c, double t, long now) {
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < px.length; i++) {
            float tw = (float) (.5 + .5 * Math.sin(t * (1.2 + (i % 5) * .35) + pv[i]));
            p.setColor(a(0xFFFFFF, (int) (70 + 160 * tw)));
            c.drawCircle(px[i], py[i], ps[i] * (.8f + .4f * tw), p);
        }
        // now and then, a shooting star
        if (shootAt < 0 && now > nextShoot) {
            shootAt = now; nextShoot = now + 14000 + rnd.nextInt(20000);
            shootX = w * (.15f + rnd.nextFloat() * .5f); shootY = h * (.05f + rnd.nextFloat() * .2f);
        }
        if (shootAt >= 0) {
            float e = (now - shootAt) / 900f;
            if (e > 1) shootAt = -1;
            else {
                float hx = shootX + e * w * .35f, hy = shootY + e * h * .12f;
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.6f * d); p.setStrokeCap(Paint.Cap.ROUND);
                p.setShader(new LinearGradient(hx, hy, hx - w * .12f, hy - h * .04f, a(0xFFFFFF, (int) (220 * (1 - e))), 0, Shader.TileMode.CLAMP));
                c.drawLine(hx, hy, hx - w * .12f, hy - h * .04f, p);
                p.setShader(null);
            }
        }
    }

    private void drawPokes(Canvas c, long now, float dt) {
        for (int k = pokes.size() - 1; k >= 0; k--) {
            Poke o = pokes.get(k);
            float e = (now - o.t) / 1100f;
            if (e >= 1) { pokes.remove(k); continue; }
            float fade = 1 - e;
            if (o.bx != null) {
                p.setStyle(Paint.Style.FILL);
                for (int i = 0; i < o.bx.length; i++) {
                    o.bx[i] += o.bvx[i] * dt; o.by[i] += o.bvy[i] * dt;
                    o.bvx[i] *= .96f; o.bvy[i] = o.bvy[i] * .96f + (kind.equals("snow") ? 40 * d * dt : 0);
                    p.setColor(a(kind.equals("snow") ? 0xFFFFFF : 0xFFF2C4, (int) (230 * fade)));
                    c.drawCircle(o.bx[i], o.by[i], (kind.equals("snow") ? 2.6f : 1.8f) * d, p);
                }
            } else {
                // a ring: golden in the sun, silver in the rain, soft white in the fog
                int col = kind.equals("rain") || kind.equals("storm") ? 0xDCE6F2 : kind.equals("fog") || kind.equals("cloudy") ? 0xFFFFFF : 0xFFD98A;
                p.setStyle(Paint.Style.STROKE);
                for (int r = 0; r < 2; r++) {
                    float ee = Math.max(0, e - r * .18f);
                    p.setStrokeWidth((2.4f - ee * 1.6f) * d);
                    p.setColor(a(col, (int) (200 * Math.max(0, 1 - ee))));
                    c.drawCircle(o.x, o.y, (14 + 90 * ee) * d, p);
                }
            }
        }
    }

    private static int a(int rgb, int alpha) { return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF); }
}
