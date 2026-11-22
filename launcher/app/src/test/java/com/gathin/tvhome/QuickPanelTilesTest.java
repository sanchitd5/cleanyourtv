package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

/** quick_panel: how one "Label=target" entry is read (QuickSettingsActivity.tile). */
public class QuickPanelTilesTest {
    @Test public void anEntryIsItsLabelAndItsTarget() {
        assertArrayEquals(
                new String[]{"Picture", "com.mediatek.tv.settings/.displayandsound.picture.PictureActivity"},
                QuickSettingsActivity.tile("Picture=com.mediatek.tv.settings/.displayandsound.picture.PictureActivity"));
        assertArrayEquals(new String[]{"Wi-Fi", "android.settings.WIFI_SETTINGS"},
                QuickSettingsActivity.tile("Wi-Fi=android.settings.WIFI_SETTINGS"));
        assertArrayEquals(new String[]{"Inputs", "inputs"}, QuickSettingsActivity.tile("Inputs=inputs"));
    }

    @Test public void spacesAroundTheHalvesAreTrimmed() {
        assertArrayEquals(new String[]{"Audio output", "a.pkg/.Audio"},
                QuickSettingsActivity.tile("  Audio output = a.pkg/.Audio "));
    }

    @Test public void theLabelEndsAtTheFirstEqualsSign() {
        assertArrayEquals(new String[]{"A", "b=c"}, QuickSettingsActivity.tile("A=b=c"));
    }

    @Test public void anEntryWithoutBothHalvesIsNoTile() {
        assertNull(QuickSettingsActivity.tile("Picture"));
        assertNull(QuickSettingsActivity.tile("=a.pkg/.Picture"));
        assertNull(QuickSettingsActivity.tile("Picture="));
        assertNull(QuickSettingsActivity.tile(" = "));
        assertNull(QuickSettingsActivity.tile(""));
    }
}
