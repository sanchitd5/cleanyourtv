package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VideoUnderPanelTest {
    @Test
    public void coveredOnlyWhilePausedAndStillStarted() {
        assertTrue(VideoUnderPanel.coveredByPanel(false, true));
        assertFalse(VideoUnderPanel.coveredByPanel(true, true));
        assertFalse(VideoUnderPanel.coveredByPanel(false, false));
    }
}
