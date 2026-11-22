package com.velthon.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

/** The one-time setup the Velthon lock needs on phones that installed it from a file (not the Play Store). */
final class LockSetup {
    private LockSetup() {}

    static void show(final Activity a) {
        if (LockSaver.ready(a)) {
            new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(L.t("Velthon lock screen")).setMessage(L.t("Ready: the Velthon window comes up as soon as you wake the phone."))
                    .setPositiveButton(L.t("Done"), null).show();
            return;
        }
        String msg = L.t("Android puts its own lock screen first unless Velthon is allowed to open before it. Because Velthon was installed from a file, Android hides that switch until you unlock it once:")
                + "\n\n1.  " + L.t("Tap \"App info\" below, then the ⋮ menu at the top right, then \"Allow restricted settings\" (confirm with your fingerprint or PIN).")
                + "\n\n2.  " + L.t("Come back here and tap \"Display over other apps\", then turn it on for Velthon.")
                + "\n\n" + L.t("If the ⋮ menu has no \"Allow restricted settings\", first try step 2 once: Android then shows the option.");
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(L.t("Set up the Velthon lock screen"))
                .setMessage(msg)
                .setPositiveButton(L.t("Display over other apps"), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        try { a.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + a.getPackageName()))); }
                        catch (Throwable t) { try { a.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)); } catch (Throwable ignored) { } }
                    } })
                .setNeutralButton(L.t("App info"), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        try { a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.getPackageName()))); }
                        catch (Throwable ignored) { }
                    } })
                .setNegativeButton(L.t("Not now"), null).show();
    }
}
