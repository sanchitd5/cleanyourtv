package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GreetingOnceTest {
    @Test public void theKeyIsTheDateAndThePartOfTheDay() {
        assertEquals("2026-11-22/greeting_evening", GreetingOnce.key("2026-11-22", "greeting_evening"));
    }

    @Test public void theFirstHomeOfAPartIsDue() {
        assertTrue(GreetingOnce.due(null, "2026-11-22/greeting_morning"));
        assertTrue(GreetingOnce.due("2026-11-21/greeting_evening", "2026-11-22/greeting_morning"));
        assertTrue(GreetingOnce.due("2026-11-22/greeting_morning", "2026-11-22/greeting_afternoon"));
    }

    @Test public void theSamePartAgainIsNot() {
        assertFalse(GreetingOnce.due("2026-11-22/greeting_morning", "2026-11-22/greeting_morning"));
    }

    @Test public void theSamePartOnTheNextDayIsDue() {
        assertTrue(GreetingOnce.due("2026-11-22/greeting_morning", "2026-11-23/greeting_morning"));
    }

    @Test public void noKeyForNowIsNeverDue() {
        assertFalse(GreetingOnce.due(null, null));
        assertFalse(GreetingOnce.due("x", ""));
    }
}
