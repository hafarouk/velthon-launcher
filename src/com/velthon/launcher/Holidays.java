package com.velthon.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The public holidays where the phone is (the country found for the weather, else the phone's region), from the
 * free Nager.Date service, kept for each year so the calendar works offline. Holidays of one province or state
 * show only there.
 */
final class Holidays {
    private Holidays() { }
    private static volatile boolean busy;

    /** The country (two letters) the holidays are for, or "". */
    static String country(Context c) {
        String cc = U.prefs(c).getString("wx_cc", "");
        if (cc == null || cc.length() != 2) cc = Locale.getDefault().getCountry();
        return cc == null || cc.length() != 2 ? "" : cc.toUpperCase(Locale.US);
    }

    /** The country's name in the page language, e.g. "Canada". */
    static String countryName(Context c, Locale in) {
        String cc = country(c);
        return cc.isEmpty() ? "" : new Locale("", cc).getDisplayCountry(in);
    }

    /** The holidays of the given day, in the page language (empty if none or not known yet). */
    static ArrayList<String> on(Context c, Calendar day) {
        HashMap<String, ArrayList<String>> y = year(c, day.get(Calendar.YEAR));
        ArrayList<String> l = y.get(key(day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH)));
        return l == null ? new ArrayList<String>() : l;
    }

    /** The holidays from today on, up to {@code days} days ahead: {days from today, name}. */
    static ArrayList<Object[]> ahead(Context c, int days) {
        ArrayList<Object[]> out = new ArrayList<>();
        Calendar d = Calendar.getInstance();
        HashMap<String, ArrayList<String>> y = year(c, d.get(Calendar.YEAR));
        if (y.isEmpty() && year(c, d.get(Calendar.YEAR) + 1).isEmpty()) return out;   // nothing known: no work
        for (int i = 0; i <= days; i++, d.add(Calendar.DAY_OF_MONTH, 1))
            for (String n : on(c, d)) out.add(new Object[] {i, n});
        return out;
    }

    private static String key(int m, int d) { return (m < 10 ? "0" : "") + m + "-" + (d < 10 ? "0" : "") + d; }

    private static final HashMap<String, HashMap<String, ArrayList<String>>> parsed = new HashMap<>();

    private static HashMap<String, ArrayList<String>> year(Context c, int y) {
        String cc = country(c), lang = U.langs(c), reg = regionCode(cc, U.prefs(c).getString("wx_region", ""));
        String id = cc + y + lang + reg;
        synchronized (parsed) {
            HashMap<String, ArrayList<String>> m = parsed.get(id);
            if (m != null) return m;
            m = new HashMap<>();
            String raw = U.prefs(c).getString("hol_" + cc + "_" + y, "");
            try {
                JSONArray a = new JSONArray(raw.isEmpty() ? "[]" : raw);
                for (int i = 0; i < a.length() && i < 200; i++) {
                    JSONObject h = a.optJSONObject(i); if (h == null) continue;
                    JSONArray ty = h.optJSONArray("types");   // days off only, not school days or observances
                    if (ty != null && !ty.toString().contains("\"Public\"")) continue;
                    JSONArray where = h.optJSONArray("counties");
                    if (where != null && where.length() > 0) {   // only in some provinces or states: is it ours?
                        boolean here = false;
                        for (int k = 0; k < where.length(); k++) if (where.optString(k).equalsIgnoreCase(reg)) here = true;
                        if (!here) continue;
                    }
                    String date = h.optString("date", "");   // 2026-12-25
                    if (date.length() != 10) continue;
                    String name = lang.equals("en") ? h.optString("name") : h.optString("localName", h.optString("name"));
                    if (lang.equals("ar") && !name.matches(".*[\\u0600-\\u06FF].*")) name = h.optString("name", name);
                    if (name == null || name.trim().isEmpty() || name.length() > 80) continue;
                    String k = date.substring(5);
                    ArrayList<String> l = m.get(k);
                    if (l == null) { l = new ArrayList<>(); m.put(k, l); }
                    if (!l.contains(name.trim())) l.add(name.trim());
                }
            } catch (Throwable ignored) { }
            parsed.put(id, m);
            return m;
        }
    }

    /** Fetches this year and the next when they are missing or a month old; {@code done} runs (off the main thread) if anything changed. */
    static void load(final Context ctx, final Runnable done) {
        final Context c = ctx.getApplicationContext();
        final String cc = country(c);
        if (cc.isEmpty() || busy) return;
        final int y = Calendar.getInstance().get(Calendar.YEAR);
        final SharedPreferences p = U.prefs(c);
        final long now = System.currentTimeMillis();
        boolean need = false;
        for (int k = y; k <= y + 1; k++)
            if (now - p.getLong("hol_at_" + cc + "_" + k, 0) > 30L * 86_400_000L) need = true;
        if (!need) return;
        busy = true;
        new Thread(new Runnable() { @Override public void run() {
            boolean changed = false;
            try {
                for (int k = y; k <= y + 1; k++) {
                    if (now - p.getLong("hol_at_" + cc + "_" + k, 0) <= 30L * 86_400_000L) continue;
                    String t;
                    try { t = Weather.text("https://date.nager.at/api/v3/PublicHolidays/" + k + "/" + cc); }
                    catch (java.io.FileNotFoundException none) { t = "[]"; }   // a country the service does not cover
                    t = t == null ? "" : t.trim();
                    if (t.isEmpty()) t = "[]";
                    if (t.length() > 200_000 || !t.startsWith("[")) continue;
                    new JSONArray(t);   // only keep a real answer
                    p.edit().putString("hol_" + cc + "_" + k, t).putLong("hol_at_" + cc + "_" + k, now).apply();
                    changed = true;
                }
            } catch (Throwable ignored) {
            } finally {
                busy = false;
                if (changed) { synchronized (parsed) { parsed.clear(); } if (done != null) done.run(); }
            }
        } }, "holidays").start();
    }

    /** "CA-ON" for Ontario in Canada (from the place name found for the weather), "" when unknown. */
    private static String lastIn = null, lastOut = "";
    static String regionCode(String cc, String region) {
        if (region == null) return "";
        String in = cc + "|" + region;
        if (in.equals(lastIn)) return lastOut;
        lastOut = regionCode0(cc, region); lastIn = in;
        return lastOut;
    }
    private static String regionCode0(String cc, String region) {
        String r = java.text.Normalizer.normalize(region.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.US);
        if (r.isEmpty()) return "";
        if (r.length() == 2) return cc + "-" + r.toUpperCase(Locale.US);
        String[][] t = cc.equals("CA") ? CA : cc.equals("US") ? US : cc.equals("AU") ? AU : cc.equals("DE") ? DE : null;
        if (t == null) return "";
        for (String[] e : t) for (int i = 1; i < e.length; i++) if (r.equals(e[i])) return cc + "-" + e[0];
        return "";
    }
    private static final String[][] CA = {
        {"AB", "alberta"}, {"BC", "british columbia", "colombie-britannique"}, {"MB", "manitoba"},
        {"NB", "new brunswick", "nouveau-brunswick"}, {"NL", "newfoundland and labrador", "terre-neuve-et-labrador"},
        {"NS", "nova scotia", "nouvelle-ecosse"}, {"ON", "ontario"}, {"PE", "prince edward island", "ile-du-prince-edouard"},
        {"QC", "quebec"}, {"SK", "saskatchewan"}, {"NT", "northwest territories", "territoires du nord-ouest"},
        {"NU", "nunavut"}, {"YT", "yukon"}};
    private static final String[][] AU = {
        {"NSW", "new south wales"}, {"VIC", "victoria"}, {"QLD", "queensland"}, {"SA", "south australia"},
        {"WA", "western australia"}, {"TAS", "tasmania"}, {"ACT", "australian capital territory"}, {"NT", "northern territory"}};
    private static final String[][] DE = {
        {"BW", "baden-wurttemberg"}, {"BY", "bavaria", "bayern"}, {"BE", "berlin"}, {"BB", "brandenburg"}, {"HB", "bremen"},
        {"HH", "hamburg"}, {"HE", "hesse", "hessen"}, {"MV", "mecklenburg-vorpommern", "mecklenburg-western pomerania"},
        {"NI", "lower saxony", "niedersachsen"}, {"NW", "north rhine-westphalia", "nordrhein-westfalen"},
        {"RP", "rhineland-palatinate", "rheinland-pfalz"}, {"SL", "saarland"}, {"SN", "saxony", "sachsen"},
        {"ST", "saxony-anhalt", "sachsen-anhalt"}, {"SH", "schleswig-holstein"}, {"TH", "thuringia", "thuringen"}};
    private static final String[][] US = {
        {"AL", "alabama"}, {"AK", "alaska"}, {"AZ", "arizona"}, {"AR", "arkansas"}, {"CA", "california"}, {"CO", "colorado"},
        {"CT", "connecticut"}, {"DE", "delaware"}, {"DC", "district of columbia", "washington, d.c."}, {"FL", "florida"},
        {"GA", "georgia"}, {"HI", "hawaii"}, {"ID", "idaho"}, {"IL", "illinois"}, {"IN", "indiana"}, {"IA", "iowa"},
        {"KS", "kansas"}, {"KY", "kentucky"}, {"LA", "louisiana"}, {"ME", "maine"}, {"MD", "maryland"},
        {"MA", "massachusetts"}, {"MI", "michigan"}, {"MN", "minnesota"}, {"MS", "mississippi"}, {"MO", "missouri"},
        {"MT", "montana"}, {"NE", "nebraska"}, {"NV", "nevada"}, {"NH", "new hampshire"}, {"NJ", "new jersey"},
        {"NM", "new mexico"}, {"NY", "new york"}, {"NC", "north carolina"}, {"ND", "north dakota"}, {"OH", "ohio"},
        {"OK", "oklahoma"}, {"OR", "oregon"}, {"PA", "pennsylvania"}, {"RI", "rhode island"}, {"SC", "south carolina"},
        {"SD", "south dakota"}, {"TN", "tennessee"}, {"TX", "texas"}, {"UT", "utah"}, {"VT", "vermont"},
        {"VA", "virginia"}, {"WA", "washington"}, {"WV", "west virginia"}, {"WI", "wisconsin"}, {"WY", "wyoming"}};
}
