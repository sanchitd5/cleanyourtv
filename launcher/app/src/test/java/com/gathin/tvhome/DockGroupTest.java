package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The input group's offset math (HOME-DESIGN.md 19.15) and Config.inputGroup. */
public class DockGroupTest {
    @Test public void offAddsNoRoom() {
        assertEquals(0f, DockDecoration.groupInsetPx("off", 47f, 2f), 0f);
        assertEquals(0f, DockDecoration.groupInsetPx(null, 47f, 2f), 0f);
        assertEquals(0f, DockDecoration.groupInsetPx("nonsense", 47f, 2f), 0f);
    }

    @Test public void dividerAddsEightDp() {
        assertEquals(16f, DockDecoration.groupInsetPx("divider", 47f, 2f), 1e-4);
        assertEquals(8f, DockDecoration.groupInsetPx("divider", 10f, 1f), 1e-4);
    }

    @Test public void separateLiftsTheGapToThirtyTwoDp() {
        float d = 2f;
        float gutter6 = 23.5f * d, gutter5 = 25.6f * d;
        assertEquals(32f * d - gutter6, DockDecoration.groupInsetPx("separate", gutter6, d), 1e-3);
        assertEquals(32f * d - gutter5, DockDecoration.groupInsetPx("separate", gutter5, d), 1e-3);
        assertEquals(32f * d, gutter6 + DockDecoration.groupInsetPx("separate", gutter6, d), 1e-3);
    }

    @Test public void theInsetIsNeverNegative() {
        for (String mode : new String[]{"off", "divider", "separate"}) {
            for (float gutter : new float[]{0f, 20f, 47f, 64f, 200f}) {
                assertTrue(DockDecoration.groupInsetPx(mode, gutter, 2f) >= 0f);
            }
        }
        assertEquals(0f, DockDecoration.groupInsetPx("separate", 200f, 2f), 0f);
    }

    @Test public void theDividerSitsInTheMiddleOfTheGap() {
        assertEquals(150f, DockDecoration.dividerX(100f, 200f), 1e-4);
        assertEquals(150f, DockDecoration.dividerX(200f, 100f), 1e-4);
    }

    @Test public void inputGroupTakesTheThreeWordsInAnyCaseAndElseOff() {
        assertEquals("off", Config.inputGroup("off"));
        assertEquals("divider", Config.inputGroup("divider"));
        assertEquals("divider", Config.inputGroup(" DIVIDER "));
        assertEquals("separate", Config.inputGroup("Separate"));
        assertEquals("off", Config.inputGroup("groups"));
        assertEquals("off", Config.inputGroup(""));
        assertEquals("off", Config.inputGroup(null));
    }

    @Test public void reduceTransparencyChosenOnTheTvWinsOverTheTheme() {
        assertEquals(true, Config.chosenSwitch(1, false));
        assertEquals(false, Config.chosenSwitch(0, true));
        assertEquals(true, Config.chosenSwitch(-1, true));
        assertEquals(false, Config.chosenSwitch(-1, false));
        assertEquals(true, Config.chosenSwitch(7, true));
    }
}
