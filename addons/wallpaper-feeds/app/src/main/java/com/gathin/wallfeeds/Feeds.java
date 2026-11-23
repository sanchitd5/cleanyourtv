package com.gathin.wallfeeds;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The feeds, read and planned without Android: what each source offers, what the TV can play, and how much of
 * its storage the clips may take. Fetcher does the network and the files.
 */
final class Feeds {
    static final long MB = 1024L * 1024;
    static final long GB = 1024 * MB;

    /** The launcher's caption limit (WallpaperCaptions.MAX_LENGTH). */
    static final int CAPTION_MAX = 80;
    /** A file name the launcher's ThemeFiles accepts. */
    private static final String SAFE = "[^A-Za-z0-9_.-]";

    static final String Q_4K = "url-4K-SDR";
    static final String Q_1080_HEVC = "url-1080-SDR";
    static final String Q_1080_H264 = "url-1080-H264";

    /** Clips: plenty of variety, and a ceiling however big the disk. */
    static final int MAX_CLIPS = 30;
    static final int MAX_STILLS = 30;
    /** New clips per run: a refresh should not take the household's bandwidth for an evening. */
    static final int CLIPS_PER_RUN = 2;
    /** A full shelf still swaps one clip for a new one when its oldest is this old. */
    static final long ROTATE_AFTER_MS = 7L * 24 * 60 * 60 * 1000;
    /** Never more than this for clips, whatever is free. */
    static final long MAX_BUDGET = 6 * GB;

    private Feeds() {}

    /** One picture or clip a feed offers. */
    static final class Item {
        final String name, url, caption;
        final boolean clip;
        /** An aerial's category key (see {@link #CATEGORIES}), "" for pictures and uncategorised clips. */
        final String category;

        Item(String name, String url, String caption, boolean clip, String category) {
            this.name = name;
            this.url = url;
            this.caption = caption;
            this.clip = clip;
            this.category = category;
        }
    }

    // ---- options ----

    /** Aerial categories: key, label, and the first eight characters of Apple's category id in entries.json. */
    static final String[][] CATEGORIES = {
        {"space", "Space", "55B7C95D"},
        {"landscapes", "Landscapes", "A33A55D9"},
        {"cities", "Cities", "5EF41171"},
        {"underwater", "Underwater", "8BE8B524"},
    };
    /** Clips to keep: 0 is Auto (what the storage budget allows). Pictures to keep. Refresh hours: 0 is manual. */
    static final int[] KEEP_CLIPS = {0, 4, 8, 16, 30};
    static final int[] KEEP_STILLS = {8, 15, 30};
    static final int[] REFRESH_HOURS = {6, 12, 24, 0};
    static final String PREF_AUTO = "auto", PREF_1080 = "1080", PREF_4K = "4k";

    /** The category key of an aerial's {@code categories} ids, or "" when it has none of the four. */
    static String categoryOf(JSONArray ids) {
        if (ids == null) return "";
        for (int i = 0; i < ids.length(); i++) {
            String id = ids.optString(i, "").toUpperCase(java.util.Locale.ROOT);
            for (String[] c : CATEGORIES) if (id.startsWith(c[2])) return c[0];
        }
        return "";
    }

    /** Whether a clip of {@code category} may be fetched or kept; one in none of the four needs them all on. */
    static boolean categoryOn(String category, Set<String> enabled) {
        return category.isEmpty() ? enabled.size() >= CATEGORIES.length : enabled.contains(category);
    }

    /** The offered clips whose category is enabled; pictures pass. */
    static List<Item> inCategories(List<Item> items, Set<String> enabled) {
        List<Item> out = new ArrayList<>();
        for (Item i : items) if (!i.clip || categoryOn(i.category, enabled)) out.add(i);
        return out;
    }

    /** The clips to keep: the chosen number, or what the budget allows when Auto (0), never above the budget. */
    static int clipTarget(long budget, long clipBytes, int keep) {
        int fit = clipTarget(budget, clipBytes);
        return keep > 0 ? Math.min(fit, keep) : fit;
    }

    /** The pictures to keep: one of {@link #KEEP_STILLS}, else the cap. */
    static int stillKeep(int pref) {
        for (int k : KEEP_STILLS) if (k == pref) return k;
        return MAX_STILLS;
    }

    /** The refresh period in ms, 0 for manual only; anything unknown is twelve hours. */
    static long periodMs(int hours) {
        for (int h : REFRESH_HOURS) if (h == hours) return h * 60L * 60 * 1000;
        return 12L * 60 * 60 * 1000;
    }

    // ---- storage ----

    /** What the TV keeps free for itself and its apps: 4 GB, or 15% of the volume when that is more. */
    static long reserve(long total) {
        return Math.max(4 * GB, total * 15 / 100);
    }

    /**
     * The bytes clips may take: a quarter of what is spare above the reserve, counting what the clips already
     * hold as spare (so the shelf does not shrink just because it is full), capped at {@link #MAX_BUDGET}.
     */
    static long budget(long total, long free, long used) {
        long spare = free + used - reserve(total);
        return spare <= 0 ? 0 : Math.min(MAX_BUDGET, spare / 4);
    }

    /** How many clips of about {@code clipBytes} fit the budget, up to {@link #MAX_CLIPS}. */
    static int clipTarget(long budget, long clipBytes) {
        if (clipBytes <= 0) return 0;
        return (int) Math.min(MAX_CLIPS, budget / clipBytes);
    }

    /** A download fits when, with its temporary copy and the launcher's, the reserve is still free after it. */
    static boolean fits(long total, long free, long bytes) {
        return free - 2 * bytes >= reserve(total);
    }

    // ---- quality ----

    /** Typical sizes of one aerial, to plan before a HEAD request says. */
    static long typicalBytes(String quality) {
        switch (quality) {
            case Q_4K: return 350 * MB;
            case Q_1080_HEVC: return 180 * MB;
            default: return 300 * MB;
        }
    }

    /**
     * The aerial file to fetch. 4K only on a 4K panel with a 4K HEVC decoder, 3 GB of memory (the launcher
     * decodes it behind Home) and room for at least eight 4K clips; else 1080p HEVC, the smallest, wherever
     * HEVC decodes; else 1080p H.264, which every TV plays.
     */
    static String quality(boolean hevc4k, boolean hevc1080, int displayWidth, long ramBytes, long budget) {
        if (hevc4k && displayWidth >= 3840 && ramBytes >= 3 * GB && budget >= 8 * typicalBytes(Q_4K)) return Q_4K;
        return hevc1080 ? Q_1080_HEVC : Q_1080_H264;
    }

    /**
     * The chosen quality: "4k" only where a 4K HEVC decoder exists (the panel and memory checks are the person's
     * call then), "1080" HEVC or H.264, anything else the automatic choice.
     */
    static String quality(String pref, boolean hevc4k, boolean hevc1080, int displayWidth, long ramBytes, long budget) {
        if (PREF_4K.equals(pref) && hevc4k) return Q_4K;
        if (PREF_1080.equals(pref)) return hevc1080 ? Q_1080_HEVC : Q_1080_H264;
        return quality(hevc4k, hevc1080, displayWidth, ramBytes, budget);
    }

    /** The quality and the ones below it, for an asset that lacks the preferred file. */
    static String[] fallbacks(String quality) {
        switch (quality) {
            case Q_4K: return new String[] {Q_4K, Q_1080_HEVC, Q_1080_H264};
            case Q_1080_HEVC: return new String[] {Q_1080_HEVC, Q_1080_H264};
            default: return new String[] {Q_1080_H264};
        }
    }

    // ---- sources ----

    /** Bing's HPImageArchive JSON: one 1920x1080 picture a day. */
    static List<Item> bing(String json) throws Exception {
        List<Item> out = new ArrayList<>();
        JSONArray images = new JSONObject(json).optJSONArray("images");
        if (images == null) return out;
        for (int i = 0; i < images.length(); i++) {
            JSONObject o = images.getJSONObject(i);
            String day = o.optString("startdate").replaceAll("[^0-9]", "");
            String url = o.optString("url");
            if (day.length() != 8 || !url.startsWith("/th?")) continue;
            String copyright = o.optString("copyright").trim();
            int open = copyright.lastIndexOf(" (©");
            String place = open > 0 ? copyright.substring(0, open) : copyright;
            String credit = open > 0 && copyright.endsWith(")")
                    ? copyright.substring(open + 2, copyright.length() - 1) : "";
            out.add(new Item("bing-" + day + ".jpg", "https://www.bing.com" + url, caption(place, credit), false, ""));
        }
        return out;
    }

    /**
     * Wikimedia's featured-content feed for one day: its picture of the day, when it is a landscape wide enough
     * for the screen (a portrait picture would be cropped to a strip). The 1920 px thumbnail, not the original.
     */
    static Item wikimedia(String json, String day) throws Exception {
        JSONObject image = new JSONObject(json).optJSONObject("image");
        if (image == null) return null;
        JSONObject original = image.optJSONObject("image");
        JSONObject thumb = image.optJSONObject("thumbnail");
        if (original == null || thumb == null) return null;
        int w = original.optInt("width"), h = original.optInt("height");
        if (w < 1920 || h <= 0 || w * 2 < h * 3) return null;
        String src = thumb.optString("source");
        int q = src.indexOf('?');
        if (q > 0) src = src.substring(0, q);
        String sized = src.replaceFirst("/\\d+px-([^/]+)$", "/1920px-$1");
        if (sized.equals(src) || !sized.startsWith("https://")) return null;
        String title = image.optString("title").replaceFirst("^File:", "").replaceFirst("\\.[A-Za-z0-9]+$", "")
                .replace('_', ' ').trim();
        JSONObject artist = image.optJSONObject("artist");
        JSONObject license = image.optJSONObject("license");
        String who = artist == null ? "" : artist.optString("text").replaceAll("\\s+", " ").trim();
        String lic = license == null ? "" : license.optString("type").trim();
        String credit = who.isEmpty() ? lic : lic.isEmpty() ? who : who + ", " + lic;
        return new Item("wm-" + day.replaceAll("[^0-9]", "") + ".jpg", sized, caption(title, credit), false, "");
    }

    /** Apple's aerial manifest (entries.json): every clip, at the first quality in {@code order} it has. */
    static List<Item> aerials(String json, String[] order) throws Exception {
        List<Item> out = new ArrayList<>();
        JSONArray assets = new JSONObject(json).optJSONArray("assets");
        if (assets == null) return out;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.getJSONObject(i);
            String url = null;
            for (String key : order) {
                String u = a.optString(key, "");
                if (u.startsWith("https://")) { url = u; break; }
            }
            String id = a.optString("shotID", a.optString("id", "")).replaceAll(SAFE, "");
            if (url == null || id.isEmpty()) continue;
            // A QuickTime .mov is the MPEG-4 family Android's extractor reads; the launcher takes .mp4 names.
            out.add(new Item("aerial-" + id + ".mp4", url, caption(a.optString("accessibilityLabel").trim(), ""), true,
                    categoryOf(a.optJSONArray("categories"))));
        }
        return out;
    }

    /** The first file named {@code name} in a tar stream (Apple ships the manifest inside resources-N.tar). */
    static byte[] tarEntry(InputStream in, String name, long maxBytes) throws IOException {
        byte[] header = new byte[512];
        while (readFully(in, header)) {
            if (header[0] == 0) return null;   // the end-of-archive block
            String entry = cString(header, 0, 100);
            String octal = cString(header, 124, 12).trim();
            long size = octal.isEmpty() ? 0 : Long.parseLong(octal, 8);
            long padded = (size + 511) / 512 * 512;
            if (name.equals(entry) || ("./" + name).equals(entry)) {
                if (size > maxBytes) throw new IOException(name + " is " + size + " bytes");
                byte[] data = new byte[(int) size];
                if (!readFully(in, data)) throw new IOException("tar cut short");
                return data;
            }
            long skip = padded;
            while (skip > 0) {
                long n = in.skip(skip);
                if (n <= 0) { if (in.read() < 0) return null; n = 1; }
                skip -= n;
            }
        }
        return null;
    }

    // ---- captions ----

    /** "name · credit" when both fit the launcher's caption, else the name cut to it. */
    static String caption(String name, String credit) {
        name = name == null ? "" : name.trim();
        credit = credit == null ? "" : credit.trim();
        String both = name.isEmpty() ? credit : credit.isEmpty() ? name : name + " · " + credit;
        if (both.length() <= CAPTION_MAX) return both;
        return name.length() <= CAPTION_MAX ? name : name.substring(0, CAPTION_MAX - 1).trim() + "…";
    }

    /** captions.txt as the launcher reads it: "file<TAB>name", one per line, in name order. */
    static String captionsFile(Map<String, String> captions) {
        StringBuilder sb = new StringBuilder("# Written by Wallpaper feeds.\n");
        for (Map.Entry<String, String> e : new TreeMap<>(captions).entrySet()) {
            String text = e.getValue() == null ? "" : e.getValue().replaceAll("[\\t\\r\\n]+", " ").trim();
            if (!text.isEmpty()) sb.append(e.getKey()).append('\t').append(text).append('\n');
        }
        return sb.toString();
    }

    // ---- helpers ----

    static String text(InputStream in, long maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (out.size() + n > maxBytes) throw new IOException("answer over " + maxBytes + " bytes");
            out.write(buf, 0, n);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static boolean readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) return false;
            off += n;
        }
        return true;
    }

    private static String cString(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && b[end] != 0) end++;
        return new String(b, off, end - off, StandardCharsets.US_ASCII);
    }
}
