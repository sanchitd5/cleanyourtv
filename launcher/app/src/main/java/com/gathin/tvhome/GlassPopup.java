package com.gathin.tvhome;

import android.view.View;
import android.widget.PopupWindow;

/**
 * A popup that closes with the glass exit motion (HOME-DESIGN.md 19.7): {@link #dismiss()} (Back, a touch outside) fades and
 * shrinks the panel in 110 ms and then goes; {@link #dismissNow()} (a chosen row, Home pausing) goes at once. With no
 * {@link GlassMotionController} set (glass off) it is a plain PopupWindow.
 */
final class GlassPopup extends PopupWindow {
    private GlassMotionController motion;
    private boolean closing;
    private final Runnable finish = () -> {
        closing = false;
        super.dismiss();
    };

    GlassPopup(View content, int width, int height, boolean focusable) {
        super(content, width, height, focusable);
    }

    void setMotion(GlassMotionController motion) {
        this.motion = motion;
    }

    GlassMotionController motion() {
        return motion;
    }

    @Override
    public void dismiss() {
        if (motion == null || !isShowing()) {
            super.dismiss();
            return;
        }
        if (closing) return;
        closing = true;
        motion.close(finish);
    }

    void dismissNow() {
        if (motion != null) motion.cancel();
        closing = false;
        super.dismiss();
    }

    /** Dismisses any popup at once: a {@link GlassPopup} skips its exit motion. */
    static void now(PopupWindow popup) {
        if (popup instanceof GlassPopup) ((GlassPopup) popup).dismissNow();
        else if (popup != null) popup.dismiss();
    }
}
