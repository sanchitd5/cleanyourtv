package com.gathin.tvhome;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.util.TypedValue;

/**
 * The owner's credit line on Home (theme {@code watermark}): one quiet line at the bottom right, text only. It is chrome
 * for the stillness guard (HOME-DESIGN.md 7): {@link HomeIdle} drifts it with the header and fades it out at the calm
 * stage; it lives in Home's own layout, so the screensaver (ClockActivity, ClockDream) never has it. The pure rules are
 * here so they are tested.
 */
final class Watermark {
    /** Home idle time before it goes when neither calm nor dim is set: a static line must not outlive 20 s of idleness. */
    static final long FALLBACK_HIDE_MS = 20_000L;

    private Watermark() {}

    /** Idle time at which the line fades out: the first of the calm and dim stages that is on, else {@link #FALLBACK_HIDE_MS}. */
    static long hideAfterMs(long calmMs, long dimMs) {
        long a = calmMs > 0 ? calmMs : Long.MAX_VALUE, b = dimMs > 0 ? dimMs : Long.MAX_VALUE;
        long m = Math.min(a, b);
        return m == Long.MAX_VALUE ? FALLBACK_HIDE_MS : m;
    }

    /** Whether the line shows: never in the screensaver, and only before the calm (or dim) stage; any key resets idleMs to 0. */
    static boolean visible(String text, boolean screensaver, long idleMs, long calmMs, long dimMs) {
        return text != null && !text.isEmpty() && !screensaver && idleMs < hideAfterMs(calmMs, dimMs);
    }

    /**
     * Adds the line to Home's root, or returns null when there is no text. 13 sp, the theme's regular font, white at 35 %,
     * tracked 0.04, right edge on the dock band's right edge (903 dp), text bottom 31 dp above the screen's edge. Not focusable.
     */
    static TextView attach(Context context, FrameLayout root, String text) {
        if (text == null || text.isEmpty()) return null;
        float dp = context.getResources().getDisplayMetrics().density;
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setTextColor(0x59FFFFFF);
        view.setLetterSpacing(0.04f);
        view.setShadowLayer(4 * dp, 0, dp, 0x66000000);
        int pad = Math.round(8 * dp);   // room for the shadow, taken back by the margins
        view.setPadding(pad, pad, pad, pad);
        view.setSingleLine(true);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        view.setMaxWidth(Math.round(520 * dp) + 2 * pad);
        view.setFocusable(false);
        view.setClickable(false);
        view.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        try {
            view.setTypeface(UiFont.get(context, R.string.font_regular));
        } catch (Exception ignored) {
            // the system font is fine
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        params.bottomMargin = Math.round(31 * dp) - pad;
        params.setMarginEnd(Math.round(57 * dp) - pad);
        root.addView(view, params);
        return view;
    }
}
