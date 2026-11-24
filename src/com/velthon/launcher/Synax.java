package com.velthon.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Synaxarium (Coptic book of saints) for today, from CopticChurch.net: the first commemoration of the day
 * ("Saint of the day"), every day of the year, and a small picture of that saint when Wikipedia has one.
 * Fetched once a day in the background, kept for offline use.
 */
final class Synax {
    private Synax() { }

    /** Coptic month (1..13, 13 = the little month Nasie) and day for a date. */
    static int[] date(Calendar day) {
        int[] md = Church.coptic(day);
        // Church.coptic folds the 5 or 6 days of Nasie into month 12: give them their own month
        if (md[0] == 12 && md[1] > 30) return new int[] {13, md[1] - 30};
        return md;
    }

    /** Today's page on CopticChurch.net, in English or Arabic. */
    static String url(Context c) {
        int[] md = date(Calendar.getInstance());
        boolean ar = U.langs(c).equals("ar");
        return "https://copticchurch.net/synaxarium/" + (ar ? "ar/" + md[0] + "_" + md[1] : md[0] + "_" + md[1] + ".html");
    }

    private static String today() {
        int[] md = date(Calendar.getInstance());
        return md[0] + "_" + md[1];
    }

    /** Today's saint (e.g. "The Departure of St. Justus"), or null if not fetched yet. */
    static String saint(Context c) {
        String k = U.prefs(c).getString("syn_day", "");
        return k.equals(today() + (U.langs(c).equals("ar") ? "ar" : "")) ? U.prefs(c).getString("syn_name", null) : null;
    }
    /** All of today's commemorations, one per line, or null. */
    static String all(Context c) { return saint(c) == null ? null : U.prefs(c).getString("syn_all", null); }

    static Bitmap picture(Context c) {
        if (saint(c) == null) return null;
        File f = new File(c.getFilesDir(), "saint_" + today() + ".png");
        return f.exists() ? BitmapFactory.decodeFile(f.getPath()) : null;
    }

    private static boolean busy;
    /** Fetches today's saint if needed; {@code done} runs on the caller's thread side (posted by the caller). */
    static void load(final Context ctx, final Runnable done) {
        final Context c = ctx.getApplicationContext();
        if (!Church.calendar(c).equals("orthodox") || busy) return;
        if (saint(c) != null && System.currentTimeMillis() - U.prefs(c).getLong("syn_at", 0) < 6 * 3600_000L) return;
        busy = true;
        new Thread(new Runnable() { @Override public void run() {
            try {
                boolean ar = U.langs(c).equals("ar");
                String html = text(url(c));
                java.util.ArrayList<String> items = new java.util.ArrayList<>();
                Matcher m = Pattern.compile("<a[^>]+href=\"#(\\d+)\"[^>]*>(.*?)</a>", Pattern.DOTALL).matcher(html);
                while (m.find()) { String t = clean(m.group(2)); if (t.length() > 3 && !items.contains(t)) items.add(t); }
                if (items.isEmpty()) {
                    Matcher h = Pattern.compile("<h3[^>]*>(.*?)</h3>", Pattern.DOTALL).matcher(html);
                    while (h.find()) { String t = clean(h.group(1)).replaceFirst("^\\d+\\.\\s*", ""); if (t.length() > 3) items.add(t); }
                }
                if (items.isEmpty()) return;
                StringBuilder all = new StringBuilder();
                for (String s : items) { if (all.length() > 0) all.append('\n'); all.append(s); }
                U.prefs(c).edit().putString("syn_day", today() + (ar ? "ar" : "")).putString("syn_name", items.get(0))
                        .putString("syn_all", all.toString()).putLong("syn_at", System.currentTimeMillis()).apply();
                // a picture: the saint's Wikipedia article image, when the article clearly is that saint
                File f = new File(c.getFilesDir(), "saint_" + today() + ".png");
                if (!f.exists()) {
                    String en = items.get(0);
                    if (ar) { try { en = firstItem(text("https://copticchurch.net/synaxarium/" + today() + ".html")); } catch (Throwable ignored) { } }
                    Bitmap b = wikiPicture(en);
                    if (b != null) {
                        for (File old : c.getFilesDir().listFiles()) if (old.getName().startsWith("saint_")) old.delete();
                        try (FileOutputStream o = new FileOutputStream(f)) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                busy = false;
                if (done != null) done.run();
            }
        } }).start();
    }

    private static String firstItem(String html) {
        Matcher m = Pattern.compile("<a[^>]+href=\"#1\"[^>]*>(.*?)</a>", Pattern.DOTALL).matcher(html);
        return m.find() ? clean(m.group(1)) : null;
    }

    /** "The Martyrdom of St. George of Cappadocia." -> "George of Cappadocia" (who the day is about). */
    static String who(String item) {
        if (item == null) return null;
        String s = item.replaceAll("[.]$", "").trim();
        s = s.replaceFirst("(?i)^(the\\s+)?(commemoration|departure|martyrdom|consecration|appearance|feast|translation|relocation|assembly|council|birth|nativity|annunciation|dormition|assumption|entrance|return|discovery|finding|repose)\\s+of\\s+(the\\s+)?", "");
        s = s.replaceFirst("(?i)^(the\\s+)?(honorable|great|righteous|holy|blessed|pure)\\s+", "");
        s = s.replaceFirst("(?i)^(st\\.|saint|abba|anba|pope|the)\\s+", "");
        s = s.replaceAll("(?i),?\\s+the\\s+\\d+\\w*\\s+(pope|patriarch).*$", "");
        return s.trim();
    }

    private static Bitmap wikiPicture(String item) {
        String who = who(item);
        if (who == null || who.length() < 3) return null;
        try {
            String q = URLEncoder.encode(who + " saint", "UTF-8");
            String json = text("https://en.wikipedia.org/w/api.php?action=query&format=json&generator=search&gsrlimit=1&gsrsearch=" + q
                    + "&prop=pageimages&piprop=thumbnail&pithumbsize=200");
            Matcher t = Pattern.compile("\"title\":\"([^\"]+)\"").matcher(json);
            Matcher s = Pattern.compile("\"source\":\"([^\"]+)\"").matcher(json);
            if (!t.find() || !s.find()) return null;
            // only if the article is about that saint: its title carries the saint's main name
            String main = who.split("[ ,]")[0].toLowerCase(java.util.Locale.ROOT);
            if (main.length() < 3 || !t.group(1).toLowerCase(java.util.Locale.ROOT).contains(main)) return null;
            String src = s.group(1).replace("\\/", "/");
            if (!src.startsWith("https://upload.wikimedia.org/")) return null;          // pictures only from Wikimedia
            byte[] img = bytes(src);
            BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(img, 0, img.length, o);
            if (o.outWidth <= 0 || o.outHeight <= 0 || o.outWidth > 4000 || o.outHeight > 4000) return null;
            int sample = Math.max(1, Math.max(o.outWidth, o.outHeight) / 400);
            o = new BitmapFactory.Options(); o.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(img, 0, img.length, o);
        } catch (Throwable e) { return null; }
    }

    private static String clean(String s) {
        return s.replaceAll("<[^>]+>", "").replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"")
                .replace("&nbsp;", " ").replaceAll("\\s+", " ").replaceFirst("^\\d+\\.\\s*", "").trim();
    }
    private static String text(String url) throws Exception { return new String(bytes(url), "UTF-8"); }
    private static final int MAX_BYTES = 2_000_000;   // nothing we fetch is larger: a bigger answer is refused
    private static byte[] bytes(String url) throws Exception {
        if (!url.startsWith("https://")) throw new java.io.IOException("https only");
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(12000); h.setReadTimeout(15000);
        h.setInstanceFollowRedirects(true);
        h.setRequestProperty("User-Agent", "VelthonLauncher/1.0 (Android; contact: velthon)");
        try (InputStream in = h.getInputStream()) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0) { bo.write(b, 0, n); if (bo.size() > MAX_BYTES) throw new java.io.IOException("too large"); }
            if (!h.getURL().getProtocol().equals("https")) throw new java.io.IOException("redirected off https");
            return bo.toByteArray();
        } finally { h.disconnect(); }
    }
}
