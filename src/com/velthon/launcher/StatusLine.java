package com.velthon.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.view.View;

/** Battery and network for the lock screen: one centred row under the verse, in its own soft blue, the two parts
 *  divided by a thin bar, so it reads as the lock screen's own and not the phone's status bar. */
final class StatusLine extends View {
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private int level = -1;
    private boolean charging;
    private String net = "";
    private int bars = -1;        // 0..4, -1 = none
    private boolean wifi;
    private String gen = "";      // 5G, LTE, LTE+, 3G... (needs the phone permission)
    private int override = 0;     // Android 11+: what the status bar shows (5G on LTE anchor, LTE+)
    private android.telephony.PhoneStateListener psl;

    StatusLine(Context c) {
        super(c);
        d = getResources().getDisplayMetrics().density;
        text.setColor(0xEEFFFFFF); text.setTextSize(14 * d); text.setTypeface(U.sans(c));
        text.setShadowLayer(3 * d, 0, d, 0x99000000);
        line.setStyle(Paint.Style.STROKE); line.setStrokeWidth(1.4f * d); line.setColor(0xEEFFFFFF); line.setStrokeJoin(Paint.Join.ROUND);
        line.setShadowLayer(3 * d, 0, d, 0x99000000);
        fill.setColor(0xEEFFFFFF);
        setLayerType(LAYER_TYPE_SOFTWARE, null);   // the soft shadow behind the strokes
    }

    private final BroadcastReceiver battery = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { readBattery(i); }
    };
    private final Runnable tick = new Runnable() { @Override public void run() { readNetwork(); if (getParent() instanceof View) ((View) getParent()).invalidate(); postDelayed(this, 20_000); } };

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        try { readBattery(getContext().registerReceiver(battery, new IntentFilter(Intent.ACTION_BATTERY_CHANGED))); } catch (Throwable ignored) { }
        post(tick);
        if (Build.VERSION.SDK_INT >= 30 && phoneAllowed()) try {
            psl = new android.telephony.PhoneStateListener() {
                // Android 11+ only; this API-28 build doesn't know the method, the phone calls it all the same
                public void onDisplayInfoChanged(android.telephony.TelephonyDisplayInfo i) {
                    override = i.getOverrideNetworkType(); readNetwork(); invalidate();
                }
            };
            ((TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE)).listen(psl, 0x00100000 /* LISTEN_DISPLAY_INFO_CHANGED */);
        } catch (Throwable t) { psl = null; }
    }

    private boolean phoneAllowed() {
        return Build.VERSION.SDK_INT < 23 || getContext().checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /** The label the phone's own status bar would show. */
    private String generation(TelephonyManager tm) {
        if (!phoneAllowed()) return "";
        int t;
        try { t = tm.getDataNetworkType(); } catch (Throwable e) { return ""; }
        if (t == 0) try { t = tm.getVoiceNetworkType(); } catch (Throwable ignored) { }
        if (override == 3 || override == 4 || override == 5) return "5G";
        switch (t) {
            case 20: return "5G";
            case TelephonyManager.NETWORK_TYPE_LTE: case 19 /* LTE_CA */: return override == 1 || override == 2 || t == 19 ? "LTE+" : "LTE";
            case TelephonyManager.NETWORK_TYPE_HSPAP: return "H+";
            case TelephonyManager.NETWORK_TYPE_HSPA: case TelephonyManager.NETWORK_TYPE_HSDPA: case TelephonyManager.NETWORK_TYPE_HSUPA: return "H";
            case TelephonyManager.NETWORK_TYPE_UMTS: case TelephonyManager.NETWORK_TYPE_EVDO_0: case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_EVDO_B: case TelephonyManager.NETWORK_TYPE_EHRPD: case 17 /* TD_SCDMA */: return "3G";
            case TelephonyManager.NETWORK_TYPE_EDGE: return "E";
            case TelephonyManager.NETWORK_TYPE_GPRS: case TelephonyManager.NETWORK_TYPE_CDMA: case TelephonyManager.NETWORK_TYPE_1xRTT: return "G";
            default: return "";
        }
    }
    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        try { getContext().unregisterReceiver(battery); } catch (Throwable ignored) { }
        removeCallbacks(tick);
        if (psl != null) try { ((TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE)).listen(psl, 0); } catch (Throwable ignored) { }
        psl = null;
    }

    private void readBattery(Intent i) {
        if (i == null) return;
        int l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        level = l < 0 ? -1 : Math.round(l * 100f / Math.max(1, s));
        int st = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
        charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
        if (getParent() instanceof View) ((View) getParent()).invalidate();
    }

    private void readNetwork() {
        Context c = getContext();
        wifi = false; bars = -1; net = ""; gen = "";
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            NetworkCapabilities nc = n != null ? cm.getNetworkCapabilities(n) : null;
            if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                wifi = true; net = "Wi-Fi";
                int rssi = Integer.MIN_VALUE;
                if (Build.VERSION.SDK_INT >= 29) try { rssi = (Integer) NetworkCapabilities.class.getMethod("getSignalStrength").invoke(nc); } catch (Throwable ignored) { }
                bars = rssi == Integer.MIN_VALUE ? 3 : rssi >= -55 ? 3 : rssi >= -67 ? 2 : rssi >= -78 ? 1 : 0;
                return;
            }
        } catch (Throwable ignored) { }
        try {
            TelephonyManager tm = (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) return;
            String op = tm.getNetworkOperatorName();
            net = op == null ? "" : op.trim();
            if (Build.VERSION.SDK_INT >= 28 && tm.getSignalStrength() != null) bars = tm.getSignalStrength().getLevel();
            gen = generation(tm);
            if (tm.getSimState() != TelephonyManager.SIM_STATE_READY && net.isEmpty()) { net = L.t("No SIM"); bars = -1; }
        } catch (Throwable ignored) { }
        if (net.isEmpty() && bars < 0) net = L.t("No service");
    }

    /** The lock screen draws the row itself, under the verse; this view only keeps the readings fresh. */
    @Override protected void onDraw(Canvas c) { }

    static final int INK = 0xFFA9DBFF;
    private int ink = INK;   // the colour of this frame (deeper on a bright sky)   // a soft sky blue: apart from the white verse and the gold reference

    float rowHeight() { return 26 * d; }

    /** One centred row: network (signal, 5G, carrier)  |  battery (percent, icon). */
    void drawRow(Canvas c, float cx, float mid, float alpha, boolean brightSky) {
        int a = Math.round(255 * Math.max(0, Math.min(1, alpha)));
        // on a bright sky: deep blue with a light halo, a deep amber divider; on a dark one: soft blue with a dark shadow
        final int INK = brightSky ? 0xFF1F4E7A : StatusLine.INK, GOLD = brightSky ? 0xFF8A5212 : 0xFFF2B640;
        int halo = brightSky ? 0xB3FFFFFF : 0x99000000;
        text.setShadowLayer(3 * d, 0, brightSky ? .5f * d : d, halo);
        line.setShadowLayer(3 * d, 0, brightSky ? .5f * d : d, halo);
        ink = INK;
        text.setColor(INK); text.setAlpha(a); line.setColor(INK); line.setAlpha(a); fill.setColor(INK); fill.setAlpha(a);
        String label = wifi ? net : !gen.isEmpty() ? (net.isEmpty() ? gen : gen + "  " + net) : net;
        boolean icon = wifi || bars >= 0;
        float iw = 18 * d, gap = 7 * d, sepGap = 14 * d;
        float lw = (icon ? iw + (label.isEmpty() ? 0 : gap) : 0) + (label.isEmpty() ? 0 : text.measureText(label));
        String pct = level >= 0 ? level + "%" : "";
        float bw = 24 * d, bh = 11 * d;
        float rw = level >= 0 ? text.measureText(pct) + gap + bw + 3 * d : 0;
        boolean both = lw > 0 && rw > 0;
        float total = lw + rw + (both ? sepGap * 2 + 1.5f * d : 0);
        float x = cx - total / 2f, base = mid + text.getTextSize() * .35f;
        if (lw > 0) {
            if (icon) { if (wifi) drawWifi(c, x, mid, iw); else drawBars(c, x, mid, iw); x += iw + (label.isEmpty() ? 0 : gap); }
            if (!label.isEmpty()) { c.drawText(label, x, base, text); x += text.measureText(label); }
        }
        if (both) {   // the thin divider
            x += sepGap;
            fill.setColor(GOLD); fill.setAlpha(a);   // gold, like the verse's reference above it
            c.drawRoundRect(new RectF(x, mid - 8 * d, x + 1.5f * d, mid + 8 * d), d, d, fill);
            fill.setColor(INK); fill.setAlpha(a);
            x += 1.5f * d + sepGap;
        }
        if (rw > 0) {
            boolean low = level <= 15 && !charging;
            text.setColor(low ? 0xFFFF8A80 : INK); text.setAlpha(a);
            c.drawText(pct, x, base, text);
            x += text.measureText(pct) + gap;
            RectF body = new RectF(x, mid - bh / 2, x + bw, mid + bh / 2);
            line.setColor(INK); line.setAlpha(a);
            c.drawRoundRect(body, 3 * d, 3 * d, line);
            fill.setColor(INK); fill.setAlpha(a);
            c.drawRoundRect(new RectF(body.right + d, mid - 2.8f * d, body.right + 3 * d, mid + 2.8f * d), d, d, fill);
            float in = 2.2f * d;
            fill.setColor(charging ? 0xFF7CE38B : low ? 0xFFFF8A80 : INK); fill.setAlpha(a);
            c.drawRoundRect(new RectF(body.left + in, body.top + in, body.left + in + (bw - 2 * in) * level / 100f, body.bottom - in), 1.5f * d, 1.5f * d, fill);
            if (charging) {
                Path p = new Path();
                float bx = body.centerX();
                p.moveTo(bx + 1.5f * d, mid - 4.5f * d); p.lineTo(bx - 3 * d, mid + d); p.lineTo(bx, mid + d);
                p.lineTo(bx - 1.5f * d, mid + 4.5f * d); p.lineTo(bx + 3 * d, mid - d); p.lineTo(bx, mid - d); p.close();
                Paint b = new Paint(Paint.ANTI_ALIAS_FLAG); b.setColor(0xFF1B1B1B);
                c.drawPath(p, b);
            }
        }
        text.setAlpha(255); line.setAlpha(255); fill.setAlpha(255);
    }

    /** Signal: one solid right triangle with rounded corners, filled from the left as far as the signal reaches;
     *  the rest stays a soft glass shade. */
    private void drawBars(Canvas c, float x, float mid, float w) {
        float sz = 15 * d, left = x + (w - sz) / 2f, bottom = mid + sz / 2f, top = mid - sz / 2f, right = left + sz;
        Path p = new Path();
        p.moveTo(left, bottom); p.lineTo(right, top); p.lineTo(right, bottom); p.close();
        fill.setPathEffect(new android.graphics.CornerPathEffect(2.2f * d));
        fill.setShadowLayer(3 * d, 0, d, 0x66000000);
        fill.setColor((ink & 0x00FFFFFF) | 0x47000000);
        c.drawPath(p, fill);
        fill.clearShadowLayer();
        if (bars > 0) {
            c.save();
            c.clipRect(left - d, top - d, left + sz * Math.min(4, bars) / 4f, bottom + d);
            fill.setColor(ink);
            c.drawPath(p, fill);
            c.restore();
        }
        fill.setPathEffect(null);
    }

    /** Wi-Fi: a solid quarter fan, filled from the point up as far as the signal reaches. */
    private void drawWifi(Canvas c, float x, float mid, float w) {
        float r = 10.5f * d, cx = x + w / 2f, cy = mid + r * .62f;
        RectF o = new RectF(cx - r, cy - r, cx + r, cy + r);
        fill.setShadowLayer(3 * d, 0, d, 0x66000000);
        fill.setColor((ink & 0x00FFFFFF) | 0x47000000);
        c.drawArc(o, 225, 90, true, fill);
        fill.clearShadowLayer();
        int lv = Math.max(1, Math.min(3, bars + 1));   // 0..3 → a third, two thirds, full
        float rr = r * (lv == 3 ? 1f : lv == 2 ? .72f : .45f);
        fill.setColor(ink);
        c.drawArc(new RectF(cx - rr, cy - rr, cx + rr, cy + rr), 225, 90, true, fill);
    }
}
