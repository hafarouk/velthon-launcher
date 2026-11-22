package com.velthon.launcher;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Where this phone's fingerprint sensor is: under the screen (with its exact spot), in the power button on the side,
 * on the back, or unknown. Android has no public way to ask, so this reads what the system itself uses (the
 * fingerprint service's sensor list, then the system's own layout settings) and falls back on what the maker usually does.
 */
final class FpSensor {
    static final int UNKNOWN = 0, UNDER_SCREEN = 1, SIDE = 2, BACK = 3;
    int type = UNKNOWN;
    float x = -1, y = -1, r = -1;   // under the screen: the centre and radius, in screen pixels
    float sideY = -1;               // side: where along the right edge (pixels), when the phone says

    private static FpSensor cached;

    static FpSensor of(Context c) {
        if (cached != null) return cached;
        FpSensor s = new FpSensor();
        try { s.fromService(c); } catch (Throwable ignored) { }
        if (s.type == UNKNOWN) try { s.fromResources(); } catch (Throwable ignored) { }
        if (s.type == UNKNOWN) s.guess();
        U.prefs(c).edit().putString("fp_sensor", s.toString()).apply();   // shown in the backup, handy when something looks off
        cached = s;
        return s;
    }

    /** The fingerprint service's own description of each sensor (Android 11+). */
    private void fromService(Context c) throws Exception {
        Object fm = c.getSystemService(Context.FINGERPRINT_SERVICE);
        if (fm == null) return;
        Method m = fm.getClass().getMethod("getSensorPropertiesInternal");
        List<?> list = (List<?>) m.invoke(fm);
        if (list == null || list.isEmpty()) return;
        Object p = list.get(0);
        int st = intField(p, "sensorType", -1);
        // FingerprintSensorProperties: 1 rear, 2 ultrasonic under-display, 3 optical under-display, 4 power button (side)
        if (st == 2 || st == 3) {
            type = UNDER_SCREEN;
            float lx = intField(p, "sensorLocationX", -1), ly = intField(p, "sensorLocationY", -1), lr = intField(p, "sensorRadius", -1);
            if (lx < 0) {   // Android 13+: the location sits in its own object
                try {
                    Object loc = p.getClass().getMethod("getLocation").invoke(p);
                    lx = (Integer) loc.getClass().getMethod("getSensorLocationX").invoke(loc);
                    ly = (Integer) loc.getClass().getMethod("getSensorLocationY").invoke(loc);
                    lr = (Integer) loc.getClass().getMethod("getSensorRadius").invoke(loc);
                } catch (Throwable ignored) { }
            }
            x = lx; y = ly; r = lr;
        } else if (st == 4) {
            type = SIDE;
            try {
                Object loc = p.getClass().getMethod("getLocation").invoke(p);
                int ly = (Integer) loc.getClass().getMethod("getSensorLocationY").invoke(loc);
                if (ly > 0) sideY = ly;
            } catch (Throwable ignored) { }
        } else if (st == 1) type = BACK;
    }

    /** The system's layout settings for the sensor, which some phones fill in. */
    private void fromResources() {
        Resources r0 = Resources.getSystem();
        int id = r0.getIdentifier("config_udfps_sensor_props", "array", "android");
        if (id != 0) {
            int[] a = r0.getIntArray(id);
            if (a != null && a.length >= 3 && a[0] > 0) { type = UNDER_SCREEN; x = a[0]; y = a[1]; r = a[2]; return; }
        }
        id = r0.getIdentifier("config_sfps_sensor_props", "array", "android");
        if (id != 0) {
            try { String[] a = r0.getStringArray(id); if (a != null && a.length > 0) { type = SIDE; return; } } catch (Throwable ignored) { }
        }
        id = r0.getIdentifier("config_is_powerbutton_fps", "bool", "android");
        if (id != 0 && r0.getBoolean(id)) type = SIDE;
    }

    /** No answer from the phone: go by the maker. Motorola, Sony and most mid-range phones put it in the power button. */
    private void guess() {
        String m = (Build.MANUFACTURER + " " + Build.MODEL).toLowerCase(java.util.Locale.ROOT);
        if (m.contains("motorola") || m.contains("moto") || m.contains("sony") || m.contains("xperia")) type = SIDE;
    }

    private static int intField(Object o, String name, int def) {
        for (Class<?> k = o.getClass(); k != null; k = k.getSuperclass()) {
            try { Field f = k.getDeclaredField(name); f.setAccessible(true); return f.getInt(o); } catch (Throwable ignored) { }
        }
        return def;
    }

    @Override public String toString() {
        return (type == UNDER_SCREEN ? "under-screen " + (int) x + "," + (int) y + " r" + (int) r : type == SIDE ? "side" + (sideY > 0 ? " y" + (int) sideY : "") : type == BACK ? "back" : "unknown")
                + " · " + Build.MANUFACTURER + " " + Build.MODEL;
    }
}
