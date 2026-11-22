package com.gathin.tvhome;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * screensaver_wallpapers (HOME-DESIGN.md section 18): a theme folder of stills, with its own captions.txt (the
 * photographer's credit line), that the clock screensaver shows instead of Home's wallpapers. Read from config.json
 * here so the folder does not have to go through Config's constructor; no network, the files are only read.
 */
final class ScreensaverStills {
    /** The one folder ThemeProvider and theme.sh handle; the key names it so a theme says out loud that it has one. */
    static final String FOLDER = "screensaver";

    private ScreensaverStills() { }

    /** {@link #FOLDER} when the key names it, else "": no other folder is pushed, so no other is read. */
    static String folder(String raw) {
        return raw != null && FOLDER.equals(raw.trim()) ? FOLDER : "";
    }

    /** The folder's pictures in name order, or none: no folder, a missing one, or one without images. */
    static List<File> images(File themeDir, String folder) {
        String name = folder(folder);
        if (themeDir == null || name.isEmpty()) return Collections.emptyList();
        return WallpaperRotation.images(new File(themeDir, name));
    }

    /** The credit line for a picture, or null when captions.txt has none for it. */
    static String credit(Map<String, String> captions, File picture) {
        return captions == null || picture == null ? null : captions.get(picture.getName());
    }

    /** The folder's stills for the theme in {@code ctx}, or none. */
    static List<File> load(Context ctx) {
        File dir = Config.dir(ctx);
        if (dir == null) return Collections.emptyList();
        String name = folder(keyOf(new File(dir, Config.FILE_NAME)));
        return images(dir, name);
    }

    private static String keyOf(File config) {
        if (!config.isFile() || config.length() > 64 * 1024) return "";
        try {
            JSONObject json = new JSONObject(new String(Files.readAllBytes(config.toPath()), StandardCharsets.UTF_8));
            Object v = json.opt("screensaver_wallpapers");
            return v instanceof String ? (String) v : "";
        } catch (Exception e) {
            return "";
        }
    }
}
