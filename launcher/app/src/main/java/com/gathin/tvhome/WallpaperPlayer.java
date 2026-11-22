package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.media.MediaPlayer;
import android.os.PersistableBundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Displays and rotates a theme's local wallpaper images while Home is visible, and its clips over them
 * (wallpaper_video): muted, looped, on the hardware decoder, and only while Home is in front.
 */
final class WallpaperPlayer implements MediaPlayer.OnInfoListener, MediaPlayer.OnErrorListener, Runnable {
    private final FrameLayout root;
    private final ImageView image;
    private final int backgroundColor;
    private final List<File> files;
    private final long interval;
    private final String order;
    private final SharedPreferences preferences;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private final int width;
    /** config wallpaper_true_color: ARGB_8888 instead of RGB_565. */
    private final Bitmap.Config bitmapConfig;
    private final int height;
    /** wallpaper_dim as an ARGB black, painted into each picture once instead of a full-screen shade view. */
    private final int dimColor;
    /** The picture on screen, released once the next one has been drawn. */
    private Bitmap current;
    private boolean active;
    private boolean closed;
    private boolean loading;
    private String displayed = "";
    /** wallpaper_captions: the shown picture's name from wallpapers/captions.txt; null when off or there are none. */
    private final TextView caption;
    private final Map<String, String> names;
    /** wallpaper_moods: the collection's groups by time of day; empty rotates through all of it. */
    private final Map<String, Set<String>> moods;
    /** config.xml latitude and longitude, for the sun's times. */
    private final String latitude, longitude;
    /** wallpaper_video: the collection's clips; empty when off. */
    private final List<File> videos;
    private final long videoInterval;
    /** The clips' layer, under the picture; gone while Home is not on screen and after a clip failed. */
    private VideoView video;
    /** A clip is on screen: the caption names it, not the picture that faded away over it. */
    private boolean videoShown;
    /**
     * The clip the view has, to move on from, and since when; 0 when the next play() has to give it another.
     * Not kept across a restart of Home, which starts the collection again.
     */
    private String playing = "";
    private long since;
    private static boolean logged;
    /** wallpaper_caption_seconds: the caption fades out this long after it appears; 0 keeps it for as long as its picture shows. */
    private final long captionMs;
    private final Runnable fadeCaption = new Runnable() {
        @Override public void run() {
            if (caption != null) caption.animate().alpha(0f).setDuration(1200).start();
        }
    };

    WallpaperPlayer(Context context, FrameLayout root, Config config) {
        this.root = root;
        backgroundColor = config.backgroundColor;
        files = new ArrayList<>(config.wallpapers);
        interval = config.wallpaperRotationMinutes * 60_000L;
        bitmapConfig = config.wallpaperTrueColor ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
        // The dim is painted into the picture (decode), so no full-screen shade layer is drawn on every frame.
        dimColor = config.wallpaperDim > 0 ? (config.wallpaperDim * 255 / 100) << 24 : 0;
        videos = config.wallpaperVideos;
        captionMs = config.wallpaperCaptionSeconds * 1000L;
        videoInterval = config.wallpaperVideoMinutes * 60_000L;
        order = config.wallpaperOrder;
        preferences = context.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE);
        moods = config.wallpaperMoods;
        latitude = moods.isEmpty() ? "" : context.getString(R.string.latitude);
        longitude = moods.isEmpty() ? "" : context.getString(R.string.longitude);
        // Playback state belongs to one collection. Changing the collection starts from its first
        // picture instead of resuming a filename that belonged to the previous one.
        String collection = collectionKey(files);
        if (!collection.equals(preferences.getString("collection", ""))) {
            preferences.edit().putString("collection", collection)
                    .remove("image").remove("changed").apply();
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        List<File> named = files.isEmpty() ? videos : files;
        names = config.wallpaperCaptions && config.wallpaperEnabled && !named.isEmpty()
                ? WallpaperCaptions.read(named.get(0).getParentFile()) : Collections.emptyMap();
        caption = names.isEmpty() ? null : makeCaption(context, root);
        if (config.wallpaperEnabled && !files.isEmpty()) {
            root.addView(image, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            showFirst();
        }
        if (videos.isEmpty()) return;
        // A VideoView is a SurfaceView: the clip is its own layer behind Home's window, so Home is not drawn
        // again for every frame of it. The picture lies over it and fades away once the clip has a frame.
        video = new VideoView(context);
        video.setFocusable(false);
        // 0 is AudioManager.AUDIOFOCUS_NONE: a silent clip must not stop the music another app is playing.
        video.setAudioFocusRequest(0);
        video.setOnInfoListener(this);
        video.setOnErrorListener(this);
        root.addView(video, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /**
     * The picture Home opens with is decoded right here, on the main thread, as the single wallpaper always was:
     * Home's first frame already has it instead of drawing the plain colour and the picture popping in later.
     * Only the later changes of a rotation decode in the background.
     */
    private void showFirst() {
        String previous = preferences.getString("image", "");
        List<File> playable = playable();
        File first = WallpaperRotation.find(playable, previous);
        if (first == null) first = WallpaperRotation.pick(playable, previous, order, random);
        while (first != null) {
            Bitmap bitmap = decode(first);
            if (bitmap != null) {
                show(bitmap);
                // The picture covers the whole screen: the root's plain colour under it is overdraw.
                root.setBackground(null);
                displayed = first.getName();
                showCaption(false);
                if (!displayed.equals(previous)) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
                return;
            }
            files.remove(first);   // unreadable or over 4K: try another, or keep the plain colour
            playable = playable();
            first = WallpaperRotation.pick(playable, previous, order, random);
        }
    }

    void start() {
        active = true;
        update();
        play();
        // Back on Home: the current caption shows again for its seconds.
        if (captionMs > 0) showCaption(false);
    }

    /** The caption view, or null when wallpaper_captions is off or there are no names. */
    TextView caption() {
        return caption;
    }

    /** The docked caption (top_shelf): 13 sp, on the shelf's meta line, at the strength the scrim is made for. */
    void dockCaption() {
        if (caption == null) return;
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        caption.setTextColor(0xE6FFFFFF);
    }

    /** Home is paused: the clip stops on its frame, and goes on from there if Home was only covered by a panel. */
    void stop() {
        active = false;
        if (caption != null && captionMs > 0) {
            caption.removeCallbacks(fadeCaption);
            caption.animate().cancel();
        }
        if (video != null) video.pause();
    }

    /**
     * Home is no longer on screen (onStop): the player and its decoder go, so nothing decodes behind an app, an
     * HDMI input or the screensaver. Gone, the view has no surface either; play() brings it back, and the view
     * then opens its clip again, from the start, under the picture.
     */
    void release() {
        if (video == null) return;
        video.suspend();
        video.setVisibility(View.GONE);
        image.animate().cancel();
        image.setAlpha(1f);
        if (!videoShown) return;
        videoShown = false;
        showCaption(true);
    }

    /**
     * Starts a clip, or goes on with the paused one, while Home is in front. A clip stays for
     * wallpaper_video_rotation_minutes of the clock from when it was opened, not of Home being open, so a Home
     * that is looked at a minute at a time still gets through the collection. After a clip that failed, nothing
     * is tried until Home next comes forward, and then another one is.
     */
    private void play() {
        if (video == null || !active || closed) return;
        try {
            long now = System.currentTimeMillis();
            // Another clip: the first one, the one after a clip that failed or that update() faded out, or Home is
            // back after longer than a clip stays.
            if (since == 0 || !videoShown && WallpaperRotation.due(since, now, videoInterval)) {
                File clip = WallpaperRotation.pick(videos, playing, order, random);
                playing = clip.getName();
                since = now;
                video.setVideoPath(clip.getPath());
            }
            video.setVisibility(View.VISIBLE);
            video.start();
        } catch (Exception failure) {
            fail(failure.toString());
        }
    }

    /** No clip: the picture stays and nothing on screen says so; logcat has the first reason only. */
    private void fail(String why) {
        if (!logged) Log.w("TvHome.Wallpaper", "video: " + why);
        logged = true;
        since = 0;
        release();
    }

    /** The picture is back over the clip that is leaving (update): open the next one under it. */
    @Override
    public void run() {
        since = 0;
        play();
    }

    /**
     * 3 is MEDIA_INFO_VIDEO_RENDERING_START: the clip's first frame is on its surface, and its decoder has a name.
     * The picture is still over the clip here, so this is also early enough to set the clip up - every time,
     * because the view makes a new player whenever its surface comes back.
     */
    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        if (what != 3) return false;
        mp.setLooping(true);
        // Silent whatever the file holds; a clip is meant to have no sound track at all.
        mp.setVolume(0f, 0f);
        // VideoView asks for the screen to stay on while a clip plays; Home must never hold it beyond the system timeout.
        mp.setScreenOnWhilePlaying(false);
        video.setKeepScreenOn(false);
        if (videoShown) return false;
        PersistableBundle stats = mp.getMetrics();
        String codec = stats == null ? null : stats.getString("android.media.mediaplayer.video.codec");
        if (WallpaperRotation.softwareCodec(codec)) {
            fail(codec);
            return false;
        }
        float[] scale = WallpaperSize.cropScale(mp.getVideoWidth(), mp.getVideoHeight(), width, height);
        video.setScaleX(scale[0]);
        video.setScaleY(scale[1]);
        videoShown = true;
        showCaption(true);
        image.animate().alpha(0f).setDuration(800).start();
        return false;
    }

    /** Handled here, so the view shows no "Can't play this video" dialog over Home. */
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        fail(what + " " + extra);
        return true;
    }

    /** Drops the wallpaper texture when Home is hidden and Android asks the process to free memory. */
    void releaseForMemoryPressure() {
        if (current == null) return;
        image.setImageDrawable(null);
        // The renderer may still have a queued frame that references this hardware bitmap. Drop our references and
        // let Android reclaim its native buffer after rendering releases it instead of recycling it mid-frame.
        current = null;
        displayed = "";
        root.setBackgroundColor(backgroundColor);
    }

    /** The pictures the rotation picks from now: wallpaper_moods' group for the time of day, or the whole collection. */
    private List<File> playable() {
        return WallpaperMood.playable(files, moods, latitude, longitude);
    }

    void close() {
        active = false;
        closed = true;
        if (caption != null) caption.removeCallbacks(fadeCaption);
        release();
        decoder.shutdownNow();
    }

    void update() {
        if (!active || closed) return;
        // The next clip: the picture fades back in over this one, and out again once the next has a frame.
        if (videoShown && videos.size() > 1
                && WallpaperRotation.due(since, System.currentTimeMillis(), videoInterval)) {
            videoShown = false;
            image.animate().alpha(1f).setDuration(800).withEndAction(this).start();
        }
        if (loading || files.isEmpty()) return;
        String previous = preferences.getString("image", "");
        // A picture outside the time of day's group (the group changed) is replaced at once, not after the interval.
        List<File> playable = playable();
        File selected = WallpaperRotation.find(playable, previous);
        long changed = preferences.getLong("changed", 0);
        if (selected == null || WallpaperRotation.due(changed, System.currentTimeMillis(), interval)) {
            selected = WallpaperRotation.pick(playable, previous, order, random);
        }
        if (selected == null || selected.getName().equals(displayed)) return;
        File next = selected;
        loading = true;
        decoder.execute(() -> {
            Bitmap bitmap = decode(next);
            root.post(() -> {
                loading = false;
                if (closed || !active) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                if (bitmap == null) {
                    files.remove(next);
                    Log.w("TvHome.Wallpaper", "Skipping unreadable image: " + next.getName());
                    update();
                    return;
                }
                show(bitmap);
                displayed = next.getName();
                showCaption(true);
                if (!displayed.equals(previous) || changed == 0) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
            });
        });
    }

    /**
     * Small light text under the top-right buttons, above everything else and never focusable. It sits a gap
     * below the buttons' row rather than flush against it; the offset is fixed, so a taller top bar (font scale,
     * an extra date line) eats into that gap — and it has to stay clear of the clock block and of the tiles.
     */
    static TextView makeCaption(Context context, FrameLayout root) {
        float dp = context.getResources().getDisplayMetrics().density;
        TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        text.setTextColor(0xD9FFFFFF);
        text.setShadowLayer(6 * dp, 0, dp, 0xCC000000);
        // Room for the shadow (6 dp of blur, 1 dp down) inside the view: Home's root, and the layer a fade draws
        // through, cut everything at the view's edges, which left a hard-edged dark box around the text. The
        // margins below take the padding back, so the text stays where it was.
        int pad = Math.round(8 * dp);
        text.setPadding(pad, pad, pad, pad);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setMaxWidth(Math.round(520 * dp) + 2 * pad);
        text.setFocusable(false);
        text.setAlpha(0f);
        try {
            text.setTypeface(UiFont.get(context, R.string.font_regular));
        } catch (Exception ignored) {
            // the system font is fine
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        params.topMargin = Math.round(137 * dp) - pad;
        params.setMarginEnd(Math.round(62 * dp) - pad);
        root.addView(text, params);
        return text;
    }

    /** The displayed picture's name, faded across a change; a picture without a name hides the caption. */
    private void showCaption(boolean fade) {
        if (caption == null) return;
        caption.removeCallbacks(fadeCaption);
        showCaptionNow(fade);
        if (captionMs > 0 && caption.getContentDescription() != null) caption.postDelayed(fadeCaption, captionMs + (fade ? 600 : 0));
    }

    private void showCaptionNow(boolean fade) {
        String name = names.get(videoShown ? playing : displayed);
        caption.animate().cancel();
        if (name == null) {
            caption.setAlpha(0f);
            return;
        }
        caption.setContentDescription(name);
        if (!fade || caption.getAlpha() == 0f) {
            caption.setText(name);
            caption.setAlpha(1f);
            return;
        }
        caption.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            caption.setText(name);
            caption.animate().alpha(1f).setDuration(350).start();
        }).start();
    }

    /** Names plus sizes: enough to notice a different collection without hashing every image. */
    private static String collectionKey(List<File> images) {
        StringBuilder key = new StringBuilder();
        for (File file : images) key.append(file.getName()).append(':').append(file.length()).append(';');
        return key.toString();
    }

    /** Puts a picture on screen and releases the previous one after a frame has drawn the new one. */
    private void show(Bitmap bitmap) {
        Bitmap old = current;
        current = bitmap;
        // The wallpaper covers the whole root; avoid drawing the fallback colour under it.
        root.setBackground(null);
        image.setImageBitmap(bitmap);
        if (old != null && old != bitmap) image.postDelayed(old::recycle, 500);
    }

    /**
     * A picture sampled and scaled down to cover the screen, never bigger than 4K; null when it cannot be read.
     * Home's and the clock screensaver's.
     */
    static Bitmap decode(File file, int screenW, int screenH, Bitmap.Config config, boolean mutable) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int width = options.outWidth, height = options.outHeight;
            if (width <= 0 || height <= 0) return null;
            int sample = WallpaperSize.sampleSize(width, height, screenW, screenH);
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(width / sample, height / sample, screenW, screenH);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = config;
            options.inMutable = mutable;
            return BitmapFactory.decodeFile(file.getPath(), options);
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }

    private Bitmap decode(File file) {
        Bitmap decoded = decode(file, width, height, bitmapConfig, dimColor != 0);
        if (decoded == null) return null;
        try {
            if (dimColor != 0) new Canvas(decoded).drawColor(dimColor);
            // One copy in GPU memory instead of a software bitmap plus its texture; the decode stays off the UI
            // thread for rotations, and Home's return no longer uploads 4-8 MB to the GPU.
            Bitmap gpu = decoded.copy(Bitmap.Config.HARDWARE, false);
            if (gpu == null) return decoded;
            decoded.recycle();
            return gpu;
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }
}
