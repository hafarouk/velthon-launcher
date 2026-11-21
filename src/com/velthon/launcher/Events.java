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
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, now - 3 * 86_400_000L);     // far enough back to catch long and all-day events still going
        ContentUris.appendId(b, now + 14 * 86_400_000L);
        String[] cols = {CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY};
        boolean ar = U.langs(c).equals("ar");
        try (Cursor q = c.getContentResolver().query(b.build(), cols,
                CalendarContract.Instances.END + " > ? AND " + CalendarContract.Instances.VISIBLE + " = 1",
                new String[] {String.valueOf(now)}, CalendarContract.Instances.BEGIN + " ASC")) {
            ArrayList<String[]> live = new ArrayList<>(), coming = new ArrayList<>();
            if (q != null) while (q.moveToNext() && coming.size() < n) {
                String title = q.getString(0);
                if (title == null || title.trim().isEmpty()) title = L.t("Event");
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
                    String until = allDay ? L.t("All day")
                            : L.t("until ") + DateFormat.getTimeFormat(c).format(new java.util.Date(end));
                    live.add(new String[] {title.trim(), L.t("Now  ·  ") + until, "live"});
                } else coming.add(new String[] {title.trim(), when(c, begin, allDay, ar)});
            }
            out.addAll(live); out.addAll(coming);
            while (out.size() > n) out.remove(out.size() - 1);
        } catch (Throwable ignored) { }
        cached = out; cachedAt = now;
        return out;
    }

    private static String when(Context c, long t, boolean allDay, boolean ar) {
        Calendar at = Calendar.getInstance(); at.setTimeInMillis(t);
        Calendar today = Calendar.getInstance();
        int dd = dayIndex(at) - dayIndex(today);
        String day = dd <= 0 ? L.t("Today") : dd == 1 ? L.t("Tomorrow")
                : new java.text.SimpleDateFormat(dd < 7 ? "EEE" : "EEE d MMM", Locale.getDefault()).format(at.getTime());
        if (allDay) return day;
        return day + " " + DateFormat.getTimeFormat(c).format(at.getTime());
    }
    private static int dayIndex(Calendar c) { return c.get(Calendar.YEAR) * 400 + c.get(Calendar.DAY_OF_YEAR); }
}
