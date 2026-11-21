package com.velthon.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

/** Verses: the verse of the hour, every verse, saved verses, and the settings. */
public class VersesActivity extends Activity {
    private Scene scene;
    private LinearLayout page;
    private FrameLayout body;
    private final Glass[] tabG = new Glass[3];
    private final TextView[] tabT = new TextView[3];
    private int index;
    private TextToSpeech tts;
    private boolean ttsReady;

    private int dp(float v) { return U.dpi(this, v); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        U.clearBars(getWindow());   // no dark band: the picture runs behind the status bar
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        index = getIntent().getIntExtra("verse", U.verseOfHour(this).index);

        scene = new Scene(this);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0x40060A18);
        scene.addView(page, new FrameLayout.LayoutParams(-1, -1));
        setContentView(scene);
        scene.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                page.setPadding(dp(18), in.getSystemWindowInsetTop() + dp(8), dp(18), in.getSystemWindowInsetBottom() + dp(12));
                return in;
            }
        });

        // top bar
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = iconButton(Ico.BACK);
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finish(); } });
        bar.addView(back);
        ImageView logo = new ImageView(this);
        logo.setImageBitmap(U.bitmap(this, "logo_small.png"));
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(dp(30), dp(30)); ll.leftMargin = dp(6); ll.rightMargin = dp(8);
        bar.addView(logo, ll);
        TextView title = text(22, Color.WHITE, U.sansBold(this));
        title.setText(L.t("Verses"));
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView gear = iconButton(Ico.GEAR);
        gear.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { settings(); } });
        bar.addView(gear);
        page.addView(bar, new LinearLayout.LayoutParams(-1, dp(52)));

        // tabs
        Glass tabs = new Glass(this, 999, Glass.CLEAR);
        tabs.setPadding(dp(5), dp(5), dp(5), dp(5));
        String[] names = {"Verse", "All", L.t("Saved")};
        for (int i = 0; i < 3; i++) {
            Glass g = new Glass(this, 999, Glass.GOLD);
            g.blur = false;
            g.setGravity(Gravity.CENTER);
            TextView t = text(14.5f, Color.WHITE, U.sans(this));
            t.setText(names[i]);
            g.addView(t);
            final int k = i;
            g.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { show(k); } });
            tabs.addView(g, new LinearLayout.LayoutParams(0, dp(38), 1f));
            tabG[i] = g; tabT[i] = t;
        }
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2); tl.topMargin = dp(6); tl.bottomMargin = dp(14);
        page.addView(tabs, tl);

        body = new FrameLayout(this);
        page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        String open = getIntent().getStringExtra("open");
        show("saved".equals(open) ? 2 : "search".equals(open) ? 1 : 0);
        if ("settings".equals(open)) { startActivity(new Intent(this, SettingsActivity.class)); finish(); }
    }

    @Override protected void onResume() { super.onResume(); scene.refresh(); }
    @Override protected void onDestroy() { super.onDestroy(); if (tts != null) tts.shutdown(); }

    private TextView text(float sp, int color, android.graphics.Typeface tf) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }
    private ImageView iconButton(int type) {
        ImageView v = new ImageView(this);
        v.setImageDrawable(new Ico(type, Color.WHITE));
        v.setPadding(dp(10), dp(10), dp(10), dp(10));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        return v;
    }

    private void show(int k) {
        for (int i = 0; i < 3; i++) {
            tabG[i].setWillNotDraw(i != k);
            tabT[i].setTypeface(i == k ? U.sansBold(this) : U.sans(this));
            tabT[i].setTextColor(i == k ? 0xFF141414 : 0xCCFFFFFF);
        }
        body.removeAllViews();
        if (k == 0) showVerse();
        else showList(k == 2);
    }

    // ---------------------------------------------------------------- one verse
    private void showVerse() {
        final ArrayList<U.Verse> all = U.verses(this);
        final U.Verse v = all.get(((index % all.size()) + all.size()) % all.size());
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col);

        Glass card = new Glass(this, 28, Glass.CLEAR);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(22), dp(28), dp(22), dp(22));
        String langs = U.langs(this);
        if (!langs.equals("ar")) {
            TextView en = text(25, Color.WHITE, U.serif(this));
            en.setGravity(Gravity.CENTER); en.setLineSpacing(0, 1.2f); en.setText(v.en);
            en.setShadowLayer(5, 0, 2, 0x99000000);
            card.addView(en, new LinearLayout.LayoutParams(-1, -2));
            TextView r = text(15, 0xFFF0B43C, U.serif(this)); r.setGravity(Gravity.CENTER); r.setText(v.refEn);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2); rl.topMargin = dp(10);
            card.addView(r, rl);
        }
        if (!langs.equals("en")) {
            TextView ar = text(25, Color.WHITE, android.graphics.Typeface.SERIF);
            ar.setGravity(Gravity.CENTER); ar.setLineSpacing(0, 1.25f); ar.setText(v.ar);
            ar.setTextDirection(View.TEXT_DIRECTION_RTL);
            ar.setShadowLayer(5, 0, 2, 0x99000000);
            LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1, -2); al.topMargin = dp(18);
            card.addView(ar, al);
            TextView r = text(15, 0xFFF0B43C, android.graphics.Typeface.SERIF); r.setGravity(Gravity.CENTER); r.setText(v.refAr);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2); rl.topMargin = dp(6);
            card.addView(r, rl);
        }
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2); cl.topMargin = dp(20);
        col.addView(card, cl);

        // previous / random / next
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.addView(round(Ico.BACK, new View.OnClickListener() { @Override public void onClick(View x) { index--; show(0); } }));
        nav.addView(round(Ico.SHUFFLE, new View.OnClickListener() { @Override public void onClick(View x) { index = (int) (Math.random() * all.size()); show(0); } }));
        nav.addView(round(Ico.NEXT, new View.OnClickListener() { @Override public void onClick(View x) { index++; show(0); } }));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2); nl.topMargin = dp(22);
        col.addView(nav, nl);

        // listen / save / share
        LinearLayout acts = new LinearLayout(this);
        acts.setGravity(Gravity.CENTER);
        final boolean saved = U.saved(this).contains(v.index);
        acts.addView(chip(Ico.SOUND, L.t("Listen"), new View.OnClickListener() { @Override public void onClick(View x) { listen(v); } }));
        acts.addView(chip(saved ? Ico.HEART_FULL : Ico.HEART, saved ? L.t("Saved") : L.t("Save"), new View.OnClickListener() {
            @Override public void onClick(View x) { U.toggleSaved(VersesActivity.this, v.index); show(0); } }));
        acts.addView(chip(Ico.SHARE, L.t("Share"), new View.OnClickListener() { @Override public void onClick(View x) { share(v); } }));
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1, -2); al.topMargin = dp(16);
        col.addView(acts, al);
        body.addView(sv, new FrameLayout.LayoutParams(-1, -1));
    }

    private View round(int type, View.OnClickListener l) {
        Glass g = new Glass(this, 999, Glass.CLEAR);
        g.setGravity(Gravity.CENTER);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(new Ico(type, Color.WHITE));
        g.addView(iv, new LinearLayout.LayoutParams(dp(24), dp(24)));
        g.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(56), dp(56)); p.leftMargin = p.rightMargin = dp(10);
        g.setLayoutParams(p);
        return g;
    }
    private View chip(int type, String label, View.OnClickListener l) {
        Glass g = new Glass(this, 999, Glass.CLEAR);
        g.setGravity(Gravity.CENTER_VERTICAL);
        g.setPadding(dp(14), dp(9), dp(16), dp(9));
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(new Ico(type, type == Ico.HEART_FULL ? 0xFFFF8FA8 : Color.WHITE));
        g.addView(iv, new LinearLayout.LayoutParams(dp(17), dp(17)));
        TextView t = text(14, Color.WHITE, U.sans(this)); t.setText(label); t.setPadding(dp(7), 0, 0, 0);
        g.addView(t);
        g.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2); p.leftMargin = p.rightMargin = dp(6);
        g.setLayoutParams(p);
        return g;
    }

    private void share(U.Verse v) {
        String langs = U.langs(this);
        StringBuilder t = new StringBuilder();
        if (!langs.equals("ar")) t.append(v.en).append("\n— ").append(v.refEn);
        if (!langs.equals("en")) { if (t.length() > 0) t.append("\n\n"); t.append(v.ar).append("\n— ").append(v.refAr); }
        startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t.toString()), L.t("Share verse")));
    }
    private void listen(final U.Verse v) {
        final boolean arabic = U.langs(this).equals("ar");
        if (tts == null) {
            tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
                @Override public void onInit(int s) { ttsReady = s == TextToSpeech.SUCCESS; if (ttsReady) speak(v, arabic); }
            });
        } else if (ttsReady) speak(v, arabic);
    }
    private void speak(U.Verse v, boolean arabic) {
        if (tts.isSpeaking()) { tts.stop(); return; }
        int r = tts.setLanguage(arabic ? new Locale("ar") : Locale.US);
        String t = arabic ? v.ar + ". " + v.refAr : v.en + " " + v.refEn;
        if (arabic && r < 0) { tts.setLanguage(Locale.US); t = v.en + " " + v.refEn; }
        tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "verse");
    }

    // ---------------------------------------------------------------- lists
    private void showList(final boolean savedOnly) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        final ArrayList<U.Verse> items = new ArrayList<>();
        final EditText q = new EditText(this);
        if (!savedOnly) {
            Glass box = new Glass(this, 999, Glass.GOLD_EDGE);
            box.blur = false;
            box.setGravity(Gravity.CENTER_VERTICAL);
            box.setPadding(dp(16), 0, dp(12), 0);
            ImageView si = new ImageView(this); si.setImageDrawable(new Ico(Ico.SEARCH, Color.WHITE));
            box.addView(si, new LinearLayout.LayoutParams(dp(20), dp(20)));
            q.setBackground(null); q.setHint("Search verses (English or Arabic)"); q.setHintTextColor(0xCCFFFFFF);
            q.setTextColor(Color.WHITE); q.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f); q.setSingleLine(true);
            q.setPadding(dp(10), 0, 0, 0);
            box.addView(q, new LinearLayout.LayoutParams(0, -1, 1f));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, dp(48)); bl.bottomMargin = dp(12);
            col.addView(box, bl);
        }
        Glass panel = new Glass(this, 24, Glass.DARK);
        final ListView list = new ListView(this);
        list.setDivider(new android.graphics.drawable.ColorDrawable(0x22FFFFFF));
        list.setDividerHeight(1);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0x22FFFFFF));
        list.setVerticalScrollBarEnabled(false);
        panel.addView(list, new LinearLayout.LayoutParams(-1, -1));
        col.addView(panel, new LinearLayout.LayoutParams(-1, 0, 1f));
        final TextView empty = text(15, 0xCCFFFFFF, U.sans(this));
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(24), dp(40), dp(24), dp(40));
        empty.setText(savedOnly ? "Tap Save on any verse and it will be kept here." : "No verse matches.");

        final BaseAdapter ad = new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Object getItem(int i) { return items.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View v, ViewGroup p) {
                if (v == null) {
                    LinearLayout l = new LinearLayout(VersesActivity.this);
                    l.setOrientation(LinearLayout.VERTICAL);
                    l.setPadding(dp(18), dp(14), dp(18), dp(14));
                    TextView a = text(16, Color.WHITE, U.serif(VersesActivity.this));
                    a.setMaxLines(3); a.setEllipsize(TextUtils.TruncateAt.END); a.setLineSpacing(0, 1.15f);
                    l.addView(a);
                    TextView r = text(13, 0xFFF0B43C, U.sans(VersesActivity.this)); r.setPadding(0, dp(6), 0, 0);
                    l.addView(r);
                    v = l;
                }
                U.Verse x = items.get(i);
                boolean arabic = U.langs(VersesActivity.this).equals("ar");
                TextView a = (TextView) ((LinearLayout) v).getChildAt(0);
                a.setText(arabic ? x.ar : x.en);
                a.setTypeface(arabic ? android.graphics.Typeface.SERIF : U.serif(VersesActivity.this));
                a.setGravity(arabic ? Gravity.RIGHT : Gravity.LEFT);
                ((TextView) ((LinearLayout) v).getChildAt(1)).setText(arabic ? x.refAr : x.refEn + "  ·  " + x.refAr);
                return v;
            }
        };
        list.setAdapter(ad);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) { index = items.get(pos).index; show(0); }
        });
        final Runnable fill = new Runnable() { @Override public void run() {
            items.clear();
            String s = q.getText().toString().trim().toLowerCase(Locale.ROOT);
            ArrayList<U.Verse> all = U.verses(VersesActivity.this);
            if (savedOnly) {
                ArrayList<Integer> keep = new ArrayList<>(U.saved(VersesActivity.this));
                for (int i = keep.size() - 1; i >= 0; i--) if (keep.get(i) < all.size()) items.add(all.get(keep.get(i)));
            } else {
                for (U.Verse v : all)
                    if (s.isEmpty() || v.en.toLowerCase(Locale.ROOT).contains(s) || v.ar.contains(s)
                            || v.refEn.toLowerCase(Locale.ROOT).contains(s) || v.refAr.contains(s)) items.add(v);
            }
            ad.notifyDataSetChanged();
            empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        } };
        q.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { fill.run(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        fill.run();
        FrameLayout f = new FrameLayout(this);
        f.addView(col, new FrameLayout.LayoutParams(-1, -1));
        f.addView(empty, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        body.addView(f, new FrameLayout.LayoutParams(-1, -1));
        if ("search".equals(getIntent().getStringExtra("open")) && !savedOnly) {
            getIntent().removeExtra("open");
            q.requestFocus();
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    // ---------------------------------------------------------------- settings
    private void settings() {
        startActivity(new Intent(this, SettingsActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
    @SuppressWarnings("unused") private void oldSettings() {
        final android.content.SharedPreferences p = U.prefs(this);
        final String[] langV = {"en", "ar"}, langN = {L.t("English"), L.t("Arabic")};
        final String[] seasonV = {"auto", "spring", "summer", "fall", "winter"}, seasonN = {"Follow the calendar", L.t("Spring"), L.t("Summer"), L.t("Autumn"), L.t("Winter")};
        final String[] unitV = {"auto", "c", "f"}, unitN = {L.t("Automatic"), L.t("Celsius (°C)"), L.t("Fahrenheit (°F)")};
        final String[] churchV = {"orthodox", "catholic", "off"}, churchN = {L.t("Coptic Orthodox"), L.t("Catholic"), L.t("Off")};
        String city = p.getString("city", "");
        final String[] items = {
                "Verse language: " + langN[idx(langV, U.langs(VersesActivity.this))],
                "Season outside the window: " + seasonN[idx(seasonV, p.getString("season", "auto"))],
                "Temperature: " + unitN[idx(unitV, p.getString("units", "auto"))],
                "Church calendar: " + churchN[idx(churchV, p.getString("church", "orthodox"))],
                "Weather city: " + (city.isEmpty() ? "your location" : city),
                "Turn on the Velthon screensaver",
                "Preview the screensaver",
                "Make Velthon your home screen",
                "App icons: " + (p.getBoolean("velthon_icons", false) ? L.t("Velthon line icons") : "the phone's own icons"),
                "Home screen columns: " + p.getInt("columns", 5),
                "About Velthon Launcher"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(L.t("Settings"))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        switch (which) {
                            case 0: choose(L.t("Verse language"), langN, langV, "langs"); break;
                            case 1: choose("Season outside the window", seasonN, seasonV, "season"); break;
                            case 2: choose(L.t("Temperature"), unitN, unitV, "units"); break;
                            case 3: choose(L.t("Church calendar"), churchN, churchV, "church"); break;
                            case 4: askCity(); break;
                            case 5: openScreensaverSettings(); break;
                            case 6: startActivity(new Intent(VersesActivity.this, PreviewActivity.class)); break;
                            case 7: openHomeSettings(); break;
                            case 8:
                                new AlertDialog.Builder(VersesActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                        .setTitle(L.t("App icons"))
                                        .setSingleChoiceItems(new String[] {"The phone's own icons", "Velthon line icons (main apps)"},
                                                p.getBoolean("velthon_icons", false) ? 1 : 0, new DialogInterface.OnClickListener() {
                                            @Override public void onClick(DialogInterface d2, int w) {
                                                p.edit().putBoolean("velthon_icons", w == 1).apply();
                                                d2.dismiss();
                                                Toast.makeText(VersesActivity.this, "Icons change when you go back to the home screen", Toast.LENGTH_SHORT).show();
                                            }
                                        }).show();
                                break;
                            case 9:
                                new AlertDialog.Builder(VersesActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                        .setTitle("Home screen columns")
                                        .setSingleChoiceItems(new String[] {"4 columns (bigger icons)", "5 columns (more apps)"},
                                                p.getInt("columns", 5) == 4 ? 0 : 1, new DialogInterface.OnClickListener() {
                                            @Override public void onClick(DialogInterface d2, int w) { p.edit().putInt("columns", w == 0 ? 4 : 5).apply(); d2.dismiss(); }
                                        }).show();
                                break;
                            case 10: about(); break;
                        }
                    }
                }).show();
    }
    private static int idx(String[] a, String v) { for (int i = 0; i < a.length; i++) if (a[i].equals(v)) return i; return 0; }

    private void choose(String title, String[] names, final String[] values, final String key) {
        int cur = idx(values, U.prefs(this).getString(key, values[0]));
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setSingleChoiceItems(names, cur, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        U.prefs(VersesActivity.this).edit().putString(key, values[which]).apply();
                        if (key.equals("units")) U.prefs(VersesActivity.this).edit().putLong("wx_at", 0).apply();
                        d.dismiss();
                        scene.refresh();
                        show(0);
                    }
                }).show();
    }
    private void askCity() {
        final EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        e.setHint("e.g. Toronto (leave empty to use your location)");
        e.setText(U.prefs(this).getString("city", ""));
        FrameLayout f = new FrameLayout(this);
        f.setPadding(dp(22), dp(8), dp(22), 0);
        f.addView(e);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(L.t("Weather city"))
                .setView(f)
                .setPositiveButton(L.t("Save"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        U.prefs(VersesActivity.this).edit().putString("city", e.getText().toString().trim()).putLong("wx_at", 0).apply();
                        Weather.refresh(VersesActivity.this, true, null);
                    }
                })
                .setNegativeButton(L.t("Cancel"), null).show();
    }
    private void openScreensaverSettings() {
        String[] actions = {Settings.ACTION_DREAM_SETTINGS, Settings.ACTION_DISPLAY_SETTINGS, Settings.ACTION_SETTINGS};
        for (String a : actions) {
            try { startActivity(new Intent(a)); Toast.makeText(this, "Choose \"Velthon\" as the screen saver", Toast.LENGTH_LONG).show(); return; }
            catch (Throwable ignored) { }
        }
    }
    private void openHomeSettings() {
        try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
        catch (Throwable t) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Throwable ignored) { }
        }
    }
    private void about() {
        String v = "1.0";
        try { v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Throwable ignored) { }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Velthon Launcher " + v)
                .setMessage("A light home screen and screensaver with a verse every hour.\n\n"
                        + "Weather: Open-Meteo. Weather icons: Meteocons by Bas Milius (MIT).\nFonts: Playfair Display, Source Sans 3 (SIL OFL).")
                .setPositiveButton("OK", null).show();
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
