package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WatermarkTest {
    private static final String T = "Created by Example";

    @Test public void cleaningTrimsAndStripsControls() {
        assertEquals("", Config.cleanWatermark(null));
        assertEquals("", Config.cleanWatermark("   "));
        assertEquals(T, Config.cleanWatermark("  " + T + "\n"));
        assertEquals("AB", Config.cleanWatermark("A\u0000\u0007B"));
        assertEquals("A B", Config.cleanWatermark("A \t\nB".replace("\t\n", "")));
    }

    @Test public void cleaningCapsAtSixtyCharactersWithoutSplittingPairs() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 80; i++) sb.append('x');
        assertEquals(60, Config.cleanWatermark(sb.toString()).length());
        StringBuilder emoji = new StringBuilder();
        for (int i = 0; i < 70; i++) emoji.appendCodePoint(0x1F600);
        String out = Config.cleanWatermark(emoji.toString());
        assertEquals(60, out.codePointCount(0, out.length()));
    }

    @Test public void noTextNeverShows() {
        assertFalse(Watermark.visible("", false, 0, 20_000, 90_000));
        assertFalse(Watermark.visible(null, false, 0, 20_000, 90_000));
    }

    @Test public void shownUntilTheCalmStage() {
        assertTrue(Watermark.visible(T, false, 0, 20_000, 90_000));
        assertTrue(Watermark.visible(T, false, 19_999, 20_000, 90_000));
        assertFalse(Watermark.visible(T, false, 20_000, 20_000, 90_000));
        assertFalse(Watermark.visible(T, false, 95_000, 20_000, 90_000));   // dimmed: absent
    }

    @Test public void withoutCalmTheDimStageThenTheFallbackApply() {
        assertEquals(90_000, Watermark.hideAfterMs(0, 90_000));
        assertEquals(Watermark.FALLBACK_HIDE_MS, Watermark.hideAfterMs(0, 0));
        assertEquals(20_000, Watermark.hideAfterMs(20_000, 0));
        assertEquals(20_000, Watermark.hideAfterMs(60_000, 20_000));
    }

    @Test public void neverInTheScreensaver() {
        assertFalse(Watermark.visible(T, true, 0, 20_000, 90_000));
    }

    @Test public void anyKeyBringsItBack() {
        assertFalse(Watermark.visible(T, false, 30_000, 20_000, 90_000));
        assertTrue(Watermark.visible(T, false, 0, 20_000, 90_000));   // idle restarts at 0 on a key
    }
}
