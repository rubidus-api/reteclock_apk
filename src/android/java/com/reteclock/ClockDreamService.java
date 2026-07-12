package com.reteclock;

import android.service.dreams.DreamService;

/**
 * The clock as a system screensaver (Daydream), available on Android 4.2 and newer.
 *
 * The class extends an API 17 type. Older platforms never load it because they do not have a
 * Daydream host, so declaring the service in the manifest stays safe down to the minimum SDK.
 */
public class ClockDreamService extends DreamService {

    private ClockView view;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        view = new ClockView(this);
        setContentView(view);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        view.start();
    }

    @Override
    public void onDreamingStopped() {
        view.stop();
        super.onDreamingStopped();
    }
}
