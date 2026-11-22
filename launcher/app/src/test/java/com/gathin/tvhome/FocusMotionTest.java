package com.gathin.tvhome;

import android.view.KeyEvent;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FocusMotionTest {
    @Test public void directionOfTheFourKeys() {
        assertArrayEquals(new int[]{1, 0}, FocusMotion.direction(KeyEvent.KEYCODE_DPAD_RIGHT, false));
        assertArrayEquals(new int[]{-1, 0}, FocusMotion.direction(KeyEvent.KEYCODE_DPAD_LEFT, false));
        assertArrayEquals(new int[]{0, 1}, FocusMotion.direction(KeyEvent.KEYCODE_DPAD_DOWN, false));
        assertArrayEquals(new int[]{0, -1}, FocusMotion.direction(KeyEvent.KEYCODE_DPAD_UP, false));
    }

    @Test public void anRtlGridMirrorsXOnly() {
        assertArrayEquals(new int[]{-1, 0}, FocusMotion.direction(KeyEvent.KEYCODE_DPAD_RIGHT, true));
        assertArrayEquals(new int[]{1, 0}, FocusMotion.direction(KeyEvent.KEYCODE_DPAD_LEFT, true));
        assertArrayEquals(new int[]{0, 1}, FocusMotion.direction(KeyEvent.KEYCODE_DPAD_DOWN, true));
        assertArrayEquals(new int[]{0, -1}, FocusMotion.direction(KeyEvent.KEYCODE_DPAD_UP, true));
    }

    @Test public void anyOtherKeyIsNoDirection() {
        assertArrayEquals(new int[]{0, 0}, FocusMotion.direction(KeyEvent.KEYCODE_ENTER, false));
        assertArrayEquals(new int[]{0, 0}, FocusMotion.direction(0, true));
    }

    @Test public void noParallaxKeepsTodaysBannerScale() {
        assertEquals(1.035f, FocusMotion.bannerScale(0, 114.5f), 1e-6f);
        assertEquals(1.035f, FocusMotion.bannerScale(0, 180f), 1e-6f);
    }

    @Test public void fourDpOnASixColumnTileZoomsAboutEightPercent() {
        assertEquals(1.087f, FocusMotion.bannerScale(4, 114.5f), 0.001f);
    }

    @Test public void theZoomAlwaysCoversTheShift() {
        // The banner moves P dp either way; each edge gains (scale - 1) * width / 2 of slack.
        for (float width : new float[]{60f, 90f, 114.5f, 140f, 180f}) {
            for (int p = 0; p <= 6; p++) {
                float scale = FocusMotion.bannerScale(p, width);
                float slack = (scale - 1f) * width / 2f;
                assertTrue("P=" + p + " width=" + width, p <= slack + 1e-4f);
            }
        }
    }

    @Test public void theZoomGrowsWithTheShiftAndShrinksWithTheTile() {
        assertTrue(FocusMotion.bannerScale(6, 114.5f) > FocusMotion.bannerScale(4, 114.5f));
        assertTrue(FocusMotion.bannerScale(4, 90f) > FocusMotion.bannerScale(4, 140f));
    }

    @Test public void aDegenerateWidthFallsBackToTheBaseScale() {
        assertEquals(1.035f, FocusMotion.bannerScale(4, 0f), 0f);
    }

    @Test public void theSheenCrossesLeftToRightAndBack() {
        assertEquals(-160f, FocusMotion.sheenFrom(200f, 1), 1e-3f);
        assertEquals(260f, FocusMotion.sheenTo(200f, 1), 1e-3f);
        assertEquals(-160f, FocusMotion.sheenFrom(200f, 0), 1e-3f);
        assertEquals(260f, FocusMotion.sheenTo(200f, 0), 1e-3f);
        assertEquals(160f, FocusMotion.sheenFrom(200f, -1), 1e-3f);
        assertEquals(-260f, FocusMotion.sheenTo(200f, -1), 1e-3f);
    }

    @Test public void theShelfFollowsTheRow() {
        assertEquals(1, FocusMotion.shelfDir(2, 3, false));
        assertEquals(-1, FocusMotion.shelfDir(3, 2, false));
        assertEquals(0, FocusMotion.shelfDir(3, 3, false));
        assertEquals(-1, FocusMotion.shelfDir(2, 3, true));
        assertEquals(1, FocusMotion.shelfDir(3, 2, true));
        assertEquals(0, FocusMotion.shelfDir(3, 3, true));
    }
}
