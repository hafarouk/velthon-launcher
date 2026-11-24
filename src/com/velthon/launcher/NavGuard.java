package com.velthon.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

/** Velthon never changes the phone's own settings. One exception, on request: Xiaomi (MIUI / HyperOS) switches the
 *  phone from full-screen gestures to buttons when another home screen is chosen. If the phone used gestures, and
 *  the person allows it once (a permission given from a computer), Velthon puts gestures back, and keeps them. */
final class NavGuard {
    private NavGuard() {}
    private static final String KEY = "force_fsg_nav_bar";       // Xiaomi's own switch: 1 = full-screen gestures
    private static final String PERM = "android.permission.WRITE_SECURE_SETTINGS";

    static boolean xiaomi() {
        String m = (Build.MANUFACTURER + " " + Build.BRAND).toLowerCase(java.util.Locale.ROOT);
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco");
    }
    /** Xiaomi's gesture switch, or -1 when the phone has none. */
    static int xiaomiGestures(Context c) {
        try { return Settings.Global.getInt(c.getContentResolver(), KEY); } catch (Throwable t) { return -1; }
    }
    static boolean allowed(Context c) {
        return Build.VERSION.SDK_INT < 23 || c.checkSelfPermission(PERM) == PackageManager.PERMISSION_GRANTED;
    }
    /** Remembers how the phone navigated before Xiaomi changed it (first look), and restores gestures if wanted. */
    static void check(Context c) {
        if (!xiaomi()) return;
        int now = xiaomiGestures(c);
        if (now < 0) return;
        android.content.SharedPreferences p = U.prefs(c);
        if (!p.contains("nav_first")) p.edit().putInt("nav_first", now).apply();
        if (p.getString("nav_want", "").equals("gestures") && now != 1 && allowed(c)) {
            try { Settings.Global.putInt(c.getContentResolver(), KEY, 1); } catch (Throwable ignored) { }
        }
    }
    /** On a Xiaomi now on buttons: asks once whether to keep full-screen gestures. */
    static void askOnce(final Activity a) {
        if (!xiaomi() || xiaomiGestures(a) != 0 || U.prefs(a).contains("nav_want")) return;
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(L.t("Full-screen gestures"))
                .setMessage(L.t("Xiaomi switches to navigation buttons whenever another home screen is used. Velthon can keep your full-screen gestures, after a one-time permission."))
                .setPositiveButton(L.t("Keep gestures"), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        U.prefs(a).edit().putString("nav_want", "gestures").apply();
                        if (allowed(a)) check(a); else guide(a);
                    } })
                .setNegativeButton(L.t("Keep buttons"), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) { U.prefs(a).edit().putString("nav_want", "buttons").apply(); } })
                .show();
    }
    /** How to give the permission (once), and the other way. */
    static void guide(final Activity a) {
        String cmd = "adb shell pm grant " + a.getPackageName() + " " + PERM;
        String msg = L.t("Once, from a computer:") + "\n\n"
                + "1. " + L.t("Settings › About phone › tap \"OS version\" 7 times.") + "\n"
                + "2. " + L.t("Settings › Additional settings › Developer options: turn on USB debugging and USB debugging (Security settings).") + "\n"
                + "3. " + L.t("Connect the phone to a computer with Android platform-tools and run:") + "\n\n" + cmd + "\n\n"
                + L.t("Velthon then turns full-screen gestures back on, and keeps them on. You can turn USB debugging off again afterwards.") + "\n\n"
                + L.t("Without a computer: Settings › Special features › Second space. In the second space choose Velthon as the home screen, switch back, and turn on full-screen gestures. The recent apps screen may not work this way.");
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(L.t("Keep full-screen gestures"))
                .setMessage(msg)
                .setPositiveButton(L.t("Copy the command"), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        try {
                            android.content.ClipboardManager cb = (android.content.ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                            cb.setPrimaryClip(android.content.ClipData.newPlainText("adb", cmd));
                            android.widget.Toast.makeText(a, L.t("Copied"), android.widget.Toast.LENGTH_SHORT).show();
                        } catch (Throwable ignored) { }
                    } })
                .setNegativeButton(L.t("Close"), null)
                .show();
    }
}
