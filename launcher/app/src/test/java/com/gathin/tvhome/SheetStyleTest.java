package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SheetStyleTest {
    @Test
    public void rolesFollowLayoutSizes() {
        assertEquals(SheetStyle.TITLE, SheetStyle.roleFor(34f));
        assertEquals(SheetStyle.SECTION, SheetStyle.roleFor(20f));
        assertEquals(SheetStyle.HINT, SheetStyle.roleFor(14f));
    }

    @Test
    public void valueStartIsAfterFirstColon() {
        assertEquals(6, SheetStyle.valueStart("Dock: 5"));
        assertEquals(-1, SheetStyle.valueStart("Reorder"));
        assertEquals(-1, SheetStyle.valueStart("Dock: "));
        assertEquals(-1, SheetStyle.valueStart(null));
        assertEquals(3, SheetStyle.valueStart("A: b: c"));
    }
}
