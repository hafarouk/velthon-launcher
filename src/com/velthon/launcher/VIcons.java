package com.velthon.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Velthon's own icons for the main apps. The app that the phone uses for each job (its dialer, its
 * messages app, its browser...) is found from the system, so it works on any phone brand.
 */
final class VIcons {
    private VIcons() { }

    static void apply(Context c, List<Launcher.App> apps) {
        PackageManager pm = c.getPackageManager();
        HashMap<String, String> byPkg = new HashMap<>();
        role(pm, byPkg, new Intent(Intent.ACTION_DIAL), "phone");
        role(pm, byPkg, new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")), "messages");
        role(pm, byPkg, new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")), "browser");
        role(pm, byPkg, new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "camera");
        role(pm, byPkg, main(Intent.CATEGORY_APP_CONTACTS), "contacts");
        role(pm, byPkg, main(Intent.CATEGORY_APP_GALLERY), "gallery");
        role(pm, byPkg, main(Intent.CATEGORY_APP_CALENDAR), "calendar");
        role(pm, byPkg, main(Intent.CATEGORY_APP_EMAIL), "mail");
        role(pm, byPkg, main(Intent.CATEGORY_APP_MAPS), "maps");
        role(pm, byPkg, main(Intent.CATEGORY_APP_MUSIC), "music");
        role(pm, byPkg, main(Intent.CATEGORY_APP_CALCULATOR), "calculator");
        role(pm, byPkg, new Intent(AlarmClock.ACTION_SHOW_ALARMS), "clock");
        role(pm, byPkg, new Intent(Settings.ACTION_SETTINGS), "settings");
        role(pm, byPkg, main(Intent.CATEGORY_APP_MARKET), "store");

        HashMap<String, Bitmap> cache = new HashMap<>();
        java.util.HashSet<String> used = new java.util.HashSet<>();
        // 1) the apps the phone uses for each job
        for (Launcher.App a : apps) {
            String name = byPkg.get(a.cn.getPackageName());
            if (name != null && !used.contains(name + "|" + a.cn.getPackageName())) {
                if (set(c, a, name, cache)) used.add(name + "|" + a.cn.getPackageName());
            }
        }
        // 2) common system apps recognised by their name, when no app took that icon yet
        java.util.HashSet<String> taken = new java.util.HashSet<>(byPkg.values());
        for (Launcher.App a : apps) {
            if (byPkg.containsKey(a.cn.getPackageName())) continue;
            String l = a.label.toLowerCase(Locale.ROOT), p = a.cn.getPackageName();
            String name = null;
            if (p.contains("documentsui") || l.equals("files") || l.equals("my files") || l.equals("file manager")) name = "files";
            else if (l.equals("notes") || l.equals("samsung notes") || l.equals("keep notes") || l.equals("memo")) name = "notes";
            else if (l.equals("weather")) name = "weather";
            else if (l.equals("clock") && !taken.contains("clock")) name = "clock";
            else if (l.equals("calculator") && !taken.contains("calculator")) name = "calculator";
            else if (l.equals("contacts") && !taken.contains("contacts")) name = "contacts";
            else if ((l.equals("gallery") || l.equals("photos")) && !taken.contains("gallery")) name = "gallery";
            else if (l.equals("calendar") && !taken.contains("calendar")) name = "calendar";
            if (name != null && set(c, a, name, cache)) taken.add(name);
        }
    }

    private static Intent main(String category) { return Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category); }

    private static void role(PackageManager pm, HashMap<String, String> out, Intent i, String name) {
        try {
            ResolveInfo r = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            String pkg = r == null || r.activityInfo == null ? null : r.activityInfo.packageName;
            if (pkg == null || pkg.equals("android")) {        // no default chosen yet: the first app that does it
                List<ResolveInfo> all = pm.queryIntentActivities(i, 0);
                pkg = all.isEmpty() ? null : all.get(0).activityInfo.packageName;
            }
            if (pkg != null && !out.containsKey(pkg)) out.put(pkg, name);
        } catch (Throwable ignored) { }
    }

    private static boolean set(Context c, Launcher.App a, String name, HashMap<String, Bitmap> cache) {
        Bitmap b = cache.get(name);
        if (b == null) { b = U.bitmap(c, "vicons/" + name + ".png"); if (b == null) return false; cache.put(name, b); }
        a.icon = new BitmapDrawable(c.getResources(), b);
        return true;
    }
}
