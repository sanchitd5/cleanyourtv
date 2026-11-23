package com.gathin.tvhome;

/**
 * Whether Home's wallpaper clip keeps playing while Home is paused. A translucent activity over Home (the quick panel) only
 * pauses it: Home is still visible, so the clip goes on and the panel's glass tracks it. Only onStop (an app, an HDMI input,
 * the screensaver, screen off) pauses the clip and releases the decoder.
 */
final class VideoUnderPanel {
    private VideoUnderPanel() { }

    /** Paused but still visible: something translucent covers Home, so its glass wants live samples. */
    static boolean coveredByPanel(boolean resumed, boolean started) {
        return !resumed && started;
    }
}
