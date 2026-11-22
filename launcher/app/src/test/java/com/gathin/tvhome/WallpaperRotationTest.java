package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.Test;

public class WallpaperRotationTest {

    private static File touch(File dir, String name, int bytes) throws IOException {
        File f = new File(dir, name);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(new byte[bytes]);
        }
        return f;
    }

    @Test
    public void imagesKeepsPicturesOnlySortedByName() throws IOException {
        File dir = Files.createTempDirectory("walls").toFile();
        touch(dir, "b.JPG", 10);
        touch(dir, "a.png", 10);
        touch(dir, "notes.txt", 10);
        touch(dir, "empty.jpg", 0);
        new File(dir, "folder.jpg").mkdir();
        List<File> images = WallpaperRotation.images(dir);
        assertEquals(Arrays.asList("a.png", "b.JPG"), names(images));
        assertTrue(WallpaperRotation.images(null).isEmpty());
        assertTrue(WallpaperRotation.images(new File(dir, "missing")).isEmpty());
    }

    @Test
    public void pickAvoidsThePreviousPictureWhenThereIsAnother() {
        List<File> images = Arrays.asList(new File("a.jpg"), new File("b.jpg"));
        Random random = new Random(1);
        for (int i = 0; i < 20; i++) {
            assertNotEquals("a.jpg", WallpaperRotation.pick(images, "a.jpg", random).getName());
        }
        assertEquals("a.jpg", WallpaperRotation.pick(Collections.singletonList(new File("a.jpg")), "a.jpg", random).getName());
        assertNull(WallpaperRotation.pick(Collections.emptyList(), "", random));
    }

    @Test
    public void sequentialAdvancesInSortedOrderAndWraps() {
        List<File> images = Arrays.asList(new File("a.jpg"), new File("b.jpg"), new File("c.jpg"));
        assertEquals("b.jpg", WallpaperRotation.next(images, "a.jpg").getName());
        assertEquals("c.jpg", WallpaperRotation.next(images, "b.jpg").getName());
        assertEquals("a.jpg", WallpaperRotation.next(images, "c.jpg").getName());
        // A first run, or an image removed from the collection, starts at the first picture.
        assertEquals("a.jpg", WallpaperRotation.next(images, "").getName());
        assertEquals("a.jpg", WallpaperRotation.next(images, "gone.jpg").getName());
        assertEquals("only.jpg", WallpaperRotation.next(
                Collections.singletonList(new File("only.jpg")), "only.jpg").getName());
        assertNull(WallpaperRotation.next(Collections.emptyList(), ""));
    }

    @Test
    public void pickUsesTheRequestedOrder() {
        List<File> images = Arrays.asList(new File("a.jpg"), new File("b.jpg"), new File("c.jpg"));
        Random random = new Random(1);
        assertEquals("b.jpg", WallpaperRotation.pick(images, "a.jpg", WallpaperRotation.ORDER_SEQUENTIAL, random).getName());
        assertEquals("a.jpg", WallpaperRotation.pick(images, "c.jpg", WallpaperRotation.ORDER_SEQUENTIAL, random).getName());
        for (int i = 0; i < 20; i++) {
            assertNotEquals("a.jpg", WallpaperRotation.pick(
                    images, "a.jpg", WallpaperRotation.ORDER_SHUFFLE, random).getName());
        }
        // Unknown order keeps the historical shuffle rather than failing or blanking.
        assertNotEquals("a.jpg", WallpaperRotation.pick(images, "a.jpg", "random", random).getName());
    }

    @Test
    public void findsTheFirstPictureWhenThePreviousOneIsGone() {
        List<File> images = Arrays.asList(new File("a.jpg"), new File("b.jpg"));
        assertEquals("a.jpg", WallpaperRotation.next(images, "deleted.jpg").getName());
    }

    @Test
    public void findMatchesByName() {
        List<File> images = Arrays.asList(new File("a.jpg"), new File("b.jpg"));
        assertEquals("b.jpg", WallpaperRotation.find(images, "b.jpg").getName());
        assertNull(WallpaperRotation.find(images, "c.jpg"));
    }

    @Test
    public void anOnTvPickReplacesTheRotation() {
        List<File> collection = Arrays.asList(new File("a.jpg"), new File("b.jpg"), new File("c.jpg"));
        File single = new File("wallpaper.jpg");
        // No choice, or a picture that has gone: the theme's rotation decides.
        assertNull(WallpaperRotation.chosen(collection, single, null));
        assertNull(WallpaperRotation.chosen(collection, single, "gone.jpg"));
        assertNull(WallpaperRotation.chosen(collection, null, "wallpaper.jpg"));
        // None: no wallpaper at all.
        assertTrue(WallpaperRotation.chosen(collection, single, WallpaperRotation.CHOICE_NONE).isEmpty());
        assertTrue(WallpaperRotation.chosen(Collections.emptyList(), null, WallpaperRotation.CHOICE_NONE).isEmpty());
        // A picked picture is the only one shown, so the rotation can't move past it.
        assertEquals(Collections.singletonList("b.jpg"), names(WallpaperRotation.chosen(collection, single, "b.jpg")));
        assertEquals(Collections.singletonList("wallpaper.jpg"),
                names(WallpaperRotation.chosen(collection, single, "wallpaper.jpg")));
        // Picking again simply replaces the earlier pick.
        assertEquals(Collections.singletonList("c.jpg"), names(WallpaperRotation.chosen(collection, single, "c.jpg")));
    }

    @Test
    public void homeRebuildsWhenTheSettingsRevisionMoved() {
        assertFalse(Config.settingsChanged(0, 0));
        assertFalse(Config.settingsChanged(3, 3));
        assertTrue(Config.settingsChanged(0, 1));
        assertTrue(Config.settingsChanged(4, 0));   // settings cleared
    }

    @Test
    public void dueOnlyWithRotationOnAndTheIntervalPassed() {
        long minute = 60_000L;
        assertFalse(WallpaperRotation.due(0, 10 * minute, 0));             // rotation off
        assertTrue(WallpaperRotation.due(0, 10 * minute, 5 * minute));      // never changed yet
        assertFalse(WallpaperRotation.due(8 * minute, 10 * minute, 5 * minute));
        assertTrue(WallpaperRotation.due(5 * minute, 10 * minute, 5 * minute));
        assertTrue(WallpaperRotation.due(20 * minute, 10 * minute, 5 * minute));   // clock went back
    }

    @Test
    public void videosKeepsClipsOnlySortedByName() throws IOException {
        File dir = Files.createTempDirectory("walls").toFile();
        touch(dir, "02-night.MP4", 10);
        touch(dir, "01-aurora.mp4", 10);
        touch(dir, "03-still.jpg", 10);
        touch(dir, "captions.txt", 10);
        touch(dir, "empty.mp4", 0);
        touch(dir, "clip.mp4.part", 10);
        new File(dir, "folder.mp4").mkdir();
        assertEquals(Arrays.asList("01-aurora.mp4", "02-night.MP4"), names(WallpaperRotation.videos(dir)));
        // The pictures of the same folder are the collection it always was: a clip is not one of them.
        assertEquals(Collections.singletonList("03-still.jpg"), names(WallpaperRotation.images(dir)));
        assertTrue(WallpaperRotation.videos(null).isEmpty());
        assertTrue(WallpaperRotation.videos(new File(dir, "missing")).isEmpty());
    }

    @Test
    public void aClipHasNoSizeLimitButTheCollectionHasACount() throws IOException {
        File dir = Files.createTempDirectory("walls").toFile();
        touch(dir, "big.mp4", 11 * 1024 * 1024);   // over the 10 MB a picture may be
        touch(dir, "big.jpg", 11 * 1024 * 1024);
        assertEquals(Collections.singletonList("big.mp4"), names(WallpaperRotation.videos(dir)));
        assertTrue(WallpaperRotation.images(dir).isEmpty());
        for (int i = 0; i < 105; i++) touch(dir, String.format("clip-%03d.mp4", i), 1);
        assertEquals(100, WallpaperRotation.videos(dir).size());
    }

    @Test
    public void clipsRotateInTheOrderThePicturesDo() throws IOException {
        File dir = Files.createTempDirectory("walls").toFile();
        touch(dir, "03-ocean.mp4", 1);
        touch(dir, "01-aurora.mp4", 1);
        touch(dir, "02-night.mp4", 1);
        List<File> clips = WallpaperRotation.videos(dir);
        Random random = new Random(1);
        String order = WallpaperRotation.ORDER_SEQUENTIAL;
        assertEquals("01-aurora.mp4", WallpaperRotation.pick(clips, "", order, random).getName());
        assertEquals("02-night.mp4", WallpaperRotation.pick(clips, "01-aurora.mp4", order, random).getName());
        assertEquals("01-aurora.mp4", WallpaperRotation.pick(clips, "03-ocean.mp4", order, random).getName());
        // The clip Home left on is the one it comes back to, until the interval has passed.
        assertEquals("02-night.mp4", WallpaperRotation.find(clips, "02-night.mp4").getName());
        assertNull(WallpaperRotation.find(clips, "gone.mp4"));
    }

    @Test
    public void onlyAndroidsOwnDecodersAreSoftware() {
        assertTrue(WallpaperRotation.softwareCodec("c2.android.avc.decoder"));
        assertTrue(WallpaperRotation.softwareCodec("OMX.google.h264.decoder"));
        assertFalse(WallpaperRotation.softwareCodec("OMX.MTK.VIDEO.DECODER.AVC"));
        assertFalse(WallpaperRotation.softwareCodec("c2.mtk.avc.decoder"));
        // A player that does not say which decoder it took is left to play.
        assertFalse(WallpaperRotation.softwareCodec(null));
        assertFalse(WallpaperRotation.softwareCodec(""));
    }

    private static List<String> names(List<File> files) {
        String[] out = new String[files.size()];
        for (int i = 0; i < out.length; i++) out[i] = files.get(i).getName();
        return Arrays.asList(out);
    }
}
