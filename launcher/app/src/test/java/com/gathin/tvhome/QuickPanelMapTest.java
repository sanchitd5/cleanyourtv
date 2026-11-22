package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class QuickPanelMapTest {
    private static final String M = "com.mediatek.tv.settings/.displayandsound.";

    @Test
    public void controlsAreInputsActionsAndSystemSettings() {
        assertEquals(QuickPanelMap.CONTROL, QuickPanelMap.kind("inputs"));
        assertEquals(QuickPanelMap.CONTROL, QuickPanelMap.kind("android.settings.WIFI_SETTINGS"));
        assertEquals(QuickPanelMap.CONTROL, QuickPanelMap.kind("android.settings.BLUETOOTH_SETTINGS"));
        assertEquals(QuickPanelMap.CONTROL, QuickPanelMap.kind("com.android.tv.settings/.MainSettings"));
        assertEquals(QuickPanelMap.CONTROL, QuickPanelMap.kind(null));
    }

    @Test
    public void vendorActivitiesAreModules() {
        assertEquals(QuickPanelMap.MODULE, QuickPanelMap.kind(M + "picture.PictureActivity"));
        assertEquals(QuickPanelMap.MODULE, QuickPanelMap.kind(M + "sound.SoundActivity"));
    }

    @Test
    public void glyphByName() {
        assertEquals(QuickPanelMap.INPUTS, QuickPanelMap.glyph("inputs"));
        assertEquals(QuickPanelMap.WIFI, QuickPanelMap.glyph("android.settings.WIFI_SETTINGS"));
        assertEquals(QuickPanelMap.BLUETOOTH, QuickPanelMap.glyph("android.settings.BLUETOOTH_SETTINGS"));
        assertEquals(QuickPanelMap.PICTURE, QuickPanelMap.glyph(M + "picture.PictureActivity"));
        assertEquals(QuickPanelMap.SOUND, QuickPanelMap.glyph(M + "sound.SoundActivity"));
        assertEquals(QuickPanelMap.SCREEN, QuickPanelMap.glyph(M + "screen.ScreenModeActivity"));
        assertEquals(QuickPanelMap.AUDIO, QuickPanelMap.glyph(M + "audiooutput.AudioOutputActivity"));
        assertEquals(QuickPanelMap.SETTINGS, QuickPanelMap.glyph("com.android.tv.settings/.MainSettings"));
        assertEquals(QuickPanelMap.GENERIC, QuickPanelMap.glyph("x.y/.Z"));
    }
}
