package com.velthon.launcher;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/** Lets a double tap on the home screen turn the screen off and lock the phone, the way the well-known
 *  launchers do it. It only locks the screen when asked; it reads nothing on the screen. */
public class LockService extends AccessibilityService {
    static LockService on;
    @Override protected void onServiceConnected() { on = this; }
    @Override public boolean onUnbind(android.content.Intent i) { on = null; return super.onUnbind(i); }
    @Override public void onDestroy() { on = null; super.onDestroy(); }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) { }
    @Override public void onInterrupt() { }
    static boolean lock() {
        LockService s = on;
        if (s == null || android.os.Build.VERSION.SDK_INT < 28) return false;
        return s.performGlobalAction(8);   // GLOBAL_ACTION_LOCK_SCREEN
    }
}
