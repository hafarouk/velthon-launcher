package com.velthon.launcher;

import android.service.dreams.DreamService;

/** The Android screen saver ("Daydream"): shown while charging or docked, chosen in Settings › Display › Screen saver. */
public class VerseDream extends DreamService {
    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        setContentView(new SaverView(this));
    }
}
