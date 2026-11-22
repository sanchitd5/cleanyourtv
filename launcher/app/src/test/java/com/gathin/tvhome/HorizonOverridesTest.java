package com.gathin.tvhome;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** On-TV Horizon choices: the theme's value, then the saved choice, then back to the theme once it is cleared. */
public class HorizonOverridesTest {
    /** Absent is -1, which is what a cleared key reads back as. */
    private static final int ABSENT = -1;

    @Test public void aSwitchFollowsTheThemeUntilChosenThenFollowsTheChoice() {
        assertTrue(Config.chosenSwitch(ABSENT, true));
        assertFalse(Config.chosenSwitch(ABSENT, false));
        assertFalse(Config.chosenSwitch(0, true));    // Off beats a theme that turns it on
        assertTrue(Config.chosenSwitch(1, false));    // On beats a theme that leaves it off
        assertTrue(Config.chosenSwitch(ABSENT, true)); // cleared: the theme again
        assertTrue(Config.chosenSwitch(7, true));     // not one of ours: the theme
    }

    @Test public void aNumberKeepsZeroAsARealChoiceForOffAndNever() {
        assertEquals(10, Config.chosenTiming(ABSENT, 10, 1440));
        assertEquals(0, Config.chosenTiming(0, 10, 1440));      // Never
        assertEquals(30, Config.chosenTiming(30, 10, 1440));
        assertEquals(10, Config.chosenTiming(ABSENT, 10, 1440)); // cleared
        assertEquals(10, Config.chosenTiming(5000, 10, 1440));   // out of range: the theme
        assertEquals(60, Config.chosenTiming(60, 100, 100));     // shelf scrim
    }

    @Test public void theGreetingIsOneChoiceOverTwoThemeKeys() {
        assertArrayEquals(new boolean[]{true, true}, Config.chosenGreeting(ABSENT, true, true));
        assertArrayEquals(new boolean[]{false, true}, Config.chosenGreeting(ABSENT, false, true));
        assertArrayEquals(new boolean[]{false, false}, Config.chosenGreeting(Config.GREETING_OFF, true, true));
        assertArrayEquals(new boolean[]{true, false}, Config.chosenGreeting(Config.GREETING_ALWAYS, false, true));
        assertArrayEquals(new boolean[]{true, true}, Config.chosenGreeting(Config.GREETING_ONCE, false, false));
        assertEquals(Config.GREETING_OFF, Config.greetingMode(false, true));
        assertEquals(Config.GREETING_ALWAYS, Config.greetingMode(true, false));
        assertEquals(Config.GREETING_ONCE, Config.greetingMode(true, true));
    }

    @Test public void theScreensaverFolderIsSwitchedOnOrOffOverTheTheme() {
        assertTrue(ScreensaverStills.useFolder(ABSENT, "screensaver"));
        assertFalse(ScreensaverStills.useFolder(ABSENT, ""));
        assertFalse(ScreensaverStills.useFolder(0, "screensaver"));   // Home wallpapers
        assertTrue(ScreensaverStills.useFolder(1, ""));               // folder, though the theme does not name it
    }

    @Test public void everyRowsKeyIsListedSoFollowThemeForgetsIt() {
        Set<String> keys = new HashSet<>(java.util.Arrays.asList(Config.HORIZON_KEYS));
        assertEquals(Config.HORIZON_KEYS.length, keys.size());
        for (String key : new String[]{Config.DOCK_TILES_KEY, Config.HOME_IDLE_CALM_KEY, Config.HOME_IDLE_DIM_KEY,
                Config.CLOCK_DIM_AFTER_KEY, Config.CLOCK_BLANK_AFTER_KEY, Config.TOP_SHELF_KEY,
                Config.TOP_SHELF_SCRIM_KEY, Config.INPUT_TILES_KEY, Config.INPUT_TILES_EMPTY_KEY,
                Config.GREETING_MODE_KEY, Config.FOCUS_PARALLAX_KEY, Config.FOCUS_SHEEN_KEY,
                Config.WALLPAPER_VIDEO_KEY, Config.VIDEO_MINUTES_KEY, Config.CAPTION_SECONDS_KEY,
                Config.HOME_IDLE_SHIFT_KEY, Config.REDUCED_MOTION_KEY, Config.SCREENSAVER_PHOTOS_KEY}) {
            assertTrue(key, keys.contains(key));
        }
    }

    @Test public void theRowsOffersEndWithTheirOffOrNeverValue() {
        assertEquals(0, Config.VIDEO_MINUTES_CHOICES[Config.VIDEO_MINUTES_CHOICES.length - 1]);
        assertEquals(0, Config.CAPTION_SECONDS_CHOICES[Config.CAPTION_SECONDS_CHOICES.length - 1]);
        assertEquals(0, Config.CLOCK_BLANK_AFTER_CHOICES[0]);
    }
}
