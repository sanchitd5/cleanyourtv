package com.gathin.tvhome;

/** Pure rules for the glass settings sheet (HOME-DESIGN.md 19.5, 19.6): which text role a plain label has, and where a row's value starts. */
final class SheetStyle {
    private SheetStyle() { }

    static final int TITLE = 0, SECTION = 1, HINT = 2;
    /** Section label: 13 sp, caps, tracking 0.16 em. */
    static final float SECTION_SP = 13f, SECTION_TRACKING = 0.16f;

    /**
     * The role of a non-focusable label by its layout size in sp: the 34 sp sheet title, the 20 sp section headings, and the 14 sp
     * hints under rows. Sizes come from the layout, so no layout needs a tag.
     */
    static int roleFor(float sp) {
        if (sp >= 30f) return TITLE;
        return sp >= 18f ? SECTION : HINT;
    }

    /** Index where the value of a "Label: value" row starts (after the first ": "), or -1 when the row has none. */
    static int valueStart(CharSequence text) {
        if (text == null) return -1;
        int i = text.toString().indexOf(": ");
        return i < 0 || i + 2 >= text.length() ? -1 : i + 2;
    }
}
