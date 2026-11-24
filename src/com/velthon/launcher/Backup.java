package com.velthon.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Saving Velthon's set-up to a file and bringing it back; clearing what can be made again. */
final class Backup {
    private Backup() {}

    /** Things that belong to this phone only and must not travel to another one (widget slots, caches, logs). */
    private static boolean skip(String k) {
        return k.startsWith("wx_") || k.startsWith("lock_log") || k.startsWith("widget") || k.equals("lat") || k.equals("lon")
                || k.endsWith("_asked") || k.startsWith("synax") || k.startsWith("saint")
                || k.startsWith("nav_") || k.equals("pending_scale");
    }

    static String export(Context c) throws Exception {
        JSONObject root = new JSONObject();
        root.put("app", "Velthon Launcher");
        root.put("version", BuildConfigVersion.name(c));
        root.put("saved", System.currentTimeMillis());
        JSONObject p = new JSONObject();
        for (Map.Entry<String, ?> e : U.prefs(c).getAll().entrySet()) {
            String k = e.getKey(); Object v = e.getValue();
            if (skip(k) || v == null) continue;
            JSONObject o = new JSONObject();
            if (v instanceof Boolean) o.put("b", v);
            else if (v instanceof Integer) o.put("i", v);
            else if (v instanceof Long) o.put("l", v);
            else if (v instanceof Float) o.put("f", ((Float) v).doubleValue());
            else if (v instanceof Set) { JSONArray a = new JSONArray(); for (Object x : (Set<?>) v) a.put(String.valueOf(x)); o.put("set", a); }
            else o.put("s", String.valueOf(v));
            p.put(k, o);
        }
        root.put("settings", p);
        File bg = new File(c.getFilesDir(), "bg_custom.jpg");    // your own background picture travels too
        if (bg.exists() && bg.length() < 6_000_000) root.put("background", Base64.encodeToString(read(bg), Base64.NO_WRAP));
        return root.toString(1);
    }

    /** Puts a saved set-up back. Returns the number of settings restored. */
    static int restore(Context c, String json) throws Exception {
        if (json == null || json.length() > 12_000_000) throw new IllegalArgumentException("not a Velthon backup");
        JSONObject root = new JSONObject(json);
        if (!root.optString("app").startsWith("Velthon")) throw new IllegalArgumentException("not a Velthon backup");
        JSONObject p = root.getJSONObject("settings");
        SharedPreferences.Editor ed = U.prefs(c).edit();
        java.util.Iterator<String> it = p.keys(); int n = 0;
        while (it.hasNext()) {
            String k = it.next(); JSONObject o = p.optJSONObject(k);
            // only plain setting names and sensible values: a damaged or tampered file cannot inject anything else
            if (o == null || skip(k) || k.length() > 64 || !k.matches("[A-Za-z0-9_.\\-]+")) continue;
            if (o.has("s") && o.optString("s").length() > 20000) continue;
            if (o.has("b")) ed.putBoolean(k, o.getBoolean("b"));
            else if (o.has("i")) ed.putInt(k, o.getInt("i"));
            else if (o.has("l")) ed.putLong(k, o.getLong("l"));
            else if (o.has("f")) ed.putFloat(k, (float) o.getDouble("f"));
            else if (o.has("set")) { Set<String> s = new HashSet<>(); JSONArray a = o.getJSONArray("set"); for (int i = 0; i < a.length(); i++) s.add(a.getString(i)); ed.putStringSet(k, s); }
            else ed.putString(k, o.optString("s"));
            n++;
        }
        ed.commit();
        String bg = root.optString("background", "");
        if (!bg.isEmpty() && bg.length() < 9_000_000) {
            byte[] img = Base64.decode(bg, Base64.DEFAULT);
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options(); o.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeByteArray(img, 0, img.length, o);
            if (o.outWidth > 0 && o.outHeight > 0 && o.outWidth <= 8000 && o.outHeight <= 8000)   // a real picture, not a bomb
                try (FileOutputStream out = new FileOutputStream(new File(c.getFilesDir(), "bg_custom.jpg"))) { out.write(img); }
        }
        return n;
    }

    /** Clears what Velthon can fetch or draw again (saint pictures, weather, caches). Returns the bytes freed. */
    static long cleanUp(Context c) {
        long freed = wipe(c.getCacheDir(), false);
        if (c.getExternalCacheDir() != null) freed += wipe(c.getExternalCacheDir(), false);
        File[] fs = c.getFilesDir().listFiles();
        if (fs != null) for (File f : fs) if (f.getName().startsWith("saint_") || f.getName().endsWith(".tmp")) { freed += f.length(); f.delete(); }
        SharedPreferences.Editor ed = U.prefs(c).edit();
        for (String k : U.prefs(c).getAll().keySet()) if (k.startsWith("lock_log") || k.startsWith("synax")) ed.remove(k);
        ed.apply();
        return freed;
    }

    /** Everything back to how it was on the first start (your apps and the phone are not touched). */
    static void resetAll(Context c) {
        U.prefs(c).edit().clear().commit();
        wipe(c.getCacheDir(), false);
        File[] fs = c.getFilesDir().listFiles();
        if (fs != null) for (File f : fs) wipe(f, true);
    }

    static long size(Context c) {
        return du(c.getCacheDir()) + du(c.getFilesDir());
    }

    private static long wipe(File f, boolean self) {
        if (f == null || !f.exists()) return 0;
        long n = 0;
        if (f.isDirectory()) { File[] ch = f.listFiles(); if (ch != null) for (File x : ch) n += wipe(x, true); }
        else n = f.length();
        if (self) f.delete();
        return n;
    }
    private static long du(File f) {
        if (f == null || !f.exists()) return 0;
        if (!f.isDirectory()) return f.length();
        long n = 0; File[] ch = f.listFiles(); if (ch != null) for (File x : ch) n += du(x);
        return n;
    }
    static byte[] read(File f) throws Exception { try (InputStream in = new FileInputStream(f)) { return read(in); } }
    static byte[] read(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[16384]; int r;
        while ((r = in.read(buf)) > 0) b.write(buf, 0, r);
        return b.toByteArray();
    }

    /** The app's version name, read from the package (there is no generated BuildConfig in this build). */
    static final class BuildConfigVersion {
        static String name(Context c) {
            try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; } catch (Throwable t) { return "?"; }
        }
    }
}
