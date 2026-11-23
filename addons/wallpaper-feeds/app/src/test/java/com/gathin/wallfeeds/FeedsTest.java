package com.gathin.wallfeeds;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.gathin.wallfeeds.Feeds.GB;
import static com.gathin.wallfeeds.Feeds.MB;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FeedsTest {

    // ---- storage ----

    @Test public void theReserveIsFourGigabytesOrFifteenPercent() {
        assertEquals(4 * GB, Feeds.reserve(8 * GB));
        assertEquals(15 * GB, Feeds.reserve(100 * GB));
    }

    @Test public void theBudgetIsAQuarterOfTheSpareCappedAtSixGigabytes() {
        // 55 GB TV with 52 GB free: plenty, so the cap.
        assertEquals(6 * GB, Feeds.budget(55 * GB, 52 * GB, 0));
        // 8 GB TV with 6 GB free: (6 - 4) / 4.
        assertEquals(512 * MB, Feeds.budget(8 * GB, 6 * GB, 0));
        // Below the reserve: nothing.
        assertEquals(0, Feeds.budget(8 * GB, 3 * GB, 0));
    }

    @Test public void heldClipsCountAsSpareSoAFullShelfKeepsItsBudget() {
        assertEquals(Feeds.budget(8 * GB, 6 * GB, 0), Feeds.budget(8 * GB, 6 * GB - 300 * MB, 300 * MB));
    }

    @Test public void theClipTargetFitsTheBudgetUpToThirty() {
        assertEquals(2, Feeds.clipTarget(512 * MB, 180 * MB));
        assertEquals(Feeds.MAX_CLIPS, Feeds.clipTarget(100 * GB, 180 * MB));
        assertEquals(0, Feeds.clipTarget(100 * MB, 180 * MB));
        assertEquals(0, Feeds.clipTarget(GB, 0));
    }

    @Test public void aDownloadMustLeaveTheReserveWithItsTwoCopies() {
        assertTrue(Feeds.fits(8 * GB, 5 * GB, 400 * MB));
        assertFalse(Feeds.fits(8 * GB, 4 * GB + 100 * MB, 100 * MB));
    }

    // ---- quality ----

    @Test public void fourKOnlyWithEverythingInPlace() {
        assertEquals(Feeds.Q_4K, Feeds.quality(true, true, 3840, 4 * GB, 6 * GB));
        assertEquals(Feeds.Q_1080_HEVC, Feeds.quality(true, true, 1920, 4 * GB, 6 * GB));     // 1080p panel
        assertEquals(Feeds.Q_1080_HEVC, Feeds.quality(true, true, 3840, 2 * GB, 6 * GB));     // little memory
        assertEquals(Feeds.Q_1080_HEVC, Feeds.quality(true, true, 3840, 4 * GB, GB));         // little room
        assertEquals(Feeds.Q_1080_HEVC, Feeds.quality(false, true, 3840, 4 * GB, 6 * GB));    // no 4K decoder
        assertEquals(Feeds.Q_1080_H264, Feeds.quality(false, false, 1920, GB, GB));           // no HEVC at all
    }

    @Test public void fallbacksGoDownNeverUp() {
        assertArrayEquals(new String[] {Feeds.Q_4K, Feeds.Q_1080_HEVC, Feeds.Q_1080_H264}, Feeds.fallbacks(Feeds.Q_4K));
        assertArrayEquals(new String[] {Feeds.Q_1080_H264}, Feeds.fallbacks(Feeds.Q_1080_H264));
    }

    // ---- sources ----

    @Test public void bingGivesADatedPictureWithPlaceAndCredit() throws Exception {
        String json = "{\"images\":[{\"startdate\":\"20261122\",\"url\":\"/th?id=OHR.JustaHermitage_EN-US0130271816_1920x1080.jpg&pid=hp\","
                + "\"copyright\":\"The chapel of Santa Justa, Spain (© Tramont_ana/Getty Images)\"},"
                + "{\"startdate\":\"bad\",\"url\":\"/th?id=x\"},{\"startdate\":\"20261121\",\"url\":\"https://evil.example/x.jpg\"}]}";
        List<Feeds.Item> items = Feeds.bing(json);
        assertEquals(1, items.size());
        Feeds.Item it = items.get(0);
        assertEquals("bing-20261122.jpg", it.name);
        assertEquals("https://www.bing.com/th?id=OHR.JustaHermitage_EN-US0130271816_1920x1080.jpg&pid=hp", it.url);
        assertEquals("The chapel of Santa Justa, Spain · © Tramont_ana/Getty Images", it.caption);
        assertFalse(it.clip);
    }

    @Test public void wikimediaTakesAWideLandscapeAtNineteenTwenty() throws Exception {
        String json = "{\"image\":{\"title\":\"File:Pointe_Saint_Mathieu_-_Phare.jpg\","
                + "\"image\":{\"width\":6000,\"height\":3319},"
                + "\"thumbnail\":{\"source\":\"https://thumb.wikimedia.org/wikipedia/commons/thumb/4/46/P.jpg/960px-P.jpg?utm_source=x\"},"
                + "\"artist\":{\"text\":\"JackyM59\"},\"license\":{\"type\":\"CC BY-SA 4.0\"}}}";
        Feeds.Item it = Feeds.wikimedia(json, "2026/11/23");
        assertEquals("wm-20261123.jpg", it.name);
        assertEquals("https://thumb.wikimedia.org/wikipedia/commons/thumb/4/46/P.jpg/1920px-P.jpg", it.url);
        assertEquals("Pointe Saint Mathieu - Phare · JackyM59, CC BY-SA 4.0", it.caption);
    }

    @Test public void wikimediaSkipsPortraitsSmallPicturesAndDaysWithoutOne() throws Exception {
        String portrait = "{\"image\":{\"title\":\"File:a.jpg\",\"image\":{\"width\":3000,\"height\":4000},"
                + "\"thumbnail\":{\"source\":\"https://x/960px-a.jpg\"}}}";
        String small = "{\"image\":{\"title\":\"File:a.jpg\",\"image\":{\"width\":1200,\"height\":600},"
                + "\"thumbnail\":{\"source\":\"https://x/960px-a.jpg\"}}}";
        assertNull(Feeds.wikimedia(portrait, "2026/11/23"));
        assertNull(Feeds.wikimedia(small, "2026/11/23"));
        assertNull(Feeds.wikimedia("{\"tfa\":{}}", "2026/11/23"));
    }

    @Test public void aerialsTakeTheBestQualityEachAssetHas() throws Exception {
        String json = "{\"assets\":[{\"shotID\":\"SE_A016_C009\",\"accessibilityLabel\":\"Seals\","
                + "\"url-4K-SDR\":\"https://sylvan.apple.com/4k.mov\",\"url-1080-SDR\":\"https://sylvan.apple.com/1080.mov\","
                + "\"url-1080-H264\":\"https://sylvan.apple.com/avc.mov\"},"
                + "{\"id\":\"829E69BA-BB53\",\"accessibilityLabel\":\"Los Angeles\",\"url-1080-H264\":\"https://sylvan.apple.com/la.mov\"},"
                + "{\"shotID\":\"../x\",\"url-1080-H264\":\"http://plain.example/x.mov\"}]}";
        List<Feeds.Item> items = Feeds.aerials(json, Feeds.fallbacks(Feeds.Q_1080_HEVC));
        assertEquals(2, items.size());
        assertEquals("aerial-SE_A016_C009.mp4", items.get(0).name);
        assertEquals("https://sylvan.apple.com/1080.mov", items.get(0).url);
        assertEquals("Seals", items.get(0).caption);
        assertTrue(items.get(0).clip);
        assertEquals("aerial-829E69BA-BB53.mp4", items.get(1).name);
        assertEquals("https://sylvan.apple.com/la.mov", items.get(1).url);
    }

    @Test public void theManifestComesOutOfTheTar() throws Exception {
        ByteArrayOutputStream tar = new ByteArrayOutputStream();
        entry(tar, "README", "skip me, I am longer than nothing");
        entry(tar, "./entries.json", "{\"assets\":[]}");
        tar.write(new byte[1024]);
        byte[] got = Feeds.tarEntry(new ByteArrayInputStream(tar.toByteArray()), "entries.json", MB);
        assertEquals("{\"assets\":[]}", new String(got, StandardCharsets.UTF_8));
        assertNull(Feeds.tarEntry(new ByteArrayInputStream(tar.toByteArray()), "other.json", MB));
    }

    private static void entry(ByteArrayOutputStream out, String name, String body) throws Exception {
        byte[] header = new byte[512];
        byte[] n = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(n, 0, header, 0, n.length);
        byte[] size = String.format("%011o", body.length()).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(size, 0, header, 124, size.length);
        out.write(header);
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        out.write(data);
        out.write(new byte[(512 - data.length % 512) % 512]);
    }

    // ---- captions ----

    @Test public void aCaptionFitsTheLaunchersEightyCharacters() {
        assertEquals("Seals", Feeds.caption("Seals", ""));
        assertEquals("A · B", Feeds.caption("A", "B"));
        String place = "x".repeat(70);
        assertEquals(place, Feeds.caption(place, "a credit that does not fit"));
        assertEquals(80, Feeds.caption("y".repeat(100), "").length());
    }

    @Test public void captionsFileIsTabSeparatedAndSorted() {
        Map<String, String> m = new HashMap<>();
        m.put("wm-20261123.jpg", "Phare\twith a tab");
        m.put("bing-20261122.jpg", "Santa Justa");
        m.put("aerial-x.mp4", "");
        assertEquals("# Written by Wallpaper feeds.\nbing-20261122.jpg\tSanta Justa\nwm-20261123.jpg\tPhare with a tab\n",
                Feeds.captionsFile(m));
    }
}
