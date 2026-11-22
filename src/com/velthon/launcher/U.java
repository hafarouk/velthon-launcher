package com.velthon.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorMatrix;
import android.graphics.Typeface;
import android.util.TypedValue;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;

/** Shared helpers: assets, verses, seasons, the sun, settings. Everything is loaded once and cached. */
final class U {
    private U() { }

    static float dp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }
    static int dpi(Context c, float v) { return Math.round(dp(c, v)); }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("velthon", Context.MODE_PRIVATE);
    }

    // ---------- assets ----------
    private static final HashMap<String, Bitmap> BMP = new HashMap<>();
    static Bitmap bitmap(Context c, String path) {
        synchronized (BMP) {
            if (BMP.containsKey(path)) return BMP.get(path);
        }
        Bitmap b = null;
        try (InputStream in = c.getAssets().open(path)) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = path.endsWith(".jpg") ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;
            b = BitmapFactory.decodeStream(in, null, o);
        } catch (Throwable ignored) { }
        synchronized (BMP) { BMP.put(path, b); }
        return b;
    }

    // ---------- the background picture ----------
    /** "window" (Bible and coffee), "candle", "mist", "night" or "custom" (from the gallery). */
    static String bg(Context c) { return prefs(c).getString("bg", "weather"); }
    /** The live weather sky: the sun photograph, graded for today's weather (and the moon at night). */
    static boolean isSky(Context c) { return bg(c).equals("weather"); }
    static String skyName(Context c) {
        boolean night = night(c), has = Weather.has(c);
        int code = has ? prefs(c).getInt("wx_code", 0) : 0;
        if (code >= 95) return "storm";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "snow";
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return night ? "storm" : "rain";
        if (code == 45 || code == 48) return night ? "night_cloudy" : "fog";
        if (code == 3) return night ? "night_cloudy" : "cloudy";
        if (code == 2) return night ? "night" : "partly";
        return night ? "night" : "sunny";
    }
    /** Bright skies (the sun, light cloud, snow, fog) take dark verse text; dark skies take white. */
    static boolean skyIsBright(Context c) {
        if (!isSky(c)) return false;
        String n = skyName(c);
        return n.equals("sunny") || n.equals("partly") || n.equals("cloudy") || n.equals("snow") || n.equals("fog");
    }
    /** The verse sits inside the sun or the moon: it needs dark text there (navy under the moon). */
    static int skyVerseColor(Context c) {
        if (!isSky(c)) return 0;
        String n = skyName(c);
        if (n.equals("night") || n.equals("night_cloudy")) return 0xFF16223E;
        return skyIsBright(c) ? 0xFF3A2A12 : 0;
    }
    /** Where the sun or moon is in the sky pictures (fraction of width, height). */
    static final float SKY_FX = .497f, SKY_FY = .412f;
    private static String lastSky;
    static boolean isWindow(Context c) { return bg(c).equals("window"); }
    private static Bitmap custom; private static long customAt;
    static Bitmap background(Context c) {
        String b = bg(c);
        if (b.equals("custom")) {
            java.io.File f = new java.io.File(c.getFilesDir(), "bg_custom.jpg");
            if (f.exists()) {
                if (custom == null || customAt != f.lastModified()) {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inPreferredConfig = Bitmap.Config.RGB_565;
                    custom = BitmapFactory.decodeFile(f.getPath(), o);
                    customAt = f.lastModified();
                }
                if (custom != null) return custom;
            }
            b = "window";
        }
        if (b.equals("weather")) {
            String path = "sky/" + skyName(c) + ".jpg";
            if (lastSky != null && !lastSky.equals(path)) synchronized (BMP) { BMP.remove(lastSky); }   // keep only today's sky in memory
            lastSky = path;
            return bitmap(c, path);
        }
        if (b.equals("candle") || b.equals("mist") || b.equals("night")) return bitmap(c, "bg/" + b + ".jpg");
        return bitmap(c, "scene.jpg");
    }
    static boolean cornerLeaves(Context c) { return false; }   // leaves were retired: the window stays clear

    private static Typeface serif, sans, sansBold, sansLight;
    static Typeface serif(Context c) {
        if (serif == null) {
            try { serif = Typeface.createFromAsset(c.getAssets(), "fonts/playfair_semibold.ttf"); }
            catch (Throwable t) { serif = Typeface.SERIF; }
        }
        return serif;
    }
    static Typeface serifBold(Context c) { return Typeface.create(serif(c), Typeface.BOLD); }
    static Typeface sans(Context c) {
        if (sans == null) {
            try { sans = Typeface.createFromAsset(c.getAssets(), "fonts/sourcesans_regular.ttf"); }
            catch (Throwable t) { sans = Typeface.SANS_SERIF; }
        }
        return sans;
    }
    static Typeface sansBold(Context c) {
        if (sansBold == null) {
            try { sansBold = Typeface.createFromAsset(c.getAssets(), "fonts/sourcesans_semibold.ttf"); }
            catch (Throwable t) { sansBold = Typeface.DEFAULT_BOLD; }
        }
        return sansBold;
    }
    static Typeface light() {
        if (sansLight == null) sansLight = Typeface.create("sans-serif-light", Typeface.NORMAL);
        return sansLight;
    }

    // ---------- verses ----------
    static final class Verse {
        final int index; final String en, ar, refEn, refAr; String ref;
        Verse(int i, String en, String ar, String refEn, String refAr) {
            this.index = i; this.en = en; this.ar = ar; this.refEn = refEn; this.refAr = refAr;
        }
    }
    private static ArrayList<Verse> verses;
    static synchronized ArrayList<Verse> verses(Context c) {
        if (verses != null) return verses;
        verses = new ArrayList<>();
        try (InputStream in = c.getAssets().open("bible_verses.json")) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            JSONArray a = new JSONArray(bo.toString("UTF-8"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Verse v = new Verse(i, o.optString("en"), o.optString("ar"),
                        o.optString("ref_en", o.optString("ref")), o.optString("ref_ar"));
                v.ref = o.optString("ref");
                verses.add(v);
            }
        } catch (Throwable ignored) { }
        if (verses.isEmpty()) verses.add(new Verse(0, "The Lord is my shepherd; I shall not want.", "اَلرَّبُّ رَاعِيَّ فَلاَ يُعْوِزُنِي شَيْءٌ.", "Psalms 23:1", "مزامير ٢٣:١"));
        return verses;
    }
    /** The verse of the hour: it changes every hour. On a feast day, every other hour is that feast's reading. */
    static Verse verseOfHour(Context c) {
        ArrayList<Verse> v = verses(c);
        long slot = verseSlot(c);
        Church.Feast f = Church.today(c);
        if (f != null && !prefs(c).getString("feast_verses", "on").equals("off") && slot % 2 == 0 && prefs(c).getInt("verse_shift", 0) == 0) {
            String[] refs = Church.readings(f.id);
            if (refs != null) {
                String want = refs[(int) ((slot / 2) % refs.length)];
                for (Verse x : v) if (want.equals(x.ref)) return x;
            }
        }
        long step = slot + prefs(c).getInt("verse_shift", 0);
        return v.get((int) (((step * 37) % v.size() + v.size()) % v.size()));
    }
    /** How often the verse changes, in minutes (15, 30, 60 or a day). */
    static int versePeriod(Context c) {
        try { return Integer.parseInt(prefs(c).getString("verse_every", "5")); } catch (NumberFormatException e) { return 5; }
    }
    /** Which verse period we are in now: changes every {@link #versePeriod} minutes. */
    static long verseSlot(Context c) {
        Calendar k = Calendar.getInstance();
        long minutes = (k.get(Calendar.YEAR) * 400L + k.get(Calendar.DAY_OF_YEAR)) * 1440L + k.get(Calendar.HOUR_OF_DAY) * 60L + k.get(Calendar.MINUTE);
        return minutes / Math.max(1, versePeriod(c));
    }

    /** Which languages to show: "both", "en" or "ar". */
    static String langs(Context c) {
        String l = prefs(c).getString("langs", "");
        if (l.equals("en") || l.equals("ar")) return l;
        return java.util.Locale.getDefault().getLanguage().equals("ar") ? "ar" : "en";   // one language only
    }

    static java.util.LinkedHashSet<Integer> saved(Context c) {
        java.util.LinkedHashSet<Integer> s = new java.util.LinkedHashSet<>();
        for (String p : prefs(c).getString("saved", "").split(",")) {
            try { if (!p.isEmpty()) s.add(Integer.parseInt(p)); } catch (NumberFormatException ignored) { }
        }
        return s;
    }
    static boolean toggleSaved(Context c, int index) {
        java.util.LinkedHashSet<Integer> s = saved(c);
        boolean now = !s.remove(index);
        if (now) s.add(index);
        StringBuilder b = new StringBuilder();
        for (Integer i : s) { if (b.length() > 0) b.append(','); b.append(i); }
        prefs(c).edit().putString("saved", b.toString()).apply();
        return now;
    }

    // ---------- seasons (the same colours as the TV app) ----------
    static final int SPRING = 1, SUMMER = 2, FALL = 3, WINTER = 4;
    static int season(Context c) {
        String s = prefs(c).getString("season", "auto");
        if (s.equals("spring")) return SPRING;
        if (s.equals("summer")) return SUMMER;
        if (s.equals("fall")) return FALL;
        if (s.equals("winter")) return WINTER;
        int m = Calendar.getInstance().get(Calendar.MONTH);
        boolean south = java.util.TimeZone.getDefault().getID().startsWith("Australia")
                || java.util.TimeZone.getDefault().getID().startsWith("Antarctica");
        int s1 = (m == 11 || m <= 1) ? WINTER : m <= 4 ? SPRING : m <= 7 ? SUMMER : FALL;
        if (south) s1 = s1 == WINTER ? SUMMER : s1 == SUMMER ? WINTER : s1 == SPRING ? FALL : SPRING;
        return s1;
    }
    static ColorMatrix seasonMatrix(int s) {
        switch (s) {
            case SPRING: return new ColorMatrix(new float[] {
                    .92f, .08f, 0, 0, 6, .02f, 1.12f, 0, 0, 14, 0, .05f, .85f, 0, 0, 0, 0, 0, 1, 0});
            case SUMMER: {
                ColorMatrix m = new ColorMatrix(); m.setSaturation(1.25f);
                m.postConcat(new ColorMatrix(new float[] {1.05f, 0, 0, 0, 6, 0, 1.04f, 0, 0, 4, 0, 0, .86f, 0, 0, 0, 0, 0, 1, 0}));
                return m;
            }
            case WINTER: {
                ColorMatrix m = new ColorMatrix(); m.setSaturation(.18f);
                m.postConcat(new ColorMatrix(new float[] {1.05f, 0, 0, 0, 38, 0, 1.08f, 0, 0, 44, 0, 0, 1.15f, 0, 54, 0, 0, 0, 1, 0}));
                return m;
            }
            default: return new ColorMatrix(new float[] {
                    .85f, .95f, -.15f, 0, 8, .18f, .52f, 0, 0, 0, 0, .08f, .35f, 0, 0, 0, 0, 0, 1, 0});
        }
    }
    /** How dark the evening makes things outside the window: 0 day .. 1 night. */
    static float evening(Context c) {
        double lat = Double.longBitsToDouble(prefs(c).getLong("lat", Double.doubleToLongBits(Double.NaN)));
        double lon = Double.longBitsToDouble(prefs(c).getLong("lon", Double.doubleToLongBits(Double.NaN)));
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            lon = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3600000.0 * 15.0;
            lat = 45;
        }
        double alt = sunAltitude(System.currentTimeMillis() / 1000L, lat, lon);
        return (float) Math.max(0, Math.min(1, (6 - alt) / 12.0));
    }
    static boolean night(Context c) { return evening(c) > .55f; }

    static double sunAltitude(long unix, double lat, double lon) {
        double d = unix / 86400.0 - 10957.5;
        double g = Math.toRadians((357.529 + 0.98560028 * d) % 360);
        double q = (280.459 + 0.98564736 * d) % 360;
        double L = Math.toRadians(q + 1.915 * Math.sin(g) + 0.020 * Math.sin(2 * g));
        double e = Math.toRadians(23.439 - 0.00000036 * d);
        double ra = Math.atan2(Math.cos(e) * Math.sin(L), Math.cos(L));
        double dec = Math.asin(Math.sin(e) * Math.sin(L));
        double gmst = (18.697374558 + 24.06570982441908 * d) % 24;
        double ha = Math.toRadians(gmst * 15 + lon) - ra;
        double la = Math.toRadians(lat);
        return Math.toDegrees(Math.asin(Math.sin(la) * Math.sin(dec) + Math.cos(la) * Math.cos(dec) * Math.cos(ha)));
    }

    // ---------- a cheap blur for the frosted glass (done once, on a small copy) ----------
    static Bitmap blur(Bitmap src, int radius) {
        Bitmap b = src.copy(Bitmap.Config.ARGB_8888, true);
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h], tmp = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) {
            boxH(px, tmp, w, h, radius);
            boxV(tmp, px, w, h, radius);
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
        return b;
    }
    private static void boxH(int[] in, int[] out, int w, int h, int r) {
        float k = 1f / (r + r + 1);
        for (int y = 0; y < h; y++) {
            int ti = y * w; float a = 0, rr = 0, g = 0, bb = 0;
            for (int i = -r; i <= r; i++) { int p = in[ti + Math.max(0, Math.min(w - 1, i))];
                a += p >>> 24; rr += (p >> 16) & 255; g += (p >> 8) & 255; bb += p & 255; }
            for (int x = 0; x < w; x++) {
                out[ti + x] = (Math.round(a * k) << 24) | (Math.round(rr * k) << 16) | (Math.round(g * k) << 8) | Math.round(bb * k);
                int p1 = in[ti + Math.min(w - 1, x + r + 1)], p0 = in[ti + Math.max(0, x - r)];
                a += (p1 >>> 24) - (p0 >>> 24); rr += ((p1 >> 16) & 255) - ((p0 >> 16) & 255);
                g += ((p1 >> 8) & 255) - ((p0 >> 8) & 255); bb += (p1 & 255) - (p0 & 255);
            }
        }
    }
    private static void boxV(int[] in, int[] out, int w, int h, int r) {
        float k = 1f / (r + r + 1);
        for (int x = 0; x < w; x++) {
            float a = 0, rr = 0, g = 0, bb = 0;
            for (int i = -r; i <= r; i++) { int p = in[Math.max(0, Math.min(h - 1, i)) * w + x];
                a += p >>> 24; rr += (p >> 16) & 255; g += (p >> 8) & 255; bb += p & 255; }
            for (int y = 0; y < h; y++) {
                out[y * w + x] = (Math.round(a * k) << 24) | (Math.round(rr * k) << 16) | (Math.round(g * k) << 8) | Math.round(bb * k);
                int p1 = in[Math.min(h - 1, y + r + 1) * w + x], p0 = in[Math.max(0, y - r) * w + x];
                a += (p1 >>> 24) - (p0 >>> 24); rr += ((p1 >> 16) & 255) - ((p0 >> 16) & 255);
                g += ((p1 >> 8) & 255) - ((p0 >> 8) & 255); bb += (p1 & 255) - (p0 & 255);
            }
        }
    }

    /** Light motion (Settings › Atmosphere › Animations), and never when the phone's own animations are off. */
    /** True when the phone is driven by swipe gestures (no Back button on screen). Without them, every panel gets an X. */
    static boolean gestureNav(android.content.Context c) {
        try { return android.provider.Settings.Secure.getInt(c.getContentResolver(), "navigation_mode", 0) == 2; } catch (Throwable t) { return false; }
    }
    /** A small round X that closes a panel (shown when the phone is not on gesture navigation). */
    static android.view.View closeX(android.content.Context c, final Runnable close) {
        android.widget.ImageView x = new android.widget.ImageView(c);
        x.setImageDrawable(new Ico(Ico.CLOSE, 0xFFFFFFFF));
        int p = Math.round(dp(c, 9)); x.setPadding(p, p, p, p);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL); g.setColor(0x40FFFFFF); g.setStroke(Math.round(dp(c, 1)), 0x55FFFFFF);
        x.setBackground(g);
        x.setContentDescription("Close");
        x.setOnClickListener(new android.view.View.OnClickListener() { @Override public void onClick(android.view.View v) { close.run(); } });
        x.setLayoutParams(new android.widget.LinearLayout.LayoutParams(Math.round(dp(c, 36)), Math.round(dp(c, 36))));
        return x;
    }

    static boolean anims(android.content.Context c) {
        if (!prefs(c).getString("anims", "on").equals("on")) return false;
        if (android.os.Build.VERSION.SDK_INT >= 26) { try { return android.animation.ValueAnimator.areAnimatorsEnabled(); } catch (Throwable ignored) { } }
        return true;
    }

    /** Fully see-through status and navigation bars: no translucent tint, no contrast scrim from the phone. */
    static void clearBars(android.view.Window w) {
        w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS | android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(0); w.setNavigationBarColor(0);
        try { w.getClass().getMethod("setStatusBarContrastEnforced", boolean.class).invoke(w, false); } catch (Throwable ignored) { }
        try { w.getClass().getMethod("setNavigationBarContrastEnforced", boolean.class).invoke(w, false); } catch (Throwable ignored) { }
    }
}
