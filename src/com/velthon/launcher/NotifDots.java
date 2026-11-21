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
    @Override public void onNotificationPosted(StatusBarNotification n) { refresh(); }
    @Override public void onNotificationRemoved(StatusBarNotification n) { refresh(); }

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
