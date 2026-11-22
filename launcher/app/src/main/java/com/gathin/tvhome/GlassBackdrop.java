package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.view.SurfaceView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * What a glass panel shows behind itself (HOME-DESIGN.md 19.4): a process-wide holder of one small pre-blurred copy of what
 * Home shows, shared by Home's popups, the quick panel and Launcher settings (all one process). Step G1 fixes the surface
 * of this class and holds nothing: every getter answers "nothing yet" until G2 fills it. Threading: publishers call from the
 * wallpaper thread, readers from the main thread, so state is volatile and the listener list is guarded.
 *
 * Bitmaps are {@link #WIDTH} x {@link #HEIGHT} ARGB_8888, immutable once published (a new one replaces the old).
 */
final class GlassBackdrop {
    private GlassBackdrop() { }

    /** Size of a backdrop bitmap: 1/15 of 1920x1080. */
    static final int WIDTH = 128, HEIGHT = 72;
    /** The 6 % magnification about the panel centre that is the refraction cue (19.4). */
    static final float MAGNIFY = 1.06f;

    /** Told, on the main thread's looper or the publisher's thread, that {@link #generation()} moved. Held weakly. */
    interface Listener {
        void onBackdropChanged();
    }

    private static final Object LOCK = new Object();
    private static final List<WeakReference<Listener>> listeners = new ArrayList<>();
    private static volatile Bitmap still;
    private static volatile Bitmap live;
    private static volatile int generation;
    private static volatile boolean homeVisible;
    private static volatile boolean liveUnavailable;

    // ---- state: G2 fills it ----

    /** The blurred copy of the still wallpaper, or null before the first picture / with no wallpaper / after release. */
    static Bitmap still() {
        return still;
    }

    /** The blurred copy of the clip's latest frame, or null (not sampled yet, off, or unavailable on this TV). */
    static Bitmap live() {
        return live;
    }

    /** What a panel should draw: the live sample when there is one, else the still's copy, else null (a flat tint). */
    static Bitmap current() {
        Bitmap l = live;
        return l != null ? l : still;
    }

    /** Moves whenever {@link #current()} may have changed; a drawable remembers it to know when to redraw. */
    static int generation() {
        return generation;
    }

    /** G2: publish the still's copy (null clears it). */
    static void publishStill(Bitmap blurred) {
        still = blurred;
        changed();
    }

    /** G2: publish a live sample (null clears it). */
    static void publishLive(Bitmap blurred) {
        live = blurred;
        changed();
    }

    /** Home is between onStart and onStop (also while paused under the translucent quick panel): the Glass versus Tinted test. */
    static boolean homeVisible() {
        return homeVisible;
    }

    static void setHomeVisible(boolean visible) {
        homeVisible = visible;
        changed();
    }

    /** False once one PixelCopy failed or read back black: the still's copy is used for the rest of the process. */
    static boolean liveAvailable() {
        return !liveUnavailable;
    }

    static void markLiveUnavailable() {
        liveUnavailable = true;
        publishLive(null);
    }

    /**
     * G2: ask for the clip's current frame. {@code crop} is {@link WallpaperSize#cropScale}'s result for the clip. Answers
     * whether a request was made; G1's stub never makes one. The result arrives through {@link #publishLive}.
     */
    static boolean requestLive(SurfaceView clip, float[] crop) {
        return false;
    }

    /** Drops both copies and the unavailable mark (the wallpaper went, or memory pressure). Listeners are told. */
    static void clear() {
        still = null;
        live = null;
        liveUnavailable = false;
        changed();
    }

    /** Drops only the live sample (Home's onStop released the decoder). */
    static void clearLive() {
        publishLive(null);
    }

    static void addListener(Listener l) {
        synchronized (LOCK) {
            prune();
            for (WeakReference<Listener> ref : listeners) if (ref.get() == l) return;
            listeners.add(new WeakReference<>(l));
        }
    }

    static void removeListener(Listener l) {
        synchronized (LOCK) {
            for (int i = listeners.size() - 1; i >= 0; i--) {
                Listener held = listeners.get(i).get();
                if (held == null || held == l) listeners.remove(i);
            }
        }
    }

    private static void prune() {
        for (int i = listeners.size() - 1; i >= 0; i--) if (listeners.get(i).get() == null) listeners.remove(i);
    }

    private static void changed() {
        List<Listener> toTell = new ArrayList<>();
        synchronized (LOCK) {
            generation++;
            prune();
            for (WeakReference<Listener> ref : listeners) {
                Listener l = ref.get();
                if (l != null) toTell.add(l);
            }
        }
        for (Listener l : toTell) l.onBackdropChanged();
    }

    // ---- geometry: pure ----

    /**
     * The matrix values that draw the {@link #WIDTH} x {@link #HEIGHT} bitmap, full-screen aligned and magnified about the
     * panel centre, into the panel's own coordinates: {@code out = {scaleX, scaleY, translateX, translateY}}, so a bitmap
     * pixel (bx, by) lands at {@code (bx * scaleX + translateX, by * scaleY + translateY)} in the panel. The panel is at
     * (panelLeft, panelTop) on a screen of screenW x screenH, and is panelW x panelH.
     */
    static void align(float panelLeft, float panelTop, float panelW, float panelH, float screenW, float screenH,
                      float magnify, float[] out) {
        float sx = screenW / WIDTH * magnify, sy = screenH / HEIGHT * magnify;
        float cx = panelW / 2f, cy = panelH / 2f;
        out[0] = sx;
        out[1] = sy;
        out[2] = magnify * (-panelLeft - cx) + cx;
        out[3] = magnify * (-panelTop - cy) + cy;
    }

    /**
     * The bitmap pixels under a panel, for {@link GlassTint#peak}: {@code out = {left, top, right, bottom}} (right and
     * bottom exclusive), the panel's screen rectangle scaled down by the bitmap's ratio, widened to whole pixels and kept
     * inside the bitmap. The 6 % magnification is ignored: the tint is for what is behind the panel, not what is drawn in it.
     */
    static void regionUnder(float panelLeft, float panelTop, float panelW, float panelH, float screenW, float screenH, int[] out) {
        float kx = WIDTH / screenW, ky = HEIGHT / screenH;
        out[0] = Math.max(0, (int) Math.floor(panelLeft * kx));
        out[1] = Math.max(0, (int) Math.floor(panelTop * ky));
        out[2] = Math.min(WIDTH, (int) Math.ceil((panelLeft + panelW) * kx));
        out[3] = Math.min(HEIGHT, (int) Math.ceil((panelTop + panelH) * ky));
    }
}
