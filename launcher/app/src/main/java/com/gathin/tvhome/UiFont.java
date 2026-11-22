package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.util.TypedValue;

/**
 * The typeface behind one of the font_* strings in res/values/fonts.xml, for views built in code. The string
 * points at a bundled font, or, in locales Outfit can't draw (values-vi), names a system family (#833).
 * A bundled Outfit font becomes its Inter weight when the font choice is Inter ({@link Fonts}).
 */
final class UiFont {
    private UiFont() {}

    static Typeface get(Context context, int fontString) {
        Resources res = context.getResources();
        TypedValue value = new TypedValue();
        res.getValue(fontString, value, true);
        String name = String.valueOf(value.string);
        if (name.startsWith("res/")) return res.getFont(Fonts.resource(Fonts.current(), value.resourceId));
        return Typeface.create(name, Typeface.NORMAL);
    }

    /** A bundled font resource (R.font.*) as a Typeface. */
    static Typeface load(Context context, int fontResource) {
        return context.getResources().getFont(fontResource);
    }
}
