package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The numbers of HOME-DESIGN.md section 21.10. */
public class TopBarLayoutTest {
    @Test public void baselinesAndMargin() {
        assertEquals(160, TopBarLayout.baselineY(124, 72));
        assertEquals(123, TopBarLayout.baselineY(108, 30));
        assertEquals(114, TopBarLayout.marginX(2f));
        assertEquals(124, TopBarLayout.centreY("split", 2f));
        assertEquals(108, TopBarLayout.centreY("tvos", 2f));
    }

    @Test public void theGreetingSlot() {
        assertEquals(200, TopBarLayout.greetingBaselineY("split", true, 2f));
        assertEquals(158, TopBarLayout.greetingBaselineY("tvos", true, 2f));
        assertEquals(118, TopBarLayout.greetingBaselineY("tvos", false, 2f));
    }

    @Test public void theSplitRow() {
        TopBarLayout.SplitRow r = TopBarLayout.splitRow(114, 239, 43, 203);
        assertEquals(369, r.ampmX);
        assertEquals(436, r.sepX);
        assertEquals(462, r.dateX);
        assertEquals(665, r.end);
        assertEquals(377, TopBarLayout.splitRow(114, 239, 0, 203).sepX);
    }

    @Test public void theCapsule() {
        TopBarLayout.Capsule c = TopBarLayout.capsule(1806, 108, new int[]{72, 72}, 101, 36);
        assertEquals(1449, c.left);
        assertEquals(64, c.top);
        assertEquals(152, c.bottom);
        assertEquals(1806, c.right);
        assertEquals(1629, c.clockX);
        assertEquals(1493, TopBarLayout.capsule(1806, 108, new int[]{72, 72}, 101, 0).left);
    }

    @Test public void theZone() {
        assertEquals(597, TopBarLayout.zoneRight(168, 1f), 1);
        assertEquals(628, TopBarLayout.zoneRight(126, 1f), 1);
        assertEquals(0, TopBarLayout.zoneRight(400, 1f));
    }

    @Test public void theCornerStretch() {
        assertEquals(1.134f, TopBarLayout.cornerScaleX(677, 168, 596, 208), 0.005f);
        assertEquals(1.073f, TopBarLayout.cornerScaleX(596, 208), 0.005f);
        assertEquals(1f, TopBarLayout.cornerScaleX(596, 126, 596, 166), 0.005f);
        assertEquals(1.25f, TopBarLayout.cornerScaleX(889, 196), 0f);
        assertEquals(1.25f, TopBarLayout.cornerScaleX(10, 500), 0f);
    }

    @Test public void todaysFullDateIsOutsideAndTheRegressionIsPinned() {
        assertTrue(889 > TopBarLayout.zoneRight(196, 1f));
        assertTrue(889 > TopBarLayout.zoneRight(196, 1.25f));
        assertTrue(566 <= TopBarLayout.zoneRight(196, 1f));
    }

    @Test public void theCornerKeepsTheFloorAtEachZonePoint() {
        float[][] pts = {{596, 126, 1f}, {596, 166, 1f}, {677, 168, 1.134f}, {596, 208, 1.073f}};
        for (float[] p : pts) {
            // A hair inside the limit: the stretch is computed to the pixel.
            assertTrue("x=" + p[0] + " y=" + p[1], ShelfScrim.cornerAlphaAt(p[0] - 1, p[1], 1920, 1080, p[2]) >= 0.57f);
        }
    }

    @Test public void theDateMaxWidthStaysInsideTheZoneWithDrift() {
        int w = TopBarLayout.dateMaxWidth(114, 118, 1f, 2f);
        assertTrue("a 470 px line fits at tvos s=1", w >= 470);
        assertTrue(114 + w + 12 <= TopBarLayout.zoneRight(118 + 8, 1f));
    }

    @Test public void driftMatchesTheStillGuardOrbit() {
        float x = 0, y = 0;
        for (int n = 0; n < 8; n++) {
            x = Math.max(x, Math.abs(StillGuard.orbit(n)[0]));
            y = Math.max(y, Math.abs(StillGuard.orbit(n)[1]));
        }
        assertEquals(TopBarLayout.DRIFT_X_DP, x, 1e-4f);
        assertEquals(TopBarLayout.DRIFT_Y_DP, y, 1e-4f);
    }

    @Test public void fallbacksAreClassic() {
        assertEquals("classic", TopBarLayout.mode("tvos", true, false, false));
        assertEquals("classic", TopBarLayout.mode("split", false, true, false));
        assertEquals("classic", TopBarLayout.mode("split", false, false, true));
        assertEquals("classic", TopBarLayout.mode("nonsense", false, false, false));
        assertEquals("tvos", TopBarLayout.mode("tvos", false, false, false));
    }

    @Test public void stableNeedsTwoEqualPassesOrTheTimeout() {
        int[] a = {100, 40, 200, 90, 30, 180}, b = {100, 40, 220, 90, 30, 200};
        assertFalse("first pass", TopBarLayout.stable(null, a, 10));
        assertFalse("widths moved", TopBarLayout.stable(a, b, 10));
        assertTrue("same twice", TopBarLayout.stable(a, a.clone(), 10));
        assertTrue("timeout", TopBarLayout.stable(null, a, TopBarLayout.SETTLE_TIMEOUT_MS));
        assertTrue("timeout while moving", TopBarLayout.stable(a, b, 900));
    }
}
