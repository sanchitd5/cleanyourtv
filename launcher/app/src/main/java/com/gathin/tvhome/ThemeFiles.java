package com.gathin.tvhome;

import java.util.List;
import java.util.regex.Pattern;

/** The files ThemeProvider may write or delete under Config.dir, by the URI's path segments. */
final class ThemeFiles {
    static final String WALLPAPERS = "wallpapers";
    /** input_logos: a theme's logo files for HDMI devices live in inputs/, named as a collection image is. */
    static final String INPUTS = "inputs";
    /** wallpaper_feeds: written by the wallpaper-feeds addon, never by a theme push, so a theme change keeps it. */
    static final String FEEDS = "feeds";
    /** The names tools/theme.sh accepts for a collection image, and the types WallpaperRotation reads. */
    private static final Pattern IMAGE = Pattern.compile("[A-Za-z0-9_.-]+\\.(jpg|jpeg|png|webp)",
            Pattern.CASE_INSENSITIVE);

    private ThemeFiles() {}

    /** The path relative to the theme directory, e.g. "config.json" or "wallpapers/dusk.jpg", or null if refused. */
    static String path(List<String> segments) {
        if (segments == null) return null;
        if (segments.size() == 1) {
            String name = segments.get(0);
            return Config.FILE_NAME.equals(name) || "wallpaper.jpg".equals(name) || "theme_revision".equals(name)
                    ? name : null;
        }
        if (segments.size() == 2 && WALLPAPERS.equals(segments.get(0))
                && (collectionImage(segments.get(1)) || clip(segments.get(1))
                || WallpaperCaptions.FILE_NAME.equals(segments.get(1)))) {
            return WALLPAPERS + "/" + segments.get(1);
        }
        if (segments.size() == 2 && INPUTS.equals(segments.get(0)) && collectionImage(segments.get(1))) {
            return INPUTS + "/" + segments.get(1);
        }
        // wallpaper_feeds: the wallpaper-feeds addon's downloads (stills, clips and their captions.txt).
        if (segments.size() == 2 && FEEDS.equals(segments.get(0))
                && (collectionImage(segments.get(1)) || clip(segments.get(1))
                || WallpaperCaptions.FILE_NAME.equals(segments.get(1)))) {
            return FEEDS + "/" + segments.get(1);
        }
        // h-guard: screensaver_wallpapers' folder (stills and their captions.txt).
        if (segments.size() == 2 && ScreensaverStills.FOLDER.equals(segments.get(0))
                && (collectionImage(segments.get(1)) || WallpaperCaptions.FILE_NAME.equals(segments.get(1)))) {
            return segments.get(0) + "/" + segments.get(1);
        }
        return null;
    }

    /** True for "wallpapers" alone: a delete of it clears the collection's images. */
    static boolean isCollection(List<String> segments) {
        return segments != null && segments.size() == 1 && WALLPAPERS.equals(segments.get(0));
    }

    /**
     * wallpaper_video: a clip is named as a picture is, with .mp4 where the picture has its suffix. It is not a
     * collection image: clearing the collection leaves the clips, which tools/theme.sh replaces one by one, and
     * only the ones that changed.
     */
    static boolean clip(String name) {
        int end = name == null ? 0 : name.length() - 4;
        return end > 0 && name.regionMatches(true, end, ".mp4", 0, 4)
                && collectionImage(name.substring(0, end) + ".jpg");
    }

    static boolean collectionImage(String name) {
        return name != null && !name.startsWith(".") && IMAGE.matcher(name).matches();
    }
}
