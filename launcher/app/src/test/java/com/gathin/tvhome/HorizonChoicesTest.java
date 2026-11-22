package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** The on-TV overrides for the Horizon dock size and idle timings (HOME-DESIGN.md section 16, decisions 1 and 6). */
public class HorizonChoicesTest {
    @Test public void aDockSizeChosenOnTheTvWinsOverTheTheme() {
        assertEquals(5, Config.chosenDockTiles(5, 6));
        assertEquals(6, Config.chosenDockTiles(6, 5));
    }

    @Test public void noDockChoiceKeepsTheCurrentColumns() {
        assertEquals(6, Config.chosenDockTiles(-1, 6));
        assertEquals(4, Config.chosenDockTiles(-1, 4));
    }

    @Test public void onlyFiveOrSixIsADockChoice() {
        assertEquals(6, Config.chosenDockTiles(4, 6));
        assertEquals(6, Config.chosenDockTiles(7, 6));
        assertEquals(6, Config.chosenDockTiles(0, 6));
    }

    @Test public void aTimingChosenOnTheTvWinsOverTheTheme() {
        assertEquals(30, Config.chosenTiming(30, 90, 3600));
        assertEquals(3600, Config.chosenTiming(3600, 90, 3600));
    }

    @Test public void zeroIsARealChoiceAndMeansOff() {
        // The blank stage's "Off": the theme says 30 minutes, the owner picked Off.
        assertEquals(0, Config.chosenTiming(0, 30, 720));
    }

    @Test public void noTimingChoiceKeepsTheThemeValue() {
        assertEquals(90, Config.chosenTiming(-1, 90, 3600));
    }

    @Test public void anOutOfRangeTimingIsIgnored() {
        assertEquals(90, Config.chosenTiming(-2, 90, 3600));
        assertEquals(90, Config.chosenTiming(3601, 90, 3600));
    }

    @Test public void everyOfferedChoiceIsAcceptedByItsKey() {
        for (int v : Config.DOCK_TILE_CHOICES) assertEquals(v, Config.chosenDockTiles(v, 6));
        for (int v : Config.HOME_IDLE_CALM_CHOICES) assertEquals(v, Config.chosenTiming(v, 20, 600));
        for (int v : Config.HOME_IDLE_DIM_CHOICES) assertEquals(v, Config.chosenTiming(v, 90, 3600));
        for (int v : Config.CLOCK_DIM_AFTER_CHOICES) assertEquals(v, Config.chosenTiming(v, 5, 240));
        for (int v : Config.CLOCK_BLANK_AFTER_CHOICES) assertEquals(v, Config.chosenTiming(v, 30, 720));
    }

    @Test public void aTopBarLayoutChosenOnTheTvWinsAndFollowThemeKeepsTheTheme() {
        assertEquals("classic", Config.chosenTopBarLayout(0, "tvos"));
        assertEquals("split", Config.chosenTopBarLayout(1, "classic"));
        assertEquals("tvos", Config.chosenTopBarLayout(2, "classic"));
        assertEquals("tvos", Config.chosenTopBarLayout(-1, "tvos"));
        assertEquals("split", Config.chosenTopBarLayout(3, "split"));
    }

    @Test public void anUnknownTopBarLayoutIsClassic() {
        assertEquals("classic", Config.topBarLayout("nonsense"));
        assertEquals("classic", Config.topBarLayout(null));
        assertEquals("tvos", Config.topBarLayout(" TVOS "));
        assertEquals("split", Config.topBarLayout("split"));
    }

    @Test public void theTopBarKeysAreForgottenByFollowTheme() {
        java.util.List<String> keys = java.util.Arrays.asList(Config.HORIZON_KEYS);
        org.junit.Assert.assertTrue(keys.contains(Config.TOP_BAR_LAYOUT_KEY));
        org.junit.Assert.assertTrue(keys.contains(Config.TOP_BAR_DATE_KEY));
    }
}
