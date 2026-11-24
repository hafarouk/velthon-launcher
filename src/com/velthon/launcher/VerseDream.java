package com.velthon.launcher;

import android.service.dreams.DreamService;

/** The Android screen saver ("Daydream"): shown while charging or docked, chosen in Settings › Display › Screen saver. */
public class VerseDream extends DreamService {
    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        SaverView sv = new SaverView(this);
        sv.calm = true;
        root.addView(sv, new android.widget.FrameLayout.LayoutParams(-1, -1));
        StatusLine st = new StatusLine(this);        // battery and network under the verse, as on the lock screen
        root.addView(st, new android.widget.FrameLayout.LayoutParams(1, 1));
        sv.status = st;
        setContentView(root);
    }
}
