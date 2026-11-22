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

/** The lock screen's own slim status row: the network on the left (carrier and signal, or Wi-Fi), the battery on the right.
 *  It sits in the band beside the camera, where the phone's status bar would be. */
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
        text.setColor(0xEEFFFFFF); text.setTextSize(14 * d); text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setShadowLayer(3 * d, 0, d, 0x99000000);
        line.setStyle(Paint.Style.STROKE); line.setStrokeWidth(1.4f * d); line.setColor(0xEEFFFFFF); line.setStrokeJoin(Paint.Join.ROUND);
        line.setShadowLayer(3 * d, 0, d, 0x99000000);
        fill.setColor(0xEEFFFFFF);
        setLayerType(LAYER_TYPE_SOFTWARE, null);   // the soft shadow behind the strokes
    }

    private final BroadcastReceiver battery = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { readBattery(i); }
    };
    private final Runnable tick = new Runnable() { @Override public void run() { readNetwork(); invalidate(); postDelayed(this, 20_000); } };

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
        invalidate();
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

    @Override protected void onDraw(Canvas c) {
        float h = getHeight(), mid = h / 2f, pad = 4 * d;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        // the network: left (right in Arabic)
        float x = rtl ? getWidth() - pad : pad;
        float iw = 18 * d;
        float ix = rtl ? x - iw : x;
        if (wifi) drawWifi(c, ix, mid, iw); else if (bars >= 0) drawBars(c, ix, mid, iw);
        String label = !wifi && !gen.isEmpty() ? (net.isEmpty() ? gen : gen + "  " + net) : net;
        if (!label.isEmpty()) {
            float tw = text.measureText(label);
            float tx = rtl ? (bars >= 0 || wifi ? ix - 6 * d - tw : x - tw) : (bars >= 0 || wifi ? ix + iw + 6 * d : x);
            c.drawText(label, tx, mid + text.getTextSize() * .35f, text);
        }
        // the battery: right (left in Arabic)
        if (level < 0) return;
        String pct = level + "%";
        float bw = 24 * d, bh = 12 * d, tw = text.measureText(pct);
        float bx = rtl ? pad + tw + 6 * d : getWidth() - pad - bw - 2 * d;
        RectF body = new RectF(bx, mid - bh / 2, bx + bw, mid + bh / 2);
        int col = charging ? 0xFF7CE38B : level <= 15 ? 0xFFFF8A80 : 0xEEFFFFFF;
        line.setColor(0xEEFFFFFF);
        c.drawRoundRect(body, 3 * d, 3 * d, line);
        c.drawRoundRect(new RectF(body.right + d, mid - 3 * d, body.right + 3 * d, mid + 3 * d), d, d, fill);
        fill.setColor(col);
        float in = 2.2f * d;
        c.drawRoundRect(new RectF(body.left + in, body.top + in, body.left + in + (bw - 2 * in) * level / 100f, body.bottom - in), 1.5f * d, 1.5f * d, fill);
        fill.setColor(0xEEFFFFFF);
        if (charging) {   // a small bolt over the battery
            Path p = new Path();
            float cx = body.centerX(), cy = mid;
            p.moveTo(cx + 1.5f * d, cy - 5 * d); p.lineTo(cx - 3 * d, cy + d); p.lineTo(cx, cy + d);
            p.lineTo(cx - 1.5f * d, cy + 5 * d); p.lineTo(cx + 3 * d, cy - d); p.lineTo(cx, cy - d); p.close();
            Paint b = new Paint(Paint.ANTI_ALIAS_FLAG); b.setColor(0xFF1B1B1B);
            c.drawPath(p, b);
        }
        float tx = rtl ? pad : bx - 6 * d - tw;
        text.setColor(level <= 15 && !charging ? 0xFFFF8A80 : 0xEEFFFFFF);
        c.drawText(pct, tx, mid + text.getTextSize() * .35f, text);
        text.setColor(0xEEFFFFFF);
    }

    private void drawBars(Canvas c, float x, float mid, float w) {
        float gap = 2 * d, bw = (w - 3 * gap) / 4f, top = mid - 7 * d, bottom = mid + 7 * d;
        for (int i = 0; i < 4; i++) {
            float bh = (bottom - top) * (i + 1) / 4f;
            fill.setColor(i < Math.max(1, bars) && bars > 0 ? 0xEEFFFFFF : 0x55FFFFFF);
            c.drawRoundRect(new RectF(x + i * (bw + gap), bottom - bh, x + i * (bw + gap) + bw, bottom), d, d, fill);
        }
        fill.setColor(0xEEFFFFFF);
    }

    private void drawWifi(Canvas c, float x, float mid, float w) {
        float cx = x + w / 2f, cy = mid + 6 * d;
        for (int i = 0; i < 3; i++) {
            float r = (5 + i * 4.5f) * d;
            line.setColor(i <= bars - 1 || (i == 0 && bars >= 0) ? 0xEEFFFFFF : 0x55FFFFFF);
            c.drawArc(new RectF(cx - r, cy - r, cx + r, cy + r), 225, 90, false, line);
        }
        line.setColor(0xEEFFFFFF);
        c.drawCircle(cx, cy - d, 1.6f * d, fill);
    }
}
