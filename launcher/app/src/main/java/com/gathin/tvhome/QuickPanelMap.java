package com.gathin.tvhome;

import java.util.Locale;

/**
 * Which part of the Control Center style quick panel a quick_panel target belongs to (pure, so it is unit-tested): round
 * controls for the shortcuts that are toggles or Settings pages (Inputs, Wi-Fi, Bluetooth, the system's Settings), modules
 * (rounded rectangles in a two-column grid) for vendor activities such as the MediaTek picture and sound screens.
 */
final class QuickPanelMap {
    private QuickPanelMap() { }

    static final int CONTROL = 0, MODULE = 1;
    static final String INPUTS = "inputs", WIFI = "wifi", BLUETOOTH = "bluetooth", SETTINGS = "settings",
            PICTURE = "picture", SOUND = "sound", SCREEN = "screen", AUDIO = "audio", GENERIC = "generic";

    /** The word inputs, an intent action, and activities of the system's own Settings are controls; a vendor activity is a module. */
    static int kind(String target) {
        if (target == null || target.equals(INPUTS) || target.indexOf('/') < 0) return CONTROL;
        String pkg = target.substring(0, target.indexOf('/'));
        return pkg.equals("com.android.tv.settings") || pkg.equals("com.android.settings") || pkg.equals("android")
                ? CONTROL : MODULE;
    }

    /** A glyph key for a target, by what its name says; {@link #GENERIC} when it says nothing. */
    static String glyph(String target) {
        String t = target == null ? "" : target.toLowerCase(Locale.ROOT);
        t = t.substring(Math.max(t.lastIndexOf('.'), t.lastIndexOf('/')) + 1);   // the class or action name, not its package
        if (t.equals(INPUTS)) return INPUTS;
        if (t.contains("wifi")) return WIFI;
        if (t.contains("bluetooth")) return BLUETOOTH;
        if (t.contains("audiooutput")) return AUDIO;
        if (t.contains("sound")) return SOUND;
        if (t.contains("screen")) return SCREEN;
        if (t.contains("picture") || t.contains("display")) return PICTURE;
        if (t.contains("settings")) return SETTINGS;
        return GENERIC;
    }
}
