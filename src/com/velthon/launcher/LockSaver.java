package com.velthon.launcher;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The screensaver as the lock screen. Whenever the screen goes off it is put over the phone's lock screen, so waking
 * the phone shows the window, clock, verse and next events. At the bottom, the phone's own way in: a fingerprint,
 * face or lock button (whatever this phone uses). Tap it (or anywhere) and the phone asks for the fingerprint, face or
 * PIN as usual; a rear fingerprint sensor unlocks straight away and closes it too.
 *
 * Getting on screen: recent Android versions do not let apps open a screen from the background, even with "display
 * over other apps". So, like alarm and call apps, it is shown with a full-screen notification, which the phone allows
 * over the lock screen; a direct start is tried as well, where the phone still permits it.
 */
public class LockSaver extends Activity {
    private static boolean watching, showing;
    private static final int NOTE = 77;
    private static final String CHANNEL = "velthon_lock";

    /** Called by the launcher: from then on, every time the screen goes off (or on, still locked) the saver is put in place. */
    /** A short record of the last few locks (shown under "Lock check" in Settings), to see what happened on a phone. */
    static void log(Context c, String what) {
        try {
            String t = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date());
            String old = U.prefs(c).getString("lock_log", "");
            String n = t + " " + what + (old.isEmpty() ? "" : "\n" + old);
            String[] l = n.split("\n"); StringBuilder b = new StringBuilder();
            for (int i = 0; i < Math.min(l.length, 12); i++) { if (i > 0) b.append('\n'); b.append(l[i]); }
            U.prefs(c).edit().putString("lock_log", b.toString()).apply();
        } catch (Throwable ignored) { }
    }
    static String check(Context c) {
        StringBuilder b = new StringBuilder();
        b.append(Build.VERSION.SDK_INT < 23 || android.provider.Settings.canDrawOverlays(c) ? "✓" : "✗").append(" over other apps   ");
        b.append(fullScreenAllowed(c) ? "✓" : "✗").append(" full-screen   ");
        try {
            android.os.PowerManager pm = (android.os.PowerManager) c.getSystemService(POWER_SERVICE);
            b.append(Build.VERSION.SDK_INT < 23 || pm.isIgnoringBatteryOptimizations(c.getPackageName()) ? "✓" : "✗").append(" battery");
        } catch (Throwable ignored) { }
        String l = U.prefs(c).getString("lock_log", "");
        if (!l.isEmpty()) b.append("\n").append(l);
        return b.toString();
    }

    static void watch(Context c) {
        if (watching) return;
        watching = true;
        final Context app = c.getApplicationContext();
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        app.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent i) {
                String a = i.getAction();
                KeyguardManager kg = (KeyguardManager) app.getSystemService(KEYGUARD_SERVICE);
                log(app, (Intent.ACTION_SCREEN_OFF.equals(a) ? "off" : Intent.ACTION_SCREEN_ON.equals(a) ? "on" : "unlocked")
                        + (kg != null && kg.isKeyguardLocked() ? " L" : "") + (showing ? " shown" : "") + (inCall(app) ? " call" : ""));
                if (Intent.ACTION_USER_PRESENT.equals(a)) { clearNote(app); return; }
                if (!U.prefs(app).getString("lock_saver", "on").equals("on") || showing) return;
                if (inCall(app)) return;                                   // the screen also goes off against your ear
                if (busyAudio(app)) return;    // a voice note or video held to the ear turns the screen off too: never interrupt it
                if (Intent.ACTION_SCREEN_ON.equals(a)) {
                    // Too late: the phone's own lock is already on screen. Opening ours now would only flash the
                    // app's icon and stack a second lock on top, so leave the phone's lock alone this time.
                    clearNote(app); log(app, "late, skipped"); return;
                }
                show(app);
            }
        }, f);
    }

    /** While the launcher is open: make the notification channel, so the phone can ask about notifications then. */
    static void prepare(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
            if (nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, L.t("Lock screen"), NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription("Shows the Velthon window on the lock screen");
                ch.setSound(null, null); ch.enableVibration(false); ch.setShowBadge(false);
                ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                nm.createNotificationChannel(ch);
            }
        } catch (Throwable ignored) { }
    }
    /** May this app show full-screen notifications (needed on Android 14 and later)? */
    static boolean fullScreenAllowed(Context c) {
        if (Build.VERSION.SDK_INT < 34) return true;
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
            return (Boolean) nm.getClass().getMethod("canUseFullScreenIntent").invoke(nm);
        } catch (Throwable t) { return true; }
    }
    private static void show(Context app) {
        Intent open = new Intent(app, LockSaver.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        // 1) straight away, where the phone still allows it
        try { app.startActivity(open); } catch (Throwable t) { log(app, "start failed " + t.getClass().getSimpleName()); }
        // 2) a full-screen notification (as alarm and call apps do): the phone opens it over the lock screen
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                NotificationManager nm = (NotificationManager) app.getSystemService(NOTIFICATION_SERVICE);
                if (nm.getNotificationChannel(CHANNEL) == null) {
                    NotificationChannel ch = new NotificationChannel(CHANNEL, L.t("Lock screen"), NotificationManager.IMPORTANCE_HIGH);
                    ch.setDescription("Shows the Velthon window on the lock screen");
                    ch.setSound(null, null); ch.enableVibration(false); ch.setShowBadge(false);
                    ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                    nm.createNotificationChannel(ch);
                }
                PendingIntent pi = PendingIntent.getActivity(app, 7, open, PendingIntent.FLAG_UPDATE_CURRENT | 0x04000000 /* IMMUTABLE */);
                Notification n = new Notification.Builder(app, CHANNEL)
                        .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                        .setContentTitle("Velthon")
                        .setContentText(L.t("The window on your lock screen"))
                        .setCategory(Notification.CATEGORY_ALARM)
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        .setFullScreenIntent(pi, true)
                        .setContentIntent(pi)
                        .setAutoCancel(true)
                        .setTimeoutAfter(15000)
                        .build();
                nm.notify(NOTE, n);
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() { @Override public void run() {
                    if (showing) clearNote(app); } }, 3000);
            } catch (Throwable ignored) { }
        }
    }
    static void clearNote(Context c) {
        try { ((NotificationManager) c.getSystemService(NOTIFICATION_SERVICE)).cancel(NOTE); } catch (Throwable ignored) { }
    }
    /** Sound is playing through an app (a WhatsApp voice note at the ear, a video): the screen going off is
     *  the proximity sensor, not the user locking the phone, so the Velthon lock must stay out of the way. */
    private static boolean busyAudio(Context c) {
        try { return ((AudioManager) c.getSystemService(AUDIO_SERVICE)).isMusicActive(); } catch (Throwable t) { return false; }
    }
    private static boolean inCall(Context c) {
        try {
            int m = ((AudioManager) c.getSystemService(AUDIO_SERVICE)).getMode();
            return m == AudioManager.MODE_IN_CALL || m == AudioManager.MODE_IN_COMMUNICATION || m == AudioManager.MODE_RINGTONE;
        } catch (Throwable t) { return false; }
    }

    /** How this phone unlocks: 2 fingerprint, 1 face, 0 PIN / pattern / swipe only. */
    static int unlockKind(Context c) {
        PackageManager pm = c.getPackageManager();
        try {
            if (pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) {
                android.hardware.fingerprint.FingerprintManager fm = (android.hardware.fingerprint.FingerprintManager) c.getSystemService(FINGERPRINT_SERVICE);
                if (fm != null && fm.isHardwareDetected() && fm.hasEnrolledFingerprints()) return 2;
            }
        } catch (Throwable ignored) { }
        if (pm.hasSystemFeature("android.hardware.biometrics.face")) return 1;
        return 0;
    }

    private BroadcastReceiver unlocked;
    private float y0;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        log(this, "window opened");
        showing = true;
        clearNote(this);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(false); }
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        // draw right up to the top edge, around the camera cut-out: no black band, the leaves hang from the very top
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30 ? 3 /* ALWAYS */ : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root = new FrameLayout(this);
        SaverView sv = new SaverView(this);
        sv.calm = true;
        sv.reserveBottom = 120 * getResources().getDisplayMetrics().density;
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        root.addView(unlockButton(), new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        setContentView(root);
        unlocked = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { finish(); } };
        registerReceiver(unlocked, new IntentFilter(Intent.ACTION_USER_PRESENT));
    }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); clearNote(this); }
    /** A phone call always comes first: the moment it rings (or one is going on), step aside for the call screen. */
    private final Handler calls = new Handler(Looper.getMainLooper());
    private final Runnable callWatch = new Runnable() { @Override public void run() {
        if (inCall(LockSaver.this)) { log(LockSaver.this, "call: stepped aside"); finish(); return; }
        calls.postDelayed(this, 300);
    } };
    @Override protected void onResume() {
        super.onResume(); showing = true; clearNote(this);
        if (inCall(this)) { finish(); return; }
        // woken and the phone isn't locked (the screen only went dark for a moment): get out of the way at once
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        KeyguardManager kg = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (pm != null && pm.isInteractive() && kg != null && !kg.isKeyguardLocked()) { log(this, "not locked: stepped aside"); finish(); return; }
        calls.removeCallbacks(callWatch); calls.postDelayed(callWatch, 300);
    }
    @Override protected void onPause() { super.onPause(); calls.removeCallbacks(callWatch); }
    @Override protected void onStart() { super.onStart(); showing = true; }
    @Override protected void onStop() {
        super.onStop();
        // The screen going off also stops us, a moment before the phone counts itself locked.
        // Stay then: closing here is what let the phone's own lock screen show first when it woke.
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) return;
        showing = false;              // out of sight: the next screen-off must bring it back
        KeyguardManager k = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (k == null || !k.isKeyguardLocked()) finish();   // unlocked: never linger behind other apps
    }
    @Override protected void onDestroy() {
        showing = false;
        try { unregisterReceiver(unlocked); } catch (Throwable ignored) { }
        super.onDestroy();
    }
    @Override public void onBackPressed() { }   // the lock screen does not go away with Back

    /** The phone's own way in, at the bottom: a fingerprint, a face or a lock, with a word under it. */
    private View unlockButton() {
        final int kind = unlockKind(this);
        float d = getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(0, 0, 0, Math.round(26 * d));
        View icon = new View(this) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c) {
                float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f;
                p.setStyle(Paint.Style.FILL); p.setColor(0x38FFFFFF); c.drawCircle(cx, cy, r, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1, r * .03f)); p.setColor(0x59FFFFFF); c.drawCircle(cx, cy, r - p.getStrokeWidth(), p);
                p.setColor(Color.WHITE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(r * .07f);
                if (kind == 2) {          // a fingerprint: arcs, one inside the other
                    for (int i = 0; i < 4; i++) {
                        float rr = r * (.18f + i * .14f);
                        c.drawArc(new RectF(cx - rr, cy - rr * 1.12f, cx + rr, cy + rr * 1.12f), 200 - i * 6, 220 - i * 18, false, p);
                    }
                    c.drawLine(cx, cy - r * .08f, cx, cy + r * .14f, p);
                } else if (kind == 1) {   // a face
                    float s = r * .48f;
                    c.drawRoundRect(new RectF(cx - s, cy - s, cx + s, cy + s), s * .45f, s * .45f, p);
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(cx - s * .38f, cy - s * .2f, r * .05f, p); c.drawCircle(cx + s * .38f, cy - s * .2f, r * .05f, p);
                    p.setStyle(Paint.Style.STROKE);
                    c.drawArc(new RectF(cx - s * .4f, cy - s * .1f, cx + s * .4f, cy + s * .5f), 20, 140, false, p);
                } else {                  // a lock
                    float bw = r * .62f, bh = r * .5f;
                    c.drawRoundRect(new RectF(cx - bw / 2, cy - bh * .15f, cx + bw / 2, cy + bh * .85f), r * .1f, r * .1f, p);
                    c.drawArc(new RectF(cx - bw * .32f, cy - bh * .9f, cx + bw * .32f, cy + bh * .2f), 180, 180, false, p);
                }
            }
        };
        int sz = Math.round(64 * d);
        box.addView(icon, new LinearLayout.LayoutParams(sz, sz));
        TextView t = new TextView(this);
        t.setText(kind == 2 ? L.t("Touch to unlock with your fingerprint") : kind == 1 ? L.t("Tap to unlock with your face") : L.t("Tap to unlock"));
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setShadowLayer(2 * d, 0, d, 0xB3000000);
        t.setPadding(0, Math.round(10 * d), 0, 0);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER);
        box.addView(t, new LinearLayout.LayoutParams(-2, -2));
        box.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { unlock(); } });
        return box;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) y0 = e.getY();
        if (e.getActionMasked() == MotionEvent.ACTION_UP) unlock();
        return true;
    }
    private void unlock() {
        KeyguardManager k = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (k == null || !k.isKeyguardLocked()) { finish(); return; }
        if (Build.VERSION.SDK_INT >= 26) {
            // the phone's own unlock: fingerprint (in-display or on the back), face, or PIN, as set up on this phone
            k.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override public void onDismissSucceeded() { finish(); }
            });
        } else finish();
    }
}
