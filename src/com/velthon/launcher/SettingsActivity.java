package com.velthon.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Calendar;

/**
 * Settings, laid out like the TV screensaver's (Scripture, Atmosphere, Sound & voice, Weather & calendar ...),
 * drawn in the dock's style: dimmed panels without borders, each with a notch holding a gold circle.
 */
public class SettingsActivity extends Activity {
    private static final int PICK_IMAGE = 41;
    private Scene scene;
    private LinearLayout list;
    private FrameLayout layer;
    private int top, bottom;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private int dp(float v) { return U.dpi(this, v); }
    private SharedPreferences p() { return U.prefs(this); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        U.clearBars(getWindow());   // no dark band: the picture runs behind the status bar
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        scene = new Scene(this);
        View dim = new View(this);
        dim.setBackgroundColor(0x59060A18);
        scene.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        ScrollView sv = new ScrollView(this);
        sv.setClipToPadding(false);
        sv.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        sv.addView(list, new FrameLayout.LayoutParams(-1, -2));
        scene.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        layer = new FrameLayout(this);
        layer.setVisibility(View.GONE);
        scene.addView(layer, new FrameLayout.LayoutParams(-1, -1));
        setContentView(scene);
        scene.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                top = in.getSystemWindowInsetTop(); bottom = in.getSystemWindowInsetBottom();
                list.setPadding(dp(14), top + dp(14), dp(14), bottom + dp(24));
                return in;
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        String ps = p().getString("pending_scale", null);
        if (ps != null && (Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(this))) {
            p().edit().remove("pending_scale").apply();
            try { Settings.System.putFloat(getContentResolver(), Settings.System.FONT_SCALE, Float.parseFloat(ps)); } catch (Throwable ignored) { }
        }
        scene.refresh(); build();
    }

    @Override public void onBackPressed() {
        if (layer.getVisibility() == View.VISIBLE) { closeSheet(); return; }
        super.onBackPressed();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    // ---------------------------------------------------------------- the page
    private void build() {
        list.removeAllViews();
        header();

        section(Ico.BOOK, L.t("Scripture"), L.t("What appears on screen and how often"),
                choice(L.t("Verse language"), "langs", U.langs(this), new String[] {L.t("English"), L.t("Arabic")}, new String[] {"en", "ar"}),
                choice(L.t("Verse changes"), "verse_every", "5", new String[] {L.t("Every 5 minutes"), L.t("Every 15 minutes"), L.t("Every 30 minutes"), L.t("Every hour"), L.t("Once a day")},
                        new String[] {"5", "15", "30", "60", "1440"}),
                choice(L.t("Verse font"), "verse_font", "system", new String[] {L.t("The phone's font"), L.t("Classic serif")}, new String[] {"system", "serif"}),
                choice(L.t("Next-verse countdown on the home screen"), "countdown", "off", new String[] {L.t("Hidden (more room for apps)"), L.t("Shown")}, new String[] {"off", "on"}),
                choice(L.t("Verse text size"), "text_scale", "auto", new String[] {L.t("Auto fit"), L.t("Smaller"), L.t("Larger")}, new String[] {"auto", "small", "large"}),
                choice(L.t("Feast-day verses"), "feast_verses", "on", new String[] {L.t("On"), L.t("Off")}, new String[] {"on", "off"}),
                action(L.t("Saved verses"), L.t("Your favourites and all verses"), new Runnable() { @Override public void run() {
                    startActivity(new Intent(SettingsActivity.this, VersesActivity.class)); } }));

        section(Ico.LEAF, L.t("Atmosphere"), L.t("The picture, the season and the window"),
                backgrounds(),
                choice(L.t("Season theme"), "season", "auto", new String[] {L.t("Automatic (by date)"), L.t("Spring"), L.t("Summer"), L.t("Autumn"), L.t("Winter")},
                        new String[] {"auto", "spring", "summer", "fall", "winter"}),
                
                choice(L.t("Weather motion (rain, snow, sun rays, stars)"), "wx_fx", "on", new String[] {L.t("On"), L.t("Off")}, new String[] {"on", "off"}),
                choice(L.t("Animations"), "anims", "on", new String[] {L.t("Light (recommended)"), L.t("Off")}, new String[] {"on", "off"}),
                
                
                choice(L.t("Night mode"), "night_mode", "off", new String[] {L.t("Off"), "10 PM – 6 AM", "9 PM – 7 AM"}, new String[] {"off", "22", "21"}));

        String city = p().getString("city", "");
        section(Ico.CLOUD, L.t("Weather & calendar"), L.t("Forecast, alerts and church feasts"),
                action(L.t("Weather city"), city.isEmpty() ? L.t("Your location") : city, new Runnable() { @Override public void run() { askCity(); } }),
                choice(L.t("Temperature"), "units", "auto", new String[] {L.t("Automatic"), L.t("Celsius (°C)"), L.t("Fahrenheit (°F)")}, new String[] {"auto", "c", "f"}),
                choice(L.t("Weather alerts"), "wx_alerts", "on", new String[] {L.t("On"), L.t("Off")}, new String[] {"on", "off"}),
                choice(L.t("Church calendar"), "church", "orthodox", new String[] {L.t("Coptic Orthodox"), L.t("Catholic"), L.t("Off")}, new String[] {"orthodox", "catholic", "off"}));

        section(Ico.HOME, L.t("Home screen"), L.t("Apps, icons and widgets"),
                choice(L.t("App icons"), "@velthon_icons", p().getBoolean("velthon_icons", false) ? "1" : "0",
                        new String[] {L.t("The phone's own"), L.t("Velthon line icons")}, new String[] {"0", "1"}),
                choice(L.t("Icon style"), "icon_style", "glass", new String[] {L.t("Glass (symbol on frosted glass)"), L.t("Classic (full app icon)")}, new String[] {"glass", "classic"}),
                choice(L.t("Columns"), "@columns", String.valueOf(p().getInt("columns", 3)), new String[] {"3 (largest icons)", "4 (bigger icons)", "5 (more apps)"}, new String[] {"3", "4", "5"}),
                choice(L.t("Phone text size"), "@font_scale", fontScale(), new String[] {L.t("Small"), L.t("Default"), L.t("Large"), L.t("Larger"), L.t("Largest"), L.t("Huge")},
                        new String[] {"0.85", "1.0", "1.15", "1.3", "1.5", "1.8"}),
                choice(L.t("Notification dots"), "dots", "on", new String[] {L.t("On"), L.t("Off")}, new String[] {"on", "off"}),
                action(L.t("Hidden apps"), L.t("Choose apps to keep out of the app list"), new Runnable() { @Override public void run() {
                    p().edit().putBoolean("want_hidden", true).apply(); finish(); } }),
                action(L.t("Add a widget"), null, new Runnable() { @Override public void run() {
                    p().edit().putBoolean("want_widget", true).apply(); finish(); } }),
                action(L.t("Make Velthon my home screen"), null, new Runnable() { @Override public void run() { open(Settings.ACTION_HOME_SETTINGS); } }),
                action(L.t("Phone settings"), L.t("The phone's own settings (Wi-Fi, sound, display…)"), new Runnable() { @Override public void run() { open(Settings.ACTION_SETTINGS); } }));

        String[] gn = {L.t("Second home page"), L.t("Coptic calendar"), L.t("Search apps"), L.t("Recent apps & VIP"), L.t("All apps"), L.t("Notifications"), L.t("Quick settings"), L.t("Verses"), L.t("Screensaver"), L.t("Weather"), L.t("Lock the phone"), L.t("Launcher settings"), L.t("Phone settings"), L.t("Nothing")};
        String[] gv = {"page", "church", "search", "quick", "apps", "notifications", "quick_settings", "verses", "saver", "weather", "lock", "settings", "phone_settings", "none"};
        section(Ico.HOME, L.t("Gestures"), L.t("Swipe anywhere on the home screen"),
                choice(L.t("Swipe up"), "swipe_up", "search", gn, gv),
                choice(L.t("Swipe down"), "swipe_down", "quick", gn, gv),
                choice(L.t("Swipe left"), "swipe_left", "church", gn, gv),
                choice(L.t("Swipe right"), "swipe_right", "page", gn, gv),
                choice(L.t("Double tap"), "double_tap", "lock", new String[] {L.t("Lock the phone"), L.t("Screensaver"), L.t("All apps"), L.t("Nothing")}, new String[] {"lock", "saver", "apps", "none"}));

        section(Ico.MOON, L.t("Screensaver"), L.t("The window, while the phone rests or charges"),
                choice(L.t("On the lock screen"), "lock_saver", "on", new String[] {L.t("On"), L.t("Off")}, new String[] {"on", "off"}),
                choice(L.t("Next calendar events under the verse"), "lock_events", "on", new String[] {L.t("On"), L.t("Off")}, new String[] {"on", "off"}),
                action(L.t("Allow full-screen notifications"), LockSaver.fullScreenAllowed(this) ? L.t("Allowed") : L.t("Needed so the window opens on the lock screen by itself"),
                        new Runnable() { @Override public void run() {
                            try { startActivity(new Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT", android.net.Uri.parse("package:" + getPackageName()))); }
                            catch (Throwable t) {
                                try { startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())); }
                                catch (Throwable t2) { open(Settings.ACTION_SETTINGS); }
                            }
                        } }),
                action(L.t("Allow it over the lock screen"), Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this) ? "Needed once: \"Display over other apps\"" : L.t("Allowed"),
                        new Runnable() { @Override public void run() { askOverlay(); } }),
                
                action(L.t("Make it my screensaver"), "Choose \"Velthon\" in the list", new Runnable() { @Override public void run() {
                    if (!open(Settings.ACTION_DREAM_SETTINGS)) open(Settings.ACTION_DISPLAY_SETTINGS);
                    Toast.makeText(SettingsActivity.this, "Choose \"Velthon\" as the screen saver", Toast.LENGTH_LONG).show(); } }),
                action(L.t("Preview"), null, new Runnable() { @Override public void run() {
                    startActivity(new Intent(SettingsActivity.this, PreviewActivity.class)); } }));

        section(Ico.GEAR, L.t("Backup & uninstall"), L.t("Save your set-up, bring it back or remove Velthon"),
                action(L.t("Back up settings"), L.t("Save your layout, folders, VIP and choices to a file on the phone"), new Runnable() { @Override public void run() {
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
                            .putExtra(Intent.EXTRA_TITLE, "Velthon-backup-" + new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date()) + ".json");
                    try { startActivityForResult(i, SAVE_BACKUP); } catch (Throwable t) { Toast.makeText(SettingsActivity.this, "Can't open the file picker", Toast.LENGTH_SHORT).show(); } } }),
                action(L.t("Restore settings"), L.t("Bring back a saved set-up"), new Runnable() { @Override public void run() {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                    try { startActivityForResult(i, LOAD_BACKUP); } catch (Throwable t) { Toast.makeText(SettingsActivity.this, "Can't open the file picker", Toast.LENGTH_SHORT).show(); } } }),
                action(L.t("Uninstall Velthon"), L.t("Removes the launcher and everything it saved on this phone"), new Runnable() { @Override public void run() {
                    // uninstalling is where the clean-up happens: Velthon's own data is wiped first, so nothing is left behind
                    new android.app.AlertDialog.Builder(SettingsActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle(L.t("Uninstall Velthon"))
                            .setMessage(L.t("Velthon will be removed and its saved data cleaned up, so nothing is left on the phone. Your backup files stay where you saved them."))
                            .setPositiveButton(L.t("Uninstall and clean up"), new android.content.DialogInterface.OnClickListener() { @Override public void onClick(android.content.DialogInterface d, int w) {
                                Backup.cleanUp(SettingsActivity.this);
                                Backup.resetAll(SettingsActivity.this);
                                try { startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + getPackageName()))); }
                                catch (Throwable t) { open(Settings.ACTION_APPLICATION_SETTINGS); } } })
                            .setNeutralButton(L.t("Back up first"), new android.content.DialogInterface.OnClickListener() { @Override public void onClick(android.content.DialogInterface d, int w) {
                                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
                                        .putExtra(Intent.EXTRA_TITLE, "Velthon-backup-" + new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date()) + ".json");
                                try { startActivityForResult(i, SAVE_BACKUP); } catch (Throwable ignored) { } } })
                            .setNegativeButton(L.t("Cancel"), null).show(); } }));

        footer();
    }

    private static final int SAVE_BACKUP = 42, LOAD_BACKUP = 43;
    private void confirm(String title, String msg, final Runnable yes) {
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(title).setMessage(msg)
                .setPositiveButton(L.t("Continue"), new android.content.DialogInterface.OnClickListener() { @Override public void onClick(android.content.DialogInterface d, int w) { yes.run(); } })
                .setNegativeButton(L.t("Cancel"), null).show();
    }
    private void restartLauncher() {
        Intent h = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        h.setPackage(getPackageName());
        try { startActivity(h); } catch (Throwable ignored) { }
        finishAffinity();
        android.os.Process.killProcess(android.os.Process.myPid());   // start clean, with the new settings
    }

    private void header() {
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(6), dp(4), dp(6), dp(4));
        ImageView back = new ImageView(this);
        back.setImageDrawable(new Ico(Ico.BACK, Color.WHITE));
        back.setPadding(dp(8), dp(8), dp(8), dp(8));
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { onBackPressed(); } });
        h.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(6), 0, 0, 0);
        TextView a = text(24, Color.WHITE, U.serif(this));
        a.setText(L.t("Settings"));
        t.addView(a);
        TextView s = text(12.5f, 0xCCFFFFFF, U.light());
        s.setText("Velthon Launcher  ·  scripture for your home screen");
        t.addView(s);
        h.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView hl = new ImageView(this);
        hl.setImageBitmap(U.bitmap(this, "logo_small.png"));
        h.addView(hl, new LinearLayout.LayoutParams(dp(40), dp(40)));
        list.addView(h, new LinearLayout.LayoutParams(-1, -2));
    }

    private void credits() {
        String v = "";
        try { v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Throwable ignored) { }
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(L.t("Credits"))
                .setMessage("Scripture: public domain texts.\nWeather: Open-Meteo.\nWeather icons: Meteocons (MIT).\n"
                        + "Fonts: Playfair Display, Source Sans 3, Sofia Sans Extra Condensed (SIL OFL).\n\nVersion " + v)
                .setPositiveButton("OK", null).show();
    }
    private void footer() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setGravity(Gravity.CENTER_HORIZONTAL);
        f.setPadding(dp(10), dp(26), dp(10), 0);
        ImageView logo = new ImageView(this);
        logo.setImageBitmap(U.bitmap(this, "velthon_logo.png"));   // the VELTHON company mark
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        f.addView(logo, new LinearLayout.LayoutParams(dp(78), dp(80)));
        TextView d = text(13.5f, 0xF2FFFFFF, U.serif(this));
        d.setGravity(Gravity.CENTER);
        d.setPadding(0, dp(10), 0, 0);
        d.setText("Developed by VELTHON Design & Development");
        f.addView(d);
        TextView c = text(11, 0x99FFFFFF, U.light());
        c.setGravity(Gravity.CENTER);
        c.setPadding(0, dp(6), 0, dp(8));
        c.setText("© " + Calendar.getInstance().get(Calendar.YEAR) + " VELTHON Design & Development. All rights reserved.");
        // the licences stay one tap away (Open-Meteo and the fonts ask to be credited)
        c.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { credits(); } });
        f.addView(c);
        list.addView(f, new LinearLayout.LayoutParams(-1, -2));
    }

    // ---------------------------------------------------------------- a section: dimmed panel, notch, gold circle
    private void section(int icon, String title, String sub, View... rows) {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setClipChildren(false);
        final Glass card = new Glass(this, 26, Glass.WARM);
        card.setOrientation(LinearLayout.VERTICAL);
        card.notchR = dp(31);
        card.notchCy = -dp(9);
        card.setPadding(0, dp(10), 0, dp(6));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(86), 0, dp(16), dp(6));
        TextView t = text(18, Color.WHITE, U.serif(this));
        t.setText(title);
        head.addView(t);
        TextView s = text(12, 0xB3FFFFFF, U.light());
        s.setText(sub);
        head.addView(s);
        card.addView(head);
        for (View r : rows) card.addView(r, new LinearLayout.LayoutParams(-1, -2));
        card.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int tt, int r, int bb, int ol, int ot, int or, int ob) {
                if (r - l > 0) card.setNotch(dp(46) / (float) (r - l));
            }
        });
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-1, -2);
        cl.topMargin = dp(36);
        wrap.addView(card, cl);
        Bubble b = new Bubble(this);
        b.setIcon(new Ico(icon, 0xFF2A1C00));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(dp(80), dp(76), Gravity.TOP | Gravity.LEFT);
        bl.leftMargin = dp(6);
        wrap.addView(b, bl);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, -2);
        wl.topMargin = dp(12);
        list.addView(wrap, wl);
    }

    private TextView text(float sp, int color, android.graphics.Typeface tf) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setShadowLayer(dp(3), 0, dp(1), 0x66000000);
        return t;
    }

    private LinearLayout row(String label, String sub, String value) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(20), dp(11), dp(14), dp(11));
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        r.setBackgroundResource(tv.resourceId);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView l = text(15, Color.WHITE, U.sans(this));
        l.setText(label);
        col.addView(l);
        if (sub != null) {
            TextView s = text(12, 0xA6FFFFFF, U.light());
            s.setText(sub);
            col.addView(s);
        }
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        if (value != null) {
            TextView v = text(13.5f, 0xFFFFC94A, U.sansBold(this));
            v.setText(value);
            v.setPadding(dp(8), 0, dp(4), 0);
            r.addView(v);
        }
        ImageView ch = new ImageView(this);
        ch.setImageDrawable(new Ico(Ico.NEXT, 0x80FFFFFF));
        r.addView(ch, new LinearLayout.LayoutParams(dp(14), dp(14)));
        return r;
    }

    private View action(String label, String sub, final Runnable run) {
        LinearLayout r = row(label, sub, null);
        r.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { run.run(); } });
        return r;
    }

    /** A setting with a few choices. Keys starting with "@" are stored as numbers or yes/no. */
    private View choice(final String label, final String key, String def, final String[] names, final String[] values) {
        final String cur = key.startsWith("@") ? def : key.equals("langs") ? def : p().getString(key, def);
        int i = 0;
        for (int k = 0; k < values.length; k++) if (values[k].equals(cur)) i = k;
        LinearLayout r = row(label, null, names[i]);
        final int sel = i;
        r.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { sheet(label, names, values, sel, key); } });
        return r;
    }

    /** To appear over the lock screen when another app was open, Android needs L.t("Display over other apps"). */
    private void askDots() {
        String on = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (on != null && on.contains(getPackageName())) return;
        Toast.makeText(this, "Allow notification access for Velthon to show the dots", Toast.LENGTH_LONG).show();
        open("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
    }
    private void askOverlay() {
        if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) return;
        Toast.makeText(this, "Allow Velthon to display over other apps, so the window shows on the lock screen", Toast.LENGTH_LONG).show();
        try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + getPackageName()))); }
        catch (Throwable t) { open(Settings.ACTION_MANAGE_OVERLAY_PERMISSION); }
    }
    /** The phone's own text size (the same setting as Display › Font size), nearest step. */
    private String fontScale() {
        float f = 1f;
        try { f = Settings.System.getFloat(getContentResolver(), Settings.System.FONT_SCALE, 1f); } catch (Throwable ignored) { }
        String[] v = {"0.85", "1.0", "1.15", "1.3", "1.5", "1.8"}; String best = "1.0"; float d = 9;
        for (String x : v) if (Math.abs(Float.parseFloat(x) - f) < d) { d = Math.abs(Float.parseFloat(x) - f); best = x; }
        return best;
    }
    private void setFontScale(String v) {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(this)) {
            p().edit().putString("pending_scale", v).apply();   // set as soon as permission is given
            Toast.makeText(this, "Allow Velthon to modify system settings, then come back", Toast.LENGTH_LONG).show();
            try { startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:" + getPackageName()))); }
            catch (Throwable t) { open(Settings.ACTION_DISPLAY_SETTINGS); }
            return;
        }
        try {
            Settings.System.putFloat(getContentResolver(), Settings.System.FONT_SCALE, Float.parseFloat(v));
        } catch (Throwable t) {
            Toast.makeText(this, L.t("Change it in the phone's Display settings"), Toast.LENGTH_LONG).show();
            open(Settings.ACTION_DISPLAY_SETTINGS);
        }
        build();
    }
    private void save(String key, String v) {
        SharedPreferences.Editor e = p().edit();
        if (key.equals("@velthon_icons")) e.putBoolean("velthon_icons", v.equals("1"));
        else if (key.equals("@columns")) e.putInt("columns", Integer.parseInt(v));
        else if (key.equals("@font_scale")) { setFontScale(v); return; }
        else e.putString(key, v);
        if (key.equals("units")) e.putLong("wx_at", 0);
        if (key.equals("lock_saver") && v.equals("on")) askOverlay();
        if (key.equals("dots") && v.equals("on")) askDots();
        if (key.equals("lock_events") && v.equals("on") && Build.VERSION.SDK_INT >= 23 && !Events.allowed(this))
            requestPermissions(new String[] {android.Manifest.permission.READ_CALENDAR}, 41);
        if (key.equals("double_tap") && v.equals("lock") && LockService.on == null && Build.VERSION.SDK_INT >= 28) {
            Toast.makeText(this, "Turn on \"Velthon double-tap lock\" in Accessibility › Installed apps", Toast.LENGTH_LONG).show();
            open(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        }
        e.apply();
        scene.refresh();
        build();
    }

    // ---------------------------------------------------------------- the picker: a panel from the bottom, like the dock
    private void sheet(String title, final String[] names, final String[] values, int sel, final String key) {
        layer.removeAllViews();
        View dim = new View(this);
        dim.setBackgroundColor(0x99000000);
        dim.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { closeSheet(); } });
        layer.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout wrap = new FrameLayout(this);
        wrap.setClipChildren(false);
        Glass panel = new Glass(this, 30, Glass.SHEET);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.notchR = dp(31); panel.notchCy = -dp(9); panel.notchX = .5f;
        panel.setPadding(dp(8), dp(30), dp(8), bottom + dp(14));
        TextView t = text(17, Color.WHITE, U.serif(this));
        t.setGravity(Gravity.CENTER);
        t.setText(title);
        panel.addView(t, new LinearLayout.LayoutParams(-1, -2));
        for (int i = 0; i < names.length; i++) {
            final int k = i;
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(20), dp(13), dp(20), dp(13));
            TypedValue tv = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            r.setBackgroundResource(tv.resourceId);
            TextView l = text(15.5f, i == sel ? 0xFFFFC94A : Color.WHITE, i == sel ? U.sansBold(this) : U.sans(this));
            l.setText(names[i]);
            r.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
            if (i == sel) {
                View dot = new View(this) {
                    final android.graphics.Paint pp = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                    @Override protected void onDraw(Canvas c) { pp.setColor(0xFFFFC400); c.drawCircle(getWidth() / 2f, getHeight() / 2f, getWidth() / 2f, pp); }
                };
                r.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));
            }
            r.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { closeSheet(); save(key, values[k]); } });
            panel.addView(r, new LinearLayout.LayoutParams(-1, -2));
        }
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        pl.topMargin = dp(36);
        wrap.addView(panel, pl);
        Bubble b = new Bubble(this);
        b.setIcon(new Ico(Ico.GEAR, 0xFF2A1C00));
        wrap.addView(b, new FrameLayout.LayoutParams(dp(80), dp(76), Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        FrameLayout.LayoutParams wl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        wl.leftMargin = wl.rightMargin = dp(8);
        layer.addView(wrap, wl);
        layer.setVisibility(View.VISIBLE);
        wrap.setTranslationY(dp(400));
        wrap.animate().translationY(0).setDuration(260).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    }
    private void closeSheet() { layer.setVisibility(View.GONE); layer.removeAllViews(); }

    // ---------------------------------------------------------------- backgrounds: suggestions and the gallery
    private static final String[] BG = {"weather", "window", "candle", "mist", "night", "custom"};
    private static final String[] BG_NAMES = {L.t("Live weather sky"), L.t("Bible & coffee"), L.t("Candlelight"), L.t("Misty forest"), L.t("Starry night"), L.t("From gallery")};

    private View backgrounds() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(6), 0, dp(4));
        TextView l = text(15, Color.WHITE, U.sans(this));
        l.setText(L.t("Background picture"));
        l.setPadding(dp(20), 0, dp(20), dp(8));
        box.addView(l);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout strip = new LinearLayout(this);
        String cur = U.bg(this);
        File custom = new File(getFilesDir(), "bg_custom.jpg");
        for (int i = 0; i < BG.length; i++) {
            final String id = BG[i];
            Bitmap thumb;
            if (id.equals("custom")) thumb = custom.exists() ? thumbFile(custom) : null;
            else if (id.equals("weather")) thumb = thumbAsset("sky/" + U.skyName(this) + ".jpg");
            else thumb = thumbAsset(id.equals("window") ? "scene.jpg" : "bg/" + id + ".jpg");
            LinearLayout t = new LinearLayout(this);
            t.setOrientation(LinearLayout.VERTICAL);
            t.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout f = new FrameLayout(this);
            f.setClipChildren(false);
            Thumb th = new Thumb(thumb, id.equals("custom"));
            f.addView(th, new FrameLayout.LayoutParams(dp(78), dp(138)));
            if (id.equals(cur)) {
                Bubble ok = new Bubble(this);
                ok.setIcon(new Ico(Ico.HEART_FULL, 0xFF2A1C00));
                ok.setScaleX(.62f); ok.setScaleY(.62f);
                FrameLayout.LayoutParams ol = new FrameLayout.LayoutParams(dp(80), dp(76), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                ol.bottomMargin = -dp(30);
                f.addView(ok, ol);
            }
            t.addView(f, new LinearLayout.LayoutParams(dp(78), dp(138)));
            TextView n = text(11.5f, id.equals(cur) ? 0xFFFFC94A : 0xD9FFFFFF, id.equals(cur) ? U.sansBold(this) : U.sans(this));
            n.setText(BG_NAMES[i]);
            n.setPadding(0, dp(12), 0, 0);
            t.addView(n);
            t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                if (id.equals("custom")) pickImage(); else save("bg", id);
            } });
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(dp(92), -2);
            strip.addView(t, tl);
        }
        hs.addView(strip);
        box.addView(hs);
        return box;
    }

    /** A rounded picture tile (no border); the gallery tile shows a "+" when nothing was chosen yet. */
    private final class Thumb extends View {
        private final Bitmap b; private final boolean add;
        private final Path clip = new Path();
        private final android.graphics.Paint pp = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
        Thumb(Bitmap b, boolean add) { super(SettingsActivity.this); this.b = b; this.add = add; }
        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            clip.reset();
            clip.addRoundRect(new RectF(0, 0, w, h), dp(14), dp(14), Path.Direction.CW);
            c.save(); c.clipPath(clip);
            if (b != null) {
                float k = Math.max(w / (float) b.getWidth(), h / (float) b.getHeight());
                c.translate((w - b.getWidth() * k) / 2f, (h - b.getHeight() * k) / 2f);
                c.scale(k, k);
                c.drawBitmap(b, 0, 0, pp);
            } else {
                c.drawColor(0x40000000);
            }
            c.restore();
            if (add) {
                pp.setColor(b == null ? 0xE6FFFFFF : 0xCCFFFFFF);
                pp.setStyle(android.graphics.Paint.Style.STROKE);
                pp.setStrokeWidth(dp(2.2f));
                pp.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                float cx = w / 2f, cy = h / 2f, r = dp(11);
                if (b != null) { pp.setStyle(android.graphics.Paint.Style.FILL); pp.setColor(0x80000000); c.drawCircle(cx, cy, dp(18), pp); pp.setStyle(android.graphics.Paint.Style.STROKE); pp.setColor(Color.WHITE); }
                c.drawLine(cx - r, cy, cx + r, cy, pp);
                c.drawLine(cx, cy - r, cx, cy + r, pp);
                pp.setStyle(android.graphics.Paint.Style.FILL);
            }
        }
    }

    private Bitmap thumbAsset(String path) {
        try (InputStream in = getAssets().open(path)) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 8;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeStream(in, null, o);
        } catch (Throwable t) { return null; }
    }
    private Bitmap thumbFile(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 8;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(Intent.createChooser(i, L.t("Choose a background")), PICK_IMAGE); }
        catch (Throwable t) { Toast.makeText(this, L.t("No gallery app found"), Toast.LENGTH_SHORT).show(); }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if ((req == SAVE_BACKUP || req == LOAD_BACKUP) && res == RESULT_OK && data != null && data.getData() != null) {
            final Uri u = data.getData(); final boolean save = req == SAVE_BACKUP;
            new Thread(new Runnable() { @Override public void run() {
                String msg; boolean restart = false;
                try {
                    if (save) {
                        try (java.io.OutputStream out = getContentResolver().openOutputStream(u, "wt")) { out.write(Backup.export(SettingsActivity.this).getBytes("UTF-8")); }
                        msg = L.t("Backup saved");
                    } else {
                        String json;
                        try (InputStream in = getContentResolver().openInputStream(u)) { json = new String(Backup.read(in), "UTF-8"); }
                        int n = Backup.restore(SettingsActivity.this, json);
                        msg = L.t("Settings restored") + " (" + n + ")"; restart = true;
                    }
                } catch (Throwable t) { msg = save ? L.t("The backup couldn't be saved") : L.t("That file isn't a Velthon backup"); }
                final String m = msg; final boolean r = restart;
                ui.post(new Runnable() { @Override public void run() {
                    Toast.makeText(SettingsActivity.this, m, Toast.LENGTH_LONG).show();
                    if (r) ui.postDelayed(new Runnable() { @Override public void run() { restartLauncher(); } }, 900);
                } });
            } }).start();
            return;
        }
        if (req != PICK_IMAGE || res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        Toast.makeText(this, L.t("Setting your background…"), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() { @Override public void run() {
            boolean ok = false;
            try {
                // read the size, then decode at about the screen's size, and keep a copy in the app
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                try (InputStream in = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
                int target = Math.max(getResources().getDisplayMetrics().heightPixels, 1600);
                int sample = 1;
                while (Math.min(o.outWidth, o.outHeight) / (sample * 2) >= Math.min(target, getResources().getDisplayMetrics().widthPixels)
                        && Math.max(o.outWidth, o.outHeight) / (sample * 2) >= target) sample *= 2;
                BitmapFactory.Options d = new BitmapFactory.Options();
                d.inSampleSize = sample;
                Bitmap b;
                try (InputStream in = getContentResolver().openInputStream(uri)) { b = BitmapFactory.decodeStream(in, null, d); }
                if (b != null) {
                    float k = Math.min(1f, target * 1.2f / Math.max(b.getWidth(), b.getHeight()));
                    if (k < 1f) { Bitmap s = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * k), Math.round(b.getHeight() * k), true); b.recycle(); b = s; }
                    File tmp = new File(getFilesDir(), "bg_custom.tmp");
                    try (FileOutputStream out = new FileOutputStream(tmp)) { b.compress(Bitmap.CompressFormat.JPEG, 88, out); }
                    b.recycle();
                    ok = tmp.renameTo(new File(getFilesDir(), "bg_custom.jpg"));
                }
            } catch (Throwable t) { android.util.Log.w("Velthon", "background: " + t); }
            final boolean done = ok;
            ui.post(new Runnable() { @Override public void run() {
                if (done) save("bg", "custom");
                else Toast.makeText(SettingsActivity.this, L.t("That picture couldn't be opened"), Toast.LENGTH_SHORT).show();
            } });
        } }).start();
    }

    // ---------------------------------------------------------------- small helpers
    private boolean open(String action) {
        try { startActivity(new Intent(action)); return true; } catch (Throwable t) { return false; }
    }
    private void askCity() {
        final EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        e.setHint("e.g. Toronto (leave empty to use your location)");
        e.setText(p().getString("city", ""));
        FrameLayout f = new FrameLayout(this);
        f.setPadding(dp(22), dp(8), dp(22), 0);
        f.addView(e);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(L.t("Weather city"))
                .setView(f)
                .setPositiveButton(L.t("Save"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        p().edit().putString("city", e.getText().toString().trim()).putLong("wx_at", 0).apply();
                        Weather.refresh(SettingsActivity.this, true, null);
                        build();
                    }
                })
                .setNegativeButton(L.t("Cancel"), null).show();
    }

    // leaving for the home screen closes this page, so it never lingers in Recents;
    // pages it opens itself (gallery, system settings, share) come back to it
    private boolean expectBack;
    @Override public void startActivity(Intent i) { expectBack = true; super.startActivity(i); }
    @Override public void startActivityForResult(Intent i, int code) { expectBack = true; super.startActivityForResult(i, code); }
    @Override protected void onStop() {
        super.onStop();
        if (!expectBack && !isChangingConfigurations() && !isFinishing()) finish();
    }
    @Override protected void onRestart() { super.onRestart(); expectBack = false; }
}
