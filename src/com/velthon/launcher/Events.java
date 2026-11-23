package com.velthon.launcher;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.CalendarContract;
import android.text.format.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

/** The next events from the phone's calendars (needs the calendar permission), as short lines for the lock screen. */
final class Events {
    private Events() { }

    static boolean allowed(Context c) {
        return Build.VERSION.SDK_INT < 23 || c.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    private static long cachedAt; private static ArrayList<String[]> cached = new ArrayList<>();

    /** Up to {@code n} events that have not ended, within 14 days: {title, "Today 3:00 PM"}. */
    static ArrayList<String[]> next(Context c, int n) {
        long now = System.currentTimeMillis();
        if (now - cachedAt < 60_000L) return cached;
        ArrayList<String[]> out = new ArrayList<>();
        if (!allowed(c) || !U.prefs(c).getString("lock_events", "on").equals("on")) { cached = out; cachedAt = now; return out; }
        out = upcoming(c, n, 14);
        cached = out; cachedAt = now;
        return out;
    }

    /** Up to {@code n} of your own events that have not ended, within {@code days} days (no cache, any setting). */
    static ArrayList<String[]> upcoming(Context c, int n, int days) {
        long now = System.currentTimeMillis();
        ArrayList<String[]> out = new ArrayList<>();
        if (!allowed(c)) return out;
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, now - 3 * 86_400_000L);     // far enough back to catch long and all-day events still going
        ContentUris.appendId(b, now + days * 86_400_000L);
        String[] cols = {CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY};
        boolean ar = U.langs(c).equals("ar");
        try (Cursor q = c.getContentResolver().query(b.build(), cols,
                CalendarContract.Instances.END + " > ? AND " + CalendarContract.Instances.VISIBLE + " = 1",
                new String[] {String.valueOf(now)}, CalendarContract.Instances.BEGIN + " ASC")) {
            ArrayList<String[]> live = new ArrayList<>(), coming = new ArrayList<>();
            if (q != null) while (q.moveToNext() && coming.size() < n) {
                String title = q.getString(0);
                if (title == null || title.trim().isEmpty()) title = L.in(U.langs(c), "Event");
                long begin = q.getLong(1), end = q.getLong(2);
                boolean allDay = q.getInt(3) == 1;
                if (allDay) {   // all-day events are stored in UTC midnight
                    Calendar u = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")); u.setTimeInMillis(begin);
                    Calendar l = Calendar.getInstance(); l.clear();
                    l.set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH));
                    begin = l.getTimeInMillis();
                }
                if (allDay) {   // the end too
                    Calendar u = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")); u.setTimeInMillis(end);
                    Calendar l = Calendar.getInstance(); l.clear();
                    l.set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH));
                    end = l.getTimeInMillis();
                    if (end <= now) continue;
                }
                if (begin <= now) {   // happening now: first, with when it ends
                    String until = allDay ? L.in(U.langs(c), "All day")
                            : L.in(U.langs(c), "until ") + DateFormat.getTimeFormat(c).format(new java.util.Date(end));
                    live.add(new String[] {title.trim(), L.in(U.langs(c), "Now  ·  ") + until, "live"});
                } else coming.add(new String[] {title.trim(), when(c, begin, allDay, ar)});
            }
            out.addAll(live); out.addAll(coming);
            while (out.size() > n) out.remove(out.size() - 1);
        } catch (Throwable ignored) { }
        return out;
    }

    private static String when(Context c, long t, boolean allDay, boolean ar) {
        Calendar at = Calendar.getInstance(); at.setTimeInMillis(t);
        Calendar today = Calendar.getInstance();
        int dd = dayIndex(at) - dayIndex(today);
        String day = dd <= 0 ? L.in(U.langs(c), "Today") : dd == 1 ? L.in(U.langs(c), "Tomorrow")
                : new java.text.SimpleDateFormat(dd < 7 ? "EEE" : DateFormat.getBestDateTimePattern(U.langLocale(c), "EEEdMMM"), U.langLocale(c)).format(at.getTime());
        if (allDay) return day;
        return day + " " + DateFormat.getTimeFormat(c).format(at.getTime());
    }
    private static int dayIndex(Calendar c) { return c.get(Calendar.YEAR) * 400 + c.get(Calendar.DAY_OF_YEAR); }

    /** Every event of a month, by day of month: {title, time or "All day"}. Empty without calendar permission. */
    static java.util.HashMap<Integer, ArrayList<String[]>> month(Context c, int year, int month0) {
        java.util.HashMap<Integer, ArrayList<String[]>> out = new java.util.HashMap<>();
        if (!allowed(c)) return out;
        Calendar from = Calendar.getInstance(); from.clear(); from.set(year, month0, 1);
        Calendar to = (Calendar) from.clone(); to.add(Calendar.MONTH, 1);
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, from.getTimeInMillis() - 86_400_000L);
        ContentUris.appendId(b, to.getTimeInMillis() + 86_400_000L);
        String[] cols = {CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY};
        try (Cursor q = c.getContentResolver().query(b.build(), cols, CalendarContract.Instances.VISIBLE + " = 1", null, CalendarContract.Instances.BEGIN + " ASC")) {
            if (q != null) while (q.moveToNext()) {
                String title = q.getString(0);
                if (title == null || title.trim().isEmpty()) title = L.in(U.langs(c), "Event");
                long begin = q.getLong(1), end = q.getLong(2);
                boolean allDay = q.getInt(3) == 1;
                Calendar s = Calendar.getInstance(allDay ? java.util.TimeZone.getTimeZone("UTC") : java.util.TimeZone.getDefault());
                s.setTimeInMillis(begin);
                Calendar en = Calendar.getInstance(allDay ? java.util.TimeZone.getTimeZone("UTC") : java.util.TimeZone.getDefault());
                en.setTimeInMillis(Math.max(begin, end - 1));
                // an event over several days shows on each of them
                Calendar d = Calendar.getInstance(); d.clear(); d.set(s.get(Calendar.YEAR), s.get(Calendar.MONTH), s.get(Calendar.DAY_OF_MONTH));
                Calendar last = Calendar.getInstance(); last.clear(); last.set(en.get(Calendar.YEAR), en.get(Calendar.MONTH), en.get(Calendar.DAY_OF_MONTH));
                for (int guard = 0; !d.after(last) && guard < 62; guard++) {
                    if (d.get(Calendar.YEAR) == year && d.get(Calendar.MONTH) == month0) {
                        String when = allDay ? L.in(U.langs(c), "All day") : DateFormat.getTimeFormat(c).format(new java.util.Date(begin));
                        ArrayList<String[]> l = out.get(d.get(Calendar.DAY_OF_MONTH));
                        if (l == null) { l = new ArrayList<>(); out.put(d.get(Calendar.DAY_OF_MONTH), l); }
                        l.add(new String[] {title.trim(), when});
                    }
                    d.add(Calendar.DAY_OF_MONTH, 1);
                }
            }
        } catch (Throwable ignored) { }
        return out;
    }
}
