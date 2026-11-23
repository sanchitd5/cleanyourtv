package com.gathin.wallfeeds;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PicksTest {

    private static void add(JSONObject held, String name, boolean clip, long added, String category) throws Exception {
        held.put(name, new JSONObject().put("caption", name).put("size", 100).put("added", added)
                .put("clip", clip).put("category", category));
    }

    private static JSONObject shelf() throws Exception {
        JSONObject h = new JSONObject();
        add(h, "bing-20261120.jpg", false, 1, "");
        add(h, "bing-20261121.jpg", false, 2, "");
        add(h, "wm-20261119.jpg", false, 3, "");
        add(h, "aerial-A.mp4", true, 10, "space");
        add(h, "aerial-B.mp4", true, 20, "cities");
        add(h, "aerial-C.mp4", true, 30, "underwater");
        return h;
    }

    @Test public void okCyclesNormalPinnedHiddenNormal() throws Exception {
        JSONObject h = shelf();
        assertEquals(Picks.PINNED, Picks.cycle(h, "aerial-A.mp4"));
        assertEquals(Picks.PINNED, Picks.stateOf(h, "aerial-A.mp4"));
        assertEquals(Picks.HIDDEN, Picks.cycle(h, "aerial-A.mp4"));
        assertFalse(h.getJSONObject("aerial-A.mp4").optBoolean("pinned"));
        assertEquals(Picks.HIDDEN, Picks.stateOf(h, "aerial-A.mp4"));
        assertEquals(Picks.NORMAL, Picks.cycle(h, "aerial-A.mp4"));
        assertFalse(h.has("aerial-A.mp4"));              // un-blocklisted: a refresh may fetch it again
        assertEquals(-1, Picks.stateOf(h, "aerial-A.mp4"));
        assertEquals(Picks.NORMAL, Picks.cycle(h, "nope"));
    }

    @Test public void aHiddenItemIsNoFileButStaysKnownSoItIsNeverFetchedAgain() throws Exception {
        JSONObject h = shelf();
        Picks.cycle(h, "bing-20261120.jpg");
        Picks.cycle(h, "bing-20261120.jpg");
        assertTrue(h.has("bing-20261120.jpg"));
        assertFalse(Picks.files(h).contains("bing-20261120.jpg"));
        assertEquals(2, Picks.count(h, false));
        assertEquals(0, Picks.clipBytes(new JSONObject()));
        assertEquals(300, Picks.clipBytes(h));
    }

    @Test public void evictionDropsTheOldestAndSkipsPins() throws Exception {
        JSONObject h = shelf();
        Picks.cycle(h, "bing-20261120.jpg");   // pinned: the oldest picture
        assertEquals("wm-20261119.jpg", Picks.oldest(h, false));
        assertEquals(Arrays.asList("wm-20261119.jpg"), Picks.overflow(h, false, 2));
        // Pictures go by the day in their name across sources, not by when they were added.
        assertEquals(Arrays.asList("wm-20261119.jpg", "bing-20261121.jpg"), Picks.overflow(h, false, 1));
        assertEquals(Arrays.asList("wm-20261119.jpg", "bing-20261121.jpg"), Picks.overflow(h, false, 0));   // the pin stays
        assertEquals("aerial-A.mp4", Picks.oldest(h, true));
        assertEquals(Arrays.asList("aerial-A.mp4"), Picks.overflow(h, true, 2));
        assertTrue(Picks.overflow(h, true, 3).isEmpty());
    }

    @Test public void theWeeklyRotationNeverPicksAPinnedClip() throws Exception {
        JSONObject h = shelf();
        Picks.cycle(h, "aerial-A.mp4");        // the oldest clip, pinned
        assertEquals("aerial-B.mp4", Picks.oldest(h, true));
        Picks.cycle(h, "aerial-B.mp4");
        Picks.cycle(h, "aerial-C.mp4");
        assertNull(Picks.oldest(h, true));      // all pinned: nothing to rotate
        assertEquals(3, Picks.pinned(h, true));
    }

    @Test public void aDisabledCategoryRemovesItsUnpinnedClipsOnly() throws Exception {
        JSONObject h = shelf();
        add(h, "aerial-old.mp4", true, 5, "");   // from before categories were recorded: kept
        Picks.cycle(h, "aerial-B.mp4");          // pinned cities clip
        Set<String> on = new HashSet<>(Arrays.asList("space"));
        List<String> gone = Picks.disabledCategories(h, on);
        assertEquals(Arrays.asList("aerial-C.mp4"), gone);
        assertTrue(Picks.disabledCategories(h, new HashSet<>(Arrays.asList("space", "cities", "underwater"))).isEmpty());
    }

    @Test public void showOnlyPinnedRemovesEverythingElse() throws Exception {
        JSONObject h = shelf();
        Picks.cycle(h, "wm-20261119.jpg");
        Picks.cycle(h, "aerial-C.mp4");
        Picks.cycle(h, "aerial-B.mp4");
        Picks.cycle(h, "aerial-B.mp4");          // hidden: no file to remove
        List<String> gone = Picks.unpinned(h);
        assertEquals(3, gone.size());
        assertFalse(gone.contains("wm-20261119.jpg"));
        assertFalse(gone.contains("aerial-C.mp4"));
        assertFalse(gone.contains("aerial-B.mp4"));
    }

    @Test public void theGalleryListsPicturesThenClipsNewestFirstHiddenIncluded() throws Exception {
        JSONObject h = shelf();
        Picks.cycle(h, "aerial-B.mp4");
        Picks.cycle(h, "aerial-B.mp4");
        List<Picks.Entry> e = Picks.entries(h);
        assertEquals(6, e.size());
        assertEquals("wm-20261119.jpg", e.get(0).name);
        assertEquals("bing-20261120.jpg", e.get(2).name);
        assertEquals("aerial-C.mp4", e.get(3).name);
        assertTrue(e.get(3).clip);
        assertEquals(Picks.HIDDEN, e.get(4).state);
    }
}
