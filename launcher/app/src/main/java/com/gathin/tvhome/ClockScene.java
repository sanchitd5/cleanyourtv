package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The clock screensaver's scene (layout dream_clock): the thin clock and date on black over a slow ambient
 * glow. Every minute the clock drifts to a new spot and the glow shifts colour, so nothing burns in; a
 * ClockRefresh keeps its TextClocks current where Android would not.
 * Shared by ClockDream (the system screensaver) and ClockActivity (Home left idle).
 */
final class ClockScene {

    /** The pause between moves (theme clock_shift_seconds; 60 s unless a theme says otherwise). */
    private long moveMs = 60_000L;
    /** Stillness guard (HOME-DESIGN.md section 7.6): how often dim and blank are checked, and how fast they happen. */
    private static final long GUARD_CHECK_MS = 15_000L, DIM_RAMP_MS = 600_000L, FADE_MS = 4000L;
    /** The glow's colour change: 16 steps over 8 s. Animated per frame it redrew the full-screen gradient ~480 times. */
    private static final int GLOW_STEPS = 16;
    private static final long GLOW_STEP_MS = 500L;
    /** Deep, low-brightness glow colours; the glow is drawn at partial alpha on black. */
    private static final int[] PALETTE = {0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final View content;
    private final ClockRefresh clockRefresh;
    private final ImageView ambient;
    private final GradientDrawable glow;
    private int colorIndex;
    private int glowColor;
    /** clock_wallpaper: a picture replaced the coloured glow. */
    private boolean wallpaperShown;
    /** clock_glow false: plain black behind the clock, whether or not a picture shows. */
    private boolean glowOff;
    /** clock_wallpaper: the picture start() decodes, its darkening, and the resources to draw it with. */
    private File wallpaperFile;
    private int wallpaperShade;
    private Resources wallpaperRes;
    /** One number per decode request: a result whose number is no longer the current one is dropped. Main thread only. */
    private int wallpaperGeneration;
    /** clock_wallpaper with a collection: the pictures, how long each lasts, and how the next one is picked. */
    private List<File> rotation;
    private long rotationMs;
    private long shownMs;
    private String order;
    private Map<String, Set<String>> moods;
    private String latitude, longitude;
    /** clock_aerial: the picture drifts, and its name from wallpapers/captions.txt shows for its first minute. */
    private boolean aerial;
    private Map<String, String> names;
    private TextView caption;
    /** Which still the caption belongs to: it alternates sides with each one (StillGuard.captionAtStart). */
    private int pictureIndex;
    /** Seconds the caption stays (wallpaper_caption_seconds); 0 keeps it until the next move, as before. */
    private int captionSeconds;
    /** screensaver_wallpapers: the stills are credited from their own captions.txt, with or without clock_aerial. */
    private boolean stills;
    /** The guard: a seed and step for the clock's spots, when the scene began, the dim and blank settings, and where it stands. */
    private long seed;
    private int step;
    private long startedAt = -1;
    private long dimAfterMs, blankAfterMs;
    private float dimFloor = 0.35f;
    private float contentAlpha = 1f;
    private boolean blanked;
    private ObjectAnimator dimAnim;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            moveClock();
            shiftGlow();
            if (aerial && wallpaperShown) drift();
            if (caption != null) caption.animate().alpha(0f).setDuration(1200).start();
            advanceWallpaper();
            handler.postDelayed(this, moveMs);
        }
    };

    /** Every GUARD_CHECK_MS: blank once its time is up, otherwise dim by the curve. */
    private final Runnable guard = new Runnable() {
        @Override
        public void run() {
            long elapsed = SystemClock.elapsedRealtime() - startedAt;
            if (StillGuard.blank(elapsed, blankAfterMs)) {
                blank();
                return;
            }
            applyDim(StillGuard.dim(elapsed, dimAfterMs, DIM_RAMP_MS, dimFloor));
            handler.postDelayed(this, GUARD_CHECK_MS);
        }
    };

    /** root is the inflated dream_clock layout. */
    ClockScene(View root) {
        content = root.findViewById(R.id.dreamContent);
        clockRefresh = new ClockRefresh(content);
        ambient = root.findViewById(R.id.dreamAmbient);
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambient.setBackground(glow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius((r - l) * 0.55f));
        colorIndex = random.nextInt(PALETTE.length);
        setGlow(PALETTE[colorIndex]);
    }

    void start() {
        if (blanked) return;   // blank ended the work; a resume must not restart it
        if (startedAt < 0) startedAt = SystemClock.elapsedRealtime();
        seed = random.nextLong();
        step = 0;
        content.setAlpha(0f);
        content.post(() -> {
            if (blanked) return;
            placeClock();
            content.animate().alpha(contentAlpha).setDuration(1200).start();
        });
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, moveMs);
        handler.removeCallbacks(guard);
        if (dimAfterMs > 0 || blankAfterMs > 0) handler.postDelayed(guard, GUARD_CHECK_MS);
        clockRefresh.start();
        if (wallpaperFile != null && !wallpaperShown) loadWallpaper();
    }

    void stop() {
        handler.removeCallbacksAndMessages(null);   // the tick and any glow steps still queued
        clockRefresh.stop();
        content.animate().cancel();
        if (aerial) ambient.animate().cancel();
        if (dimAnim != null) dimAnim.cancel();
        wallpaperGeneration++;   // a decode still running must not attach its picture to a stopped scene
    }

    /**
     * clock_wallpaper: put the picture Home is showing behind the clock, darkened by clock_wallpaper_dim, in
     * place of the glow, and carry a collection's rotation on while the screensaver is up. Decoded off the main
     * thread at screen size in RGB_565 (4 MB at 1080p), like Home's own; until then, or if it cannot be read,
     * the glow stays, unless clock_glow is false.
     * Call before start(), which starts the decode (again after a stop() that dropped an unfinished one).
     */
    void useHomeWallpaper(Context ctx) {
        Config cfg;
        try {
            cfg = Config.load(ctx);
        } catch (RuntimeException e) {
            return;
        }
        // clock_glow false: plain black behind the clock (OLED); the clock itself still moves every minute.
        glowOff = !cfg.clockGlow;
        if (glowOff) ambient.setBackground(null);
        // The guard: spacing of the moves, and the dim and blank times (0 is off; a choice made on the TV is in cfg).
        moveMs = cfg.clockShiftSeconds * 1000L;
        dimAfterMs = cfg.clockDimAfterMinutes * 60_000L;
        blankAfterMs = cfg.clockBlankAfterMinutes * 60_000L;
        dimFloor = cfg.clockDimFloor / 100f;
        captionSeconds = cfg.wallpaperCaptionSeconds;
        // screensaver_wallpapers: the theme's own stills and their credits instead of Home's wallpapers.
        List<File> own = ScreensaverStills.load(ctx);
        if (!own.isEmpty()) {
            rotation = new ArrayList<>(own);
            wallpaperFile = rotation.get(random.nextInt(rotation.size()));   // not always the same first picture
            rotationMs = (cfg.wallpaperRotationMinutes > 0 ? cfg.wallpaperRotationMinutes : 15) * 60_000L;
            order = cfg.wallpaperOrder;
            moods = Collections.emptyMap();
            latitude = "";
            longitude = "";
            aerial = cfg.clockAerial;
            stills = true;
            names = WallpaperCaptions.read(wallpaperFile.getParentFile());
            wallpaperShade = (cfg.clockWallpaperDim * 255 / 100) << 24;
            wallpaperRes = ctx.getResources();
            return;
        }
        if (!cfg.clockWallpaper) return;
        // What Home plays, and Config puts the single wallpaper in it when a theme has no collection.
        // Reading cfg.wallpaper first held the screensaver on wallpaper.jpg while Home rotated.
        if (cfg.wallpapers.isEmpty()) {
            wallpaperFile = null;
        } else {
            // Start on the picture Home is showing. WallpaperPlayer records it under the same
            // preferences and the screensaver runs in the same process, so this is just a read.
            rotation = new ArrayList<>(cfg.wallpapers);
            String shown = ctx.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE)
                    .getString("image", "");
            File current = WallpaperRotation.find(rotation, shown);
            wallpaperFile = current != null ? current : rotation.get(0);
            // Home only rotates while it is visible, so the screensaver carries the rotation on
            // itself; without this it holds one picture for as long as it is up.
            rotationMs = cfg.wallpaperRotationMinutes * 60_000L;
            order = cfg.wallpaperOrder;
            moods = cfg.wallpaperMoods;
            latitude = moods.isEmpty() ? "" : ctx.getString(R.string.latitude);
            longitude = moods.isEmpty() ? "" : ctx.getString(R.string.longitude);
            aerial = cfg.clockAerial;
            if (aerial) names = WallpaperCaptions.read(wallpaperFile.getParentFile());
        }
        wallpaperShade = (cfg.clockWallpaperDim * 255 / 100) << 24;
        wallpaperRes = ctx.getResources();
    }

    private void loadWallpaper() {
        File file = wallpaperFile;
        int shade = wallpaperShade;
        Resources res = wallpaperRes;
        DisplayMetrics dm = res.getDisplayMetrics();
        int w = dm.widthPixels, h = dm.heightPixels;
        int generation = ++wallpaperGeneration;
        new Thread(() -> {
            Bitmap bmp = WallpaperPlayer.decode(file, w, h, Bitmap.Config.RGB_565, false);
            if (bmp == null) return;
            handler.post(() -> {
                if (generation != wallpaperGeneration || wallpaperShown) {
                    bmp.recycle();   // stopped meanwhile (or already shown): this picture was never attached
                    return;
                }
                wallpaperShown = true;
                // As the image, not the background: a background is stretched to the view, so a picture of
                // another shape than the screen would be distorted. centerCrop scales and crops it like Home.
                ambient.setBackground(null);
                ambient.setImageDrawable(new LayerDrawable(new Drawable[]{
                        new BitmapDrawable(res, bmp), new ColorDrawable(shade)}));
                if (aerial) drift();
                if (aerial || stills) nameWallpaper(file);
            });
        }, "clock-wallpaper").start();
    }

    /** clock_aerial: one leg of the drift, as long as a tick, so the next one takes over as this one ends. */
    private void drift() {
        float[] to = pan(random.nextFloat(), random.nextFloat(), random.nextFloat());
        ambient.animate().scaleX(to[0]).scaleY(to[0]).translationX(to[1] * ambient.getWidth())
                .translationY(to[2] * ambient.getHeight()).setDuration(moveMs).start();
    }

    /**
     * Where a leg of the drift ends, from three random numbers between 0 and 1: {scale, x, y}, the shifts as a
     * fraction of the picture's size. Zoomed in by 8 to 16 %, and shifted by no more than that zoom leaves over
     * on each side, so the picture's edge never comes into view - nor on the way there, from any other such end.
     */
    static float[] pan(float zoom, float x, float y) {
        float scale = 1.08f + 0.08f * zoom;
        float room = (scale - 1f) / 2f;
        return new float[]{scale, (2f * x - 1f) * room, (2f * y - 1f) * room};
    }

    /**
     * clock_aerial or screensaver_wallpapers: the picture's name fades in at the bottom, in Home's caption style,
     * on alternating sides picture by picture; the next tick (or wallpaper_caption_seconds) fades it out again.
     * A picture without a line in captions.txt shows none.
     */
    private void nameWallpaper(File file) {
        String name = names.get(file.getName());
        if (name == null || blanked) return;
        if (caption == null) {
            caption = WallpaperPlayer.makeCaption(ambient.getContext(), (FrameLayout) ambient.getParent());
        }
        FrameLayout.LayoutParams corner = (FrameLayout.LayoutParams) caption.getLayoutParams();
        int edge = corner.getMarginEnd();   // as far from the corner as Home's is from the edge
        corner.gravity = Gravity.BOTTOM | (StillGuard.captionAtStart(pictureIndex++) ? Gravity.START : Gravity.END);
        corner.topMargin = 0;
        corner.bottomMargin = edge;
        corner.setMarginStart(edge);
        corner.setMarginEnd(edge);
        caption.setLayoutParams(corner);
        caption.setText(name);
        caption.animate().cancel();
        caption.setAlpha(0f);
        caption.animate().alpha(1f).setDuration(1200).start();
        handler.removeCallbacks(hideCaption);
        if (captionSeconds > 0) handler.postDelayed(hideCaption, captionSeconds * 1000L);
    }

    private final Runnable hideCaption = () -> {
        if (caption != null) caption.animate().alpha(0f).setDuration(1200).start();
    };

    /** Step to the next picture once its turn is up; the tick already runs every minute. */
    private void advanceWallpaper() {
        if (rotation == null || rotationMs <= 0 || rotation.size() < 2) return;
        shownMs += moveMs;
        if (shownMs < rotationMs) return;
        shownMs = 0;
        // The same pick as Home's, so a shuffled collection stays shuffled and wallpaper_moods still
        // decides what may show at this hour.
        File next = WallpaperRotation.pick(WallpaperMood.playable(rotation, moods, latitude, longitude),
                wallpaperFile.getName(), order, random);
        if (next.equals(wallpaperFile)) return;   // a mood group of one
        wallpaperFile = next;
        wallpaperShown = false;
        loadWallpaper();
    }

    private void moveClock() {
        content.animate().alpha(0f).setDuration(900).withLayer().withEndAction(() -> {
            if (blanked) return;
            placeClock();
            content.animate().alpha(contentAlpha).setDuration(1200).withLayer().start();
        }).start();
    }

    /** A random spot that keeps the whole clock on screen, with the glow centred behind it. */
    private void placeClock() {
        View parent = (View) content.getParent();
        int maxX = Math.max(0, parent.getWidth() - content.getWidth());
        int maxY = Math.max(0, parent.getHeight() - content.getHeight());
        float[] at = StillGuard.spot(step++, seed);   // a shuffled 4x3 grid: no region holds the clock twice in a row
        float x = maxX * at[0];
        float y = maxY * at[1];
        content.setX(x);
        content.setY(y);
        if (parent.getWidth() > 0 && parent.getHeight() > 0) {
            glow.setGradientCenter((x + content.getWidth() / 2f) / parent.getWidth(),
                    (y + content.getHeight() / 2f) / parent.getHeight());
        }
    }

    /** The dim curve's factor f: the clock fades in to f from now on, and the picture slides to half-way at the floor. */
    private void applyDim(float f) {
        contentAlpha = f;
        float picture = StillGuard.pictureAlpha(f, dimFloor);
        if (Math.abs(ambient.getAlpha() - picture) < 0.005f) return;
        if (dimAnim != null) dimAnim.cancel();
        dimAnim = ObjectAnimator.ofFloat(ambient, View.ALPHA, ambient.getAlpha(), picture);
        dimAnim.setDuration(FADE_MS);
        dimAnim.start();
    }

    /** Fade everything to black, then stop all work. The window stays black; ClockActivity's keys still finish it. */
    private void blank() {
        blanked = true;
        handler.removeCallbacks(tick);
        handler.removeCallbacks(guard);
        if (dimAnim != null) dimAnim.cancel();
        content.animate().cancel();
        content.animate().alpha(0f).setDuration(FADE_MS).start();
        ambient.animate().cancel();
        ambient.animate().alpha(0f).setDuration(FADE_MS).start();
        if (caption != null) {
            caption.animate().cancel();
            caption.animate().alpha(0f).setDuration(FADE_MS).start();
        }
        handler.postDelayed(() -> {
            handler.removeCallbacksAndMessages(null);
            clockRefresh.stop();
            wallpaperGeneration++;
        }, FADE_MS);
    }

    private void shiftGlow() {
        if (wallpaperShown || glowOff) return;
        int from = glowColor;
        colorIndex = (colorIndex + 1) % PALETTE.length;
        int to = PALETTE[colorIndex];
        ArgbEvaluator mix = new ArgbEvaluator();
        for (int i = 1; i <= GLOW_STEPS; i++) {
            float f = (float) i / GLOW_STEPS;
            handler.postDelayed(() -> setGlow((int) mix.evaluate(f, from, to)), i * GLOW_STEP_MS);
        }
    }

    private void setGlow(int color) {
        glowColor = color;
        int c = color & 0x00FFFFFF;
        glow.setColors(new int[]{0x40000000 | c, 0x14000000 | c, 0x00000000 | c});
    }
}
