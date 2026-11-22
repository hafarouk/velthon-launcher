package com.velthon.launcher;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.HashSet;

/** Notification dots: which apps have something waiting. The phone lets this run once you allow
 *  "Notification access" for Velthon (Settings › Home screen › Notification dots). */
public class NotifDots extends NotificationListenerService {
    private static final HashSet<String> WAITING = new HashSet<>();
    static Runnable onChange;
    static boolean has(String pkg) { synchronized (WAITING) { return pkg != null && WAITING.contains(pkg); } }

    @Override public void onListenerConnected() { refresh(); }
    @Override public void onListenerDisconnected() { synchronized (WAITING) { WAITING.clear(); } changed(); }
    // Performance: a call or a chat app updates its notifications many times a second (call timers, "typing…").
    // Each update used to ask the system for every notification on the phone; now an update only changes the
    // set when it matters, and the full re-count runs at most once every few seconds, off the busy moment.
    private final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
    private long lastFull;
    private final Runnable full = new Runnable() { @Override public void run() { lastFull = android.os.SystemClock.uptimeMillis(); refresh(); } };
    @Override public void onNotificationPosted(StatusBarNotification n) {
        if (n == null || n.isOngoing() || !n.isClearable()) return;                       // calls, music, downloads: no dot, nothing to do
        if ((n.getNotification().flags & android.app.Notification.FLAG_GROUP_SUMMARY) != 0) return;
        boolean added; synchronized (WAITING) { added = WAITING.add(n.getPackageName()); }
        if (added) changed();
    }
    @Override public void onNotificationRemoved(StatusBarNotification n) {
        if (n != null && n.isOngoing()) return;
        h.removeCallbacks(full);
        long wait = Math.max(600, 4000 - (android.os.SystemClock.uptimeMillis() - lastFull));
        h.postDelayed(full, wait);
    }

    private void refresh() {
        HashSet<String> now = new HashSet<>();
        try {
            StatusBarNotification[] all = getActiveNotifications();
            if (all != null) for (StatusBarNotification n : all) {
                if (n.isOngoing() || !n.isClearable()) continue;          // music, navigation, downloads: no dot
                if ((n.getNotification().flags & android.app.Notification.FLAG_GROUP_SUMMARY) != 0) continue;
                now.add(n.getPackageName());
            }
        } catch (Throwable ignored) { }
        boolean diff;
        synchronized (WAITING) { diff = !now.equals(WAITING); WAITING.clear(); WAITING.addAll(now); }
        if (diff) changed();
    }
    private static void changed() { Runnable r = onChange; if (r != null) r.run(); }
}
