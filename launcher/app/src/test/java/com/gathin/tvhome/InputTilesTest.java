package com.gathin.tvhome;

import android.media.tv.TvInputManager;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class InputTilesTest {
    private static final int CONNECTED = TvInputManager.INPUT_STATE_CONNECTED;
    private static final int STANDBY = TvInputManager.INPUT_STATE_CONNECTED_STANDBY;
    private static final int DISCONNECTED = TvInputManager.INPUT_STATE_DISCONNECTED;

    @Test public void aDeviceOnThePortMakesATile() {
        assertTrue(InputTiles.visible(false, true, false, DISCONNECTED));
        assertTrue(InputTiles.visible(false, true, true, DISCONNECTED));
    }

    @Test public void aTrustedConnectedStateMakesATile() {
        assertTrue(InputTiles.visible(false, false, true, CONNECTED));
    }

    @Test public void anUntrustedStateDoesNot() {
        assertFalse(InputTiles.visible(false, false, false, CONNECTED));
        assertFalse(InputTiles.visible(false, false, false, -1));
    }

    @Test public void standbyAloneDoesNot() {
        assertFalse(InputTiles.visible(false, false, true, STANDBY));
        assertFalse(InputTiles.visible(false, false, true, DISCONNECTED));
        assertFalse(InputTiles.visible(false, false, true, -1));
    }

    @Test public void showEmptyKeepsEveryPort() {
        assertTrue(InputTiles.visible(true, false, false, -1));
        assertTrue(InputTiles.visible(true, false, true, DISCONNECTED));
    }

    @Test public void aPortWithNothingOnItIsHiddenWhateverTheDockSays() {
        // Decision 4: naming a port in dock_apps is a position, not a visibility; there is no "pinned" input.
        assertFalse(InputTiles.visible(false, false, true, DISCONNECTED));
        assertFalse(InputTiles.visible(false, false, false, DISCONNECTED));
    }

    private static InputTiles.Tile tile(String port, String name, String logo, boolean visible, int state) {
        InputDevices.Device d = InputDevices.detect(name, -1, 4);
        return new InputTiles.Tile(port, port, name, "HDMI 1", d, state, logo, visible);
    }

    @Test public void signatureIgnoresTheConnectionState() {
        List<InputTiles.Tile> a = Collections.singletonList(tile("p/HW2", "PS5", null, true, CONNECTED));
        List<InputTiles.Tile> b = Collections.singletonList(tile("p/HW2", "PS5", null, true, DISCONNECTED));
        assertEquals(InputTiles.signature(a), InputTiles.signature(b));
    }

    @Test public void signatureChangesWithNameLogoAndVisibility() {
        String base = InputTiles.signature(Collections.singletonList(tile("p/HW2", "PS5", null, true, CONNECTED)));
        assertNotEquals(base, InputTiles.signature(Collections.singletonList(tile("p/HW2", "Xbox One", null, true, CONNECTED))));
        assertNotEquals(base, InputTiles.signature(Collections.singletonList(tile("p/HW2", "PS5", "ps5.png", true, CONNECTED))));
        assertNotEquals(base, InputTiles.signature(Collections.singletonList(tile("p/HW2", "PS5", null, false, CONNECTED))));
        assertNotEquals(base, InputTiles.signature(Collections.singletonList(tile("p/HW3", "PS5", null, true, CONNECTED))));
    }

    @Test public void signatureOfNoTilesIsEmptyAndOrderMatters() {
        assertEquals("", InputTiles.signature(new ArrayList<>()));
        InputTiles.Tile x = tile("p/HW2", "PS5", null, true, CONNECTED), y = tile("p/HW3", "Roku", null, true, CONNECTED);
        List<InputTiles.Tile> xy = new ArrayList<>(), yx = new ArrayList<>();
        xy.add(x); xy.add(y); yx.add(y); yx.add(x);
        assertNotEquals(InputTiles.signature(xy), InputTiles.signature(yx));
    }

    @Test public void logoForTriesKeyThenFamilyThenKind() {
        InputDevices.Device ps5 = InputDevices.detect("PS5", -1, -1);
        Map<String, String> m = new HashMap<>();
        m.put("console", "console.png");
        assertEquals("console.png", InputTiles.logoFor(ps5, m));
        m.put("playstation", "ps.png");
        assertEquals("ps.png", InputTiles.logoFor(ps5, m));
        m.put("ps5", "ps5.png");
        assertEquals("ps5.png", InputTiles.logoFor(ps5, m));
    }

    @Test public void logoForRefusesABadFileName() {
        InputDevices.Device ps5 = InputDevices.detect("PS5", -1, -1);
        Map<String, String> m = new HashMap<>();
        m.put("ps5", "../ps5.png");
        m.put("playstation", "ps.png");
        // The bad entry is skipped; the next in order still counts.
        assertEquals("ps.png", InputTiles.logoFor(ps5, m));
        m.put("playstation", "ps.exe");
        m.put("console", ".hidden.png");
        assertNull(InputTiles.logoFor(ps5, m));
    }

    @Test public void logoForWithNothingToMatch() {
        InputDevices.Device ps5 = InputDevices.detect("PS5", -1, -1);
        assertNull(InputTiles.logoFor(ps5, Collections.emptyMap()));
        assertNull(InputTiles.logoFor(ps5, null));
        assertNull(InputTiles.logoFor(null, Collections.singletonMap("ps5", "ps5.png")));
        assertNull(InputTiles.logoFor(ps5, Collections.singletonMap("xbox", "x.png")));
    }

    @Test public void keyRoundTripsThroughLaunchActions() {
        String port = "com.mediatek.tis/.HdmiInputService/HW2";
        assertEquals("input:" + port, InputTiles.key(port));
        assertEquals(port, LaunchActions.inputId(InputTiles.key(port)));
    }

    @Test public void metaJoinsThePortAndTheState() {
        assertEquals("HDMI 1 · Connected", InputTiles.meta("HDMI 1", "Connected"));
        assertEquals("HDMI 1", InputTiles.meta("HDMI 1", null));
        assertEquals("HDMI 1", InputTiles.meta("HDMI 1", ""));
    }

    @Test public void everyKindHasAnAccentAndABackdrop() {
        for (String kind : new String[]{"console", "streamer", "stb", "pc", "hdmi", "anything-else"}) {
            assertEquals(0xFF, InputTiles.kindAccent(kind) >>> 24);
            assertEquals(2, InputTiles.kindBackdrop(kind).length);
        }
        assertEquals(0xFF4C8DFF, InputTiles.kindAccent("console"));
        assertEquals(InputTiles.kindAccent("hdmi"), InputTiles.kindAccent("anything-else"));
    }
}
