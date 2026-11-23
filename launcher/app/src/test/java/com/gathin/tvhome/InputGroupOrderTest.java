package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/** Step I1: input_group ordering, the reorder guard and the offset math (HOME-DESIGN.md 19.15). */
public class InputGroupOrderTest {
    /** The adapter's rule on plain strings: group test first, then the dock_apps rank, then the name. */
    private static List<String> sort(boolean group, List<String> favourites, String... keys) {
        List<String> list = new ArrayList<>(Arrays.asList(keys));
        Collections.sort(list, (a, b) -> {
            int g = AppAdapter.inputFirst(group, a.startsWith("input:"), b.startsWith("input:"));
            if (g != 0) return g;
            int fa = favourites.indexOf(a), fb = favourites.indexOf(b);
            if (fa >= 0 || fb >= 0) return fa < 0 ? 1 : fb < 0 ? -1 : Integer.compare(fa, fb);
            return a.compareTo(b);
        });
        return list;
    }

    @Test public void anInputListedFourthSortsFirstWhenGroupedAndFourthWhenNot() {
        List<String> dock = Arrays.asList("a", "b", "c", "input:hw2", "d");
        assertEquals(Arrays.asList("input:hw2", "a", "b", "c", "d"), sort(true, dock, "a", "b", "c", "input:hw2", "d"));
        assertEquals(Arrays.asList("a", "b", "c", "input:hw2", "d"), sort(false, dock, "d", "input:hw2", "c", "b", "a"));
    }

    @Test public void groupedInputsKeepTheirDockAppsOrder() {
        List<String> dock = Arrays.asList("input:hw3", "a", "input:hw2");
        assertEquals(Arrays.asList("input:hw3", "input:hw2", "a"), sort(true, dock, "a", "input:hw2", "input:hw3"));
    }

    @Test public void inputFirstIsSilentWhenOff() {
        assertEquals(0, AppAdapter.inputFirst(false, true, false));
        assertEquals(-1, AppAdapter.inputFirst(true, true, false));
        assertEquals(1, AppAdapter.inputFirst(true, false, true));
        assertEquals(0, AppAdapter.inputFirst(true, true, true));
    }

    @Test public void atMostThreeInputsAndTwoAppPlacesAreKept() {
        assertEquals(0, AppAdapter.groupLimit(0, 6));
        assertEquals(1, AppAdapter.groupLimit(1, 6));
        assertEquals(3, AppAdapter.groupLimit(5, 6));
        assertEquals(3, AppAdapter.groupLimit(3, 5));
        assertEquals(2, AppAdapter.groupLimit(5, 4));
        assertEquals(0, AppAdapter.groupLimit(5, 2));
        assertEquals(0, AppAdapter.groupLimit(5, 1));
    }

    @Test public void theGuardRefusesEveryCrossingAndNoMoveWithinAGroup() {
        for (int n = 0; n <= 3; n++) {
            for (int a = 0; a < 6; a++) {
                for (int b = 0; b < 6; b++) {
                    boolean crosses = n > 0 && ((a < n) != (b < n));
                    assertEquals(!crosses, GridNavigation.sameGroup(a, b, n));
                }
            }
        }
    }

    @Test public void dockEditPlacesStartAtTheFirstAppPlace() {
        assertEquals(2, GridNavigation.appSlot(0, 2, 6));
        assertEquals(5, GridNavigation.appSlot(9, 2, 6));
        assertEquals(0, GridNavigation.appSlot(0, 0, 6));
    }

    @Test public void gutterIsTheCellMinusTheTileAndNeverNegative() {
        assertEquals(47f, DockDecoration.gutterPx(1800, 6, 253), 0.01f);
        assertEquals(0f, DockDecoration.gutterPx(1200, 6, 253), 0f);
        assertEquals(0f, DockDecoration.gutterPx(1800, 0, 253), 0f);
    }

    @Test public void theGapGrowsByTheInsetOnTheFacingSidesAtFiveAndSixTiles() {
        float density = 2f;
        for (int columns : new int[]{5, 6}) {
            float g = DockDecoration.gutterPx(1840, columns, 1840 / columns - 40);   // a 40 px gutter
            float inset = DockDecoration.groupInsetPx(Config.INPUT_GROUP_DIVIDER, g, density);
            // Two cells each narrowed by inset on the facing side: each card moves inset/2, so the gap grows by inset.
            assertEquals(16f, inset, 0f);
            assertEquals(g + 16f, g + inset, 0f);
            float sep = DockDecoration.groupInsetPx(Config.INPUT_GROUP_SEPARATE, g, density);
            assertEquals(64f, g + sep, 0.01f);   // 32 dp at density 2, since g < 64 px here
        }
    }

    @Test public void insetSidesFaceEachOtherAndMirrorInRtl() {
        assertTrue(DockDecoration.insetOnRight(true, false));    // an input faces the apps on its right
        assertFalse(DockDecoration.insetOnRight(false, false));  // an app takes it on its left
        assertFalse(DockDecoration.insetOnRight(true, true));
        assertTrue(DockDecoration.insetOnRight(false, true));
    }
}
