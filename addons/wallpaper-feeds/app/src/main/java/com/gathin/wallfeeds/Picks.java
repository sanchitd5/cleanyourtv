package com.gathin.wallfeeds;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * What the person picked, without Android: the held map (name -> {caption, size, added, clip, category, pinned,
 * hidden}) and the rules on it. A pinned item is never evicted, rotated out or removed by "only pinned"; a hidden
 * one is no longer in the launcher's folder, and stays in the map as a blocklist entry so no refresh fetches it.
 */
final class Picks {
    static final int NORMAL = 0, PINNED = 1, HIDDEN = 2;

    private Picks() {}

    /** One tile of the gallery. */
    static final class Entry {
        final String name, caption;
        final boolean clip;
        final int state;

        Entry(String name, String caption, boolean clip, int state) {
            this.name = name;
            this.caption = caption;
            this.clip = clip;
            this.state = state;
        }
    }

    static int state(JSONObject o) {
        if (o == null) return NORMAL;
        return o.optBoolean("hidden") ? HIDDEN : o.optBoolean("pinned") ? PINNED : NORMAL;
    }

    /** The state of {@code name}, or -1 when the map does not know it. */
    static int stateOf(JSONObject held, String name) {
        JSONObject o = held.optJSONObject(name);
        return o == null ? -1 : state(o);
    }

    /**
     * OK on a tile: Normal, Pinned, Hidden, Normal. Returns the new state. Hidden goes back to Normal by
     * forgetting the entry (the picture returns on a refresh, if the feed still offers it), so the caller deletes
     * the file when this returns HIDDEN.
     */
    static int cycle(JSONObject held, String name) {
        JSONObject o = held.optJSONObject(name);
        if (o == null) return NORMAL;
        switch (state(o)) {
            case NORMAL:
                set(o, "pinned", true);
                return PINNED;
            case PINNED:
                o.remove("pinned");
                set(o, "hidden", true);
                return HIDDEN;
            default:
                held.remove(name);
                return NORMAL;
        }
    }

    /** The names whose files are in the launcher's folder: everything but the hidden. */
    static List<String> files(JSONObject held) {
        List<String> out = new ArrayList<>();
        for (Iterator<String> it = held.keys(); it.hasNext(); ) {
            String name = it.next();
            if (state(held.optJSONObject(name)) != HIDDEN) out.add(name);
        }
        return out;
    }

    static int count(JSONObject held, boolean clips) {
        int n = 0;
        for (String name : files(held)) if (held.optJSONObject(name).optBoolean("clip") == clips) n++;
        return n;
    }

    static int pinned(JSONObject held, boolean clips) {
        int n = 0;
        for (String name : files(held)) {
            JSONObject o = held.optJSONObject(name);
            if (o.optBoolean("clip") == clips && state(o) == PINNED) n++;
        }
        return n;
    }

    /** The bytes the unhidden clips take. */
    static long clipBytes(JSONObject held) {
        long n = 0;
        for (String name : files(held)) {
            JSONObject o = held.optJSONObject(name);
            if (o.optBoolean("clip")) n += o.optLong("size");
        }
        return n;
    }

    /** The unpinned item to drop first: a clip by when it was added, a picture by its date (the name's suffix). */
    static String oldest(JSONObject held, boolean clips) {
        List<String> order = dropOrder(held, clips);
        return order.isEmpty() ? null : order.get(0);
    }

    /** The unpinned files of one kind, first to drop first: clips by when added, pictures by the day in the name. */
    private static List<String> dropOrder(JSONObject held, boolean clips) {
        List<String> out = new ArrayList<>();
        for (String name : files(held)) {
            JSONObject o = held.optJSONObject(name);
            if (o.optBoolean("clip") == clips && state(o) != PINNED) out.add(name);
        }
        Collections.sort(out, (a, b) -> {
            long x = held.optJSONObject(a).optLong("added"), y = held.optJSONObject(b).optLong("added");
            int c = clips ? 0 : date(a).compareTo(date(b));
            return c != 0 ? c : x != y ? Long.compare(x, y) : a.compareTo(b);
        });
        return out;
    }

    /** "bing-20261122.jpg" -> "20261122.jpg": the part that sorts by day across sources. */
    static String date(String name) {
        return name.substring(name.indexOf('-') + 1);
    }

    /** What to remove, first to drop first, to bring the clips (or pictures) down to {@code keep}; pinned stay. */
    static List<String> overflow(JSONObject held, boolean clips, int keep) {
        List<String> order = dropOrder(held, clips);
        int extra = count(held, clips) - keep;
        return extra <= 0 ? new ArrayList<>() : new ArrayList<>(order.subList(0, Math.min(extra, order.size())));
    }

    /** Unpinned, unhidden clips whose category is no longer enabled. Clips with no recorded category stay. */
    static List<String> disabledCategories(JSONObject held, Set<String> enabled) {
        List<String> out = new ArrayList<>();
        for (String name : files(held)) {
            JSONObject o = held.optJSONObject(name);
            String c = o.optString("category");
            if (o.optBoolean("clip") && state(o) != PINNED && !c.isEmpty() && !enabled.contains(c)) out.add(name);
        }
        return out;
    }

    /** Every file that is not pinned: what "show only pinned" removes. */
    static List<String> unpinned(JSONObject held) {
        List<String> out = new ArrayList<>();
        for (String name : files(held)) if (state(held.optJSONObject(name)) != PINNED) out.add(name);
        return out;
    }

    /** The gallery's tiles: pictures then clips, each newest name first; hidden ones included. */
    static List<Entry> entries(JSONObject held) {
        List<String> names = new ArrayList<>();
        for (Iterator<String> it = held.keys(); it.hasNext(); ) names.add(it.next());
        Collections.sort(names, (a, b) -> {
            boolean ca = held.optJSONObject(a).optBoolean("clip"), cb = held.optJSONObject(b).optBoolean("clip");
            return ca != cb ? (ca ? 1 : -1) : b.compareTo(a);
        });
        List<Entry> out = new ArrayList<>();
        for (String name : names) {
            JSONObject o = held.optJSONObject(name);
            out.add(new Entry(name, o.optString("caption"), o.optBoolean("clip"), state(o)));
        }
        return out;
    }

    static void set(JSONObject o, String key, Object value) {
        try {
            o.put(key, value);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }
}
