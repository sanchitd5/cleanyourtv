package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class QuickPanelCreditTest {
    private static final String T = "Developed by Example";

    @Test public void usesTheWatermarkCleaner() {
        assertEquals(T, Config.visibleCredit("  " + T + "\n", true));
        assertEquals("AB", Config.visibleCredit("A\u0000B", true));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 80; i++) sb.append('x');
        assertEquals(60, Config.visibleCredit(sb.toString(), true).length());
    }

    @Test public void emptyOrAbsentIsHidden() {
        assertEquals("", Config.visibleCredit(null, true));
        assertEquals("", Config.visibleCredit("   ", true));
    }

    @Test public void watermarkOffHidesIt() {
        assertEquals("", Config.visibleCredit(T, false));
    }
}
