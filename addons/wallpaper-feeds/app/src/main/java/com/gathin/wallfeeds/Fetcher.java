package com.gathin.wallfeeds;

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.hardware.display.DisplayManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.util.Log;
import android.view.Display;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One refresh: fetch what the enabled feeds offer, write new files into the launcher's feeds/ folder through its
 * ThemeProvider, drop what is over the storage budget, and rewrite captions.txt. Run from RefreshJob only.
 */
final class Fetcher {
    private static final String TAG = "WallFeeds";
    static final String PREFS = "feeds";
    static final String KEY_AERIALS = "aerials", KEY_BING = "bing", KEY_WIKIMEDIA = "wikimedia";
    static final String KEY_STATUS = "status", KEY_LAST_RUN = "last_run";

    private static final String AUTHORITY = "com.gathin.tvhome.theme";
    private static final String FOLDER = "feeds";
    private static final String CAPTIONS = "captions.txt";
    private static final String AERIAL_MANIFEST = "https://sylvan.apple.com/Aerials/resources-16.tar";
    private static final String BING = "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=en-US";
    private static final String WIKIMEDIA = "https://api.wikimedia.org/feed/v1/wikipedia/en/featured/";
    private static final String USER_AGENT = "WallpaperFeeds/1.0 (cleanyourtv add-on; Android TV)";
    private static final int WIKIMEDIA_DAYS = 7;

    private final Context ctx;
    private final ContentResolver resolver;
    private final AtomicBoolean stop;
    private final SharedPreferences prefs;
    private final File stateFile;
    /** name -> {caption, size, added, clip}: what this app has put in the launcher's folder. */
    private final JSONObject held;
    private boolean changed;

    private Fetcher(Context ctx, AtomicBoolean stop) {
        this.ctx = ctx.getApplicationContext();
        this.resolver = this.ctx.getContentResolver();
        this.stop = stop;
        this.prefs = this.ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.stateFile = new File(this.ctx.getFilesDir(), "held.json");
        this.held = readState(stateFile);
    }

    static synchronized void run(Context ctx, AtomicBoolean stop) {
        Fetcher f = new Fetcher(ctx, stop);
        String status;
        try {
            status = f.refresh();
        } catch (SecurityException e) {
            status = "The launcher refused the write: grant WRITE_SECURE_SETTINGS (build-and-install.sh does it).";
        } catch (InterruptedIOException e) {
            status = "Stopped by the system; the next run picks up where this one left off.";
        } catch (Exception e) {
            Log.w(TAG, "refresh failed", e);
            status = "Failed: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        } finally {
            f.finish();
        }
        f.prefs.edit().putString(KEY_STATUS, status).putLong(KEY_LAST_RUN, System.currentTimeMillis()).apply();
    }

    private String refresh() throws Exception {
        if (ctx.getPackageManager().resolveContentProvider(AUTHORITY, 0) == null) {
            return "The launcher (com.gathin.tvhome) is not installed.";
        }
        reconcile();
        boolean aerials = prefs.getBoolean(KEY_AERIALS, true);
        boolean bing = prefs.getBoolean(KEY_BING, true);
        boolean wikimedia = prefs.getBoolean(KEY_WIKIMEDIA, true);
        dropSource("aerial-", aerials);
        dropSource("bing-", bing);
        dropSource("wm-", wikimedia);

        List<String> notes = new ArrayList<>();
        List<Feeds.Item> stills = new ArrayList<>();
        if (bing) {
            try {
                stills.addAll(Feeds.bing(get(BING, 256 * 1024)));
            } catch (IOException e) {
                notes.add("Bing: " + e.getMessage());
            }
        }
        if (wikimedia) stills.addAll(wikimediaDays(notes));
        fetchStills(stills, notes);

        String quality = "";
        if (aerials) quality = fetchClips(notes);

        StringBuilder s = new StringBuilder();
        s.append(count(false)).append(" pictures, ").append(count(true)).append(" clips");
        if (!quality.isEmpty()) s.append(" (").append(quality).append(')');
        s.append(". Clips use ").append(used() / Feeds.MB).append(" MB of a ").append(budget() / Feeds.MB).append(" MB budget.");
        for (String n : notes) s.append('\n').append(n);
        return s.toString();
    }

    // ---- stills ----

    private List<Feeds.Item> wikimediaDays(List<String> notes) {
        List<Feeds.Item> out = new ArrayList<>();
        SimpleDateFormat path = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
        path.setTimeZone(TimeZone.getTimeZone("UTC"));
        long now = System.currentTimeMillis();
        for (int d = 0; d < WIKIMEDIA_DAYS && !stop.get(); d++) {
            String day = path.format(new Date(now - d * 86_400_000L));
            if (held.has("wm-" + day.replace("/", "") + ".jpg")) continue;
            try {
                Feeds.Item item = Feeds.wikimedia(get(WIKIMEDIA + day, 2 * 1024 * 1024), day);
                if (item != null) out.add(item);
            } catch (Exception e) {
                notes.add("Wikimedia " + day + ": " + e.getMessage());
            }
        }
        return out;
    }

    private void fetchStills(List<Feeds.Item> offered, List<String> notes) throws IOException {
        // Newest first: the names end in the date.
        Collections.sort(offered, (a, b) -> b.name.substring(b.name.indexOf('-')).compareTo(a.name.substring(a.name.indexOf('-'))));
        for (Feeds.Item item : offered) {
            if (stop.get()) throw new InterruptedIOException();
            if (held.has(item.name)) continue;
            File part = new File(ctx.getCacheDir(), "still.part");
            try {
                download(item.url, part, 20 * Feeds.MB);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(part.getPath(), o);
                if (o.outWidth < 1280 || o.outWidth * 2 < o.outHeight * 3) {
                    notes.add(item.name + ": not a landscape picture, skipped");
                    continue;
                }
                push(part, item.name);
                hold(item, part.length());
            } catch (IOException e) {
                if (e instanceof InterruptedIOException) throw e;
                notes.add(item.name + ": " + e.getMessage());
            } finally {
                part.delete();
            }
        }
        evictOldest(false, Feeds.MAX_STILLS);
    }

    // ---- clips ----

    private String fetchClips(List<String> notes) throws Exception {
        long budget = budget();
        String quality = Feeds.quality(hevc(3840, 2160), hevc(1920, 1080), displayWidth(), ram(), budget);
        List<Feeds.Item> offered;
        try (InputStream in = open(AERIAL_MANIFEST, 0).getInputStream()) {
            byte[] manifest = Feeds.tarEntry(in, "entries.json", 8 * Feeds.MB);
            if (manifest == null) throw new IOException("no entries.json in the aerial manifest");
            offered = Feeds.aerials(new String(manifest, StandardCharsets.UTF_8), Feeds.fallbacks(quality));
        } catch (IOException e) {
            notes.add("Aerials: " + e.getMessage());
            return quality;
        }
        int target = Feeds.clipTarget(budget, Feeds.typicalBytes(quality));
        evictOldest(true, target);
        if (target == 0) {
            notes.add("Aerials: not enough free storage for a clip.");
            return quality;
        }
        // A full shelf swaps its oldest clip for a new one once a week, so the set keeps changing.
        if (count(true) >= target && oldest(true) != null
                && System.currentTimeMillis() - held.optJSONObject(oldest(true)).optLong("added") > Feeds.ROTATE_AFTER_MS) {
            remove(oldest(true));
        }
        List<Feeds.Item> fresh = new ArrayList<>();
        for (Feeds.Item item : offered) if (!held.has(item.name)) fresh.add(item);
        Collections.shuffle(fresh);
        int fetched = 0;
        for (Feeds.Item item : fresh) {
            if (fetched >= Feeds.CLIPS_PER_RUN || count(true) >= target) break;
            if (used() + Feeds.typicalBytes(quality) > budget) break;
            if (stop.get()) throw new InterruptedIOException();
            File part = new File(ctx.getCacheDir(), "clip.part");
            try {
                long size = download(item.url, part, 1024 * Feeds.MB);
                if (used() + size > budget) {
                    notes.add(item.name + ": over the storage budget, skipped");
                    part.delete();
                    break;
                }
                push(part, item.name);
                hold(item, size);
                part.delete();
                fetched++;
            } catch (IOException e) {
                if (e instanceof InterruptedIOException) throw e;   // keep the part: the next run resumes it
                notes.add(item.name + ": " + e.getMessage());
                part.delete();
            }
        }
        return quality.replace("url-", "").replace("-SDR", " HEVC").replace("-H264", " H.264");
    }

    // ---- the launcher's folder ----

    private static Uri uri(String name) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(FOLDER).appendPath(name).build();
    }

    private void push(File src, String name) throws IOException {
        try (InputStream in = new FileInputStream(src); OutputStream out = resolver.openOutputStream(uri(name), "w")) {
            if (out == null) throw new IOException("the launcher gave no stream for " + name);
            copy(in, out, Long.MAX_VALUE);
        }
        changed = true;
    }

    private void remove(String name) {
        try {
            resolver.delete(uri(name), null, null);
        } catch (RuntimeException e) {
            Log.w(TAG, "could not delete " + name, e);
        }
        held.remove(name);
        changed = true;
    }

    /** Forget files the launcher no longer has (cleared data, a reinstall). */
    private void reconcile() {
        for (String name : names()) {
            try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri(name), "r")) {
                if (pfd == null || pfd.getStatSize() <= 0) held.remove(name);
            } catch (FileNotFoundException e) {
                held.remove(name);
            } catch (IOException ignored) {
                // closing failed: the file is there
            }
        }
    }

    private void dropSource(String prefix, boolean enabled) {
        if (enabled) return;
        for (String name : names()) if (name.startsWith(prefix)) remove(name);
    }

    private void evictOldest(boolean clips, int keep) {
        while (count(clips) > keep) remove(oldest(clips));
    }

    /** Write captions.txt last, deleting it first: the folder's change marks the run done for the launcher. */
    private void finish() {
        writeState();
        if (!changed) return;
        Map<String, String> captions = new HashMap<>();
        for (String name : names()) captions.put(name, held.optJSONObject(name).optString("caption"));
        try {
            resolver.delete(uri(CAPTIONS), null, null);
            try (OutputStream out = resolver.openOutputStream(uri(CAPTIONS), "w")) {
                if (out != null) out.write(Feeds.captionsFile(captions).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.w(TAG, "could not write captions", e);
        }
    }

    // ---- state ----

    private void hold(Feeds.Item item, long size) {
        try {
            held.put(item.name, new JSONObject().put("caption", item.caption).put("size", size)
                    .put("added", System.currentTimeMillis()).put("clip", item.clip));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        writeState();
    }

    private List<String> names() {
        List<String> out = new ArrayList<>();
        for (Iterator<String> it = held.keys(); it.hasNext(); ) out.add(it.next());
        return out;
    }

    private int count(boolean clips) {
        int n = 0;
        for (String name : names()) if (held.optJSONObject(name).optBoolean("clip") == clips) n++;
        return n;
    }

    private String oldest(boolean clips) {
        String oldest = null;
        long at = Long.MAX_VALUE;
        for (String name : names()) {
            JSONObject o = held.optJSONObject(name);
            if (o.optBoolean("clip") == clips && o.optLong("added") < at) {
                at = o.optLong("added");
                oldest = name;
            }
        }
        return oldest;
    }

    private long used() {
        long n = 0;
        for (String name : names()) {
            JSONObject o = held.optJSONObject(name);
            if (o.optBoolean("clip")) n += o.optLong("size");
        }
        return n;
    }

    private static JSONObject readState(File f) {
        try (InputStream in = new FileInputStream(f)) {
            return new JSONObject(Feeds.text(in, 1024 * 1024));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private void writeState() {
        File tmp = new File(stateFile.getPath() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(held.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.w(TAG, "could not save state", e);
            return;
        }
        if (!tmp.renameTo(stateFile)) Log.w(TAG, "could not save state");
    }

    // ---- the TV ----

    /** The launcher's files live on the same shared volume as this app's external files. */
    private StatFs volume() {
        File dir = ctx.getExternalFilesDir(null);
        return new StatFs((dir != null ? dir : ctx.getFilesDir()).getPath());
    }

    long budget() {
        StatFs fs = volume();
        return Feeds.budget(fs.getTotalBytes(), fs.getAvailableBytes(), used());
    }

    private boolean hevc(int w, int h) {
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (info.isEncoder()) continue;
            for (String type : info.getSupportedTypes()) {
                if (!"video/hevc".equalsIgnoreCase(type)) continue;
                MediaCodecInfo.VideoCapabilities v = info.getCapabilitiesForType(type).getVideoCapabilities();
                if (v != null && v.areSizeAndRateSupported(w, h, 30)) return true;
            }
        }
        return false;
    }

    private int displayWidth() {
        DisplayManager dm = ctx.getSystemService(DisplayManager.class);
        Display d = dm == null ? null : dm.getDisplay(Display.DEFAULT_DISPLAY);
        if (d == null) return 0;
        Display.Mode m = d.getMode();
        return Math.max(m.getPhysicalWidth(), m.getPhysicalHeight());
    }

    private long ram() {
        ActivityManager am = ctx.getSystemService(ActivityManager.class);
        if (am == null) return 0;
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.totalMem;
    }

    // ---- network ----

    private HttpURLConnection open(String url, long from) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(30_000);
        c.setRequestProperty("User-Agent", USER_AGENT);
        if (from > 0) c.setRequestProperty("Range", "bytes=" + from + "-");
        int code = c.getResponseCode();
        if (code != 200 && code != 206) {
            c.disconnect();
            throw new IOException("HTTP " + code + " from " + Uri.parse(url).getHost());
        }
        return c;
    }

    private String get(String url, long maxBytes) throws IOException {
        HttpURLConnection c = open(url, 0);
        try (InputStream in = c.getInputStream()) {
            return Feeds.text(in, maxBytes);
        } finally {
            c.disconnect();
        }
    }

    /**
     * Download into {@code part}, resuming a part left by a stopped run for the same URL. Checks the length the
     * server announced, and that the whole download would still leave the TV its reserve.
     */
    private long download(String url, File part, long maxBytes) throws IOException {
        File marker = new File(part.getPath() + ".url");
        long have = part.isFile() && url.equals(readSmall(marker)) ? part.length() : 0;
        if (have == 0) part.delete();
        writeSmall(marker, url);
        HttpURLConnection c = open(url, have);
        long expected;
        try {
            boolean resumed = have > 0 && c.getResponseCode() == 206;
            if (!resumed) have = 0;
            long rest = c.getContentLengthLong();
            expected = rest < 0 ? -1 : have + rest;
            if (expected > maxBytes) throw new IOException("file is " + expected / Feeds.MB + " MB");
            StatFs fs = volume();
            if (rest > 0 && !Feeds.fits(fs.getTotalBytes(), fs.getAvailableBytes(), rest)) {
                throw new IOException("not enough free storage");
            }
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(part, resumed)) {
                copy(in, out, maxBytes - have);
            }
        } finally {
            c.disconnect();
        }
        long size = part.length();
        if (expected >= 0 && size != expected) throw new IOException("download cut short");
        marker.delete();
        return size;
    }

    private void copy(InputStream in, OutputStream out, long max) throws IOException {
        byte[] buf = new byte[64 * 1024];
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            if (stop.get()) throw new InterruptedIOException();
            total += n;
            if (total > max) throw new IOException("larger than expected");
            out.write(buf, 0, n);
        }
    }

    private static String readSmall(File f) {
        try (InputStream in = new FileInputStream(f)) {
            return Feeds.text(in, 4096).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static void writeSmall(File f, String s) throws IOException {
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(s.getBytes(StandardCharsets.UTF_8));
        }
    }
}
