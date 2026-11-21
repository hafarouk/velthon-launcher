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
