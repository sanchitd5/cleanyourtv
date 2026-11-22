package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ThemeFilesTest {
    @Test public void themeFilesAreAllowed() {
        assertEquals("config.json", ThemeFiles.path(Collections.singletonList("config.json")));
        assertEquals("wallpaper.jpg", ThemeFiles.path(Collections.singletonList("wallpaper.jpg")));
        assertEquals("theme_revision", ThemeFiles.path(Collections.singletonList("theme_revision")));
    }

    @Test public void collectionImagesAreAllowed() {
        assertEquals("wallpapers/dusk.jpg", ThemeFiles.path(Arrays.asList("wallpapers", "dusk.jpg")));
        assertEquals("wallpapers/Night_2.WEBP", ThemeFiles.path(Arrays.asList("wallpapers", "Night_2.WEBP")));
    }

    @Test public void theCollectionsCaptionsAreAllowed() {
        assertEquals("wallpapers/captions.txt", ThemeFiles.path(Arrays.asList("wallpapers", "captions.txt")));
        assertNull(ThemeFiles.path(Collections.singletonList("captions.txt")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", "notes.txt")));
        assertFalse(ThemeFiles.collectionImage("captions.txt"));
    }

    @Test public void theCollectionsClipsAreAllowed() {
        assertEquals("wallpapers/01-aurora.mp4", ThemeFiles.path(Arrays.asList("wallpapers", "01-aurora.mp4")));
        assertEquals("wallpapers/Night_2.MP4", ThemeFiles.path(Arrays.asList("wallpapers", "Night_2.MP4")));
        assertNull(ThemeFiles.path(Collections.singletonList("clip.mp4")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", ".mp4")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", "a b.mp4")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", "../clip.mp4")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", "clip.mp4.sh")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", "clip.mkv")));
        assertNull(ThemeFiles.path(Arrays.asList("other", "clip.mp4")));
    }

    @Test public void aClipIsNotACollectionImage() {
        // Clearing the collection's pictures must leave the clips where they are.
        assertFalse(ThemeFiles.collectionImage("01-aurora.mp4"));
        assertTrue(ThemeFiles.clip("01-aurora.mp4"));
        assertTrue(ThemeFiles.clip("01-aurora.MP4"));
        assertFalse(ThemeFiles.clip("01-aurora.jpg"));
        assertFalse(ThemeFiles.clip(".mp4"));
        assertFalse(ThemeFiles.clip("mp4"));
        assertFalse(ThemeFiles.clip("01-aurora.mp4.part"));
        assertFalse(ThemeFiles.clip("a b.mp4"));
        assertFalse(ThemeFiles.clip(null));
    }

    @Test public void anythingElseIsRefused() {
        assertNull(ThemeFiles.path(null));
        assertNull(ThemeFiles.path(Collections.emptyList()));
        assertNull(ThemeFiles.path(Collections.singletonList("cards.json")));
        assertNull(ThemeFiles.path(Collections.singletonList("..")));
        assertNull(ThemeFiles.path(Arrays.asList("..", "config.json")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", "..")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", "run.sh")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", "a b.jpg")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", "../config.jpg")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", ".jpg")));
        assertNull(ThemeFiles.path(Arrays.asList("other", "dusk.jpg")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", "sub", "dusk.jpg")));
    }

    @Test public void onlyTheBareFolderIsTheCollection() {
        assertTrue(ThemeFiles.isCollection(Collections.singletonList("wallpapers")));
        assertFalse(ThemeFiles.isCollection(Arrays.asList("wallpapers", "dusk.jpg")));
        assertFalse(ThemeFiles.isCollection(Collections.singletonList("config.json")));
    }

    @Test public void theInputLogosFolderTakesCollectionImagesOnly() {
        assertEquals("inputs/ps5.png", ThemeFiles.path(Arrays.asList("inputs", "ps5.png")));
        assertEquals("inputs/Fire_TV-2.webp", ThemeFiles.path(Arrays.asList("inputs", "Fire_TV-2.webp")));
        assertNull(ThemeFiles.path(Arrays.asList("inputs", "../x")));
        assertNull(ThemeFiles.path(Arrays.asList("inputs", "..")));
        assertNull(ThemeFiles.path(Arrays.asList("inputs", "a.mp4")));
        assertNull(ThemeFiles.path(Arrays.asList("inputs", ".hidden.png")));
        assertNull(ThemeFiles.path(Arrays.asList("inputs", "a", "ps5.png")));
        assertNull(ThemeFiles.path(Collections.singletonList("inputs")));
        assertNull(ThemeFiles.path(Arrays.asList("inputs", "notes.txt")));
    }
}
