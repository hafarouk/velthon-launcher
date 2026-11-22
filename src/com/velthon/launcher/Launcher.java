package com.velthon.launcher;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import android.graphics.Canvas;
import android.graphics.Paint;

/** The Velthon home screen: clock, weather, the verse, your apps and widgets, the dock; and the app drawer. */
public class Launcher extends Activity {
    static final int DOCK_MAX = 6;
    private static final int REQ_BIND = 11, REQ_CONFIG = 12, REQ_RECONFIG = 13;

    private Scene scene;
    private LinearLayout home;
    private Glass wxPill, card, search, dockBar;
    private ImageView wxIcon, saveIcon;
    private TextView alarmLine;
    /** "⏰ 6:30 AM · Sat": the phone's next alarm, or nothing if none is set. */
    private void showAlarm() {
        if (alarmLine == null) return;
        android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
        android.app.AlarmManager.AlarmClockInfo next = am == null ? null : am.getNextAlarmClock();
        if (next == null) { alarmLine.setVisibility(View.GONE); return; }
        java.util.Calendar at = java.util.Calendar.getInstance(); at.setTimeInMillis(next.getTriggerTime());
        java.util.Calendar now = java.util.Calendar.getInstance();
        String time = android.text.format.DateFormat.getTimeFormat(this).format(at.getTime());
        long days = (next.getTriggerTime() - now.getTimeInMillis()) / 86_400_000L;
        boolean ar = U.langs(this).equals("ar");
        String when = at.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR) ? L.t("Today")
                : days < 1 || at.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR) + 1 ? L.t("Tomorrow")
                : new java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(at.getTime());
        alarmLine.setText("  ·  ⏰ " + time + (when.equals(L.t("Today")) ? "" : " " + when));
        alarmLine.setVisibility(View.VISIBLE);
        if (dateMidV != null) ui.post(new Runnable() { @Override public void run() { fitDateRow(dateMidV); } });
    }
    private final BroadcastReceiver alarmChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { showAlarm(); } };
    private android.animation.ObjectAnimator wxFloat;
    private TextView wxTemp, wxText, en, enRef, ar, arRef, saveChip, deskHint, dockHint, churchLine, alertLine, nextLine;
    private TextClock dateView;
    private LinearLayout forecastRow, wxNow;
    private Desk desk;
    private LinearLayout dockRow;
    private FrameLayout dockWrap;
    private Bubble dockBubble;
    private int dockSel = 0, dockN = 0;
    private float dockPos = 0;
    private android.animation.ValueAnimator dockAnim;
    private final ArrayList<App> dockApps = new ArrayList<>();
    private LinearLayout dropBar;
    private Glass dropRemove, dropInfo, dropUninstall;
    private FrameLayout drawer;
    private Glass sheet;
    private EditText query;
    private GridView grid;
    private LinearLayout chips;
    private LinearLayout recentBox, recentRow;
    private String cat = "All";
    private LinearLayout folderRow, catRow, saintRow;
    private FrameLayout vipBox;
    private HorizontalScrollView catScroll;
    private ImageView saintPic; private TextView saintText;
    private final ArrayList<String> cats = new ArrayList<>();
    private String chromeSig = "";
    private final Handler ui = new Handler(Looper.getMainLooper());
    private U.Verse verse;
    private TextToSpeech tts;
    private boolean ttsReady;
    private float textScale = 1f;
    private AppWidgetHost widgetHost;
    private AppWidgetManager widgetManager;
    private int pendingWidget = -1;

    // ---------------------------------------------------------------- life cycle
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(Color.TRANSPARENT);
        U.clearBars(getWindow());   // no dark band: the picture runs behind the status bar
        w.setNavigationBarColor(Color.TRANSPARENT);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        // text follows the phone's font size only a little, so the home screen keeps room for apps
        textScale = Math.max(.9f, Math.min(1.5f, getResources().getConfiguration().fontScale));   // follows the phone's text size

        widgetManager = AppWidgetManager.getInstance(this);
        widgetHost = new Widgets.Host(this, 0x7e10);

        scene = new Scene(this);
        buildHome();
        buildDropBar();
        buildDrawer();
        setContentView(scene);
        scene.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                home.setPadding(0, in.getSystemWindowInsetTop(), 0, in.getSystemWindowInsetBottom());
                drawer.setPadding(0, in.getSystemWindowInsetTop(), 0, 0);
                sheet.setPadding(sheet.getPaddingLeft(), sheet.getPaddingTop(), sheet.getPaddingRight(), in.getSystemWindowInsetBottom());
                dropBar.setPadding(dp(16), in.getSystemWindowInsetTop() + dp(8), dp(16), 0);
                return in;
            }
        });
        scene.setOnDragListener(dragListener);
        loadApps();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED); f.addAction(Intent.ACTION_PACKAGE_REMOVED); f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
        if (Build.VERSION.SDK_INT >= 23 && !U.prefs(this).getBoolean("asked_loc", false)) {
            U.prefs(this).edit().putBoolean("asked_loc", true).apply();
            requestPermissions(new String[] {Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        Weather.refresh(this, true, wxDone);
    }

    @Override protected void onStart() {
        super.onStart();
        try { widgetHost.startListening(); } catch (Throwable ignored) { }
    }
    @Override protected void onStop() {
        super.onStop();
        // leaving (a call, the lock, another app): nothing may stay half-open, invisible and catching touches
        if (drawer != null && drawer.getVisibility() == View.VISIBLE) closeDrawerNow();
        if (quick != null && quick.getVisibility() == View.VISIBLE) { quick.animate().cancel(); quick.setVisibility(View.GONE); quick.setAlpha(1f); }
        if (home != null) { home.animate().cancel(); home.setAlpha(1f); }
        try { widgetHost.stopListening(); } catch (Throwable ignored) { }
    }

    private Boolean iconsOn;
    static boolean visible;
    private boolean alarmWatch;
    private boolean countdownOn() { return U.prefs(this).getString("countdown", "off").equals("on"); }
    // ---------------------------------------------------------------- the second home page
    /** 0 = the main page (clock, verse, weather and your apps); 1 = a second page, all for apps and widgets. */
    private int page;
    private View clockV;
    private TextView pageDots;
    private String deskKey() { return page == 0 ? "desk" : "desk_p2"; }
    private String widgetsKey() { return page == 0 ? "widgets" : "widgets_p2"; }
    private View[] infoViews() { return new View[] {clockV, card, wxPill, alertLine, nextLine, churchLine}; }
    /** dir: +1 = the new page comes in from the right, -1 from the left. */
    private void setPage(int p, final int dir) {
        if (p == page) return;
        page = p;
        if (p == 1) { for (View v : infoViews()) if (v != null) v.setVisibility(View.GONE); }
        else { clockV.setVisibility(View.VISIBLE); card.setVisibility(View.VISIBLE); wxPill.setVisibility(View.VISIBLE); nextLine.setVisibility(countdownOn() ? View.VISIBLE : View.GONE); showWeather(); showAlarm(); }
        if (resizing != null) endResize();
        showDesk();
        paintDots();
        final View box = (View) desk.getParent();
        ui.removeCallbacks(settleAll); ui.postDelayed(settleAll, 700);    // whatever happens, it ends in place
        if (U.anims(this) && hasWindowFocus()) {
            box.setTranslationX(dir * scene.getWidth() * .35f); box.setAlpha(0f);
            box.animate().translationX(0).alpha(1f).setDuration(320).setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
            if (p == 0) for (View v : infoViews()) if (v != null && v.getVisibility() == View.VISIBLE) {
                v.setAlpha(0f); v.setTranslationX(dir * dp(40));
                v.animate().alpha(1f).translationX(0).setDuration(320).setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
            }
        }
    }
    private void paintDots() {
        if (pageDots == null) return;
        android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder();
        for (int i = 0; i < 2; i++) {
            int st = b.length();
            b.append(i == 0 ? "●" : "  ●");
            b.setSpan(new android.text.style.ForegroundColorSpan(i == page ? 0xFFFFFFFF : 0x66FFFFFF), st, b.length(), 0);
        }
        pageDots.setText(b);
    }

    // ---------------------------------------------------------------- swipe down: recent apps and VIP
    private FrameLayout quick;
    private boolean quickShowing() { return quick != null && quick.getVisibility() == View.VISIBLE; }
    private void showQuick() {
        if (quick == null) {
            quick = new FrameLayout(this);
            quick.setBackgroundColor(0x66060A18);
            quick.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { hideQuick(); } });
            scene.addView(quick, new FrameLayout.LayoutParams(-1, -1));
        }
        quick.removeAllViews();
        Glass panel = new Glass(this, 30, Glass.DARK);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true);
        int topIn = dp(24);
        try { topIn = getWindow().getDecorView().getRootWindowInsets().getSystemWindowInsetTop(); } catch (Throwable ignored) { }
        panel.setPadding(dp(16), topIn + dp(30) + dp(10), dp(16), dp(18));   // clear of the status bar
        int cols = 4;
        // recently used
        ArrayList<App> rec = new ArrayList<>();
        for (String k : U.prefs(this).getString("recent", "").split("\\|")) { App a = byKey.get(k); if (a != null && !hidden(a)) rec.add(a); if (rec.size() >= 8) break; }
        TextView rh = quickHead(L.t("Recently used")); rh.setTextColor(RECENT); panel.addView(rh);
        if (rec.isEmpty()) panel.addView(quickNote(L.t("Apps you open will show here.")));
        else panel.addView(quickGrid(rec, cols, RECENT));
        // VIP
        ArrayList<App> vip = vipApps();
        panel.addView(quickHead("VIP  ★"));
        if (vip.isEmpty()) panel.addView(quickNote("Long-press any app and choose \"Add to VIP\"."));
        else panel.addView(quickGrid(vip, cols, GOLD));
        TextView hint = text(11.5f, 0x99FFFFFF, U.sans(this));
        hint.setText(L.t("Swipe up or tap outside to close"));
        hint.setGravity(Gravity.CENTER); hint.setPadding(0, dp(12), 0, 0);
        panel.addView(hint);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        pl.topMargin = -dp(30);
        quick.addView(panel, pl);
        quick.setVisibility(View.VISIBLE);
        scene.setLiving(false);
        quick.setAlpha(0f);
        quick.animate().alpha(1f).setDuration(180).start();
        panel.setTranslationY(-dp(420));
        panel.animate().translationY(0).setDuration(300).setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
    }
    private void hideQuick() {
        if (!quickShowing()) return;
        quick.animate().alpha(0f).setDuration(180).withEndAction(new Runnable() { @Override public void run() {
            quick.setVisibility(View.GONE); quick.setAlpha(1f); } }).start();
        if (quick.getChildCount() > 0) quick.getChildAt(0).animate().translationY(-dp(420)).setDuration(220).start();
        scene.setLiving(U.anims(this));
    }
    private TextView quickHead(String s) {
        TextView t = text(12.5f, 0xFFF2B640, U.sansBold(this));
        t.setText(s); t.setPadding(dp(6), dp(10), 0, dp(6));
        return t;
    }
    private TextView quickNote(String s) {
        TextView t = text(13, 0xCCFFFFFF, U.sans(this));
        t.setText(s); t.setPadding(dp(6), dp(4), 0, dp(8));
        return t;
    }
    static final int RECENT = 0xFF8FD0FF, GOLD = 0xFFF2B640;   // recent apps: light blue · VIP and folders: gold
    /** Colours the name under a tile. */
    private static View tint(View t, int c) {
        if (t instanceof TextView) ((TextView) t).setTextColor(c);
        else if (t instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) t).getChildCount(); i++) tint(((ViewGroup) t).getChildAt(i), c);
        return t;
    }
    private View quickGrid(ArrayList<App> l, int cols) { return quickGrid(l, cols, 0); }
    private View quickGrid(ArrayList<App> l, int cols, int color) {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = null;
        for (int i = 0; i < l.size(); i++) {
            if (i % cols == 0) { row = new LinearLayout(this); g.addView(row, lp(-1, -2)); }
            final App a = l.get(i);
            View t = tile(a, true, 48);
            if (color != 0) tint(t, color);
            t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { hideQuick(); open(a, v); } });
            t.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) {
                PopupMenu pm = new PopupMenu(Launcher.this, v, Gravity.CENTER);
                pm.getMenu().add(0, 1, 0, isVip(a) ? L.t("Remove from VIP") : L.t("Add to VIP  ★"));
                pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override public boolean onMenuItemClick(android.view.MenuItem it) { setVip(a, !isVip(a)); showQuick(); return true; } });
                pm.show(); return true; } });
            row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        if (row != null) for (int k = row.getChildCount(); k < cols; k++) row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        return g;
    }
    /** Home-screen parts that settle into place, one after the other, when you come back home. */
    private final ArrayList<View> enter = new ArrayList<>();
    private boolean everResumed;
    private final Runnable settleAll = new Runnable() { @Override public void run() { settle(); } };
    /** Everything on the home screen in its place and fully shown (ends any entrance animation). */
    private void settle() {
        java.util.ArrayList<View> all = new java.util.ArrayList<>(enter);
        for (View v : infoViews()) if (v != null && !all.contains(v)) all.add(v);
        if (desk != null && desk.getParent() instanceof View) all.add((View) desk.getParent());
        if (dockWrap != null) all.add(dockWrap);
        for (View v : all) {
            v.animate().cancel();
            v.setAlpha(1f); v.setTranslationX(0); v.setTranslationY(0); v.setScaleX(1f); v.setScaleY(1f);
        }
        // an app list or panel left half-faded (its animation stopped by a call or the screen going off)
        // would cover the home screen, see-through, and swallow every touch: close it for good
        if (drawer != null && drawer.getVisibility() == View.VISIBLE && drawer.getAlpha() < .98f) closeDrawerNow();
        if (quick != null && quick.getVisibility() == View.VISIBLE && quick.getAlpha() < .98f) { quick.animate().cancel(); quick.setVisibility(View.GONE); quick.setAlpha(1f); }
        if (drawer != null && drawer.getVisibility() != View.VISIBLE) { home.animate().cancel(); home.setAlpha(1f); home.setVisibility(View.VISIBLE); }
    }
    private long resumedAt;
    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        // back from the lock screen or another app: whatever was half-way, put it in place
        if (focus) { ui.removeCallbacks(settleAll); ui.postDelayed(settleAll, 900); ui.postDelayed(settleAll, 2500); }
    }
    private void entrance() {
        settle();
        if (!U.anims(this) || drawer.getVisibility() == View.VISIBLE) return;
        // never while the phone is locked or the screen is off: the animation would not run and leave things hidden
        android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        if ((km != null && km.isKeyguardLocked()) || (pm != null && !pm.isInteractive())) return;
        ui.removeCallbacks(settleAll);
        ui.postDelayed(settleAll, 1400);      // whatever happens, everything is fully shown after this
        android.view.animation.DecelerateInterpolator di = new android.view.animation.DecelerateInterpolator(2f);
        for (int i = 0; i < enter.size(); i++) {
            View v = enter.get(i);
            v.animate().cancel();
            v.setAlpha(0f); v.setTranslationY(dp(14)); v.setScaleX(.985f); v.setScaleY(.985f);
            v.animate().alpha(1f).translationY(0).scaleX(1f).scaleY(1f).setStartDelay(i * 40L).setDuration(420)
                    .setInterpolator(di).withLayer().start();
        }
        if (dockWrap != null) {
            dockWrap.animate().cancel();
            dockWrap.setTranslationY(dp(24)); dockWrap.setAlpha(0f);
            dockWrap.animate().translationY(0).alpha(1f).setStartDelay(120).setDuration(460).setInterpolator(di).withLayer().start();
        }
    }
    private final Runnable redot = new Runnable() { @Override public void run() { if (scene != null && visible) invalidateAll(scene); } };
    @Override protected void onResume() {
        visible = true;
        resumedAt = android.os.SystemClock.uptimeMillis();
        // once: ask to read the calendar, for the next events on the lock screen
        if (Build.VERSION.SDK_INT >= 23 && !Events.allowed(this) && !U.prefs(this).getBoolean("cal_asked", false)
                && U.prefs(this).getString("lock_events", "on").equals("on")) {
            U.prefs(this).edit().putBoolean("cal_asked", true).apply();
            try { requestPermissions(new String[] {android.Manifest.permission.READ_CALENDAR}, 41); } catch (Throwable ignored) { }
        }
        LockSaver.watch(this);
        if (LockSaver.ENABLED && U.prefs(this).getString("lock_saver", "on").equals("on")) {
            LockSaver.prepare(this);
            if (!LockSaver.ready(this) && !U.prefs(this).getBoolean("lock_setup_asked", false)) {
                U.prefs(this).edit().putBoolean("lock_setup_asked", true).apply();
                ui.postDelayed(new Runnable() { @Override public void run() { LockSetup.show(Launcher.this); } }, 1500);
            }
        }
        showAlarm();
        if (!alarmWatch) { alarmWatch = true;
            try { registerReceiver(alarmChanged, new IntentFilter(android.app.AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)); } catch (Throwable ignored) { } }
        // a text size chosen in Settings while permission was still being given
        String ps = U.prefs(this).getString("pending_scale", null);
        if (ps != null && (Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(this))) {
            U.prefs(this).edit().remove("pending_scale").apply();
            try { Settings.System.putFloat(getContentResolver(), Settings.System.FONT_SCALE, Float.parseFloat(ps)); } catch (Throwable ignored) { }
        }
        if (everResumed) entrance();
        everResumed = true;
        scene.setLiving(U.anims(this) && drawer.getVisibility() != View.VISIBLE);
        dotsOn = U.prefs(this).getString("dots", "on").equals("on");
        NotifDots.onChange = new Runnable() { @Override public void run() {
            ui.removeCallbacks(redot); ui.postDelayed(redot, 250); } };
        ui.post(redot);
        super.onResume();
        boolean ic = U.prefs(this).getBoolean("velthon_icons", false);
        if (iconsOn != null && iconsOn != ic) loadApps();
        iconsOn = ic;
        int cols = U.prefs(this).getInt("columns", 3);
        if (cols != desk.columns) { desk.columns = cols; desk.requestLayout(); }
        scene.refresh();
        if (U.prefs(this).getBoolean("want_hidden", false)) {
            U.prefs(this).edit().remove("want_hidden").apply();
            ui.postDelayed(new Runnable() { @Override public void run() { if (!apps.isEmpty()) hiddenAppsDialog(); } }, 400);
        }
        if (U.prefs(this).getBoolean("want_widget", false)) {
            U.prefs(this).edit().remove("want_widget").apply();
            ui.post(new Runnable() { @Override public void run() { pickWidget(); } });
        }
        showVerse();
        showWeather();
        showDesk();
        showDock();
        Weather.refresh(this, false, wxDone);
        Synax.load(this, new Runnable() { @Override public void run() { ui.post(new Runnable() { @Override public void run() { showWeather(); showSaint(); } }); } });
        scheduleHour();
        if (!minuteOn) {
            IntentFilter mf = new IntentFilter(Intent.ACTION_TIME_TICK);
            mf.addAction(Intent.ACTION_TIME_CHANGED); mf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            registerReceiver(minute, mf); minuteOn = true;
        }
        ui.removeCallbacks(countdown); countdown.run();
        startTilt();
        resting = false; ui.removeCallbacks(rest); ui.postDelayed(rest, 60_000);
        if (wxFloat != null && wxFloat.isPaused()) wxFloat.resume();
        ui.postDelayed(new Runnable() { @Override public void run() { sinkDock(); } }, 180);
    }

    private final Weather.Done wxDone = new Weather.Done() { @Override public void done() {
        showWeather(); scene.refresh();
        if (!Weather.has(Launcher.this)) { ui.removeCallbacks(wxRetry); ui.postDelayed(wxRetry, 3 * 60 * 1000L + 2000); }
    } };
    private final Runnable wxRetry = new Runnable() { @Override public void run() { Weather.refresh(Launcher.this, false, wxDone); } };

    /** Battery: the moving sky, the weather particles and the tilt sensor run for a minute after the home screen is
     *  shown or touched, then rest (a still picture) until the next touch. */
    private final Runnable rest = new Runnable() { @Override public void run() {
        scene.setLiving(false); stopTilt(); resting = true;
        if (wxFloat != null && wxFloat.isRunning()) wxFloat.pause();
    } };
    private boolean resting;
    private void awake() {
        ui.removeCallbacks(rest); ui.postDelayed(rest, 60_000);
        if (resting && visible) {
            resting = false;
            scene.setLiving(U.anims(this) && drawer.getVisibility() != View.VISIBLE);
            startTilt();
            if (wxFloat != null && wxFloat.isPaused()) wxFloat.resume();
        }
    }

    @Override protected void onPause() {
        visible = false;
        ui.removeCallbacks(rest); resting = true;
        if (wxFloat != null && wxFloat.isRunning()) wxFloat.pause();   // never animate out of sight
        ui.removeCallbacks(settleAll);
        settle();
        scene.setLiving(false);
        super.onPause();
        ui.removeCallbacks(hourly);
        ui.removeCallbacks(wxRetry);
        ui.removeCallbacks(countdown);
        stopTilt();
        if (minuteOn) { try { unregisterReceiver(minute); } catch (Throwable ignored) { } minuteOn = false; }
        if (tts != null) tts.stop();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(packages); } catch (Throwable ignored) { }
        if (tts != null) tts.shutdown();
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (Intent.ACTION_MAIN.equals(i.getAction())) {
            if (quickShowing()) hideQuick();
            else if (drawer.getVisibility() != View.VISIBLE && page != 0) setPage(0, -1);   // Home goes back to the main page
            closeDrawer();
        }
    }

    @Override public void onBackPressed() {
        if (resizing != null) { endResize(); return; }
        if (quickShowing()) { hideQuick(); return; }
        if (drawer.getVisibility() != View.VISIBLE && page != 0) { setPage(0, -1); return; }
        if (drawer.getVisibility() == View.VISIBLE) closeDrawer();
    }

    private final Runnable hourly = new Runnable() {
        @Override public void run() { showVerse(); showWeather(); scene.refresh(); scheduleHour(); }
    };
    private long verseSlotShown = -1;
    private final BroadcastReceiver minute = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (U.verseSlot(Launcher.this) != verseSlotShown) showVerse();
        }
    };
    private boolean minuteOn;

    private void scheduleHour() {
        ui.removeCallbacks(hourly);
        long now = System.currentTimeMillis();
        long next = (now / 3600000L + 1) * 3600000L + 1500 - java.util.TimeZone.getDefault().getOffset(now) % 3600000L;
        if (next <= now) next += 3600000L;
        ui.postDelayed(hourly, Math.min(next - now, 15 * 60 * 1000L));
    }

    // ---------------------------------------------------------------- small builders
    private int dp(float v) { return U.dpi(this, v); }

    private TextView text(float size, int color, android.graphics.Typeface tf) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size * textScale);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }
    private static void shadow(TextView t, float r) { t.setShadowLayer(r, 0, r / 3f, 0x99000000); }

    private ImageView icon(int type, int sizeDp, int color) {
        ImageView v = new ImageView(this);
        v.setImageDrawable(new Ico(type, color));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return v;
    }

    private Glass chip(int iconType, String label, View.OnClickListener l) {
        Glass g = new Glass(this, 999, Glass.NONE);
        g.setOrientation(LinearLayout.HORIZONTAL);
        g.setGravity(Gravity.CENTER_VERTICAL);
        g.setPadding(dp(10), dp(5), dp(11), dp(5));
        g.addView(icon(iconType, 13, Color.WHITE));
        TextView t = text(12, Color.WHITE, U.sans(this));
        t.setText(label);
        t.setSingleLine(true);
        t.setPadding(dp(5), 0, 0, 0);
        g.addView(t);
        g.setOnClickListener(l);
        g.setTag(t);
        return g;
    }

    private static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    // ---------------------------------------------------------------- home
    private void buildHome() {
        home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        scene.addView(home, new FrameLayout.LayoutParams(-1, -1));

        // the top, centred: the date, then the big glass clock across the screen
        dateView = new TextClock(this);
        dateView.setFormat12Hour("EEE d MMM"); dateView.setFormat24Hour("EEE d MMM");
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.5f * textScale);
        dateView.setTextColor(Color.WHITE);
        dateView.setTypeface(U.sans(this));
        dateView.setGravity(Gravity.CENTER);
        shadow(dateView, 5);
        dateView.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openCalendar(); } });
        // the date, with Settings and the Screensaver as two small icons on either side (no extra room taken)
        // one row: [settings] [date · alarm, centred in the space left] [screensaver]. Nothing overlaps: the date
        // and alarm shrink to fit between the two icons, whatever the phone's text size.
        LinearLayout dateRow = new LinearLayout(this);
        dateRow.setGravity(Gravity.CENTER_VERTICAL);
        final LinearLayout dateMid = new LinearLayout(this);
        dateMidV = dateMid;
        dateMid.setGravity(Gravity.CENTER);
        dateMid.addView(dateView);
        alarmLine = text(13, Color.WHITE, android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        alarmLine.setSingleLine(true);
        alarmLine.setVisibility(View.GONE);
        alarmLine.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openAlarms(); } });
        dateMid.addView(alarmLine);
        settingsLink = link(Ico.GEAR, null, new View.OnClickListener() { @Override public void onClick(View v) { openSettings(); } });
        settingsLink.setContentDescription(L.t("Launcher settings"));
        dateRow.addView(settingsLink, lp(dp(40), dp(44)));
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(0, -2, 1f); ml.leftMargin = ml.rightMargin = dp(4);
        dateRow.addView(dateMid, ml);
        saverLink = link(Ico.MOON, null, new View.OnClickListener() { @Override public void onClick(View v) {
            startActivity(new Intent(Launcher.this, PreviewActivity.class)); } });
        saverLink.setContentDescription(L.t("Screensaver"));
        dateRow.addView(saverLink, lp(dp(40), dp(44)));
        dateMid.addOnLayoutChangeListener(new View.OnLayoutChangeListener() { @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
            if (r - l != or - ol) ui.post(new Runnable() { @Override public void run() { fitDateRow(dateMid); } }); } });
        LinearLayout.LayoutParams dl = lp(-1, -2); dl.topMargin = dp(2); dl.leftMargin = dl.rightMargin = dp(14);
        home.addView(dateRow, dl);
        enter.add(dateRow);
        GlassClock clock = new GlassClock(this, 230);
        clockV = clock;
        clock.fit = true;                       // as wide as the screen allows, like the grey picture
        clock.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openAlarms(); } });
        LinearLayout.LayoutParams tl = lp(-1, -2); tl.topMargin = dp(2); tl.leftMargin = tl.rightMargin = dp(14);
        home.addView(clock, tl);

        enter.add(clock);

        churchLine = text(13.5f, 0xFFF0B43C, U.sans(this));
        churchLine.setGravity(Gravity.CENTER);
        churchLine.setMaxLines(3);
        churchLine.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openChurch(); } });
        shadow(churchLine, 5);

        // the verse, in the heart of the sun
        card = new Glass(this, 24, Glass.NONE);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(26), dp(6), dp(26), dp(4));
        en = text(24, Color.WHITE, android.graphics.Typeface.DEFAULT);
        en.setGravity(Gravity.CENTER); en.setLineSpacing(0, 1.06f); shadow(en, 4);
        card.addView(en, lp(-1, -2));
        enRef = text(15, 0xFFF0B43C, android.graphics.Typeface.DEFAULT);
        enRef.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rl = lp(-1, -2); rl.topMargin = dp(6);
        card.addView(enRef, rl);
        ar = text(24, Color.WHITE, android.graphics.Typeface.DEFAULT);
        ar.setGravity(Gravity.CENTER); ar.setLineSpacing(0, 1.12f); shadow(ar, 4);
        ar.setTextDirection(View.TEXT_DIRECTION_RTL);
        LinearLayout.LayoutParams al = lp(-1, -2); al.topMargin = dp(8);
        card.addView(ar, al);
        arRef = text(15, 0xFFF0B43C, android.graphics.Typeface.DEFAULT);
        arRef.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams arl = lp(-1, -2); arl.topMargin = dp(3);
        card.addView(arRef, arl);
        // Listen / Save / Share stay available: hold the verse
        chips = new LinearLayout(this);
        chips.setVisibility(View.GONE);
        final Glass save = chip(Ico.HEART, L.t("Save"), new View.OnClickListener() { @Override public void onClick(View v) { toggleSave(); } });
        saveChip = (TextView) save.getTag();
        saveIcon = (ImageView) save.getChildAt(0);
        chips.addView(save);
        card.addView(chips);
        card.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openVerses(null); } });
        card.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) { verseMenu(v); return true; } });
        LinearLayout.LayoutParams cardL = lp(-1, -2); cardL.topMargin = -dp(16);   // up into the clock's faded lower edge
        clock.setTranslationZ(dp(1));            // the clock stays on top, so a tap on it always opens the alarms
        home.addView(card, cardL);
        enter.add(card);
        card.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() { focusSky(); }
        });

        // the weather: "City · 4°C", then "Now Sunny · 12 PM 6° · 3 PM 7° · 6 PM 3°"
        wxPill = new Glass(this, 0, Glass.NONE);
        wxPill.setOrientation(LinearLayout.VERTICAL);
        wxPill.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout now = new LinearLayout(this);
        now.setGravity(Gravity.CENTER);
        wxIcon = new ImageView(this);
        wxIcon.setVisibility(View.GONE);
        LinearLayout.LayoutParams wil = lp(dp(28), dp(28)); wil.rightMargin = dp(6);
        now.addView(wxIcon, wil);
        wxTemp = text(15.5f, Color.WHITE, U.sansBold(this));
        wxTemp.setSingleLine(true);
        shadow(wxTemp, 5);
        now.addView(wxTemp);
        wxNow = now;
        wxPill.addView(now, lp(-2, -2));
        wxText = text(14.5f, Color.WHITE, U.sans(this));
        wxText.setSingleLine(true); wxText.setEllipsize(TextUtils.TruncateAt.END);
        wxText.setGravity(Gravity.CENTER);
        shadow(wxText, 4);
        LinearLayout.LayoutParams wtl = lp(-1, -2); wtl.topMargin = dp(3);
        wxPill.addView(wxText, wtl);
        wxPill.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { weatherSheet(); Weather.refresh(Launcher.this, true, wxDone); } });
        LinearLayout.LayoutParams wl = lp(-1, -2); wl.topMargin = 0; wl.leftMargin = wl.rightMargin = dp(18);
        home.addView(wxPill, wl);
        enter.add(wxPill);
        forecastRow = new LinearLayout(this);
        forecastRow.setVisibility(View.GONE);
        alertLine = text(13, 0xFFFF8A80, U.sansBold(this));
        alertLine.setGravity(Gravity.CENTER);
        alertLine.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { weatherSheet(); } });
        alertLine.setSingleLine(true); alertLine.setEllipsize(TextUtils.TruncateAt.END);
        shadow(alertLine, 5);
        LinearLayout.LayoutParams all = lp(-1, -2); all.topMargin = dp(2); all.leftMargin = all.rightMargin = dp(20);
        home.addView(alertLine, all);
        // "Next verse in 04:32" (tap for the next one now), then the church line
        nextLine = text(14, Color.WHITE, U.sans(this));
        nextLine.setGravity(Gravity.CENTER);
        shadow(nextLine, 4);
        nextLine.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            U.prefs(Launcher.this).edit().putInt("verse_shift", U.prefs(Launcher.this).getInt("verse_shift", 0) + 1).apply();
            showVerse(); } });
        LinearLayout.LayoutParams nll = lp(-1, -2); nll.topMargin = dp(6);
        home.addView(nextLine, nll);
        LinearLayout.LayoutParams chl = lp(-1, -2); chl.topMargin = dp(3); chl.leftMargin = chl.rightMargin = dp(20);
        home.addView(churchLine, chl);

        // your apps and widgets
        FrameLayout deskBox = new FrameLayout(this);
        desk = new Desk(this);
        desk.columns = U.prefs(this).getInt("columns", 3);
        desk.setLongClickable(true);
        FrameLayout.LayoutParams dsl = new FrameLayout.LayoutParams(-1, -1); dsl.bottomMargin = dp(12);   // room for the page dots
        deskBox.addView(desk, dsl);
        deskHint = text(13, 0xB3FFFFFF, U.sans(this));
        deskHint.setGravity(Gravity.CENTER);
        deskHint.setText("");
        deskHint.setVisibility(View.GONE);
        deskHint.setPadding(dp(24), 0, dp(24), 0);
        shadow(deskHint, 4);
        deskBox.addView(deskHint, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        desk.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) { homeMenu(v); return true; } });
        LinearLayout.LayoutParams dkl = new LinearLayout.LayoutParams(-1, 0, 1f); dkl.topMargin = dp(8);
        home.addView(deskBox, dkl);
        final View dbx = deskBox;
        deskBox.addOnLayoutChangeListener(new View.OnLayoutChangeListener() { @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
            if (b - t < dp(160)) ui.post(new Runnable() { @Override public void run() { keepDeskRoom(dbx); } }); } });
        pageDots = text(9, Color.WHITE, U.sans(this));
        pageDots.setGravity(Gravity.CENTER);
        pageDots.setShadowLayer(dp(3), 0, dp(1), 0x99000000);
        deskBox.addView(pageDots, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        paintDots();
        enter.add(deskBox);

        // search
        search = new Glass(this, 999, Glass.CLEAR);
        search.setOrientation(LinearLayout.HORIZONTAL);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setPadding(dp(16), 0, dp(14), 0);
        search.addView(icon(Ico.SEARCH, 18, Color.WHITE));
        TextView hint = text(14, 0xD9FFFFFF, U.sans(this));
        hint.setText(L.t("Search apps"));
        hint.setSingleLine(true);
        hint.setPadding(dp(10), 0, 0, 0);
        search.addView(hint, new LinearLayout.LayoutParams(0, -2, 1f));
        search.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openDrawer(true); } });
        LinearLayout.LayoutParams sl2 = lp(-1, dp(42)); sl2.leftMargin = sl2.rightMargin = dp(22); sl2.topMargin = dp(4);
        // no search bar on the home screen: the dock is the only bar; search lives at the top of the app list


        // the dock: up to six apps, nothing else. Frosted bar with a notch; the chosen app rises into a gold circle.
        dockWrap = new FrameLayout(this);
        dockWrap.setClipChildren(false);
        dockBar = new Glass(this, 0, Glass.BAND);
        dockBar.notchR = dp(31);
        dockBar.notchCy = -dp(9);
        FrameLayout dockBox = new FrameLayout(this);
        dockRow = new LinearLayout(this);
        dockRow.setGravity(Gravity.CENTER);
        dockRow.setPadding(dp(14), 0, dp(14), 0);
        dockBox.addView(dockRow, new FrameLayout.LayoutParams(-1, -1, Gravity.TOP));
        dockHint = text(12.5f, 0xCC3A2E1E, U.sans(this));
        dockHint.setVisibility(View.GONE);
        dockHint.setText(L.t("Drag up to 6 apps here"));
        dockBox.addView(dockHint, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        View handle = new View(this);
        android.graphics.drawable.GradientDrawable hd = new android.graphics.drawable.GradientDrawable();
        hd.setColor(0x99FFFFFF); hd.setCornerRadius(dp(2));
        handle.setBackground(hd);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(dp(64), dp(4), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hl.bottomMargin = dp(7);
        handle.setVisibility(View.GONE);     // the strip has no handle line
        dockBox.addView(handle, hl);
        dockBar.addView(dockBox, new LinearLayout.LayoutParams(-1, -1));
        dockBar.setClipChildren(false); dockBar.setClipToPadding(false);
        dockBox.setClipChildren(false); dockRow.setClipChildren(false); dockRow.setClipToPadding(false);
        dockWrap.addView(dockBar, new FrameLayout.LayoutParams(-1, dp(66), Gravity.BOTTOM));
        dockBubble = new Bubble(this);
        dockBubble.setVisibility(View.GONE);
        dockBubble.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            App a = dockApp(dockSel); if (a != null) launch(a, v); } });
        dockBubble.setPivotY(dockBubble.r + dp(1));
        dockBubble.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) {
            App a = dockApp(dockSel); if (a != null) startDrag(v, a, "dock", dockSel); return true; } });
        dockWrap.addView(dockBubble, new FrameLayout.LayoutParams(dp(80), dp(76), Gravity.TOP | Gravity.LEFT));
        dockBar.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) { placeBubble(); }
        });
        dockRow.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (r - l != or - ol || b - t != ob - ot) dockHoles(); }
        });
        LinearLayout.LayoutParams dbl = lp(-1, dp(66 + 30)); dbl.leftMargin = dbl.rightMargin = 0; dbl.topMargin = dp(2); dbl.bottomMargin = dp(8);
        home.addView(dockWrap, dbl);

        // make the verse card fit; it never takes more than about a third of the screen
        card.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (fitFor != verse || r - l != or - ol) ui.post(new Runnable() { @Override public void run() { fitCard(); } });
            }
        });

    }

    // ---------------------------------------------------------------- swipes, anywhere on the home screen
    /** Like the well-known launchers: a swipe that starts anywhere (on the sky, the verse, an icon or a widget)
     *  does something. Up, down, left and right each have an action, chosen in Settings › Gestures. A short
     *  slow drag counts as well as a fast flick. */
    private float gx0, gy0; private long gt0; private boolean gDone, gTrack, gInList, gOnStrip;
    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) awake();
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            dragging = false;                                   // a finger coming down means no drag is going on
            if (resizing != null && resizing.getParent() == null) resizing = null;
        }
        if (quickShowing()) {   // the recent / VIP panel: a swipe up closes it
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { gx0 = e.getX(); gy0 = e.getY(); }
            if (e.getActionMasked() == MotionEvent.ACTION_UP && gy0 - e.getY() > dp(70) && Math.abs(gy0 - e.getY()) > Math.abs(e.getX() - gx0)) { hideQuick(); return true; }
            return super.dispatchTouchEvent(e);
        }
        boolean inList = drawer.getVisibility() == View.VISIBLE && drawer.getAlpha() > .5f;
        boolean free = !dragging && resizing == null;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                gx0 = e.getX(); gy0 = e.getY(); gt0 = e.getEventTime(); gDone = false;
                gInList = inList;
                gOnStrip = inList && (inside(folderRow, e) || inside(catScroll, e));   // those scroll sideways themselves
                gTrack = free && gy0 > dp(30)             // the very top edge belongs to the phone's own shade
                        && e.getDownTime() > resumedAt + 150;  // and the swipe that unlocked the phone is not one of ours
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                gTrack = false; break;
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP:
                if (!gTrack || gDone || !free) break;
                float dx = e.getX() - gx0, dy = e.getY() - gy0, ax = Math.abs(dx), ay = Math.abs(dy);
                long dt = Math.max(1, e.getEventTime() - gt0);
                boolean far = Math.max(ax, ay) > dp(90);
                boolean flick = e.getActionMasked() == MotionEvent.ACTION_UP && Math.max(ax, ay) > dp(40) && Math.max(ax, ay) / dt > dp(1) / 2f;
                if (!far && !flick) break;
                if (Math.max(ax, ay) < Math.min(ax, ay) * 1.3f) break;   // diagonal: not sure what was meant
                if (gInList) {
                    // in the app list: left / right = the next category, down (from the top of the list) = close
                    boolean side = ax > ay;
                    if (side && gOnStrip) { gTrack = false; break; }
                    if (!side && (dy < 0 || !atTop())) { gTrack = false; break; }
                    gDone = true;
                    MotionEvent c0 = MotionEvent.obtain(e); c0.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(c0); c0.recycle();
                    if (side) stepCat(dx < 0 ? 1 : -1); else closeDrawer();
                    return true;
                }
                gDone = true;
                MotionEvent c = MotionEvent.obtain(e); c.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(c); c.recycle();   // the icon or widget under the finger lets go
                String dir = ax > ay ? (dx < 0 ? "left" : "right") : (dy < 0 ? "up" : "down");
                lastDir = dir;
                if (page == 1 && ax > ay) { setPage(0, dx < 0 ? 1 : -1); return true; }   // any side swipe on page 2: back to the main page
                gesture(U.prefs(this).getString("swipe_" + dir, defaultSwipe(dir)));
                return true;
        }
        if (gDone) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) gDone = false;
            return true;
        }
        if (e.getActionMasked() == MotionEvent.ACTION_UP && gTrack && free && !gInList && e.getEventTime() - gt0 < 300
                && Math.hypot(e.getX() - gx0, e.getY() - gy0) < dp(12)) emptyTap(e);
        return super.dispatchTouchEvent(e);
    }
    /** A tap on nothing in particular (the sky, between icons): two of them quickly = the double-tap action. */
    private long lastTap; private float tapX, tapY;
    private void emptyTap(MotionEvent e) {
        if (findClickable(scene, e.getRawX(), e.getRawY())) { lastTap = 0; return; }
        scene.poke(e.getX(), e.getY());           // the weather answers the touch
        long t = e.getEventTime();
        if (t - lastTap < 400 && Math.hypot(e.getX() - tapX, e.getY() - tapY) < dp(60)) {
            lastTap = 0;
            gesture(U.prefs(this).getString("double_tap", "lock"));
        } else { lastTap = t; tapX = e.getX(); tapY = e.getY(); }
    }
    private boolean findClickable(View v, float rx, float ry) {
        if (v.getVisibility() != View.VISIBLE) return false;
        int[] a = new int[2]; v.getLocationOnScreen(a);
        if (rx < a[0] || ry < a[1] || rx > a[0] + v.getWidth() || ry > a[1] + v.getHeight()) return false;
        if (v != scene && v != desk && v != home && (v.isClickable() || v.isLongClickable() || v instanceof android.appwidget.AppWidgetHostView)) return true;
        if (v instanceof ViewGroup) for (int i = ((ViewGroup) v).getChildCount() - 1; i >= 0; i--) if (findClickable(((ViewGroup) v).getChildAt(i), rx, ry)) return true;
        return false;
    }
    private void lockScreen() {
        if (LockService.lock()) return;
        if (Build.VERSION.SDK_INT < 28) { startActivity(new Intent(this, PreviewActivity.class)); return; }
        Toast.makeText(this, "To lock with a double tap, turn on \"Velthon double-tap lock\" (Accessibility › Installed apps)", Toast.LENGTH_LONG).show();
        try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); } catch (Throwable ignored) { }
    }
    private boolean inside(View v, MotionEvent e) {
        if (v == null || v.getVisibility() != View.VISIBLE) return false;
        int[] a = new int[2]; v.getLocationOnScreen(a);
        return e.getRawX() >= a[0] && e.getRawX() <= a[0] + v.getWidth() && e.getRawY() >= a[1] && e.getRawY() <= a[1] + v.getHeight();
    }
    private String lastDir = "right";
    static String defaultSwipe(String dir) {
        switch (dir) { case "up": return "search"; case "down": return "quick"; case "left": return "church"; default: return "page"; }
    }
    private void gesture(String what) {
        try { getWindow().getDecorView().performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY); } catch (Throwable ignored) { }
        switch (what) {
            case "apps": openDrawer(false); break;
            case "search": openDrawer(true); break;
            case "notifications": expandNotifications(); break;
            case "quick_settings": try { Object sb = getSystemService("statusbar"); sb.getClass().getMethod("expandSettingsPanel").invoke(sb); } catch (Throwable t) { expandNotifications(); } break;
            case "verses": startActivity(new Intent(this, VersesActivity.class)); overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out); break;
            case "saver": startActivity(new Intent(this, PreviewActivity.class)); break;
            case "weather": weatherSheet(); break;
            case "phone_settings": try { startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Throwable ignored) { } break;
            case "settings": openSettings(); break;
            case "lock": lockScreen(); break;
            case "page": setPage(page == 0 ? 1 : 0, lastDir.equals("left") ? 1 : -1); break;
            case "quick": showQuick(); break;
            case "church":
                startActivity(new Intent(this, ChurchActivity.class));
                overridePendingTransition(lastDir.equals("right") ? android.R.anim.slide_in_left : android.R.anim.fade_in, android.R.anim.fade_out);
                break;
            default: break;
        }
    }

    private float cardFrac = .18f;
    /** The apps always keep room for two rows: when the words above grow (a feast day, a long forecast), they give way. */
    private void keepDeskRoom(View box) {
        if (page != 0 || box.getHeight() == 0 && scene.getHeight() == 0) return;
        int need = dp(160);
        if (box.getHeight() >= need) return;
        if (churchLine.getMaxLines() > 2) { churchLine.setMaxLines(2); churchLine.setEllipsize(android.text.TextUtils.TruncateAt.END); return; }
        if (cardFrac > .11f) { cardFrac -= .025f; fitCard(); return; }
    }
    private U.Verse fitFor;
    /** Picks the largest verse size that keeps the card within about a third of the screen. */
    private void fitCard() {
        if (verse == null || card.getWidth() == 0) return;
        fitFor = verse;
        String langs = U.langs(this);
        boolean both = langs.equals("both");
        int maxH = Math.round(scene.getHeight() * cardFrac);
        int wSpec = View.MeasureSpec.makeMeasureSpec(card.getWidth(), View.MeasureSpec.EXACTLY);
        int hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        ar.setVisibility(both || langs.equals("ar") ? View.VISIBLE : View.GONE);
        arRef.setVisibility(ar.getVisibility());
        for (int pass = 0; pass < 2; pass++) {
            String ts = U.prefs(this).getString("text_scale", "auto");
            float f = ts.equals("small") ? .88f : ts.equals("large") ? 1.12f : 1f;
            for (float sz = (both ? 19 : 26) * f; sz >= (both ? 14 : 15f) * f; sz -= .5f) {
                en.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sz);
                ar.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sz * 1.04f);
                card.measure(wSpec, hSpec);
                if (card.getMeasuredHeight() <= maxH) { card.requestLayout(); return; }
            }
            if (!both) break;
            ar.setVisibility(View.GONE); arRef.setVisibility(View.GONE);   // only one language fits here
            both = false;
        }
        card.requestLayout();
    }

    private void expandNotifications() {
        try {
            Object sb = getSystemService("statusbar");
            sb.getClass().getMethod("expandNotificationsPanel").invoke(sb);
        } catch (Throwable ignored) { }
    }

    private LinearLayout settingsLink, saverLink, dateMidV;
    /** Shrinks the date and the alarm together until both fit on the line between the two icons. */
    private void fitDateRow(LinearLayout mid) {
        if (mid.getWidth() == 0) return;
        float base = 14.5f * textScale, alarmBase = 13f * textScale;
        float k = 1f;
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, base); alarmLine.setTextSize(TypedValue.COMPLEX_UNIT_DIP, alarmBase);
        for (int i = 0; i < 12; i++) {
            float need = dateView.getPaint().measureText(String.valueOf(dateView.getText())) + dateView.getPaddingLeft() + dateView.getPaddingRight();
            if (alarmLine.getVisibility() == View.VISIBLE)
                need += alarmLine.getPaint().measureText(String.valueOf(alarmLine.getText())) + alarmLine.getPaddingLeft() + alarmLine.getPaddingRight()
                        + ((LinearLayout.LayoutParams) alarmLine.getLayoutParams()).leftMargin;
            if (need <= mid.getWidth() - dp(2) || k < .62f) break;
            k *= .94f;
            dateView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, base * k); alarmLine.setTextSize(TypedValue.COMPLEX_UNIT_DIP, alarmBase * k);
        }
    }

    private LinearLayout link(int icon, String label, View.OnClickListener l) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(4), dp(6), dp(4), dp(6));
        ImageView i = new ImageView(this);
        i.setImageDrawable(new Ico(icon, Color.WHITE));
        if (label == null) {   // icon only: a comfortable touch target, the glyph centred
            r.setGravity(Gravity.CENTER);
            r.setPadding(0, 0, 0, 0);
            i.setElevation(0);
            r.addView(i, lp(dp(22), dp(22)));
            r.setOnClickListener(l);
            return r;
        }
        r.addView(i, lp(dp(18), dp(18)));
        TextView t = text(13.5f, Color.WHITE, U.sans(this));
        t.setText(label); t.setPadding(dp(7), 0, 0, 0);
        shadow(t, 4);
        r.addView(t);
        r.setOnClickListener(l);
        return r;
    }
    /** Holding the verse: listen, save, share or another verse. */
    private void verseMenu(View anchor) {
        if (verse == null) return;
        PopupMenu m = new PopupMenu(this, anchor, Gravity.CENTER);
        boolean saved = U.saved(this).contains(verse.index);
        m.getMenu().add(0, 1, 0, L.t("Listen"));
        m.getMenu().add(0, 2, 1, saved ? L.t("Remove from saved") : L.t("Save"));
        m.getMenu().add(0, 3, 2, L.t("Share"));
        m.getMenu().add(0, 4, 3, L.t("Next verse"));
        m.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override public boolean onMenuItemClick(android.view.MenuItem it) {
                switch (it.getItemId()) {
                    case 1: listen(); break;
                    case 2: toggleSave(); break;
                    case 3: share(); break;
                    default:
                        U.prefs(Launcher.this).edit().putInt("verse_shift", U.prefs(Launcher.this).getInt("verse_shift", 0) + 1).apply();
                        showVerse();
                }
                return true;
            }
        });
        m.show();
    }
    /** "Next verse in 04:32", every second while the home screen shows. */
    private final Runnable countdown = new Runnable() {
        @Override public void run() {
            if (nextLine == null) return;
            long period = U.versePeriod(Launcher.this) * 60000L;
            java.util.Calendar k = java.util.Calendar.getInstance();
            long ms = (k.get(java.util.Calendar.HOUR_OF_DAY) * 3600000L + k.get(java.util.Calendar.MINUTE) * 60000L + k.get(java.util.Calendar.SECOND) * 1000L + k.get(java.util.Calendar.MILLISECOND));
            long left = period - ms % period;
            if (U.verseSlot(Launcher.this) != verseSlotShown) showVerse();
            if (nextLine.getVisibility() != View.VISIBLE) {   // countdown hidden: no need to wake every second
                ui.postDelayed(this, Math.min(left, 60_000 - System.currentTimeMillis() % 60_000) + 50); return;
            }
            long sec = left / 1000;
            String t = sec >= 3600 ? String.format(Locale.US, "%d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60) : String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60);
            nextLine.setText(L.t("Next verse in ") + t);
            ui.postDelayed(this, 1000 - System.currentTimeMillis() % 1000);
        }
    };

    // ---------------------------------------------------------------- depth: the window moves a little as the phone tilts
    private android.hardware.SensorManager sensors;
    private float baseX = Float.NaN, baseY, tx, ty;
    private final android.hardware.SensorEventListener tilt = new android.hardware.SensorEventListener() {
        @Override public void onSensorChanged(android.hardware.SensorEvent e) {
            float gx = e.values[0], gy = e.values[1];
            if (Float.isNaN(baseX)) { baseX = gx; baseY = gy; }
            baseX += (gx - baseX) * .008f; baseY += (gy - baseY) * .008f;      // slowly re-centres to how you hold it
            float max = dp(26);
            float wantX = Math.max(-max, Math.min(max, (gx - baseX) / 3f * max));
            float wantY = Math.max(-max, Math.min(max, -(gy - baseY) / 3f * max));
            tx += (wantX - tx) * .25f; ty += (wantY - ty) * .25f;
            scene.setTilt(tx, ty);
            // the layers in front move the other way, a little: the verse floats, the dock leans with the phone
            if (drawer.getVisibility() != View.VISIBLE) {
                card.setTranslationX(-tx * .16f); card.setTranslationY(-ty * .1f);
                // the dock stays flat and still (it is the steady base of the screen)
            }
        }
        @Override public void onAccuracyChanged(android.hardware.Sensor s, int a) { }
    };
    private void startTilt() {
        if (!U.anims(this) || !U.isSky(this)) { scene.setTilt(0, 0); return; }   // the tilt follows Animations
        if (sensors == null) sensors = (android.hardware.SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensors == null) return;
        android.hardware.Sensor g = sensors.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY);
        if (g == null) g = sensors.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER);
        if (g != null) sensors.registerListener(tilt, g, android.hardware.SensorManager.SENSOR_DELAY_NORMAL);   // 5 a second: smooth enough, far lighter
    }
    private void stopTilt() {
        if (sensors != null) sensors.unregisterListener(tilt);
        card.setTranslationX(0); card.setTranslationY(0);
        if (dockWrap != null) { dockWrap.setRotationX(0); dockWrap.setRotationY(0); }
    }

    /** A soft dark shade behind the verse, strongest in the middle and fading to nothing: white text reads on the sun. */
    private final class Scrim extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int lw, lh;
        /** 1 = the verse's shade; more on a bright sky, less behind the small lines. */
        float strength = 1f; private float made = -1;
        @Override public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            if (b.width() != lw || b.height() != lh || made != strength) {
                lw = b.width(); lh = b.height(); made = strength;
                int a0 = Math.min(255, (int) (0x6B * strength)), a1 = Math.min(255, (int) (0x47 * strength)), a2 = Math.min(255, (int) (0x1A * strength));
                p.setShader(new android.graphics.RadialGradient(0, 0, 1, new int[] {a0 << 24, a1 << 24, a2 << 24, 0x00000000},
                        new float[] {0f, .5f, .8f, 1f}, android.graphics.Shader.TileMode.CLAMP));
            }
            c.save();
            c.translate(b.exactCenterX(), b.exactCenterY());
            c.scale(b.width() * .5f, b.height() * .5f);      // fills the verse area and fades to nothing at its edges
            c.drawCircle(0, 0, 1, p);
            c.restore();
        }
        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** The sky's sun sits behind the verse; bright skies get dark verse text. */
    private void focusSky() {
        if (card == null || scene == null) return;
        int[] a = new int[2], b = new int[2];
        card.getLocationInWindow(a); scene.getLocationInWindow(b);
        int cy = a[1] - b[1] + Math.round(card.getHeight() * .62f) + dp(28);   // the sun sits a little below the verse's middle
        if (cy > 0 && card.getHeight() > 0) { scene.setFocus(cy); ui.removeCallbacks(recolor); ui.postDelayed(recolor, 60); }
    }
    private void verseColors() {
        boolean bright = U.skyIsBright(this);
        // Clear, sharp text on every sky. Bright skies: deep navy, with dark amber-brown for the reference and
        // the church line, and only a thin light edge. Dark skies (rain, storm, night): pure white with a tiny
        // tight shadow. No shade behind the text.
        // Each line takes its colour from what is right behind it (sun, moon, night sky, sunset, cloud or sill):
        // dark letters on a light patch, white or gold on a dark one.
        card.setBackground(null); wxPill.setBackground(null); churchLine.setBackground(null);
        for (TextView t : new TextView[] {en, ar}) ink(t, INK, Color.WHITE);
        for (TextView t : new TextView[] {enRef, arRef}) ink(t, 0xFF874C0C, 0xFFF2BC4E);
        ink(churchLine, 0xFF7A450B, 0xFFF2BC4E);
        // the verse in the phone's font, or the classic serif (Settings › Scripture)
        boolean serif = U.prefs(this).getString("verse_font", "system").equals("serif");
        en.setTypeface(serif ? U.serifBold(this) : android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
        enRef.setTypeface(serif ? U.serif(this) : android.graphics.Typeface.DEFAULT_BOLD);
        android.graphics.Typeface phone = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL);   // medium weight: crisp on any sky
        wxTemp.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        for (TextView t : new TextView[] {wxText, nextLine, churchLine, dateView}) t.setTypeface(phone);
        ar.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
        int soft = Color.WHITE;
        ink(wxTemp, INK, Color.WHITE);
        ink(wxText, 0xFF2A3B53, Color.WHITE);
        ink(nextLine, 0xFF2A3B53, Color.WHITE);
        if (page == 0) nextLine.setVisibility(countdownOn() ? View.VISIBLE : View.GONE);
        ink(alertLine, 0xFFB3261E, 0xFFFF8A80);   // light red on dark skies, deep red on bright ones: a warning stands apart from the gold
        // the top row: white on the deep blue of a sunny sky, navy on a pale sky (snow, fog, cloud)
        String sk = U.isSky(this) ? U.skyName(this) : "";
        boolean paleTop = sk.equals("snow") || sk.equals("fog") || sk.equals("cloudy");
        boolean topLight = ink(dateView, INK, Color.WHITE);
        ink(alarmLine, INK, Color.WHITE);
        tintChip(settingsLink, topLight ? 0xFF8A5212 : 0xFFF2B640, 0x66000000); tintChip(saverLink, topLight ? 0xFF8A5212 : 0xFFF2B640, 0x66000000);
        // app names: each row together, dark letters if what is behind the row is light, white if dark
        java.util.TreeMap<Integer, ArrayList<TextView>> rows = new java.util.TreeMap<>();
        for (int i = 0; i < desk.getChildCount(); i++) {
            View v = desk.getChildAt(i);
            if (v instanceof LinearLayout && ((LinearLayout) v).getChildCount() > 1 && ((LinearLayout) v).getChildAt(1) instanceof TextView
                    && v.getVisibility() == View.VISIBLE) {
                TextView t = (TextView) ((LinearLayout) v).getChildAt(1);
                if (!rows.containsKey(v.getTop())) rows.put(v.getTop(), new ArrayList<TextView>());
                rows.get(v.getTop()).add(t);
            }
        }
        for (ArrayList<TextView> row : rows.values()) {
            float sum = 0; int n = 0;
            for (TextView t : row) if (t.getHeight() > 0) { sum += scene.brightness(t); n++; }
            boolean light = n > 0 && sum / n > LIGHT;
            int theme = textTheme();
            for (TextView t : row) {
                if (theme != 0) { t.setTextColor(theme); t.setShadowLayer(dp(2.2f), 0, dp(1), 0xE6000000); continue; }
                t.setTextColor(light ? INK : soft);
                if (light) t.setShadowLayer(dp(1.2f), 0, dp(.5f), 0xB3FFFFFF); else t.setShadowLayer(dp(1.5f), 0, dp(1), 0xCC000000);
            }
        }
    }
    static final int INK = 0xFF23334A;
    /** Above this brightness behind it, a line uses dark letters. */
    static final float LIGHT = .47f;   // where dark letters start to read better than white (contrast crossover)
    /** Colours a line by what is behind it; true when it went dark (a light patch behind). */
    private boolean ink(TextView t, int dark, int light) {
        float br = t.getVisibility() == View.VISIBLE && t.getWidth() > 0 ? scene.brightness(t) : -1;
        boolean l = br >= 0 ? br > LIGHT : U.skyIsBright(this);
        int theme = textTheme();
        if (theme != 0 && light == Color.WHITE) {   // a chosen colour for the main text; gold, red and blue accents keep theirs
            t.setTextColor(theme); crisp(t, false);
            if (scene.lastMixed || l) t.setShadowLayer(dp(3.5f), 0, 0, 0xF2000000);
            return false;
        }
        t.setTextColor(l ? dark : light);
        crisp(t, l);
        // over light and dark at once: a soft halo in the opposite tone, so the letters read on both
        if (scene.lastMixed) t.setShadowLayer(dp(3.5f), 0, 0, l ? 0xF2FFFFFF : 0xF2000000);
        return l;
    }
    /** Settings › Home screen › Text colour: 0 = automatic (by the sky behind it, as before). */
    private int textTheme() {
        switch (U.prefs(this).getString("text_theme", "auto")) {
            case "white": return Color.WHITE;
            case "cream": return 0xFFF6EBD2;
            case "gold": return 0xFFF2C66B;
            case "blue": return 0xFFBFE3FF;
            case "rose": return 0xFFFFD6D0;
            default: return 0;
        }
    }

    /** Sharp text: on a bright sky a thin light edge (no dark smudge); on a dark sky a tiny tight shadow. */
    private void crisp(TextView t, boolean onBright) {
        if (onBright) t.setShadowLayer(dp(1.2f), 0, dp(.5f), 0xB3FFFFFF);
        else t.setShadowLayer(dp(1.2f), 0, dp(1), 0xB3000000);
        int pad = dp(3);
        t.setPadding(Math.max(t.getPaddingLeft(), pad), Math.max(t.getPaddingTop(), pad / 2), Math.max(t.getPaddingRight(), pad), Math.max(t.getPaddingBottom(), pad));
    }
    /** A soft shadow for text over the sky. The view gets room for it, so it is never cut into a box. */
    private void glow(TextView t, float r, int color) {
        // a tight shadow: it lifts the letters off the sky without softening their edges
        int pad = dp(r + 1);
        t.setShadowLayer(dp(2.2f), dp(.6f), dp(1.8f), 0xD9000000);   // a crisp drop shadow, down and slightly right
        t.setPadding(Math.max(t.getPaddingLeft(), pad), Math.max(t.getPaddingTop(), pad / 2), Math.max(t.getPaddingRight(), pad), Math.max(t.getPaddingBottom(), pad));
    }
    private void tintChip(View v, int color, int sh) {
        if (v instanceof TextView) { ((TextView) v).setTextColor(color); ((TextView) v).setShadowLayer(dp(4), 0, dp(1), sh); }
        else if (v instanceof ImageView) { Drawable d = ((ImageView) v).getDrawable(); if (d instanceof Ico) { ((Ico) d).setColor(color); v.invalidate(); } }
        else if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) tintChip(((ViewGroup) v).getChildAt(i), color, sh);
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    /** Tapping the clock, date, weather or church line opens the matching thing on the phone. */
    private void openAlarms() {
        try { startActivity(new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Throwable t) { openApp(new String[] {"com.google.android.deskclock", "com.sec.android.app.clockpackage", "com.android.deskclock"}, null); }
    }
    private void openCalendar() {
        Uri u = Uri.parse("content://com.android.calendar/time/" + System.currentTimeMillis());
        try { startActivity(new Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Throwable t) {
            try { startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Throwable ignored) { }
        }
    }
    /** Tapping the weather: Velthon's own forecast card first (it never crashes), with the way on to a full weather app
     *  and to the weather service's official warnings. Some phones' own weather apps refuse to open on their own. */
    private void weatherSheet() {
        final android.app.Dialog dl = new android.app.Dialog(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        LinearLayout p = new LinearLayout(this); p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(24), dp(22), dp(24), dp(16));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xF2161C28); bg.setCornerRadius(dp(26)); bg.setStroke(dp(1), 0x33FFFFFF); p.setBackground(bg);
        TextView city = text(22, Color.WHITE, U.sansBold(this)); city.setText(wxTemp.getText()); p.addView(city);
        TextView now = text(15, 0xE6FFFFFF, U.sans(this)); now.setText(wxText.getText()); now.setPadding(0, dp(4), 0, dp(4)); p.addView(now);
        if (alertLine.getVisibility() == View.VISIBLE) {
            TextView a = text(16, 0xFFFF8A80, U.sansBold(this)); a.setText(alertLine.getText()); a.setPadding(0, dp(10), 0, 0); p.addView(a);
        }
        String[] off = Weather.officialAlert(this);
        final double lat = Double.longBitsToDouble(U.prefs(this).getLong("lat", 0)), lon = Double.longBitsToDouble(U.prefs(this).getLong("lon", 0));
        final String warnUrl = lat > 41.6 && lat < 84 && lon > -142 && lon < -52
                ? String.format(Locale.US, "https://weather.gc.ca/en/location/index.html?coords=%.3f,%.3f", lat, lon)
                : lat > 17 && lat < 72 && lon > -180 && lon < -64
                ? String.format(Locale.US, "https://forecast.weather.gov/MapClick.php?lat=%.4f&lon=%.4f", lat, lon)
                : "https://www.google.com/search?q=weather";
        LinearLayout btns = new LinearLayout(this); btns.setOrientation(LinearLayout.VERTICAL); btns.setPadding(0, dp(16), 0, 0);
        btns.addView(sheetButton(off != null ? L.t("Official warnings") : L.t("Full forecast"), 0xFFF2B640, new Runnable() { @Override public void run() {
            dl.dismiss();
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(warnUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Throwable ignored) { } } }));
        App wa = byKey.get(U.prefs(this).getString("wx_app", ""));
        btns.addView(sheetButton(wa != null ? L.t("Open") + " " + wa.label : L.t("Open weather app"), Color.WHITE, new Runnable() { @Override public void run() { dl.dismiss(); openWeatherApp(); } }));
        if (wa != null) {
            TextView ch = text(13, 0x99FFFFFF, U.sans(this)); ch.setText(L.t("Choose another weather app")); ch.setGravity(Gravity.CENTER);
            ch.setPadding(0, dp(12), 0, dp(2));
            ch.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { dl.dismiss(); pickWeatherApp(); } });
            btns.addView(ch);
        }
        p.addView(btns);
        dl.setContentView(p);
        dl.setCanceledOnTouchOutside(true);
        if (dl.getWindow() != null) {
            dl.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dl.getWindow().setLayout(Math.min(dp(380), getResources().getDisplayMetrics().widthPixels - dp(32)), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dl.show();
    }
    private View sheetButton(String label, int color, final Runnable r) {
        TextView b = text(16, color, U.sansBold(this)); b.setText(label); b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(13), dp(12), dp(13));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(0x1FFFFFFF); g.setCornerRadius(dp(18)); b.setBackground(g);
        LinearLayout.LayoutParams l = lp(-1, -2); l.topMargin = dp(8); b.setLayoutParams(l);
        b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { r.run(); } });
        return b;
    }

    /** Opens the weather app the user picked. The first time (or if that app is gone) it asks which one: some
     *  phones' built-in weather apps crash when opened from another app, so Velthon never guesses silently. */
    private void openWeatherApp() {
        App a = byKey.get(U.prefs(this).getString("wx_app", ""));
        if (a != null) { launch(a, null); return; }
        pickWeatherApp();
    }
    private void pickWeatherApp() {
        final ArrayList<App> list = new ArrayList<>();
        String[] words = {"weather", "météo", "meteo", "tiempo", "wetter", "tempo", "طقس", "forecast", "accu", "radar"};
        for (App a : apps) {
            if (a.cn == null || a.label == null || a.cn.getPackageName().equals(getPackageName())) continue;
            String l = a.label.toLowerCase(Locale.ROOT);
            for (String w : words) if (l.contains(w)) { list.add(a); break; }
        }
        ArrayList<App> rest = new ArrayList<>();
        for (App a : apps) if (a.cn != null && a.label != null && !list.contains(a) && !a.cn.getPackageName().equals(getPackageName())) rest.add(a);
        java.util.Collections.sort(rest, new java.util.Comparator<App>() { @Override public int compare(App x, App y) { return x.label.compareToIgnoreCase(y.label); } });
        final int nWeather = list.size();
        list.addAll(rest);
        String[] names = new String[list.size()];
        for (int k = 0; k < list.size(); k++) names[k] = (k < nWeather ? "☀  " : "") + list.get(k).label;
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(L.t("Which app shows the weather?"))
                .setItems(names, new android.content.DialogInterface.OnClickListener() { @Override public void onClick(android.content.DialogInterface d, int w) {
                    App a = list.get(w);
                    U.prefs(Launcher.this).edit().putString("wx_app", a.key).apply();
                    launch(a, null);
                } })
                .setNegativeButton(L.t("Cancel"), null).show();
    }
    private void openChurch() {
        // straight to today's page of the Synaxarium (CopticChurch.net), not a front page
        String url = Church.calendar(this).equals("catholic") ? "https://bible.usccb.org/daily-bible-reading" : Synax.url(this);
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Throwable ignored) { }
    }
    /** Opens the first installed package in the list, or an app whose name contains {@code labelWord}. */
    private boolean openApp(String[] pkgs, String labelWord) {
        PackageManager pm = getPackageManager();
        for (String pk : pkgs) {
            Intent i = pm.getLaunchIntentForPackage(pk);
            if (i != null) { try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true; } catch (Throwable ignored) { } }
        }
        if (labelWord != null) for (App a : apps) {
            if (a.label != null && a.label.toLowerCase(Locale.ROOT).contains(labelWord) && !a.cn.getPackageName().equals(getPackageName())) {
                launch(a, null); return true;
            }
        }
        return false;
    }

    private void openVerses(String what) {
        Intent i = new Intent(this, VersesActivity.class);
        if (what != null) i.putExtra("open", what);
        if (verse != null) i.putExtra("verse", verse.index);
        startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    /** Holding an empty spot on the home screen. */
    private void homeMenu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor, Gravity.CENTER);
        m.getMenu().add(0, 1, 0, L.t("Add a widget"));
        if (!widgetIds().isEmpty()) m.getMenu().add(0, 2, 1, L.t("Remove a widget"));
        m.getMenu().add(0, 3, 2, L.t("Home screen settings"));
        m.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override public boolean onMenuItemClick(android.view.MenuItem it) {
                if (it.getItemId() == 1) pickWidget();
                else if (it.getItemId() == 2) removeWidgetMenu();
                else openSettings();
                return true;
            }
        });
        m.show();
    }

    // ---------------------------------------------------------------- verse
    private void showVerse() {
        verse = U.verseOfHour(this);
        verseSlotShown = U.verseSlot(this);
        ui.post(new Runnable() { @Override public void run() { verseColors(); } });
        String langs = U.langs(this);
        boolean showEn = !langs.equals("ar"), showAr = !langs.equals("en");
        en.setVisibility(showEn ? View.VISIBLE : View.GONE);
        enRef.setVisibility(showEn ? View.VISIBLE : View.GONE);
        ar.setVisibility(showAr ? View.VISIBLE : View.GONE);
        arRef.setVisibility(showAr ? View.VISIBLE : View.GONE);
        boolean changed = en.getText() != null && en.getText().length() > 0 && !verse.en.contentEquals(en.getText());
        if (false && changed) {   // the verse simply changes, without animation
            TextView[] tv = {en, ar, enRef, arRef};
            for (int i = 0; i < tv.length; i++) {
                tv[i].animate().cancel();
                tv[i].setAlpha(0f); tv[i].setTranslationY(dp(10));
                tv[i].animate().alpha(1f).translationY(0).setStartDelay(i * 70L).setDuration(600)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
            }
        }
        en.setText(verse.en); enRef.setText("——   " + verse.refEn + "   ——");
        ar.setText(verse.ar); arRef.setText("——   " + verse.refAr + "   ——");
        boolean s = U.saved(this).contains(verse.index);
        saveIcon.setImageDrawable(new Ico(s ? Ico.HEART_FULL : Ico.HEART, s ? 0xFFFF8FA8 : Color.WHITE));
        saveChip.setText(s ? L.t("Saved") : L.t("Save"));
        ui.post(new Runnable() { @Override public void run() { fitCard(); } });
    }

    private void toggleSave() { if (verse != null) { U.toggleSaved(this, verse.index); showVerse(); } }

    private void share() {
        if (verse == null) return;
        String langs = U.langs(this);
        StringBuilder t = new StringBuilder();
        if (!langs.equals("ar")) t.append(verse.en).append("\n— ").append(verse.refEn);
        if (!langs.equals("en")) { if (t.length() > 0) t.append("\n\n"); t.append(verse.ar).append("\n— ").append(verse.refAr); }
        startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t.toString()), L.t("Share verse")));
    }

    private void listen() {
        if (verse == null) return;
        final boolean arabic = U.langs(this).equals("ar");
        if (tts == null) {
            tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
                @Override public void onInit(int status) { ttsReady = status == TextToSpeech.SUCCESS; if (ttsReady) speak(arabic); }
            });
        } else if (ttsReady) speak(arabic);
    }
    private void speak(boolean arabic) {
        if (tts.isSpeaking()) { tts.stop(); return; }
        tts.setSpeechRate(U.prefs(this).getString("speech_rate", "normal").equals("slow") ? .8f : 1f);
        int r = tts.setLanguage(arabic ? new Locale("ar") : Locale.US);
        String t = arabic ? verse.ar + ". " + verse.refAr : verse.en + " " + verse.refEn;
        if (arabic && r < 0) { tts.setLanguage(Locale.US); t = verse.en + " " + verse.refEn; }
        tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "verse");
    }

    // ---------------------------------------------------------------- weather
    private void showWeather() {
        scene.refresh();          // the sky follows the weather
        verseColors();
        boolean has = Weather.has(this);
        boolean arabic = U.langs(this).equals("ar");
        wxNow.setVisibility(has ? View.VISIBLE : View.GONE);
        int[][] fc = has ? Weather.forecast(this) : new int[0][];
        boolean h24 = DateFormat.is24HourFormat(this);
        if (has) {
            String city = Weather.city(this), cond = Weather.condition(this);
            wxTemp.setText(TextUtils.isEmpty(city) ? Weather.temp(this) : city + "  ·  " + Weather.temp(this));
            // the weather's own icon (Meteocons), floating gently
            android.graphics.Bitmap ib = U.bitmap(this, "wx/" + Weather.iconName(this) + ".png");
            if (ib != null) {
                wxIcon.setImageBitmap(ib);
                wxIcon.setVisibility(View.VISIBLE);
                wxIcon.setElevation(dp(2));
                if (U.anims(this) && wxFloat == null) {
                    wxFloat = android.animation.ObjectAnimator.ofFloat(wxIcon, "translationY", -dp(2.5f), dp(2.5f));
                    wxFloat.setDuration(1800); wxFloat.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                    wxFloat.setRepeatMode(android.animation.ValueAnimator.REVERSE);
                    wxFloat.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
                    wxFloat.start();
                }
            } else wxIcon.setVisibility(View.GONE);
            // the condition, then as many coming hours as fit whole on the line (nothing cut off with "…")
            float avail = (scene.getWidth() > 0 ? scene.getWidth() : getResources().getDisplayMetrics().widthPixels) - dp(48);
            String line = cond;
            for (int n = Math.min(3, fc.length); n >= 0; n--) {
                StringBuilder b = new StringBuilder(cond);
                for (int i = 0; i < n; i++) {
                    int h = fc[i][0];
                    b.append("  ·  ").append(h24 ? String.format(Locale.US, "%02d:00", h) : ((h % 12 == 0 ? 12 : h % 12) + (h < 12 ? " AM" : " PM"))).append(' ').append(fc[i][1]).append('°');
                }
                line = b.toString();
                if (wxText.getPaint().measureText(line) <= avail) break;
            }
            wxText.setText(line);
        } else { wxTemp.setText(""); wxText.setText(""); }
        forecastRow.setVisibility(View.GONE);
        // a weather alert for the next 24 hours, worked out from the forecast
        boolean alertsOn = has && !U.prefs(this).getString("wx_alerts", "on").equals("off");
        String[] al = alertsOn ? Weather.alert(this) : null;
        String[] off = alertsOn ? Weather.officialAlert(this) : null;
        if (off != null) {   // the weather service's own warning comes first: its exact name (e.g. "Snowfall warning")
            alertLine.setText("⚠ " + off[0]);
            alertLine.setVisibility(View.VISIBLE);
        }
        else if (al == null) alertLine.setVisibility(View.GONE);
        else {
            String k = al[0];
            String[] n;
            switch (k) {
                case "storm": n = new String[]{"Thunderstorms", "عواصف رعدية"}; break;
                case "ice": n = new String[]{"Freezing rain", "أمطار متجمدة"}; break;
                case "snow_heavy": n = new String[]{"Heavy snow", "ثلوج كثيفة"}; break;
                case "rain_heavy": n = new String[]{"Heavy rain", "أمطار غزيرة"}; break;
                case "wind": n = new String[]{"Strong wind gusts", "رياح قوية"}; break;
                case "cold": n = new String[]{"Extreme cold", "برد شديد"}; break;
                case "heat": n = new String[]{"Extreme heat", "حر شديد"}; break;
                case "snow": n = new String[]{"Snow", "ثلوج"}; break;
                default: n = new String[]{L.t("Freezing fog"), "ضباب متجمد"};
            }
            int h = 0; try { h = Integer.parseInt(al[1]); } catch (NumberFormatException ignored) { }
            String when = al[2].equals("1") ? (arabic ? "الآن" : "now")
                    : h24 ? String.format(Locale.US, "%02d:00", h) : ((h % 12 == 0 ? 12 : h % 12) + (h < 12 ? " AM" : " PM"));
            alertLine.setText(arabic ? "⚠ " + n[1] + " · " + when : "⚠ " + n[0] + (al[2].equals("1") ? " now" : " from " + when));
            alertLine.setVisibility(View.VISIBLE);
        }
        // the church calendar, under the verse: Coptic date, the season or fast, today's or the next feast
        String cal = Church.calendar(this);
        if (cal.equals("off")) churchLine.setVisibility(View.GONE);
        else {
            Church.Feast today = Church.today(this), next = Church.next(this);
            if (today == null) today = Church.monthly(this);
            String[] season = Church.season(this);
            StringBuilder b = new StringBuilder("✦ ");
            if (today != null) {
                b.append(arabic ? "اليوم: " + today.ar : "Today: " + today.en);
                if (next != null) b.append("  ·  ").append(arabic ? next.ar + " بعد " + next.days + " يوم" : next.en + (next.days == 1 ? " tomorrow" : " in " + next.days + " days"));
                churchLine.setTypeface(U.sansBold(this));
            } else {
                churchLine.setTypeface(U.sans(this));
                if (season != null) b.append(arabic ? season[1] : season[0]);
                if (next != null) {
                    if (b.length() > 2) b.append("  ·  ");
                    b.append(arabic ? next.ar + " بعد " + next.days + " يوم" : next.en + (next.days == 1 ? " tomorrow" : " in " + next.days + " days"));
                }
            }
            if (cal.equals("orthodox")) {
                b.append("\n").append(Church.copticDate(java.util.Calendar.getInstance(), arabic));
                String st = Synax.saint(this);          // the Synaxarium: something for every day of the year
                if (st != null) b.append("  ·  ").append(st.replaceFirst("^(?i)the\\s+", "").replaceAll("[.]$", ""));
            }
            churchLine.setText(b.toString());
            churchLine.setVisibility(View.VISIBLE);
        }
        if (page == 1) for (View v : infoViews()) if (v != null) v.setVisibility(View.GONE);
    }

    // ---------------------------------------------------------------- apps
    static final class App {
        String label, cat, key, group; ComponentName cn; UserHandle user; Drawable icon; boolean verses, mine;
        ArrayList<App> items;      // set for a folder in the Folders view
    }
    private final ArrayList<App> apps = new ArrayList<>();
    private final ArrayList<App> shown = new ArrayList<>();
    private final HashMap<String, App> byKey = new HashMap<>();

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            // an install or update sends several of these in a row: reload once, two seconds after the last
            ui.removeCallbacks(reload); ui.postDelayed(reload, 2000);
        }
    };
    private final Runnable reload = new Runnable() { @Override public void run() { loadApps(); } };
    private volatile boolean loading, loadAgain;

    private void loadApps() {
        if (loading) { loadAgain = true; return; }
        loading = true;
        final Context c = getApplicationContext();
        final boolean first = apps.isEmpty();
        Thread th = new Thread(new Runnable() { @Override public void run() {
          // the first load (after unlocking, or when Android restarted the launcher) must be fast: the home screen
          // waits for it. Later reloads (an app installed or updated) run quietly in the background.
          try { Process.setThreadPriority(first ? Process.THREAD_PRIORITY_DISPLAY : Process.THREAD_PRIORITY_BACKGROUND); } catch (Throwable ignored) { }
          try {
            final ArrayList<App> list = new ArrayList<>();
            try {
                LauncherApps la = (LauncherApps) c.getSystemService(Context.LAUNCHER_APPS_SERVICE);
                UserManager um = (UserManager) c.getSystemService(Context.USER_SERVICE);
                UserHandle me = Process.myUserHandle();
                int density = c.getResources().getDisplayMetrics().densityDpi;
                for (UserHandle u : la.getProfiles()) {
                    for (LauncherActivityInfo i : la.getActivityList(null, u)) {
                      try {   // one odd app (being updated, half installed) must never stop the rest from loading
                        if (i.getComponentName().getPackageName().equals(c.getPackageName())
                                && !i.getComponentName().getClassName().endsWith("SettingsEntry")) continue;   // "Velthon Settings" stays in the list
                        App a = new App();
                        a.label = String.valueOf(i.getLabel());
                        a.cn = i.getComponentName();
                        a.user = u;
                        a.mine = u.equals(me);
                        a.key = a.cn.flattenToString() + (a.mine ? "" : "#" + um.getSerialNumberForUser(u));
                        String own = U.prefs(c).getString("label_" + a.key, null);   // a name the user gave it
                        if (own != null && !own.trim().isEmpty()) a.label = own.trim();
                        try { a.icon = i.getBadgedIcon(density); } catch (Throwable t) { try { a.icon = i.getIcon(density); } catch (Throwable t2) { a.icon = null; } }
                        if (a.icon == null) a.icon = c.getPackageManager().getDefaultActivityIcon();
                        try { a.cat = category(i); } catch (Throwable t) { a.cat = "other"; }
                        try { a.group = group(i, a.cat); } catch (Throwable t) { a.group = "Tools"; }
                        list.add(a);
                      } catch (Throwable skipOne) { android.util.Log.w("Velthon", "app skipped: " + skipOne); }
                    }
                }
            } catch (Throwable ignored) { }
            try { if (U.prefs(c).getBoolean("velthon_icons", false)) VIcons.apply(c, list); } catch (Throwable ignored) { }
            try { Shape.clear(); } catch (Throwable ignored) { }
            App v = new App();     // Velthon's own Verses
            v.label = "Verses"; v.cat = "faith"; v.group = "Faith"; v.verses = true; v.mine = true;
            v.cn = new ComponentName(c, VersesActivity.class); v.user = Process.myUserHandle(); v.key = "velthon:verses";
            v.icon = new android.graphics.drawable.BitmapDrawable(c.getResources(), U.bitmap(c, "vicons/verses.png"));
            list.add(v);
            final Collator col = Collator.getInstance();
            Collections.sort(list, new Comparator<App>() { @Override public int compare(App x, App y) {
                int k = col.compare(x.label, y.label);
                return k != 0 ? k : Boolean.compare(y.mine, x.mine);
            } });
            ui.post(new Runnable() { @Override public void run() {
                apps.clear(); apps.addAll(list);
                byKey.clear();
                for (App a : apps) byKey.put(a.key, a);
                filter();
                showDesk();
                showDock();
            } });
          } finally {
            loading = false;
            if (loadAgain) { loadAgain = false; ui.post(new Runnable() { @Override public void run() { loadApps(); } }); }
          }
        } }, "velthon-apps");
        th.start();
    }

    /** Folder order in the Folders view. */
    private static final String[] GROUPS = {"Faith", "Communication", "Social", "Internet", "Photos & Video", "Music & Audio",
            "Games", "News & Reading", "Productivity", "Travel & Maps", "Shopping", "Finance", "Health & Fitness", "Tools"};

    /** Sorts an app into a folder by its type: what Android says it is, then its name. */
    private static String group(LauncherActivityInfo i, String cat) {
        if ("faith".equals(cat)) return "Faith";
        String s = (i.getLabel() + " " + i.getComponentName().getPackageName()).toLowerCase(Locale.ROOT);
        int c = -1;
        if (Build.VERSION.SDK_INT >= 26) c = i.getApplicationInfo().category;
        boolean game = (i.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0 || c == 0;
        if (game) return "Games";
        if (has(s, "phone", "dialer", "contacts", "message", "messaging", "mms", "sms", "mail", "gmail", "outlook", "whatsapp", "telegram",
                "signal", "messenger", "viber", "skype", "meet", "zoom", "teams", "duo", "botim", "imo")) return "Communication";
        if (c == 4 || has(s, "facebook", "instagram", "tiktok", "twitter", "snapchat", "reddit", "linkedin", "pinterest", "threads",
                "x.android", "discord", "tumblr", "bereal")) return "Social";
        if (has(s, "bank", "wallet", " pay", "pay ", "paypal", "venmo", "money", "invest", "crypto", "wealthsimple", "rbc", "td ",
                "scotia", "bmo", "cibc", "tangerine", "interac", "finance", "stock", "budget")) return "Finance";
        if (has(s, "amazon", "ebay", "shop", "walmart", "costco", "aliexpress", "temu", "shein", "etsy", "kijiji", "store", "deals",
                "flyer", "instacart", "doordash", "uber eats", "skip")) return "Shopping";
        if (c == 6 || has(s, "maps", "waze", "uber", "lyft", "transit", "airbnb", "booking", "expedia", "flight", "airline", "air canada",
                "hotel", "trip", "travel", "presto", "gps")) return "Travel & Maps";
        if (c == 1 || has(s, "music", "spotify", "podcast", "radio", "audible", "soundcloud", "shazam", "audio", "recorder")) return "Music & Audio";
        if (c == 2 || c == 3 || has(s, "camera", "photo", "gallery", "youtube", "netflix", "video", "prime video", "disney", "crave",
                "tv", "movie", "player", "snapseed", "lightroom", "picsart")) return "Photos & Video";
        if (c == 5 || has(s, "news", "kindle", "reader", "books", "magazine", "cbc", "cnn", "bbc", "flipboard")) return "News & Reading";
        if (has(s, "health", "fit", "workout", "step", "sleep", "medic", "clinic", "pharma", "doctor", "strava", "calorie")) return "Health & Fitness";
        if (c == 7 || has(s, "docs", "sheets", "slides", "drive", "office", "word", "excel", "powerpoint", "notes", "keep", "calendar",
                "todo", "task", "onenote", "notion", "dropbox", "scanner", "pdf", "claude", "chatgpt", "gemini", "copilot", "slack")) return "Productivity";
        if (has(s, "chrome", "browser", "firefox", "edge", "opera", "brave", "samsung internet", "duckduckgo", "google")) return "Internet";
        return "Tools";
    }
    private static boolean has(String s, String... words) { for (String w : words) if (s.contains(w)) return true; return false; }

    /** A folder's icon: up to four of its apps' icons in a soft square. */
    private final class FolderIcon extends Drawable {
        private final ArrayList<App> items; final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        FolderIcon(ArrayList<App> items) { this.items = items; bg.setColor(0x59FFFFFF); }
        @Override public int getIntrinsicWidth() { return dp(50); }
        @Override public int getIntrinsicHeight() { return dp(50); }
        @Override public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            float r = b.width() * .26f;
            c.drawRoundRect(new android.graphics.RectF(b), r, r, bg);
            int pad = Math.round(b.width() * .1f), gap = Math.round(b.width() * .06f);
            int cell = (b.width() - pad * 2 - gap) / 2;
            for (int i = 0; i < Math.min(4, items.size()); i++) {
                Drawable d = shaped(items.get(i), 24);
                if (d == null) continue;
                int x = b.left + pad + (i % 2) * (cell + gap), y = b.top + pad + (i / 2) * (cell + gap);
                d.setBounds(x, y, x + cell, y + cell);
                d.draw(c);
            }
        }
        @Override public void setAlpha(int a) { bg.setAlpha(a); }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private static String category(LauncherActivityInfo i) {
        String s = (i.getLabel() + " " + i.getComponentName().getPackageName()).toLowerCase(Locale.ROOT);
        String[] faith = {"bible", "church", "pray", "verse", "quran", "faith", "devotion", "worship", "psalm", "coptic",
                "agpeya", "katameros", "synaxar", "orthodox", "catholic", "gospel", "sermon", "hymn", "kholagy", "kholagi", "euchologion",
                "saint", "liturgy", "انجيل", "إنجيل", "الإنجيل", "كتاب المقدس", "صلاة", "ترانيم", "الأجبية", "اجبية", "قداس", "سنكسار", "قبطي"};
        for (String f : faith) if (s.contains(f)) return "faith";
        int cat = Build.VERSION.SDK_INT >= 26 ? i.getApplicationInfo().category : -1;
        String[] daily = {"phone", "dialer", "message", "messaging", "mms", "sms", "mail", "gmail", "camera", "photo", "gallery",
                "chat", "whatsapp", "telegram", "signal", "messenger", "facebook", "instagram", "maps", "music", "youtube",
                "calendar", "contacts", "browser", "chrome", "firefox", "news", "spotify", "netflix", "meet", "zoom", "teams", "tiktok", "twitter"};
        for (String d : daily) if (s.contains(d)) return "daily";
        if (cat >= 0 && cat <= 6) return "daily";
        return "tools";
    }

    private void launch(App a, View from) {
        if (a == null) return;
        remember(a);
        if (a.verses) { openVerses(null); return; }
        try {
            ((LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE)).startMainActivity(a.cn, a.user, from == null ? null : bounds(from), null);
        } catch (Throwable t) {
            try { startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(a.cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (Throwable t2) { Toast.makeText(this, L.t("Can't open ") + a.label, Toast.LENGTH_SHORT).show(); }
        }
    }
    private static android.graphics.Rect bounds(View v) {
        int[] p = new int[2]; v.getLocationOnScreen(p);
        return new android.graphics.Rect(p[0], p[1], p[0] + v.getWidth(), p[1] + v.getHeight());
    }

    /** The personal-profile app that does a job (dial, text, ...). */
    private String appFor(Intent i) {
        try {
            ResolveInfo r = getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            String pkg = r == null || r.activityInfo == null ? null : r.activityInfo.packageName;
            if (pkg == null || pkg.equals("android")) {
                List<ResolveInfo> all = getPackageManager().queryIntentActivities(i, 0);
                pkg = all.isEmpty() ? null : all.get(0).activityInfo.packageName;
            }
            if (pkg != null) for (App a : apps) if (a.mine && a.cn.getPackageName().equals(pkg)) return a.key;
        } catch (Throwable ignored) { }
        return null;
    }

    /** Each place gets its own copy of an icon (a shared one is hidden when the drawer closes). */
    private Drawable own(Drawable d) {
        if (d == null) return null;
        Drawable.ConstantState cs = d.getConstantState();
        return cs == null ? d : cs.newDrawable(getResources()).mutate();
    }

    /** The phone's own icon in the Velthon shape: a rounded square with a thin light edge (folders keep their preview). */
    private Drawable shaped(App a, int iconDp) {
        if (a.items != null) return a.icon;
        String st = U.prefs(this).getString("icon_style", "glass");
        return new android.graphics.drawable.BitmapDrawable(getResources(),
                st.equals("classic") ? Shape.icon(this, a.key, a.icon, dp(iconDp))
                : st.equals("colorglass") ? Shape.colorGlass(this, a.key, a.icon, dp(iconDp), false)
                : Shape.glass(this, a.key, a.icon, dp(iconDp)));
    }

    /** An icon that shows a small dot when its app has a notification waiting (Settings › Notification dots). */
    private final class DotIcon extends ImageView {
        final App app; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        DotIcon(Context c, App a) { super(c); app = a; setClipToOutline(false); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (!dotsOn) return;
            boolean any = false;
            if (app.items != null) { for (App x : app.items) if (x.cn != null && NotifDots.has(x.cn.getPackageName())) { any = true; break; } }
            else if (app.cn != null) any = NotifDots.has(app.cn.getPackageName());
            if (!any) return;
            float r = getWidth() * .11f, cx = getWidth() - r * 1.15f, cy = r * 1.15f;
            p.setColor(0x66000000); c.drawCircle(cx, cy + r * .12f, r * 1.12f, p);
            p.setColor(0xFFFFC94A); c.drawCircle(cx, cy, r, p);
        }
    }
    private boolean dotsOn;
    private static void invalidateAll(View v) {
        v.invalidate();
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) invalidateAll(((ViewGroup) v).getChildAt(i));
    }

    /** Icons give a little when pressed and spring back. Costs nothing while nothing is touched. */
    private final View.OnTouchListener PRESS = new View.OnTouchListener() {
        @Override public boolean onTouch(View v, MotionEvent e) {
            if (!U.anims(Launcher.this)) return false;
            View i = v instanceof ViewGroup && ((ViewGroup) v).getChildCount() > 0 ? ((ViewGroup) v).getChildAt(0) : v;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    i.animate().scaleX(.9f).scaleY(.9f).setDuration(90).setInterpolator(new android.view.animation.DecelerateInterpolator()).start(); break;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                    i.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new android.view.animation.OvershootInterpolator(2.2f)).start(); break;
            }
            return false;
        }
    };

    /** One app's icon with its name, as on the home screen and in the drawer. */
    private View tile(final App a, boolean label, int iconDp) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        ImageView iv = new DotIcon(this, a);
        iv.setImageDrawable(shaped(a, iconDp));
        l.addView(iv, lp(dp(iconDp), dp(iconDp)));
        if (label) {
            TextView t = text(12.5f, Color.WHITE, android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            t.setSingleLine(true); t.setEllipsize(TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(3), dp(6), dp(3), 0);
            t.setShadowLayer(dp(1.5f), 0, dp(1), 0xCC000000);   // tight, so the name stays sharp
            t.setText(a.label);
            l.addView(t, lp(-1, -2));
        }
        l.setTag(a);
        l.setOnTouchListener(PRESS);
        return l;
    }

    // ---------------------------------------------------------------- the home screen grid
    private HashMap<Integer, String> deskItems() {
        HashMap<Integer, String> m = new HashMap<>();
        for (String p : U.prefs(this).getString(deskKey(), "").split(";")) {
            int c = p.indexOf('=');
            if (c > 0) try { m.put(Integer.parseInt(p.substring(0, c)), p.substring(c + 1)); } catch (NumberFormatException ignored) { }
        }
        return m;
    }
    private void saveDesk(HashMap<Integer, String> m) {
        StringBuilder b = new StringBuilder();
        for (java.util.Map.Entry<Integer, String> e : m.entrySet()) b.append(e.getKey()).append('=').append(e.getValue()).append(';');
        U.prefs(this).edit().putString(deskKey(), b.toString()).apply();
    }

    private final Runnable recolor = new Runnable() { @Override public void run() { verseColors(); } };
    private void showDesk() {
        ui.removeCallbacks(recolor); ui.postDelayed(recolor, 120);   // app names take the sky's colours once laid out
        if (apps.isEmpty()) return;
        desk.clearIcons();
        HashMap<Integer, String> m = deskItems();
        for (java.util.Map.Entry<Integer, String> e : m.entrySet()) {
            final App a = e.getValue().startsWith("folder:") ? homeFolder(e.getValue().substring(7)) : byKey.get(e.getValue());
            if (a == null) continue;
            View t = tile(a, true, 50);
            // on the home screen the tile is a deeper, smoky glass: the symbol stands out on a bright sky
            String st = U.prefs(this).getString("icon_style", "glass");
            if (a.items == null && !st.equals("classic")
                    && t instanceof LinearLayout && ((LinearLayout) t).getChildAt(0) instanceof ImageView)
                ((ImageView) ((LinearLayout) t).getChildAt(0)).setImageDrawable(new android.graphics.drawable.BitmapDrawable(getResources(),
                        st.equals("colorglass") ? Shape.colorGlass(this, a.key, a.icon, dp(50), true) : Shape.glassDark(this, a.key, a.icon, dp(50))));
            if (a.items != null && a.icon instanceof FolderIcon)    // the same smoky glass, or the colour chosen for this folder
                ((FolderIcon) a.icon).bg.setColor(folderColor(a.key, 0x99202C40));
            final int index = e.getKey();
            t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (a.items != null) openFolder(a); else launch(a, v); } });
            t.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) { startDrag(v, a, "desk", index); return true; } });
            desk.putIcon(t, index);
        }
        showWidgets();
        deskHint.setVisibility(View.GONE);
    }


    // ---------------------------------------------------------------- your own folders on the home screen
    // stored as  id=name|key,key;id=name|key,key  — drop one app on another to make one
    private LinkedHashMap<String, String[]> hfolders() {
        LinkedHashMap<String, String[]> m = new LinkedHashMap<>();
        for (String e : U.prefs(this).getString("hfolders", "").split("\u0002")) {
            String[] p = e.split("\u0001", -1);
            if (p.length == 3 && !p[0].isEmpty()) m.put(p[0], new String[] {p[1], p[2]});
        }
        return m;
    }
    private void saveHFolders(LinkedHashMap<String, String[]> m) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String[]> e : m.entrySet()) {
            if (b.length() > 0) b.append('\u0002');
            b.append(e.getKey()).append('\u0001').append(e.getValue()[0]).append('\u0001').append(e.getValue()[1]);
        }
        U.prefs(this).edit().putString("hfolders", b.toString()).apply();
    }
    private ArrayList<String> folderKeys(String id) {
        ArrayList<String> l = new ArrayList<>();
        String[] f = hfolders().get(id);
        if (f != null) for (String k : f[1].split(",")) if (!k.isEmpty()) l.add(k);
        return l;
    }
    private void setFolder(String id, String name, ArrayList<String> keys) {
        LinkedHashMap<String, String[]> m = hfolders();
        StringBuilder b = new StringBuilder();
        for (String k : keys) { if (b.length() > 0) b.append(','); b.append(k); }
        m.put(id, new String[] {name, b.toString()});
        saveHFolders(m);
    }
    /** A new folder: named after what the apps have in common (both Social → L.t("Social")), else L.t("Folder"). */
    private String newFolder(ArrayList<String> keys) {
        String id = Long.toString(System.currentTimeMillis() % 1000000000L);
        String name = L.t("Folder"), g = null;
        for (String k : keys) {
            App a = byKey.get(k);
            String gg = a == null ? null : a.group;
            if (g == null) g = gg; else if (gg == null || !gg.equals(g)) { g = "?"; break; }
        }
        if (g != null && !g.equals("?") && !g.equals("Tools")) name = g;
        setFolder(id, name, keys);
        return id;
    }
    /** The folder as an item for the home screen (null if it is gone). */
    private App homeFolder(String id) {
        String[] f = hfolders().get(id);
        if (f == null) return null;
        App a = new App();
        a.key = "folder:" + id; a.label = f[0]; a.items = new ArrayList<>();
        for (String k : f[1].split(",")) { App x = byKey.get(k); if (x != null) a.items.add(x); }
        if (a.items.isEmpty()) return null;
        a.icon = new FolderIcon(a.items);
        return a;
    }
    /** An app left a folder: one app left turns back into that app; none left removes the folder. */
    private void tidyFolder(String id) {
        ArrayList<String> keys = folderKeys(id);
        if (keys.size() > 1) return;
        HashMap<Integer, String> m = deskItems();
        Integer at = null;
        for (Map.Entry<Integer, String> e : m.entrySet()) if (e.getValue().equals("folder:" + id)) at = e.getKey();
        if (at != null) { if (keys.size() == 1) m.put(at, keys.get(0)); else m.remove(at); saveDesk(m); }
        LinkedHashMap<String, String[]> f = hfolders(); f.remove(id); saveHFolders(f);
    }
    private void renameFolder(final String id, final Runnable after) {
        final EditText e = new EditText(this);
        String[] f = hfolders().get(id);
        e.setText(f == null ? "" : f[0]);
        e.setSelection(e.getText().length());
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(22), dp(8), dp(22), 0);
        box.addView(e);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(L.t("Folder name")).setView(box)
                .setPositiveButton(L.t("Save"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String n = e.getText().toString().trim();
                        if (!n.isEmpty()) setFolder(id, n, folderKeys(id));
                        showDesk();
                        if (after != null) after.run();
                    }
                }).setNegativeButton(L.t("Cancel"), null).show();
    }
    /** Makes a home folder from one of the automatic folders in the app list. */
    private void folderToHome(App auto) {
        ArrayList<String> keys = new ArrayList<>();
        for (App a : auto.items) keys.add(a.key);
        String id = Long.toString(System.currentTimeMillis() % 1000000000L);
        setFolder(id, auto.label, keys);
        HashMap<Integer, String> m = deskItems();
        int cell = freeCellNear(m, 0);
        if (cell < 0) { Toast.makeText(this, L.t("No room left on the home screen"), Toast.LENGTH_SHORT).show(); return; }
        m.put(cell, "folder:" + id); saveDesk(m); showDesk();
        closeDrawer();
    }

    // ---------------------------------------------------------------- the dock (up to six apps)
    private ArrayList<String> dockItems() {
        ArrayList<String> l = new ArrayList<>();
        String s = U.prefs(this).getString("dock", null);
        if (s == null) {      // first time: the phone's own dialer, messages, camera and browser (old favourites kept)
            for (int i = 0; i < 4; i++) {
                String k = U.prefs(this).getString("fav" + i, null);
                App a = k == null ? null : byKey.get(k);
                if (a != null && a.mine && !l.contains(k)) l.add(k);
            }
            if (l.size() < 3 && !apps.isEmpty()) {
                Intent[] jobs = {new Intent(Intent.ACTION_DIAL), new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
                        new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))};
                for (Intent j : jobs) { String k = appFor(j); if (k != null && !l.contains(k)) l.add(k); }
            }
            if (!apps.isEmpty()) saveDock(l);
            return l;
        }
        for (String k : s.split("\\|")) if (!k.isEmpty()) l.add(k);
        return l;
    }
    private void saveDock(ArrayList<String> l) {
        StringBuilder b = new StringBuilder();
        for (String k : l) { if (b.length() > 0) b.append('|'); b.append(k); }
        U.prefs(this).edit().putString("dock", b.toString()).apply();
    }
    private void showDock() {
        if (apps.isEmpty()) return;
        dockRow.removeAllViews();
        dockApps.clear();
        ArrayList<String> l = dockItems();
        for (int i = 0; i < l.size(); i++) {
            final App a = byKey.get(l.get(i));
            if (a == null) continue;
            final int index = dockApps.size();
            dockApps.add(a);
            View t = tile(a, false, 46);   // a little smaller, so each icon has room around it
            // the dock's own look: clear inside a thin outline, the app's symbol in the middle
            if (t instanceof LinearLayout && ((LinearLayout) t).getChildAt(0) instanceof ImageView)
                ((ImageView) ((LinearLayout) t).getChildAt(0)).setImageDrawable(new android.graphics.drawable.BitmapDrawable(getResources(), Shape.glass(this, a.key, a.icon, dp(46))));
            t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(final View v) { pressDock(index, v); } });
            t.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) { startDrag(v, a, "dock", index); return true; } });
            dockRow.addView(t, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        dockN = dockApps.size();
        // the empty places, faint, until an app is dragged there
        for (int k = dockN; k < 6; k++) {
            View e = new View(this) {
                final Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override protected void onDraw(Canvas c) {
                    float sz = dp(46), x = (getWidth() - sz) / 2f, y = (getHeight() - sz) / 2f;
                    pp.setColor(0x1FFFFFFF); pp.setStyle(Paint.Style.FILL);
                    c.drawRoundRect(new android.graphics.RectF(x, y, x + sz, y + sz), sz * .27f, sz * .27f, pp);
                }
            };
            dockRow.addView(e, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        dockHint.setVisibility(View.GONE);
        dockRow.post(new Runnable() { @Override public void run() { dockHoles(); } });
        if (dockAnim != null) dockAnim.cancel();
        dockLift = 0;
        placeBubble();
    }
    /** Each icon box is a clear window in the band; around the boxes the band keeps its milky glass. */
    private void dockHoles() {
        dockBar.holes.clear();
        float sz = dp(46), in = dp(1.2f);
        int[] b = new int[2], v = new int[2];
        dockBar.getLocationInWindow(b);
        for (int i = 0; i < dockRow.getChildCount(); i++) {
            View c = dockRow.getChildAt(i);
            c.getLocationInWindow(v);
            float cx = v[0] - b[0] + c.getWidth() / 2f, cy = v[1] - b[1] + c.getHeight() / 2f;
            if (c.getWidth() == 0) continue;
            dockBar.holes.add(new android.graphics.RectF(cx - sz / 2 + in, cy - sz / 2 + in, cx + sz / 2 - in, cy + sz / 2 - in));
        }
        dockBar.holeR = sz * .27f;
        dockBar.invalidate();
    }
    private App dockApp(int i) { return i >= 0 && i < dockApps.size() ? dockApps.get(i) : null; }

    /** All the apps rest inside the bar. Tapping one lifts it out into the gold circle (the bar opens a notch
     *  under it), then it opens; coming back home, it sinks back into the bar and the notch closes. */
    private float dockLift = 0;
    private void pressDock(int i, final View from) {
        final App a = dockApp(i);
        if (a == null || i >= dockRow.getChildCount()) return;
        dockSel = i;
        final View t = dockRow.getChildAt(i);
        t.setCameraDistance(dp(900));
        t.animate().translationY(-dp(26)).scaleX(1.14f).scaleY(1.14f).rotationX(22f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                .withEndAction(new Runnable() { @Override public void run() { launch(a, from); } }).start();
        dockLift = 1;
    }
    private void sinkDock() {
        if (dockLift <= 0) return;
        dockLift = 0;
        for (int k = 0; k < dockRow.getChildCount(); k++)
            dockRow.getChildAt(k).animate().translationY(0).scaleX(1f).scaleY(1f).rotationX(0f).setDuration(320)
                    .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator()).start();
    }
    private void placeBubble() {
        dockBar.setNotch(-1);
        dockBubble.setVisibility(View.GONE);
    }

    // ---------------------------------------------------------------- hold and move
    private static final class DragItem { App app; String from; int index; float x0, y0; boolean moved, started; View view; }
    private boolean dragging;

    private void startDrag(View v, App a, String from, int index) {
        DragItem d = new DragItem();
        d.app = a; d.from = from; d.index = index; d.view = v;
        int[] p = new int[2], s = new int[2];
        v.getLocationOnScreen(p); scene.getLocationOnScreen(s);
        d.x0 = p[0] - s[0] + v.getWidth() / 2f; d.y0 = p[1] - s[1] + v.getHeight() / 2f;
        View shadowOf = v instanceof LinearLayout && ((LinearLayout) v).getChildCount() > 0 ? ((LinearLayout) v).getChildAt(0) : v;
        try { v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); } catch (Throwable ignored) { }
        v.startDrag(ClipData.newPlainText("velthon", a.key), new View.DragShadowBuilder(shadowOf), d, 0);
    }

    private final View.OnDragListener dragListener = new View.OnDragListener() {
        @Override public boolean onDrag(View v, DragEvent e) {
            DragItem d = e.getLocalState() instanceof DragItem ? (DragItem) e.getLocalState() : null;
            if (d == null) return false;
            switch (e.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    dragging = true;
                    return true;
                case DragEvent.ACTION_DRAG_LOCATION: {
                    if (!d.started) { d.started = true; d.x0 = e.getX(); d.y0 = e.getY(); }   // measured from where the finger is
                    if (!d.moved && Math.hypot(e.getX() - d.x0, e.getY() - d.y0) > dp(22)) {
                        d.moved = true;
                        if (drawer.getVisibility() == View.VISIBLE) closeDrawerNow();
                        showDropBar(d);
                    }
                    if (d.moved) { desk.highlight(deskCellAt(e.getX(), e.getY())); markDropBar(e.getX(), e.getY()); }
                    return true;
                }
                case DragEvent.ACTION_DROP:
                    if (d.moved) drop(d, e.getX(), e.getY());
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    dragging = false;
                    desk.highlight(-1);
                    hideDropBar();
                    if (!d.moved) { final DragItem dd = d; ui.post(new Runnable() { @Override public void run() { appMenu(dd); } }); }
                    return true;
            }
            return true;
        }
    };

    private int deskCellAt(float x, float y) {
        int[] a = new int[2], s = new int[2];
        desk.getLocationOnScreen(a); scene.getLocationOnScreen(s);
        float dx = x - (a[0] - s[0]), dy = y - (a[1] - s[1]);
        if (dx < 0 || dy < 0 || dx > desk.getWidth() || dy > desk.getHeight()) return -1;
        return desk.cellAt(dx, dy);
    }
    private boolean over(View t, float x, float y) {
        if (t == null || t.getVisibility() != View.VISIBLE || t.getWidth() == 0) return false;
        if (t.getParent() instanceof View && ((View) t.getParent()).getVisibility() != View.VISIBLE) return false;
        int[] a = new int[2], s = new int[2];
        t.getLocationOnScreen(a); scene.getLocationOnScreen(s);
        float dx = x - (a[0] - s[0]), dy = y - (a[1] - s[1]);
        return dx >= -dp(8) && dy >= -dp(8) && dx <= t.getWidth() + dp(8) && dy <= t.getHeight() + dp(8);
    }

    private void drop(DragItem d, float x, float y) {
        String key = d.app.key;
        if (over(dropRemove, x, y)) { removeFrom(d); return; }
        if (d.app.items != null && (over(dropInfo, x, y) || over(dropUninstall, x, y))) { Toast.makeText(this, L.t("Open the folder to see its apps"), Toast.LENGTH_SHORT).show(); return; }
        if (over(dropInfo, x, y)) { appInfo(d.app); return; }
        if (over(dropUninstall, x, y)) { uninstall(d.app); return; }
        if (over(dockBar, x, y) && d.app.items != null) { Toast.makeText(this, L.t("Folders stay on the home screen"), Toast.LENGTH_SHORT).show(); return; }
        if (over(dockBar, x, y)) {
            ArrayList<String> l = dockItems();
            int[] a = new int[2], s = new int[2];
            dockRow.getLocationOnScreen(a); scene.getLocationOnScreen(s);
            float rx = x - (a[0] - s[0]);
            int n = l.size();
            int slot = Math.max(0, Math.min(5, (int) ((rx - dockRow.getPaddingLeft()) / ((dockRow.getWidth() - dockRow.getPaddingLeft() - dockRow.getPaddingRight()) / 6f))));
            int at = Math.min(n, slot);                          // six places; apps fill from the left
            if (d.from.equals("dock")) {
                if (d.index < l.size()) l.remove(d.index);
                l.add(Math.max(0, Math.min(slot, l.size())), key);
            } else {
                if (l.contains(key)) { Toast.makeText(this, d.app.label + " is already in the dock", Toast.LENGTH_SHORT).show(); return; }
                if (l.size() >= DOCK_MAX) { Toast.makeText(this, "The dock holds " + DOCK_MAX + " apps. Drag one out first.", Toast.LENGTH_SHORT).show(); return; }
                l.add(Math.min(at, l.size()), key);
                if (d.from.equals("desk")) { HashMap<Integer, String> m = deskItems(); m.remove(d.index); saveDesk(m); showDesk(); }
                leftFolder(d);
            }
            saveDock(l); showDock();
            return;
        }
        int cell = deskCellAt(x, y);
        if (cell >= 0) {
            HashMap<Integer, String> m = deskItems();
            String there = m.get(cell);
            // an app dropped on an app makes a folder; on a folder, it goes in
            if (d.app.items == null && there != null && !there.equals(key) && !(d.from.equals("desk") && d.index == cell)) {
                if (there.startsWith("folder:")) {
                    String id = there.substring(7);
                    ArrayList<String> keys = folderKeys(id);
                    if (!keys.contains(key)) keys.add(key);
                    String[] f = hfolders().get(id);
                    setFolder(id, f == null ? L.t("Folder") : f[0], keys);
                } else {
                    ArrayList<String> keys = new ArrayList<>();
                    keys.add(there); keys.add(key);
                    m.put(cell, "folder:" + newFolder(keys));
                }
                if (d.from.equals("desk")) m.remove(d.index);
                if (d.from.equals("dock")) { ArrayList<String> l = dockItems(); if (d.index < l.size()) l.remove(d.index); saveDock(l); showDock(); }
                saveDesk(m);
                leftFolder(d);
                showDesk();
                return;
            }
            if (d.from.equals("desk")) {
                m.remove(d.index);
                if (there != null) m.put(d.index, there);       // swap places
            } else if (there != null) {
                cell = freeCellNear(m, cell);
                if (cell < 0) { Toast.makeText(this, L.t("No room left on the home screen"), Toast.LENGTH_SHORT).show(); return; }
            }
            if (d.from.equals("dock")) { ArrayList<String> l = dockItems(); if (d.index < l.size()) l.remove(d.index); saveDock(l); showDock(); }
            m.put(cell, key);
            saveDesk(m);
            leftFolder(d);
            showDesk();
        }
    }
    /** If the app was dragged out of a home folder, it leaves that folder. */
    private void leftFolder(DragItem d) {
        if (!d.from.startsWith("hfolder:")) return;
        String id = d.from.substring(8);
        ArrayList<String> keys = folderKeys(id);
        keys.remove(d.app.key);
        String[] f = hfolders().get(id);
        setFolder(id, f == null ? L.t("Folder") : f[0], keys);
        tidyFolder(id);
        showDesk();
    }
    private int freeCellNear(HashMap<Integer, String> m, int cell) {
        int cap = Math.max(desk.capacity(), cell + 1);
        for (int r = 0; r < cap; r++) {
            if (cell + r < cap && !m.containsKey(cell + r)) return cell + r;
            if (cell - r >= 0 && !m.containsKey(cell - r)) return cell - r;
        }
        return -1;
    }
    private void removeFrom(DragItem d) {
        if (d.from.startsWith("hfolder:")) { leftFolder(d); return; }
        if (d.from.equals("desk") && d.app.items != null && d.app.key.startsWith("folder:")) {
            HashMap<Integer, String> m = deskItems(); m.remove(d.index); saveDesk(m);
            LinkedHashMap<String, String[]> f = hfolders(); f.remove(d.app.key.substring(7)); saveHFolders(f);
            showDesk(); return;
        }
        if (d.from.equals("desk")) { HashMap<Integer, String> m = deskItems(); m.remove(d.index); saveDesk(m); showDesk(); }
        else if (d.from.equals("dock")) { ArrayList<String> l = dockItems(); if (d.index < l.size()) l.remove(d.index); saveDock(l); showDock(); }
    }

    private void buildDropBar() {
        dropBar = new LinearLayout(this);
        dropBar.setGravity(Gravity.CENTER);
        dropBar.setVisibility(View.GONE);
        dropRemove = dropTarget(Ico.CLOSE, L.t("Remove"));
        dropInfo = dropTarget(Ico.GEAR, L.t("App info"));
        dropUninstall = dropTarget(Ico.CLOSE, L.t("Uninstall"));
        dropBar.addView(dropRemove); dropBar.addView(dropInfo); dropBar.addView(dropUninstall);
        scene.addView(dropBar, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
    }
    private Glass dropTarget(int type, String label) {
        Glass g = new Glass(this, 999, Glass.DARK);
        g.setGravity(Gravity.CENTER_VERTICAL);
        g.setPadding(dp(14), dp(9), dp(16), dp(9));
        g.addView(icon(type, 16, Color.WHITE));
        TextView t = text(13.5f, Color.WHITE, U.sansBold(this)); t.setText(label); t.setPadding(dp(7), 0, 0, 0); t.setSingleLine(true);
        g.addView(t);
        LinearLayout.LayoutParams p = lp(-2, -2); p.leftMargin = p.rightMargin = dp(5);
        g.setLayoutParams(p);
        return g;
    }
    private void showDropBar(DragItem d) {
        dropRemove.setVisibility(d.from.equals("drawer") ? View.GONE : View.VISIBLE);
        dropInfo.setVisibility(d.app.verses ? View.GONE : View.VISIBLE);
        dropUninstall.setVisibility(d.app.verses ? View.GONE : View.VISIBLE);
        dropBar.bringToFront();
        dropBar.setVisibility(View.VISIBLE);
        dropBar.setAlpha(0f); dropBar.animate().alpha(1f).setDuration(150).start();
    }
    private void hideDropBar() { dropBar.setVisibility(View.GONE); }
    private void markDropBar(float x, float y) {
        dropRemove.setStyle(over(dropRemove, x, y) ? Glass.GOLD : Glass.DARK);
        dropInfo.setStyle(over(dropInfo, x, y) ? Glass.GOLD : Glass.DARK);
        dropUninstall.setStyle(over(dropUninstall, x, y) ? Glass.GOLD : Glass.DARK);
    }

    /** Held without moving: the app's own shortcuts, then add / remove / info / uninstall. */
    private void appMenu(final DragItem d) {
        final App a = d.app;
        if (d.view == null || d.view.getWindowToken() == null) return;
        PopupMenu m = new PopupMenu(this, d.view);
        Menu menu = m.getMenu();
        final List<ShortcutInfo> shortcuts = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 25 && !a.verses) {
            try {
                LauncherApps la = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
                if (la.hasShortcutHostPermission()) {
                    LauncherApps.ShortcutQuery q = new LauncherApps.ShortcutQuery();
                    q.setPackage(a.cn.getPackageName());
                    q.setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST);
                    List<ShortcutInfo> got = la.getShortcuts(q, a.user);
                    if (got != null) for (ShortcutInfo s : got) {
                        if (s.getActivity() != null && !s.getActivity().equals(a.cn)) continue;
                        if (shortcuts.size() < 4) shortcuts.add(s);
                    }
                }
            } catch (Throwable ignored) { }
        }
        for (int i = 0; i < shortcuts.size(); i++) {
            CharSequence l = shortcuts.get(i).getShortLabel();
            menu.add(0, 100 + i, i, l == null ? L.t("Shortcut") : l);
        }
        if (d.from.equals("drawer")) {
            menu.add(0, 1, 10, L.t("Add to home screen"));
            menu.add(0, 2, 11, L.t("Add to dock"));
        } else menu.add(0, 3, 10, L.t("Remove from ") + L.t(d.from.equals("dock") ? "dock" : "home screen"));
        if (a.items == null) menu.add(0, 8, 10, isVip(a) ? L.t("Remove from VIP") : L.t("Add to VIP  ★"));
        if (a.items == null) menu.add(0, 6, 11, L.t("Rename"));
        if (d.from.equals("drawer") && !a.verses && a.items == null) menu.add(0, 7, 11, hidden(a) ? "Show in app list" : "Hide from app list");
        if (!a.verses) { menu.add(0, 4, 12, L.t("App info")); menu.add(0, 5, 13, L.t("Uninstall")); }
        m.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override public boolean onMenuItemClick(android.view.MenuItem it) {
                int id = it.getItemId();
                if (id >= 100) {
                    try { ((LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE)).startShortcut(shortcuts.get(id - 100), null, null); }
                    catch (Throwable t) { Toast.makeText(Launcher.this, L.t("Can't open that shortcut"), Toast.LENGTH_SHORT).show(); }
                } else if (id == 1) {
                    HashMap<Integer, String> mm = deskItems();
                    int c = freeCellNear(mm, 0);
                    if (c < 0) Toast.makeText(Launcher.this, L.t("No room left on the home screen"), Toast.LENGTH_SHORT).show();
                    else { mm.put(c, a.key); saveDesk(mm); showDesk(); closeDrawer(); }
                } else if (id == 2) {
                    ArrayList<String> l = dockItems();
                    if (l.contains(a.key)) Toast.makeText(Launcher.this, a.label + " is already in the dock", Toast.LENGTH_SHORT).show();
                    else if (l.size() >= DOCK_MAX) Toast.makeText(Launcher.this, "The dock holds " + DOCK_MAX + " apps. Remove one first.", Toast.LENGTH_SHORT).show();
                    else { l.add(a.key); saveDock(l); showDock(); closeDrawer(); }
                } else if (id == 3) removeFrom(d);
                else if (id == 4) appInfo(a);
                else if (id == 5) uninstall(a);
                else if (id == 6) renameDialog(a);
                else if (id == 8) setVip(a, !isVip(a));
                else if (id == 7) {
                    boolean h = !hidden(a); setHidden(a.key, h);
                    if (h) Toast.makeText(Launcher.this, a.label + " is hidden. Search finds it; Settings › Home screen brings it back.", Toast.LENGTH_LONG).show();
                }
                return true;
            }
        });
        m.show();
    }
    private void appInfo(App a) {
        try { ((LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE)).startAppDetailsActivity(a.cn, a.user, null, null); }
        catch (Throwable t) { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.cn.getPackageName()))); }
    }
    private void uninstall(App a) {
        try { startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.cn.getPackageName()))); } catch (Throwable ignored) { }
    }

    // ---------------------------------------------------------------- widgets
    private ArrayList<Integer> widgetIds() {
        ArrayList<Integer> l = new ArrayList<>();
        for (String s : U.prefs(this).getString(widgetsKey(), "").split(",")) {
            try { if (!s.isEmpty()) l.add(Integer.parseInt(s)); } catch (NumberFormatException ignored) { }
        }
        return l;
    }
    private void saveWidgets(ArrayList<Integer> l) {
        StringBuilder b = new StringBuilder();
        for (Integer i : l) { if (b.length() > 0) b.append(','); b.append(i); }
        U.prefs(this).edit().putString(widgetsKey(), b.toString()).apply();
    }
    private final HashMap<Integer, View> widgetViews = new HashMap<>();
    private void showWidgets() {
        desk.clearWidgets();
        widgetViews.clear();
        for (final int id : widgetIds()) {
            final AppWidgetProviderInfo info = widgetManager.getAppWidgetInfo(id);
            if (info == null) continue;
            try {
                final AppWidgetHostView v = widgetHost.createView(getApplicationContext(), id, info);
                v.setAppWidget(id, info);
                int rows = widgetRows(id, info);
                int h = rows * dp(86) - dp(6);
                int cols = U.prefs(this).getInt("wcols_" + id, 0), start = U.prefs(this).getInt("wstart_" + id, 0);
                desk.putWidget(v, h, cols, start, U.prefs(this).getInt("wrow_" + id, -1));
                widgetViews.put(id, v);
                // tell the widget how much room it has (in dp), so it lays itself out for that size
                float d = getResources().getDisplayMetrics().density;
                int full = Math.round(getResources().getDisplayMetrics().widthPixels / d) - 24;
                int wdp = cols > 0 && cols < desk.columns ? Math.round(full * cols / (float) desk.columns) - 8 : full, hdp = Math.round(h / d);
                try { v.updateAppWidgetSize(null, wdp, hdp, wdp, hdp); } catch (Throwable ignored) { }
                if (v instanceof Widgets.HoldView) ((Widgets.HoldView) v).bare = !U.prefs(this).getBoolean("wbg_" + id, false);
                if (v instanceof Widgets.HoldView) ((Widgets.HoldView) v).onHold = new Runnable() {
                    @Override public void run() { widgetMenu(id, info, v); }
                };
            } catch (Throwable ignored) { }
        }
    }

    // ---------------------------------------------------------------- resizing a widget on screen
    /** Dots on the widget's sides and bottom: drag them to make it wider, narrower, taller or shorter.
     *  Sizes snap to the home-screen grid. Tap anywhere else when done. */
    private ResizeFrame resizing;
    private void startResize(final int id, final AppWidgetProviderInfo info) {
        endResize();
        resizing = new ResizeFrame(id, info);
        scene.addView(resizing, new FrameLayout.LayoutParams(-1, -1));
        resizing.track();
    }
    private void endResize() {
        if (resizing != null) { scene.removeView(resizing); resizing = null; }
    }
    private final class ResizeFrame extends View {
        final int id; final AppWidgetProviderInfo info;
        final android.graphics.RectF r = new android.graphics.RectF();
        final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG), dim = new Paint(), label = new Paint(Paint.ANTI_ALIAS_FLAG);
        int edge = -1;          // 0 left, 1 right, 2 bottom, 3 the whole widget (moving it)
        float mx, my;
        float deskL, deskT;
        ResizeFrame(int id, AppWidgetProviderInfo info) {
            super(Launcher.this);
            this.id = id; this.info = info;
            frame.setStyle(Paint.Style.STROKE); frame.setStrokeWidth(dp(2)); frame.setColor(0xFFFFC94A);
            dot.setColor(0xFFFFC94A);
            dim.setColor(0x59000000);
            label.setColor(Color.WHITE); label.setTextSize(dp(13)); label.setTextAlign(Paint.Align.CENTER);
            label.setShadowLayer(dp(4), 0, dp(1), 0xCC000000);
        }
        /** Puts the frame around the widget as it is now. */
        void track() {
            post(new Runnable() { @Override public void run() {
                View v = widgetViews.get(id);
                if (v == null) { endResize(); return; }
                int[] a = new int[2], s = new int[2], dd = new int[2];
                v.getLocationOnScreen(a); scene.getLocationOnScreen(s); desk.getLocationOnScreen(dd);
                deskL = dd[0] - s[0]; deskT = dd[1] - s[1];
                r.set(a[0] - s[0], a[1] - s[1], a[0] - s[0] + v.getWidth(), a[1] - s[1] + v.getHeight());
                invalidate();
            } });
        }
        @Override protected void onDraw(Canvas c) {
            if (r.isEmpty()) return;
            c.drawRoundRect(r, dp(16), dp(16), frame);
            float rr = dp(9);
            c.drawCircle(r.left, r.centerY(), rr, dot);
            c.drawCircle(r.right, r.centerY(), rr, dot);
            c.drawCircle(r.centerX(), r.bottom, rr, dot);
            String hint = L.t("Drag to move  ·  dots to resize  ·  tap outside when done");
            float ty = r.bottom + dp(36) < getHeight() - dp(120) ? r.bottom + dp(34) : Math.max(dp(40), r.top - dp(16));
            float tw = label.measureText(hint) / 2f + dp(14);
            dim.setColor(0x99000000); dim.setAntiAlias(true);
            c.drawRoundRect(getWidth() / 2f - tw, ty - dp(20), getWidth() / 2f + tw, ty + dp(9), dp(15), dp(15), dim);
            c.drawText(hint, getWidth() / 2f, ty, label);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX(), y = e.getY(), near = dp(32);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    edge = Math.abs(x - r.left) < near && Math.abs(y - r.centerY()) < r.height() / 2 + near ? 0
                         : Math.abs(x - r.right) < near && Math.abs(y - r.centerY()) < r.height() / 2 + near ? 1
                         : Math.abs(y - r.bottom) < near && x > r.left - near && x < r.right + near ? 2
                         : r.contains(x, y) ? 3 : -1;
                    if (edge < 0) { endResize(); return true; }
                    mx = x; my = y;
                    if (edge == 3) { View wv = widgetViews.get(id); if (wv != null) wv.animate().alpha(.55f).setDuration(120).start(); }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float min = dp(44);
                    if (edge == 0) r.left = Math.max(deskL, Math.min(x, r.right - min));
                    else if (edge == 1) r.right = Math.min(deskL + desk.getWidth(), Math.max(x, r.left + min));
                    else if (edge == 2) r.bottom = Math.max(r.top + min, Math.min(y, deskT + desk.getHeight()));
                    else if (edge == 3) {   // carry the frame (and the widget) with the finger, inside the home grid
                        float ddx = x - mx, ddy = y - my;
                        ddx = Math.max(deskL - r.left, Math.min(deskL + desk.getWidth() - r.right, ddx));
                        ddy = Math.max(deskT - r.top, Math.min(deskT + desk.getHeight() - r.bottom, ddy));
                        r.offset(ddx, ddy); mx = x; my = y;
                        View wv = widgetViews.get(id);
                        if (wv != null) { wv.setTranslationX(wv.getTranslationX() + ddx); wv.setTranslationY(wv.getTranslationY() + ddy); }
                    }
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (edge >= 0) apply();
                    edge = -1;
                    return true;
            }
            return true;
        }
        /** Snaps to the grid and saves: columns wide, starting column, rows tall. */
        void apply() {
            int cw = desk.cellWidth(), ch = desk.cellHeight();
            if (cw <= 0 || ch <= 0) return;
            int cols = Math.max(1, Math.min(desk.columns, (int) (r.width() / cw + .4f)));   // leans small: one icon wide is easy to reach
            int start = Math.max(0, Math.min(desk.columns - cols, Math.round((r.left - deskL) / cw)));
            int rows = Math.max(1, Math.min(4, (int) ((r.height() + dp(8)) / ch + .4f)));
            int row = Math.max(0, Math.round((r.top - deskT) / ch));
            View wv = widgetViews.get(id);
            if (wv != null) { wv.setTranslationX(0); wv.setTranslationY(0); wv.setAlpha(1f); }
            U.prefs(Launcher.this).edit().putInt("wrows_" + id, rows).putInt("wrow_" + id, row)
                    .putInt("wcols_" + id, cols >= desk.columns ? 0 : cols).putInt("wstart_" + id, start).apply();
            showDesk();
            track();
        }
    }

    /** A widget's height in home-screen rows: what was chosen, else what the widget asks for. */
    private int widgetRows(int id, AppWidgetProviderInfo info) {
        int r = U.prefs(this).getInt("wrows_" + id, 0);
        if (r > 0) return r;
        int want = info.minHeight > 0 ? info.minHeight : dp(70);
        return Math.max(1, Math.min(2, (int) Math.ceil(want / (float) dp(86))));   // new widgets start at most 2 rows tall
    }
    /** Holding a widget: change its settings, its size or its place, or remove it. */
    private void widgetMenu(final int id, final AppWidgetProviderInfo info, View anchor) {
        final int rows = widgetRows(id, info);
        final ArrayList<Integer> ids = widgetIds();
        final int pos = ids.indexOf(id);
        PopupMenu m = new PopupMenu(this, anchor, Gravity.CENTER);
        if (info.configure != null) m.getMenu().add(0, 1, 0, L.t("Edit widget"));
        m.getMenu().add(0, 6, 0, L.t("Move or resize"));
        android.view.SubMenu size = m.getMenu().addSubMenu(0, 2, 1, L.t("Size  ·  ") + new String[] {"", L.t("Small"), L.t("Medium"), L.t("Large"), L.t("Extra large")}[rows]);
        String[] names = {L.t("Small (1 row)"), L.t("Medium (2 rows)"), L.t("Large (3 rows)"), L.t("Extra large (4 rows)")};
        // how many rows this widget could have: the home screen's height, minus the other widgets
        int avail = desk.getHeight();
        for (int other : ids) if (other != id) {
            AppWidgetProviderInfo oi = widgetManager.getAppWidgetInfo(other);
            if (oi != null) avail -= widgetRows(other, oi) * dp(86) + dp(4);
        }
        int fit = Math.max(1, avail / dp(86));
        for (int i = 0; i < 4; i++) {
            android.view.MenuItem it = size.add(1, 10 + i + 1, i, (i + 1 == rows ? "✓  " : "     ") + names[i] + (i + 1 > fit ? "  ·  not enough room" : ""));
            it.setEnabled(i + 1 <= fit);
        }
        size.add(1, 15, 9, "     Icon size (1 × 1)");
        final boolean bgOn = U.prefs(this).getBoolean("wbg_" + id, false);
        m.getMenu().add(0, 7, 1, bgOn ? L.t("Hide widget background") : L.t("Show widget background"));
        if (pos > 0) m.getMenu().add(0, 3, 2, L.t("Move up"));
        if (pos >= 0 && pos < ids.size() - 1) m.getMenu().add(0, 4, 3, L.t("Move down"));
        m.getMenu().add(0, 5, 4, L.t("Remove widget"));
        m.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override public boolean onMenuItemClick(android.view.MenuItem it) {
                int k = it.getItemId();
                if (k == 1) {
                    try { widgetHost.startAppWidgetConfigureActivityForResult(Launcher.this, id, 0, REQ_RECONFIG, null); }
                    catch (Throwable t) { Toast.makeText(Launcher.this, L.t("This widget has no settings"), Toast.LENGTH_SHORT).show(); }
                } else if (k == 6) {
                    startResize(id, info);
                } else if (k == 15) {
                    U.prefs(Launcher.this).edit().putInt("wrows_" + id, 1).putInt("wcols_" + id, desk.columns > 1 ? 1 : 0).apply();
                    showDesk();
                } else if (k == 7) {
                    U.prefs(Launcher.this).edit().putBoolean("wbg_" + id, !bgOn).apply();
                    showDesk();
                } else if (k >= 11 && k <= 14) {
                    U.prefs(Launcher.this).edit().putInt("wrows_" + id, k - 10).apply();
                    showDesk();
                } else if (k == 3 || k == 4) {
                    int to = k == 3 ? pos - 1 : pos + 1;
                    ids.remove(pos); ids.add(to, id);
                    saveWidgets(ids); showDesk();
                } else if (k == 5) {
                    ids.remove((Integer) id);
                    widgetHost.deleteAppWidgetId(id);
                    U.prefs(Launcher.this).edit().remove("wrows_" + id).remove("wcols_" + id).remove("wstart_" + id).remove("wbg_" + id).remove("wrow_" + id).apply();
                    saveWidgets(ids); showDesk();
                }
                return true;
            }
        });
        m.show();
    }
    /** The widget list: grouped by app, each with its preview picture and size, so you see what you'll get. */
    private void pickWidget() {
        final List<AppWidgetProviderInfo> all = new ArrayList<>(widgetManager.getInstalledProviders());
        final PackageManager pm = getPackageManager();
        final HashMap<String, String> appName = new HashMap<>();
        for (AppWidgetProviderInfo i : all) {
            String pk = i.provider.getPackageName();
            if (!appName.containsKey(pk)) {
                String n = pk;
                try { n = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pk, 0))); } catch (Throwable ignored) { }
                appName.put(pk, n);
            }
        }
        Collections.sort(all, new Comparator<AppWidgetProviderInfo>() { @Override public int compare(AppWidgetProviderInfo x, AppWidgetProviderInfo y) {
            int k = appName.get(x.provider.getPackageName()).compareToIgnoreCase(appName.get(y.provider.getPackageName()));
            return k != 0 ? k : label(x, pm).compareToIgnoreCase(label(y, pm)); } });
        final android.app.Dialog dlg = new android.app.Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xF20C0F16);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(18), dp(36), dp(10), dp(8));
        TextView title = text(22, Color.WHITE, U.serif(this));
        title.setText(L.t("Add a widget"));
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView close = new ImageView(this);
        close.setImageDrawable(new Ico(Ico.CLOSE, Color.WHITE));
        close.setPadding(dp(10), dp(10), dp(10), dp(10));
        close.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { dlg.dismiss(); } });
        head.addView(close, lp(dp(44), dp(44)));
        page.addView(head);
        TextView hint = text(12.5f, 0xB3FFFFFF, U.light());
        hint.setText(L.t("Tap a widget to add it. Later, hold it on the home screen to edit, resize, move or remove it."));
        hint.setPadding(dp(18), 0, dp(18), dp(10));
        page.addView(hint);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), 0, dp(12), dp(40));
        int density = getResources().getDisplayMetrics().densityDpi;
        String lastApp = null;
        for (final AppWidgetProviderInfo info : all) {
            String app = appName.get(info.provider.getPackageName());
            if (!app.equals(lastApp)) {
                lastApp = app;
                TextView h = text(14, 0xFFFFC94A, U.sansBold(this));
                h.setText(app);
                h.setPadding(dp(6), dp(16), dp(6), dp(6));
                list.addView(h);
            }
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(12), dp(12), dp(10));
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(0x1AFFFFFF); bg.setCornerRadius(dp(18));
            card.setBackground(bg);
            ImageView pv = new ImageView(this);
            android.graphics.drawable.Drawable d = null;
            try { d = info.loadPreviewImage(this, density); } catch (Throwable ignored) { }
            if (d == null) { try { d = info.loadIcon(this, density); } catch (Throwable ignored) { } }
            pv.setImageDrawable(d);
            pv.setAdjustViewBounds(true);
            pv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            pv.setMaxHeight(dp(150));
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, -2); pl.gravity = Gravity.CENTER_HORIZONTAL;
            card.addView(pv, pl);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(2), dp(8), dp(2), 0);
            TextView n = text(14.5f, Color.WHITE, U.sans(this));
            n.setText(label(info, pm));
            row.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
            int cols = Math.max(1, Math.min(desk.columns, (int) Math.ceil(info.minWidth / (float) dp(80))));
            int rws = Math.max(1, Math.min(4, (int) Math.ceil(info.minHeight / (float) dp(86))));
            TextView sz = text(12, 0xB3FFFFFF, U.light());
            sz.setText(cols + " × " + rws);
            row.addView(sz);
            card.addView(row);
            card.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { dlg.dismiss(); bindWidget(info); } });
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2); cl.bottomMargin = dp(10);
            list.addView(card, cl);
        }
        if (all.isEmpty()) { TextView e = text(14, Color.WHITE, U.sans(this)); e.setText(L.t("No widgets are installed.")); list.addView(e); }
        sv.addView(list);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        dlg.setContentView(page);
        dlg.show();
    }
    private static String label(AppWidgetProviderInfo i, PackageManager pm) {
        try { return i.loadLabel(pm); } catch (Throwable t) { return i.provider.getClassName(); }
    }
    private void bindWidget(AppWidgetProviderInfo info) {
        int id = widgetHost.allocateAppWidgetId();
        pendingWidget = id;
        if (widgetManager.bindAppWidgetIdIfAllowed(id, info.provider)) configureWidget(id);
        else {
            Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
            try { startActivityForResult(i, REQ_BIND); } catch (Throwable t) { widgetHost.deleteAppWidgetId(id); pendingWidget = -1; }
        }
    }
    private void configureWidget(int id) {
        AppWidgetProviderInfo info = widgetManager.getAppWidgetInfo(id);
        if (info != null && info.configure != null) {
            try { widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIG, null); return; }
            catch (Throwable ignored) { }
        }
        addWidget(id);
        pendingWidget = -1;
    }
    private void addWidget(int id) {
        ArrayList<Integer> l = widgetIds();
        if (!l.contains(id)) l.add(id);
        saveWidgets(l);
        showDesk();
    }
    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_RECONFIG) { showDesk(); return; }
        if (pendingWidget < 0) return;
        if (res != RESULT_OK) { widgetHost.deleteAppWidgetId(pendingWidget); pendingWidget = -1; return; }
        if (req == REQ_BIND) configureWidget(pendingWidget);
        else if (req == REQ_CONFIG) { addWidget(pendingWidget); pendingWidget = -1; }
    }
    private void removeWidgetMenu() {
        final ArrayList<Integer> ids = widgetIds();
        String[] names = new String[ids.size()];
        for (int i = 0; i < names.length; i++) {
            AppWidgetProviderInfo info = widgetManager.getAppWidgetInfo(ids.get(i));
            names[i] = info == null ? L.t("Widget") : label(info, getPackageManager());
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(L.t("Remove a widget"))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dlg, int which) {
                        int id = ids.remove(which);
                        widgetHost.deleteAppWidgetId(id);
                        saveWidgets(ids);
                        showDesk();
                    }
                }).setNegativeButton(L.t("Cancel"), null).show();
    }

    // ---------------------------------------------------------------- drawer
    private void buildDrawer() {
        drawer = new FrameLayout(this);
        drawer.setVisibility(View.GONE);
        drawer.setBackgroundColor(0x33060A18);
        scene.addView(drawer, new FrameLayout.LayoutParams(-1, -1));

        // the top: today's date (the time is already in the status bar) and the saint of the day
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(22), dp(10), dp(14), 0);
        LinearLayout r1 = new LinearLayout(this);
        r1.setGravity(Gravity.CENTER_VERTICAL);
        TextClock d = new TextClock(this);
        d.setFormat12Hour("EEEE, MMMM d"); d.setFormat24Hour("EEEE, MMMM d");
        d.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 21 * textScale); d.setTextColor(Color.WHITE); d.setTypeface(U.sansBold(this));
        d.setSingleLine(true); d.setEllipsize(TextUtils.TruncateAt.END); shadow(d, 5);
        d.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openCalendar(); } });
        r1.addView(d, new LinearLayout.LayoutParams(0, -2, 1f));
        // two settings, told apart at a glance: Velthon's own (gold) and the phone's (white, a phone with a gear)
        r1.addView(settingsChip(new Ico(Ico.GEAR, GOLD), "Velthon", GOLD, new View.OnClickListener() {
            @Override public void onClick(View v) { openSettings(); } }), lp(-2, -2));
        android.graphics.drawable.GradientDrawable body = new android.graphics.drawable.GradientDrawable();
        body.setCornerRadius(dp(4)); body.setStroke(dp(1.6f), Color.WHITE); body.setColor(Color.TRANSPARENT);
        android.graphics.drawable.LayerDrawable phoneIco = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[] { body, new Ico(Ico.GEAR, Color.WHITE) });
        phoneIco.setLayerInset(0, dp(5), 0, dp(5), 0);
        phoneIco.setLayerInset(1, dp(7), dp(5), dp(7), dp(5));
        LinearLayout.LayoutParams pcl = lp(-2, -2); pcl.leftMargin = dp(4);
        r1.addView(settingsChip(phoneIco, L.t("Phone"), Color.WHITE, new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                catch (Throwable t) { Toast.makeText(Launcher.this, L.t("Can't open ") + L.t("Settings"), Toast.LENGTH_SHORT).show(); } } }), pcl);
        top.addView(r1);
        saintRow = new LinearLayout(this);
        saintRow.setGravity(Gravity.CENTER_VERTICAL);
        saintRow.setPadding(0, dp(4), 0, 0);
        saintPic = new ImageView(this);
        saintPic.setScaleType(ImageView.ScaleType.CENTER_CROP);
        saintPic.setClipToOutline(true);
        saintPic.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setOval(0, 0, v.getWidth(), v.getHeight()); } });
        android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
        ring.setShape(android.graphics.drawable.GradientDrawable.OVAL); ring.setColor(0x33FFC94A);
        saintPic.setBackground(ring);
        saintRow.addView(saintPic, new LinearLayout.LayoutParams(dp(34), dp(34)));
        saintText = text(12.5f, Color.WHITE, U.sans(this));
        saintText.setMaxLines(2); saintText.setEllipsize(TextUtils.TruncateAt.END);
        saintText.setPadding(dp(10), 0, dp(8), 0); shadow(saintText, 4);
        saintRow.addView(saintText, new LinearLayout.LayoutParams(0, -2, 1f));
        saintRow.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openChurch(); } });
        top.addView(saintRow, lp(-1, -2));
        drawer.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        showSaint();

        sheet = new Glass(this, 30, Glass.DARK);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(14), dp(10), dp(14), 0);
        View grab = new View(this);
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(0x73FFFFFF); gd.setCornerRadius(dp(3));
        grab.setBackground(gd);
        LinearLayout.LayoutParams gl = lp(dp(40), dp(4)); gl.gravity = Gravity.CENTER_HORIZONTAL; gl.bottomMargin = dp(12);
        sheet.addView(grab, gl);

        Glass sbox = new Glass(this, 999, Glass.GOLD_EDGE);
        sbox.blur = false;
        sbox.setOrientation(LinearLayout.HORIZONTAL);
        sbox.setGravity(Gravity.CENTER_VERTICAL);
        sbox.setPadding(dp(14), 0, dp(12), 0);
        sbox.addView(icon(Ico.SEARCH, 18, Color.WHITE));
        query = new EditText(this) {
            @Override public boolean onDragEvent(DragEvent e) { return false; }   // apps being moved are not text
        };
        query.setBackground(null);
        query.setHint(L.t("Search apps"));
        query.setHintTextColor(0xCCFFFFFF);
        query.setTextColor(Color.WHITE);
        query.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.5f * textScale);
        query.setTypeface(U.sans(this));
        query.setSingleLine(true);
        query.setImeOptions(EditorInfo.IME_ACTION_GO);
        query.setPadding(dp(10), 0, 0, 0);
        query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { filter(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        query.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int id, KeyEvent e) {
                if (!shown.isEmpty()) { open(shown.get(0), null); return true; }
                String q = query.getText().toString().trim();
                if (q.isEmpty()) return true;
                try { startActivity(new Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", q)); }
                catch (ActivityNotFoundException ex) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))); }
                return true;
            }
        });
        sbox.addView(query, new LinearLayout.LayoutParams(0, -1, 1f));
        sheet.addView(sbox, lp(-1, dp(44)));

        // above: folders (VIP first). Below: the apps, one category at a time; swipe left or right for the next.
        TextView fh = text(12, 0xFFF2B640, U.sansBold(this));
        fh.setText(L.t("Folders")); fh.setPadding(dp(6), dp(12), 0, dp(4));
        sheet.addView(fh);
        // VIP stays put at the start; the other folders scroll beside it
        LinearLayout frow = new LinearLayout(this);
        frow.setGravity(Gravity.TOP);
        vipBox = new FrameLayout(this);
        frow.addView(vipBox, new LinearLayout.LayoutParams(-2, -2));
        View sep = new View(this); sep.setBackgroundColor(0x33FFFFFF);
        LinearLayout.LayoutParams spl = new LinearLayout.LayoutParams(Math.max(1, dp(.8f)), dp(44)); spl.topMargin = dp(10); spl.leftMargin = spl.rightMargin = dp(4);
        frow.addView(sep, spl);
        HorizontalScrollView fs = new HorizontalScrollView(this);
        fs.setHorizontalScrollBarEnabled(false); fs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        fs.setFadingEdgeLength(dp(24)); fs.setHorizontalFadingEdgeEnabled(true);
        folderRow = new LinearLayout(this);
        fs.addView(folderRow);
        frow.addView(fs, new LinearLayout.LayoutParams(0, -2, 1f));
        sheet.addView(frow, lp(-1, -2));
        View line = new View(this); line.setBackgroundColor(0x24FFFFFF);
        LinearLayout.LayoutParams ll = lp(-1, Math.max(1, dp(.7f))); ll.topMargin = dp(8); ll.leftMargin = ll.rightMargin = dp(6);
        sheet.addView(line, ll);
        catScroll = new HorizontalScrollView(this);
        catScroll.setHorizontalScrollBarEnabled(false); catScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        catRow = new LinearLayout(this);
        catRow.setPadding(dp(2), 0, dp(2), 0);
        catScroll.addView(catRow);
        LinearLayout.LayoutParams tbl = lp(-1, -2); tbl.topMargin = dp(10); tbl.bottomMargin = dp(8);
        sheet.addView(catScroll, tbl);
        cat = U.prefs(this).getString("drawer_cat", "All");

        recentBox = new LinearLayout(this);
        recentBox.setOrientation(LinearLayout.VERTICAL);
        TextView rt = text(12, RECENT, U.sansBold(this));
        rt.setText(L.t("Recently used"));
        rt.setPadding(dp(6), dp(2), 0, dp(6));
        recentBox.addView(rt);
        recentRow = new LinearLayout(this);
        recentBox.addView(recentRow, lp(-1, -2));
        LinearLayout.LayoutParams rbl = lp(-1, -2); rbl.bottomMargin = dp(6);
        sheet.addView(recentBox, rbl);

        grid = new GridView(this);
        grid.setNumColumns(U.prefs(this).getInt("columns", 3));
        grid.setVerticalSpacing(dp(14));
        grid.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        grid.setVerticalScrollBarEnabled(false);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid.setPadding(0, dp(8), 0, dp(24));
        grid.setClipToPadding(false);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                App a = shown.get(pos);
                if (a.items != null) openFolder(a); else open(a, v);
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                if (shown.get(pos).items != null) {
                    final App f = shown.get(pos);
                    PopupMenu pm = new PopupMenu(Launcher.this, v, Gravity.CENTER);
                    pm.getMenu().add(0, 1, 0, L.t("Add folder to home screen"));
                    pm.getMenu().add(0, 2, 1, L.t("Open"));
                    pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override public boolean onMenuItemClick(android.view.MenuItem it) { if (it.getItemId() == 1) folderToHome(f); else openFolder(f); return true; }
                    });
                    pm.show();
                    return true;
                }
                hideKeyboard();
                startDrag(v, shown.get(pos), "drawer", -1);
                return true;
            }
        });
        sheet.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));

        FrameLayout.LayoutParams shl = new FrameLayout.LayoutParams(-1, -1);
        shl.topMargin = dp(100);
        shl.bottomMargin = -dp(30);
        drawer.addView(sheet, shl);

        final GestureDetector close = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (a == null || b == null || dragging) return false;
                if (b.getY() - a.getY() > dp(70) && vy > Math.abs(vx) && atTop()) { closeDrawer(); return true; }
                return false;
            }
        });
        drawer.setOnTouchListener(new View.OnTouchListener() { @Override public boolean onTouch(View v, MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP && e.getY() < sheet.getTop()) { closeDrawer(); return true; }
            return close.onTouchEvent(e) || true;
        } });
        final GestureDetector pages = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (a == null || b == null || dragging) return false;
                float dx = b.getX() - a.getX(), dy = b.getY() - a.getY();
                if (Math.abs(dx) > dp(60) && Math.abs(dx) > Math.abs(dy) * 1.4f) { stepCat(dx < 0 ? 1 : -1); return true; }
                return false;
            }
        });
        grid.setOnTouchListener(new View.OnTouchListener() { @Override public boolean onTouch(View v, MotionEvent e) { close.onTouchEvent(e); return false; } });
        sheet.setOnTouchListener(new View.OnTouchListener() { @Override public boolean onTouch(View v, MotionEvent e) { return close.onTouchEvent(e) || true; } });
    }

    private boolean atTop() {
        return grid.getChildCount() == 0 || (grid.getFirstVisiblePosition() == 0 && grid.getChildAt(0).getTop() >= grid.getPaddingTop() - 2);
    }

    /** Today's saint in the app list's header (CopticChurch.net), with a small picture when there is one. */
    private void showSaint() {
        if (saintRow == null) return;
        String st = Church.calendar(this).equals("orthodox") ? Synax.saint(this) : null;
        boolean ar = U.langs(this).equals("ar");
        String cd = Church.calendar(this).equals("orthodox") ? Church.copticDate(java.util.Calendar.getInstance(), ar) : null;
        if (st == null && cd == null) { saintRow.setVisibility(View.GONE); return; }
        saintRow.setVisibility(View.VISIBLE);
        android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder();
        b.append(L.t("Saint of the day"));
        if (cd != null) b.append("  ·  ").append(cd);
        b.setSpan(new android.text.style.ForegroundColorSpan(0xFFF2B640), 0, b.length(), 0);
        b.setSpan(new android.text.style.RelativeSizeSpan(.88f), 0, b.length(), 0);
        if (st != null) b.append("\n").append(st.replaceFirst("^(?i)the\\s+", "").replaceAll("[.]$", ""));
        saintText.setText(b);
        android.graphics.Bitmap pic = Synax.picture(this);
        if (pic != null) { saintPic.setImageBitmap(pic); saintPic.setPadding(0, 0, 0, 0); }
        else { saintPic.setImageDrawable(new Ico(Ico.CROSS, 0xFFF2B640)); saintPic.setPadding(dp(8), dp(8), dp(8), dp(8)); }
    }

    /** The folders strip and the category chips, rebuilt only when the apps change. */
    private void drawerChrome() {
        if (folderRow == null) return;
        StringBuilder sig = new StringBuilder();
        for (App a : apps) sig.append(a.key).append(a.label).append(';');
        sig.append(U.prefs(this).getString("icon_style", "glass")).append(U.prefs(this).getString("hidden", "")).append('|').append(U.prefs(this).getString("vip", ""));
        String sg = sig.toString();
        if (sg.equals(chromeSig)) return;
        chromeSig = sg;
        // folders: VIP, then a folder for every type with two or more apps
        folderRow.removeAllViews();
        vipBox.removeAllViews();
        App vip = new App(); vip.label = "VIP"; vip.key = "vip"; vip.items = vipApps();
        vip.icon = new VipIcon(vip.items);
        vipBox.addView(folderTile(vip));
        LinkedHashMap<String, ArrayList<App>> g = groups();
        cats.clear(); cats.add("All");
        for (Map.Entry<String, ArrayList<App>> e : g.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            cats.add(e.getKey());
            if (e.getValue().size() < 2) continue;
            App f = new App(); f.label = e.getKey(); f.key = "group:" + e.getKey(); f.items = e.getValue();
            f.icon = new FolderIcon(e.getValue());
            folderRow.addView(folderTile(f));
        }
        if (!cats.contains(cat)) cat = "All";
        catRow.removeAllViews();
        for (final String k : cats) {
            Glass g2 = new Glass(this, 999, Glass.GOLD);
            g2.blur = false;
            g2.setGravity(Gravity.CENTER);
            g2.setPadding(dp(14), 0, dp(14), 0);
            TextView tv = text(13, Color.WHITE, U.sans(this));
            tv.setText(k); tv.setSingleLine(true);
            g2.addView(tv);
            g2.setTag(k);
            g2.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { selectCat(k, 0); } });
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, dp(34)); cl.rightMargin = dp(6);
            catRow.addView(g2, cl);
        }
        paintCats();
    }
    /** The VIP folder's own look: a gold tile with a white star, and how many apps are in it. */
    /** VIP: its apps, like any folder, inside a gold border, with a small gold star badge in the corner. */
    private final class VipIcon extends Drawable {
        final FolderIcon inner; final boolean empty; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        VipIcon(ArrayList<App> items) { inner = new FolderIcon(items); empty = items.isEmpty(); }
        @Override public int getIntrinsicWidth() { return dp(50); }
        @Override public int getIntrinsicHeight() { return dp(50); }
        @Override public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            float w = b.width(), r = w * .26f, sw = Math.max(2f, w * .045f);
            if (empty) { p.setStyle(Paint.Style.FILL); p.setColor(0x33F2B640); c.drawRoundRect(new android.graphics.RectF(b), r, r, p); }
            else { inner.setBounds(b); inner.draw(c); }
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(sw);
            p.setShader(new android.graphics.LinearGradient(0, b.top, 0, b.bottom, 0xFFF8CF6A, 0xFFD9921C, android.graphics.Shader.TileMode.CLAMP));
            android.graphics.RectF in = new android.graphics.RectF(b); in.inset(sw / 2, sw / 2);
            c.drawRoundRect(in, r, r, p);
            p.setShader(null); p.setStyle(Paint.Style.FILL);
            // the star: big in the middle when empty, a badge in the corner otherwise
            float cx = empty ? b.exactCenterX() : b.right - w * .12f, cy = empty ? b.exactCenterY() : b.top + w * .12f;
            float ro = empty ? w * .26f : w * .17f;
            if (!empty) { p.setColor(0xFFE9A92E); c.drawCircle(cx, cy, ro * 1.05f, p); ro *= .62f; }
            android.graphics.Path star = new android.graphics.Path();
            for (int i = 0; i < 10; i++) {
                double a = -Math.PI / 2 + i * Math.PI / 5;
                float rr = i % 2 == 0 ? ro : ro * .45f;
                float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
                if (i == 0) star.moveTo(x, y); else star.lineTo(x, y);
            }
            star.close();
            p.setColor(empty ? 0xFFF2B640 : Color.WHITE); c.drawPath(star, p);
        }
        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
    /** A small settings button: an icon in a frosted ring, its name beneath. */
    private View settingsChip(android.graphics.drawable.Drawable icon, String name, int color, View.OnClickListener click) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL); c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(dp(6), dp(2), dp(6), dp(2));
        ImageView iv = new ImageView(this); iv.setImageDrawable(icon);
        android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
        ring.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        ring.setColor(0x33FFFFFF); ring.setStroke(dp(1), (color & 0x00FFFFFF) | 0x99000000);
        iv.setBackground(ring); iv.setPadding(dp(8), dp(8), dp(8), dp(8));
        c.addView(iv, lp(dp(38), dp(38)));
        TextView t = text(9.5f, color, U.sansBold(this)); t.setText(name); t.setSingleLine(true); shadow(t, 3);
        LinearLayout.LayoutParams tl = lp(-2, -2); tl.topMargin = dp(2);
        c.addView(t, tl);
        c.setContentDescription(name + " " + L.t("Settings"));
        c.setOnClickListener(click);
        return c;
    }
    private View folderTile(final App f) {
        View t = tint(tile(f, true, 50), GOLD);
        t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            if (f.items.isEmpty()) { Toast.makeText(Launcher.this, "Long-press any app and choose \"Add to VIP\"", Toast.LENGTH_LONG).show(); return; }
            openFolder(f); } });
        t.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) {
            if (f.key.equals("vip") || f.items.isEmpty()) return false;
            PopupMenu pm = new PopupMenu(Launcher.this, v, Gravity.CENTER);
            pm.getMenu().add(0, 1, 0, L.t("Add folder to home screen"));
            pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override public boolean onMenuItemClick(android.view.MenuItem it) { folderToHome(f); return true; } });
            pm.show(); return true; } });
        t.setOnTouchListener(PRESS);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(dp(78), -2);
        t.setLayoutParams(l);
        return t;
    }
    private LinkedHashMap<String, ArrayList<App>> groups() {
        LinkedHashMap<String, ArrayList<App>> g = new LinkedHashMap<>();
        for (String name : GROUPS) g.put(name, new ArrayList<App>());
        for (App a : apps) { if (hidden(a)) continue; String k = a.verses ? "Faith" : a.group == null ? "Tools" : a.group;
            if (!g.containsKey(k)) g.put(k, new ArrayList<App>()); g.get(k).add(a); }
        return g;
    }
    private void paintCats() {
        for (int i = 0; i < catRow.getChildCount(); i++) {
            Glass g = (Glass) catRow.getChildAt(i);
            boolean on = cat.equals(g.getTag());
            g.setStyle(on ? Glass.GOLD : -1);
            g.setWillNotDraw(!on);
            TextView tv = (TextView) g.getChildAt(0);
            tv.setTypeface(on ? U.sansBold(this) : U.sans(this));
            tv.setTextColor(on ? 0xFF141414 : 0xE6FFFFFF);
            if (on) { final View sel = g; catScroll.post(new Runnable() { @Override public void run() {
                catScroll.smoothScrollTo(Math.max(0, sel.getLeft() - (catScroll.getWidth() - sel.getWidth()) / 2), 0); } }); }
        }
    }
    /** dir: +1 = the next category came in from the right, -1 from the left, 0 = tapped. */
    private void selectCat(String k, final int dir) {
        cat = k;
        U.prefs(this).edit().putString("drawer_cat", k).apply();
        paintCats();
        if (dir != 0 && U.anims(this)) {
            grid.animate().cancel();
            grid.animate().translationX(-dir * dp(60)).alpha(0f).setDuration(110).withEndAction(new Runnable() { @Override public void run() {
                filter();
                grid.setTranslationX(dir * dp(60));
                grid.animate().translationX(0).alpha(1f).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
            } }).start();
        } else filter();
    }
    private void stepCat(int d) {
        if (cats.isEmpty()) return;
        int i = Math.max(0, cats.indexOf(cat)) + d;
        if (i < 0 || i >= cats.size()) return;
        selectCat(cats.get(i), d);
    }
    // ---------------------------------------------------------------- VIP: favourites the user picks
    private ArrayList<String> vipKeys() {
        ArrayList<String> l = new ArrayList<>();
        for (String k : U.prefs(this).getString("vip", "").split("\u0001")) if (!k.isEmpty()) l.add(k);
        return l;
    }
    private ArrayList<App> vipApps() {
        ArrayList<App> l = new ArrayList<>();
        for (String k : vipKeys()) { App a = byKey.get(k); if (a != null) l.add(a); }
        return l;
    }
    private boolean isVip(App a) { return a.key != null && vipKeys().contains(a.key); }
    private void setVip(App a, boolean on) {
        ArrayList<String> l = vipKeys();
        l.remove(a.key); if (on) l.add(a.key);
        StringBuilder b = new StringBuilder();
        for (String k : l) { if (b.length() > 0) b.append('\u0001'); b.append(k); }
        U.prefs(this).edit().putString("vip", b.toString()).apply();
        filter();
    }

    /** The apps you opened most recently (newest first), for the top of the app list. */
    private void remember(App a) {
        ArrayList<String> l = new ArrayList<>(java.util.Arrays.asList(U.prefs(this).getString("recent", "").split("\\|")));
        l.remove(""); l.remove(a.key); l.add(0, a.key);
        while (l.size() > 12) l.remove(l.size() - 1);
        StringBuilder b = new StringBuilder();
        for (String k : l) { if (b.length() > 0) b.append('|'); b.append(k); }
        U.prefs(this).edit().putString("recent", b.toString()).apply();
    }
    // ---------------------------------------------------------------- hidden apps and own names
    private java.util.HashSet<String> hiddenSet() {
        java.util.HashSet<String> h = new java.util.HashSet<>();
        for (String k : U.prefs(this).getString("hidden", "").split("\u0001")) if (!k.isEmpty()) h.add(k);
        return h;
    }
    private boolean hidden(App a) { return a.key != null && hiddenSet().contains(a.key); }
    private void setHidden(String key, boolean hide) {
        java.util.HashSet<String> h = hiddenSet();
        if (hide) h.add(key); else h.remove(key);
        StringBuilder b = new StringBuilder();
        for (String k : h) { if (b.length() > 0) b.append('\u0001'); b.append(k); }
        U.prefs(this).edit().putString("hidden", b.toString()).apply();
        filter();
    }
    /** Settings › Home screen › Hidden apps: tick to hide, untick to bring back. */
    private void hiddenAppsDialog() {
        final ArrayList<App> list = new ArrayList<>();
        for (App a : apps) if (!a.verses) list.add(a);
        String[] names = new String[list.size()];
        final boolean[] on = new boolean[list.size()];
        java.util.HashSet<String> h = hiddenSet();
        for (int i = 0; i < list.size(); i++) { names[i] = list.get(i).label; on[i] = h.contains(list.get(i).key); }
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(L.t("Hide from the app list"))
                .setMultiChoiceItems(names, on, new android.content.DialogInterface.OnMultiChoiceClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int i, boolean c) { on[i] = c; } })
                .setPositiveButton(L.t("Done"), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        java.util.HashSet<String> nh = new java.util.HashSet<>();
                        for (String k : hiddenSet()) if (byKey.get(k) == null) nh.add(k);   // keep apps not installed now
                        for (int i = 0; i < list.size(); i++) if (on[i]) nh.add(list.get(i).key);
                        StringBuilder b = new StringBuilder();
                        for (String k : nh) { if (b.length() > 0) b.append('\u0001'); b.append(k); }
                        U.prefs(Launcher.this).edit().putString("hidden", b.toString()).apply();
                        filter();
                    } })
                .setNegativeButton(L.t("Cancel"), null).show();
    }
    private void renameDialog(final App a) {
        final android.widget.EditText e = new android.widget.EditText(this);
        e.setText(a.label); e.setSelectAllOnFocus(true); e.setSingleLine(true);
        FrameLayout box = new FrameLayout(this); box.setPadding(dp(20), dp(8), dp(20), 0); box.addView(e);
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(L.t("Rename")).setView(box)
                .setPositiveButton(L.t("Save"), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        String n = e.getText().toString().trim();
                        U.prefs(Launcher.this).edit().putString("label_" + a.key, n).apply();
                        loadApps();
                    } })
                .setNeutralButton(L.t("Original name"), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        U.prefs(Launcher.this).edit().remove("label_" + a.key).apply(); loadApps(); } })
                .setNegativeButton(L.t("Cancel"), null).show();
    }

    private void showRecent() {
        if (recentRow == null) return;
        recentRow.removeAllViews();
        String q = query == null ? "" : query.getText().toString().trim();
        int cols = U.prefs(this).getInt("columns", 3), n = 0;
        if (cat.equals("All") && q.isEmpty()) for (String k : U.prefs(this).getString("recent", "").split("\\|")) {
            final App a = byKey.get(k);
            if (a == null || hidden(a)) continue;
            View t = tint(tile(a, true, 50), RECENT);
            t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { open(a, v); } });
            t.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) { hideKeyboard(); startDrag(v, a, "drawer", -1); return true; } });
            recentRow.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            if (++n >= cols) break;
        }
        for (int i = n; i < cols && n > 0; i++) recentRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        recentBox.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
    }

    private void filter() {
        drawerChrome();
        showRecent();
        String q = query == null ? "" : query.getText().toString().trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (App a : apps) {
            if (q.isEmpty() && hidden(a)) continue;      // hidden apps still come up when searched for
            if (q.isEmpty() && !cat.equals("All")) {
                String k = a.verses ? "Faith" : a.group == null ? "Tools" : a.group;
                if (!k.equals(cat)) continue;
            }
            if (!q.isEmpty() && !a.label.toLowerCase(Locale.ROOT).contains(q)) continue;
            shown.add(a);
        }
        adapter.notifyDataSetChanged();
    }

    private final BaseAdapter adapter = new BaseAdapter() {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public View getView(int i, View v, ViewGroup parent) {
            App a = shown.get(i);
            if (v == null) v = tile(a, true, 50);
            ((ImageView) ((LinearLayout) v).getChildAt(0)).setImageDrawable(shaped(a, 50));
            ((TextView) ((LinearLayout) v).getChildAt(1)).setText(a.label);
            v.setTag(a);
            return v;
        }
    };

    /** Folder colours: the first (0) is the standard smoky glass; the rest are soft, see-through tints. */
    static final int[] FOLDER_COLORS = {0, 0x99B8862E, 0x992E6FB8, 0x992E9E6B, 0x99B8463C, 0x997B4CB8, 0x99B8B8B8, 0x99101010};
    private int folderColor(String key, int fallback) {
        int c = key == null ? 0 : U.prefs(this).getInt("fcolor_" + key, 0);
        return c == 0 ? fallback : c;
    }

    /** A folder opens over the app list: its apps, which open or can be dragged out like any other. */
    private void openFolder(final App f) {
        final android.app.Dialog dlg = new android.app.Dialog(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(16), dp(14), dp(14));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xF2181C24); bg.setCornerRadius(dp(26));
        box.setBackground(bg);
        final boolean mine = f.key != null && f.key.startsWith("folder:");
        TextView t = text(19, Color.WHITE, U.serif(this));
        t.setText(mine ? f.label + "  ✎" : f.label);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, dp(10));
        if (mine) t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            dlg.dismiss(); renameFolder(f.key.substring(7), null); } });
        box.addView(t);
        if (mine) {   // the folder's colour: a row of swatches
            LinearLayout sw = new LinearLayout(this); sw.setGravity(Gravity.CENTER); sw.setPadding(0, 0, 0, dp(12));
            final int cur = folderColor(f.key, 0);
            for (int k = 0; k < FOLDER_COLORS.length; k++) {
                final int col = FOLDER_COLORS[k];
                View dot = new View(this);
                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                gd.setColor(col == 0 ? 0x99202C40 : col | 0xFF000000);
                gd.setStroke(dp(col == cur ? 3 : 1), col == cur ? 0xFFF2B640 : 0x66FFFFFF);
                dot.setBackground(gd);
                LinearLayout.LayoutParams dl = lp(dp(26), dp(26)); dl.leftMargin = dl.rightMargin = dp(5);
                dot.setContentDescription(L.t("Folder colour"));
                dot.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                    U.prefs(Launcher.this).edit().putInt("fcolor_" + f.key, col).apply();
                    dlg.dismiss(); showDesk(); filter(); } });
                sw.addView(dot, dl);
            }
            box.addView(sw);
        }
        GridView g = new GridView(this);
        final int cols = Math.min(4, Math.max(3, f.items.size() <= 4 ? f.items.size() : 4));
        g.setNumColumns(cols);
        g.setVerticalSpacing(dp(8));
        g.setSelector(android.R.color.transparent);
        g.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return f.items.size(); }
            @Override public Object getItem(int i) { return f.items.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View v, ViewGroup parent) { return tile(f.items.get(i), true, 48); }
        });
        g.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) { dlg.dismiss(); open(f.items.get(pos), v); }
        });
        g.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, final View v, int pos, long id) {
                final App a = f.items.get(pos);
                if (!mine) { dlg.dismiss(); startDrag(v, a, "drawer", -1); return true; }
                // in your own folder: say plainly what can happen to the app, rather than relying on a drag
                final String fid = f.key.substring(7);
                android.widget.PopupMenu pm = new android.widget.PopupMenu(Launcher.this, v);
                pm.getMenu().add(0, 1, 0, L.t("Move to home screen"));
                pm.getMenu().add(0, 2, 1, L.t("Remove from folder"));
                pm.getMenu().add(0, 3, 2, L.t("Drag to a place"));
                pm.getMenu().add(0, 4, 3, L.t("App info"));
                pm.setOnMenuItemClickListener(new android.widget.PopupMenu.OnMenuItemClickListener() {
                    @Override public boolean onMenuItemClick(android.view.MenuItem it) {
                        dlg.dismiss();
                        DragItem d = new DragItem(); d.app = a; d.from = "hfolder:" + fid; d.index = -1;
                        switch (it.getItemId()) {
                            case 1: {
                                HashMap<Integer, String> m = deskItems();
                                int cell = freeCellNear(m, 0);
                                if (cell < 0) { Toast.makeText(Launcher.this, L.t("No room left on the home screen"), Toast.LENGTH_SHORT).show(); return true; }
                                m.put(cell, a.key); saveDesk(m); leftFolder(d); break;
                            }
                            case 2: leftFolder(d); Toast.makeText(Launcher.this, L.t("Still in the app list"), Toast.LENGTH_SHORT).show(); break;
                            case 3: startDrag(v, a, "hfolder:" + fid, -1); break;
                            case 4: appInfo(a); break;
                        }
                        return true; } });
                pm.show();
                return true; }
        });
        int rows = (f.items.size() + cols - 1) / cols;
        box.addView(g, new LinearLayout.LayoutParams(-1, Math.min(dp(96) * rows, dp(96) * 4)));
        dlg.setContentView(box);
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dlg.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels - dp(40), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dlg.show();
        box.setCameraDistance(dp(1200));
        box.setPivotY(0);
        box.setRotationX(-38f); box.setAlpha(0f); box.setScaleX(.92f); box.setScaleY(.92f);
        box.animate().rotationX(0).alpha(1f).scaleX(1f).scaleY(1f).setDuration(280)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    }

    private void open(App a, View from) {
        launch(a, from);
        ui.postDelayed(new Runnable() { @Override public void run() { closeDrawerNow(); } }, 400);
    }

    private void openDrawer(boolean typing) {
        if (drawer.getVisibility() == View.VISIBLE) return;
        query.setText("");
        grid.setNumColumns(U.prefs(this).getInt("columns", 3));
        drawer.setVisibility(View.VISIBLE);
        sheet.setTranslationY(scene.getHeight());
        drawer.setAlpha(0f);
        drawer.animate().alpha(1f).setDuration(180).setListener(null).start();
        sheet.animate().translationY(0).setDuration(260).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).setListener(null).start();
        home.animate().alpha(0f).setDuration(180).start();
        grid.setSelection(0);
        scene.setLiving(false);
        if (U.anims(this)) {
            // the sheet rises with a slight 3D lean, the icons arrive one after another
            sheet.setCameraDistance(dp(1600)); sheet.setPivotY(0); sheet.setPivotX(scene.getWidth() / 2f);
            sheet.setRotationX(9f);
            sheet.animate().rotationX(0f).setDuration(380).start();
            android.view.animation.AnimationSet in = new android.view.animation.AnimationSet(true);
            in.addAnimation(new android.view.animation.AlphaAnimation(0f, 1f));
            in.addAnimation(new android.view.animation.ScaleAnimation(.82f, 1f, .82f, 1f,
                    android.view.animation.Animation.RELATIVE_TO_SELF, .5f, android.view.animation.Animation.RELATIVE_TO_SELF, .5f));
            in.addAnimation(new android.view.animation.TranslateAnimation(0, 0, dp(18), 0));
            in.setDuration(300);
            in.setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f));
            android.view.animation.GridLayoutAnimationController lac = new android.view.animation.GridLayoutAnimationController(in, .12f, .12f);
            grid.setLayoutAnimation(lac);
            grid.scheduleLayoutAnimation();
            for (int i = 0; i < folderRow.getChildCount(); i++) {
                View f = folderRow.getChildAt(i);
                f.setAlpha(0f); f.setTranslationX(dp(24));
                f.animate().alpha(1f).translationX(0).setStartDelay(60 + i * 35L).setDuration(300)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
            }
        } else grid.setLayoutAnimation(null);
        if (typing) {
            query.requestFocus();
            ui.postDelayed(new Runnable() { @Override public void run() {
                ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(query, InputMethodManager.SHOW_IMPLICIT);
            } }, 280);
        }
    }

    private void closeDrawer() {
        if (drawer.getVisibility() != View.VISIBLE) return;
        scene.setLiving(U.anims(this));
        hideKeyboard();
        sheet.animate().translationY(scene.getHeight()).setDuration(220).setInterpolator(new android.view.animation.AccelerateInterpolator(1.4f)).start();
        drawer.animate().alpha(0f).setDuration(220).setListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { drawer.setVisibility(View.GONE); drawer.setAlpha(1f); drawer.animate().setListener(null); }
            @Override public void onAnimationCancel(Animator a) { onAnimationEnd(a); }
        }).start();
        home.animate().alpha(1f).setDuration(220).start();
    }
    private void closeDrawerNow() {
        hideKeyboard();
        drawer.animate().cancel(); sheet.animate().cancel(); home.animate().cancel();
        drawer.setVisibility(View.GONE);
        drawer.setAlpha(1f);
        home.setAlpha(1f);
        scene.setLiving(U.anims(this) && visible);
    }
    private void hideKeyboard() {
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(query.getWindowToken(), 0);
        query.clearFocus();
    }
}
