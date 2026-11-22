package com.gathin.tvhome;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** screensaver_wallpapers: which folder, which pictures, which credit line, and what ThemeProvider lets in. */
public class ScreensaverStillsTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void onlyTheScreensaverFolderIsNamed() {
        assertEquals("screensaver", ScreensaverStills.folder(" screensaver "));
        for (String bad : new String[]{null, "", "a/b", "..", "a b", "wallpapers", "inputs", "Screensaver", "night"}) {
            assertEquals(String.valueOf(bad), "", ScreensaverStills.folder(bad));
        }
    }

    @Test public void theFolderChoiceListsItsImagesInNameOrder() throws Exception {
        File dir = tmp.newFolder("screensaver");
        for (String n : new String[]{"b.jpg", "a.png", "captions.txt", "c.mp4"}) {
            java.nio.file.Files.write(new File(dir, n).toPath(), new byte[]{1});   // empty files are skipped
        }
        List<File> images = ScreensaverStills.images(tmp.getRoot(), "screensaver");
        assertEquals(Arrays.asList("a.png", "b.jpg"), names(images));
    }

    @Test public void noFolderAMissingFolderOrAnEmptyOneMeansNoStills() throws Exception {
        tmp.newFolder("empty");
        assertTrue(ScreensaverStills.images(tmp.getRoot(), "").isEmpty());
        assertTrue(ScreensaverStills.images(tmp.getRoot(), "missing").isEmpty());
        assertTrue(ScreensaverStills.images(tmp.getRoot(), "empty").isEmpty());
        assertTrue(ScreensaverStills.images(null, "empty").isEmpty());
        assertTrue(ScreensaverStills.images(tmp.getRoot(), "wallpapers").isEmpty());
    }

    @Test public void theCreditComesFromThatFoldersCaptions() {
        Map<String, String> captions = WallpaperCaptions.parse(Arrays.asList(
                "# credits", "a.jpg\tPhoto: Ada Lovelace on Unsplash", "b.jpg\t"));
        assertEquals("Photo: Ada Lovelace on Unsplash", ScreensaverStills.credit(captions, new File("/x/a.jpg")));
        assertNull(ScreensaverStills.credit(captions, new File("/x/b.jpg")));
        assertNull(ScreensaverStills.credit(captions, null));
        assertNull(ScreensaverStills.credit(Collections.emptyMap(), new File("a.jpg")));
    }

    @Test public void theProviderTakesTheFolderOnlyForImagesAndCaptions() {
        assertEquals("screensaver/a.jpg", ThemeFiles.path(Arrays.asList("screensaver", "a.jpg")));
        assertEquals("screensaver/captions.txt", ThemeFiles.path(Arrays.asList("screensaver", "captions.txt")));
        assertNull(ThemeFiles.path(Arrays.asList("screensaver", "run.sh")));
        assertNull(ThemeFiles.path(Arrays.asList("screensaver", "../config.json")));
        assertNull(ThemeFiles.path(Arrays.asList("night", "a.jpg")));
        assertNull(ThemeFiles.path(Arrays.asList("screensaver", "x", "a.jpg")));
    }

    private static List<String> names(List<File> files) {
        String[] out = new String[files.size()];
        for (int i = 0; i < out.length; i++) out[i] = files.get(i).getName();
        return Arrays.asList(out);
    }
}
