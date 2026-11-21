package com.velthon.launcher;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;

/** Current weather from Open-Meteo, at most once an hour, on a short-lived thread (no background service). */
final class Weather {
    private Weather() { }
    static final long EVERY = 60 * 60 * 1000L;
    private static boolean busy;

    interface Done { void done(); }

    static boolean fahrenheit(Context c) {
        String u = U.prefs(c).getString("units", "auto");
        if (u.equals("f")) return true;
        if (u.equals("c")) return false;
        String cc = Locale.getDefault().getCountry();
        return cc.equals("US") || cc.equals("LR") || cc.equals("MM") || cc.equals("BS") || cc.equals("KY") || cc.equals("PW");
    }

    static void refresh(final Context ctx, boolean force, final Done done) {
        final Context c = ctx.getApplicationContext();
        final SharedPreferences p = U.prefs(c);
        if (busy) return;
        long now = System.currentTimeMillis();
        if (!force && now - p.getLong("wx_at", 0) < EVERY && p.contains("wx_alert")) return;
        if (!force && now - p.getLong("wx_fail", 0) < 3 * 60 * 1000L) return;
        busy = true;
        new Thread(new Runnable() { @Override public void run() {
            try {
                double lat = Double.NaN, lon = Double.NaN;
                String city = null, country = null;
                String typed = p.getString("city", "").trim();
                if (!typed.isEmpty()) {
                    JSONObject g = get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(typed, "UTF-8"));
                    JSONObject r = g.optJSONArray("results") != null ? g.getJSONArray("results").optJSONObject(0) : null;
                    if (r != null) { lat = r.getDouble("latitude"); lon = r.getDouble("longitude"); city = r.optString("name"); country = r.optString("country_code"); }
                }
                if (Double.isNaN(lat)) {
                    Location l = lastLocation(c);
                    if (l != null) {
                        lat = l.getLatitude(); lon = l.getLongitude();
                        try {
                            List<Address> a = new Geocoder(c, Locale.getDefault()).getFromLocation(lat, lon, 1);
                            if (a != null && !a.isEmpty()) {
                                city = a.get(0).getLocality();
                                if (city == null) city = a.get(0).getSubAdminArea();
                                country = a.get(0).getCountryCode();
                            }
                        } catch (Throwable ignored) { }
                    }
                }
                if (Double.isNaN(lat)) {   // an approximate place from the network address
                    JSONObject g = get("https://get.geojs.io/v1/ip/geo.json");
                    lat = Double.parseDouble(g.optString("latitude", "NaN"));
                    lon = Double.parseDouble(g.optString("longitude", "NaN"));
                    city = g.optString("city", null); country = g.optString("country_code", null);
                }
                if (Double.isNaN(lat)) throw new IllegalStateException("no place");
                boolean f = fahrenheit(c);
                JSONObject w = get(String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day&hourly=temperature_2m,weather_code,is_day,wind_gusts_10m&wind_speed_unit=kmh&forecast_hours=26%s&timezone=auto",
                        lat, lon, f ? "&temperature_unit=fahrenheit" : ""));
                JSONObject cur = w.getJSONObject("current");
                // the next 12 hours, every 3 hours (as on the TV)
                StringBuilder fc = new StringBuilder();
                JSONObject hr = w.optJSONObject("hourly");
                if (hr != null) {
                    org.json.JSONArray ht = hr.getJSONArray("time"), tt = hr.getJSONArray("temperature_2m"),
                            wc = hr.getJSONArray("weather_code"), dy = hr.getJSONArray("is_day");
                    String nowHour = cur.optString("time", "").length() >= 13 ? cur.optString("time").substring(0, 13) : "";
                    int start = 0;
                    for (int i = 0; i < ht.length(); i++) if (ht.getString(i).startsWith(nowHour)) { start = i; break; }
                    // a weather alert from the next 24 hours: the most serious thing coming
                    org.json.JSONArray gu = hr.optJSONArray("wind_gusts_10m");
                    int best = 0; String alert = "";
                    for (int i = start; i < Math.min(ht.length(), start + 24); i++) {
                        int code = wc.getInt(i), sev = 0; String k = null;
                        double tc = tt.getDouble(i); if (f) tc = (tc - 32) * 5 / 9;
                        double g = gu == null ? 0 : gu.optDouble(i, 0);
                        if (code >= 95) { sev = 6; k = "storm"; }
                        else if (code == 66 || code == 67 || code == 56 || code == 57) { sev = 6; k = "ice"; }
                        else if (code == 75 || code == 86) { sev = 5; k = "snow_heavy"; }
                        else if (code == 65 || code == 82) { sev = 5; k = "rain_heavy"; }
                        else if (g >= 70) { sev = 5; k = "wind"; }
                        else if (tc <= -20) { sev = 4; k = "cold"; }
                        else if (tc >= 35) { sev = 4; k = "heat"; }
                        else if ((code >= 71 && code <= 77) || code == 85) { sev = 3; k = "snow"; }
                        else if (code == 48) { sev = 2; k = "fog"; }
                        if (sev > best) {
                            best = sev;
                            String hh = ht.getString(i);
                            alert = k + "," + (hh.length() >= 13 ? hh.substring(11, 13) : "0") + "," + (i == start ? 1 : 0);
                        }
                    }
                    p.edit().putString("wx_alert", alert).apply();
                    for (int s = 3; s <= 15; s += 3) {
                        int i = start + s;
                        if (i >= ht.length()) break;
                        String hh = ht.getString(i);
                        fc.append(hh.length() >= 16 ? hh.substring(11, 13) : "0").append(',')
                                .append((int) Math.round(tt.getDouble(i))).append(',')
                                .append(wc.getInt(i)).append(',').append(dy.getInt(i)).append(';');
                    }
                }
                p.edit()
                        .putLong("lat", Double.doubleToLongBits(lat)).putLong("lon", Double.doubleToLongBits(lon))
                        .putString("wx_city", city == null ? "" : city).putString("wx_cc", country == null ? "" : country)
                        .putInt("wx_temp", (int) Math.round(cur.getDouble("temperature_2m")))
                        .putBoolean("wx_f", f)
                        .putInt("wx_code", cur.getInt("weather_code"))
                        .putString("wx_fc", fc.toString())
                        .putLong("wx_at", System.currentTimeMillis()).apply();
            } catch (Throwable e) {
                // keep the last reading; try again in a few minutes
                android.util.Log.w("Velthon", "weather: " + e);
                p.edit().putLong("wx_at", Math.max(0, p.getLong("wx_at", 0))).putLong("wx_fail", System.currentTimeMillis()).apply();
            } finally {
                busy = false;
                new Handler(Looper.getMainLooper()).post(new Runnable() { @Override public void run() { if (done != null) done.done(); } });
            }
        } }, "velthon-weather").start();
    }

    private static Location lastLocation(Context c) {
        if (Build.VERSION.SDK_INT >= 23 && c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return null;
        try {
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            Location best = null;
            for (String prov : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(prov);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            return best;
        } catch (Throwable t) { return null; }
    }

    private static JSONObject get(String url) throws Exception {
        try { return get1(url); } catch (java.io.IOException first) { Thread.sleep(1500); return get1(url); }   // one retry on a slow network
    }
    private static JSONObject get1(String url) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(12000); h.setReadTimeout(15000);
        h.setRequestProperty("User-Agent", "VelthonLauncher/1.0");
        try (InputStream in = h.getInputStream()) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            return new JSONObject(bo.toString("UTF-8"));
        } finally { h.disconnect(); }
    }

    // ---------- what to show ----------
    static boolean has(Context c) { return U.prefs(c).getLong("wx_at", 0) > 0; }

    /** The coming weather alert, {kind, hour of day, now?}, or null. */
    static String[] alert(Context c) {
        String a = U.prefs(c).getString("wx_alert", "");
        String[] f = a.split(",");
        return f.length == 3 ? f : null;
    }

    static String iconName(Context c) { return iconName(U.prefs(c).getInt("wx_code", 0), U.night(c)); }

    /** The next 12 hours: {hour of day, temperature, code, is day}. */
    static int[][] forecast(Context c) {
        String s = U.prefs(c).getString("wx_fc", "");
        java.util.ArrayList<int[]> l = new java.util.ArrayList<>();
        for (String p : s.split(";")) {
            String[] f = p.split(",");
            if (f.length == 4) try { l.add(new int[] {Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3])}); }
            catch (NumberFormatException ignored) { }
        }
        return l.toArray(new int[0][]);
    }

    static String iconName(int code, boolean n) {
        if (code == 0) return n ? "clear-night" : "clear-day";
        if (code <= 2) return n ? "partly-cloudy-night" : "partly-cloudy-day";
        if (code == 3) return "overcast";
        if (code == 45 || code == 48) return n ? "fog-night" : "fog-day";
        if (code >= 51 && code <= 55) return "drizzle";
        if (code == 56 || code == 57 || code == 66 || code == 67) return "sleet";
        if (code >= 61 && code <= 65) return "rain";
        if (code >= 71 && code <= 77) return "snow";
        if (code >= 80 && code <= 82) return n ? "partly-cloudy-night-rain" : "partly-cloudy-day-rain";
        if (code == 85 || code == 86) return n ? "partly-cloudy-night-snow" : "partly-cloudy-day-snow";
        if (code >= 95) return n ? "thunderstorms-night-rain" : "thunderstorms-day-rain";
        return "cloudy";
    }

    static String condition(Context c) {
        int code = U.prefs(c).getInt("wx_code", 0);
        if (code == 0) return L.t("Clear");
        if (code <= 2) return L.t("Partly cloudy");
        if (code == 3) return L.t("Cloudy");
        if (code == 45 || code == 48) return L.t("Fog");
        if (code >= 51 && code <= 57) return L.t("Drizzle");
        if (code >= 61 && code <= 67) return L.t("Rain");
        if (code >= 71 && code <= 77) return L.t("Snow");
        if (code >= 80 && code <= 82) return L.t("Showers");
        if (code == 85 || code == 86) return L.t("Snow showers");
        if (code >= 95) return L.t("Thunderstorm");
        return "";
    }

    static String temp(Context c) {
        SharedPreferences p = U.prefs(c);
        return p.getInt("wx_temp", 0) + (p.getBoolean("wx_f", false) ? "°F" : "°C");
    }
    static String city(Context c) { return U.prefs(c).getString("wx_city", ""); }
}
