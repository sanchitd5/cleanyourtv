package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.TextAppearanceSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Run-time restyle of the launcher settings sheet's text (19.5): fonts through {@link UiFont}, caption section labels, 78 % hints and values. */
final class GlassSheet {
    private GlassSheet() { }

    /** The window colour behind the sheet when there is no backdrop: a vertical gradient, #14171D to #0A0B0F (19.6). */
    static GradientDrawable fallbackBackground() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF14171D, 0xFF0A0B0F});
    }

    /** Fonts and colours for every plain label and row under {@code root}. Call after the rows exist; safe to repeat. */
    static void restyle(ViewGroup root) {
        Context c = root.getContext();
        Typeface light = UiFont.get(c, R.string.font_light), medium = UiFont.get(c, R.string.font_medium),
                regular = UiFont.get(c, R.string.font_regular);
        float spPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, c.getResources().getDisplayMetrics());
        walk(root, light, medium, regular, spPx);
    }

    private static void walk(ViewGroup group, Typeface light, Typeface medium, Typeface regular, float spPx) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View v = group.getChildAt(i);
            if (v instanceof TextView) {
                TextView t = (TextView) v;
                if (t.isFocusable()) {
                    t.setTypeface(regular);
                } else {
                    switch (SheetStyle.roleFor(t.getTextSize() / spPx)) {
                        case SheetStyle.TITLE:
                            t.setTypeface(light);
                            t.setTextColor(0xFFF5F7FA);
                            break;
                        case SheetStyle.SECTION:
                            t.setTypeface(medium);
                            t.setTextColor(0xC7FFFFFF);
                            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, SheetStyle.SECTION_SP);
                            t.setAllCaps(true);
                            t.setLetterSpacing(SheetStyle.SECTION_TRACKING);
                            break;
                        default:
                            t.setTypeface(regular);
                            t.setTextColor(0xC7FFFFFF);
                    }
                }
            } else if (v instanceof ViewGroup) {
                walk((ViewGroup) v, light, medium, regular, spPx);
            }
        }
    }

    /** Shows the value half of a "Label: value" row at 78 % (ink on the focused capsule). Call after each setText. */
    static void dimValue(TextView t) {
        int start = SheetStyle.valueStart(t.getText());
        if (start < 0) return;
        SpannableString s = new SpannableString(t.getText());
        s.setSpan(new TextAppearanceSpan(null, 0, 0, t.getResources().getColorStateList(R.color.glass_ink_mid, null), null),
                start, s.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        t.setText(s);
    }
}
