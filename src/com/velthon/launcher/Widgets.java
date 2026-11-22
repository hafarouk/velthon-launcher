package com.velthon.launcher;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/** Widgets on the home screen: a host whose views notice a long hold, so a widget can be edited, resized or removed. */
final class Widgets {
    private Widgets() { }

    static final int HOST = 0x7e10;
    /** The widget shown on the lock screen: the one chosen in its menu, or else a glucose reader if there is one.
     *  0 = none. "lock_widget" -1 means the person turned it off. */
    static int lockWidget(Context c) {
        android.content.SharedPreferences p = U.prefs(c);
        int chosen = p.getInt("lock_widget", 0);
        android.appwidget.AppWidgetManager m = android.appwidget.AppWidgetManager.getInstance(c);
        if (chosen > 0) return m.getAppWidgetInfo(chosen) != null ? chosen : 0;
        if (chosen < 0) return 0;
        for (String key : new String[] {"widgets", "widgets_p2"})
            for (String s : p.getString(key, "").split(",")) {
                try {
                    int id = Integer.parseInt(s.trim());
                    AppWidgetProviderInfo i = m.getAppWidgetInfo(id);
                    if (i == null) continue;
                    String label = "";
                    try { label = i.loadLabel(c.getPackageManager()); } catch (Throwable ignored) { }
                    if (glucose(i.provider.getPackageName()) || glucose(i.provider.getClassName()) || glucose(label)) return id;
                } catch (Throwable ignored) { }
            }
        return 0;
    }
    static boolean glucose(String pkg) {
        if (pkg == null) return false;
        String q = pkg.toLowerCase(java.util.Locale.ROOT);
        for (String k : new String[] {"gluco", "glyc", "libre", "freestyle", "abbott", "dexcom", "cgm", "xdrip", "juggluco", "diabox", "glimp", "medtronic", "minimed", "guardian", "eversense", "gluroo", "sugar", "diabet", "nightscout", "androidaps", "aaps", "bg reading", "blood"})
            if (q.contains(k)) return true;
        return false;
    }

    static final class Host extends AppWidgetHost {
        Host(Context c, int id) { super(c, id); }
        @Override protected AppWidgetHostView onCreateView(Context c, int id, AppWidgetProviderInfo w) { return new HoldView(c); }
    }

    /** Lets the widget work as usual, but a long hold (without moving) is taken by the launcher. */
    static final class HoldView extends AppWidgetHostView {
        Runnable onHold;
        private boolean held;
        private float x0, y0;
        private final int slop;
        private final Runnable fire = new Runnable() {
            @Override public void run() {
                held = true;
                try { performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); } catch (Throwable ignored) { }
                if (onHold != null) onHold.run();
            }
        };

        /** True: the widget's own panel is taken away, so only its content shows over the sky. */
        boolean bare = true;
        private final Runnable strip = new Runnable() { @Override public void run() { stripBackground(); } };
        @Override protected void dispatchDraw(android.graphics.Canvas c) {
            super.dispatchDraw(c);
            if (bare && needsStrip()) post(strip);
        }
        private boolean needsStrip() {
            if (getChildCount() == 0) return false;
            View root = getChildAt(0);
            if (root.getBackground() != null) return true;
            if (root instanceof android.view.ViewGroup) {
                android.view.ViewGroup g = (android.view.ViewGroup) root;
                for (int i = 0; i < g.getChildCount(); i++) if (isPanel(g.getChildAt(i), root, i)) return true;
            }
            return false;
        }
        /** A child that fills the widget and sits behind the rest: a background in all but name. */
        private static boolean isPanel(View v, View root, int i) {
            if (v.getWidth() < root.getWidth() * .9f || v.getHeight() < root.getHeight() * .9f) return false;
            if (v.getBackground() != null) return true;
            return i == 0 && v instanceof android.widget.ImageView && ((android.widget.ImageView) v).getDrawable() != null
                    && ((android.view.ViewGroup) root).getChildCount() > 1 && v.getVisibility() == VISIBLE;
        }
        private void stripBackground() {
            if (getChildCount() == 0) return;
            View root = getChildAt(0);
            if (root.getBackground() != null) root.setBackground(null);
            if (root instanceof android.view.ViewGroup) {
                android.view.ViewGroup g = (android.view.ViewGroup) root;
                for (int i = 0; i < g.getChildCount(); i++) {
                    View v = g.getChildAt(i);
                    if (!isPanel(v, root, i)) continue;
                    if (v.getBackground() != null) v.setBackground(null);
                    else if (v instanceof android.widget.ImageView) ((android.widget.ImageView) v).setImageDrawable(null);
                }
            }
        }

        HoldView(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
        }

        /** True on the lock screen: the widget only shows, a touch goes through to the lock. */
        boolean passive;
        @Override public boolean dispatchTouchEvent(MotionEvent e) { return !passive && super.dispatchTouchEvent(e); }

        // The widget always lays itself out at one steady size: its real box, but never smaller than the size its
        // app designed for. When the box is smaller, the whole widget is scaled down to fit, so it never switches
        // to a cramped tiny layout (the glucose number that was sometimes big, sometimes tiny).
        private int wantW, wantH, toldW, toldH, savedW, savedH;
        private void want(int w, int h) {
            AppWidgetProviderInfo i = getAppWidgetInfo();
            int mw = i != null ? Math.max(i.minWidth, i.minResizeWidth) : 0, mh = i != null ? Math.max(i.minHeight, i.minResizeHeight) : 0;
            if (i != null && i.minResizeWidth > 0 && i.minResizeWidth < i.minWidth) mw = i.minWidth;
            if (i != null && i.minResizeHeight > 0 && i.minResizeHeight < i.minHeight) mh = i.minHeight;
            wantW = Math.max(w, mw); wantH = Math.max(h, mh);
            float d = getResources().getDisplayMetrics().density;
            int dw = Math.round(wantW / d), dh = Math.round(wantH / d);
            if (dw != toldW || dh != toldH) {
                toldW = dw; toldH = dh;
                try { updateAppWidgetSize(null, dw, dh, dw, dh); } catch (Throwable ignored) { }
            }
        }
        @Override protected void onMeasure(int ws, int hs) {
            super.onMeasure(ws, hs);
            int w = getMeasuredWidth(), h = getMeasuredHeight();
            if (w <= 0 || h <= 0 || getChildCount() == 0) return;
            // remember the home screen size, so the lock screen shows it the same size
            if (!passive && getAppWidgetId() > 0 && (w != savedW || h != savedH)) {
                savedW = w; savedH = h;
                U.prefs(getContext()).edit().putString("wsize_" + getAppWidgetId(), w + "x" + h).apply();
            }
            want(w, h);
            getChildAt(0).measure(MeasureSpec.makeMeasureSpec(wantW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(wantH, MeasureSpec.EXACTLY));
        }
        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            if (getChildCount() == 0 || wantW <= 0) { super.onLayout(changed, l, t, r, b); return; }
            View ch = getChildAt(0);
            int w = r - l, h = b - t;
            ch.layout(0, 0, wantW, wantH);
            float k = Math.min(1f, Math.min(w / (float) wantW, h / (float) wantH));
            ch.setPivotX(0); ch.setPivotY(0); ch.setScaleX(k); ch.setScaleY(k);
            ch.setTranslationX((w - wantW * k) / 2f); ch.setTranslationY((h - wantH * k) / 2f);
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    held = false; x0 = e.getX(); y0 = e.getY();
                    removeCallbacks(fire);
                    postDelayed(fire, ViewConfiguration.getLongPressTimeout());
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(e.getX() - x0) > slop || Math.abs(e.getY() - y0) > slop) removeCallbacks(fire);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    removeCallbacks(fire);
                    break;
            }
            return held;   // once held, the widget's own views get a cancel and the launcher keeps the touch
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (held) {
                if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) held = false;
                return true;
            }
            return super.onTouchEvent(e);
        }
    }
}
