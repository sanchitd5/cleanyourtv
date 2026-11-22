package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class InputDevicesTest {
    private static void is(String key, String kind, String wordmark, String osd) {
        InputDevices.Device d = InputDevices.detect(osd, -1, -1);
        assertNotNull(osd, d);
        assertEquals(osd, key, d.key);
        assertEquals(osd, kind, d.kind);
        assertEquals(osd, wordmark, d.name);
    }

    @Test public void playStationInEveryWriting() {
        is("ps5", "console", "PlayStation 5", "PlayStation 5");
        is("ps5", "console", "PlayStation 5", "PS5");
        is("ps5", "console", "PlayStation 5", "playstation5");
        is("ps4", "console", "PlayStation 4", "PlayStation4");
        is("ps4", "console", "PlayStation 4", "my ps4");
        is("ps3", "console", "PlayStation 3", "PS3");
        is("playstation", "console", "PlayStation", "PlayStation");
        assertEquals("playstation", InputDevices.detect("PlayStation 5", -1, -1).family);
    }

    @Test public void theMoreSpecificRowWins() {
        assertEquals("ps5", InputDevices.detect("PlayStation 5", -1, -1).key);
        assertEquals("xbox-series-x", InputDevices.detect("Xbox Series X", -1, -1).key);
        assertEquals("xbox-series-s", InputDevices.detect("XboxSeriesS", -1, -1).key);
        assertEquals("xbox-one", InputDevices.detect("Xbox One", -1, -1).key);
        assertEquals("xbox", InputDevices.detect("Xbox", -1, -1).key);
        assertEquals("xbox", InputDevices.detect("Xbox", -1, -1).family);
    }

    @Test public void otherConsoles() {
        is("switch", "console", "Nintendo Switch", "NintendoSwitch");
        is("switch", "console", "Nintendo Switch", "Nintendo Switch");
        is("nintendo", "console", "Nintendo", "Nintendo Wii");
        is("steamdeck", "console", "Steam Deck", "Steam Deck");
    }

    @Test public void streamers() {
        is("appletv", "streamer", "Apple TV", "Apple TV");
        is("appletv", "streamer", "Apple TV", "AppleTV");
        is("firetv", "streamer", "Fire TV", "Fire TV Stick");
        is("firetv", "streamer", "Fire TV", "FireStick");
        is("chromecast", "streamer", "Chromecast", "Chromecast");
        is("chromecast", "streamer", "Chromecast", "Living room Chromecast");
        is("googletv", "streamer", "Google TV Streamer", "Google TV Streamer");
        is("roku", "streamer", "Roku", "Roku Ultra");
        is("roku", "streamer", "Roku", "ROKU");
        is("shield", "streamer", "SHIELD", "SHIELD");
        is("shield", "streamer", "SHIELD", "NVIDIA Shield TV");
    }

    @Test public void setTopBoxesKeepTheNameAsSent() {
        is("stb", "stb", "Tata Play", "Tata Play");
        is("stb", "stb", "Airtel DTH", "Airtel DTH");
        is("stb", "stb", "My STB", "My STB");
        is("stb", "stb", "Set Top Box", "Set Top Box");
        is("stb", "stb", "SKY Q", "SKY Q");
    }

    @Test public void computersKeepTheNameAsSent() {
        is("pc", "pc", "My PC", "My PC");
        is("pc", "pc", "MacBook Pro", "MacBook Pro");
        is("pc", "pc", "Mac", "Mac");
        is("pc", "pc", "Office Laptop", "Office Laptop");
    }

    @Test public void negatives() {
        assertEquals("hdmi", InputDevices.detect("HDMI Switch", -1, -1).kind);
        assertEquals("hdmi", InputDevices.detect("UPC Box", -1, -1).kind);
        assertEquals("hdmi", InputDevices.detect("Living Room", -1, -1).key);
        assertNull(InputDevices.fromLabel("HDMI Switch"));
        assertNull(InputDevices.fromLabel("UPC Box"));
        assertNull(InputDevices.fromLabel("HDMI 1"));
        assertNull(InputDevices.fromLabel(""));
        assertNull(InputDevices.fromLabel("   "));
        assertNull(InputDevices.fromLabel(null));
        assertNull(InputDevices.detect(null, -1, -1));
        assertNull(InputDevices.detect("", -1, -1));
        assertNull(InputDevices.detect("  ", -1, 3));
    }

    @Test public void anUnnamedMatchKeepsTheNameAsSentOnAGenericDevice() {
        InputDevices.Device d = InputDevices.detect("Bedroom", -1, 4);
        assertEquals("hdmi", d.kind);
        assertEquals("Bedroom", d.name);
    }

    @Test public void nameBeatsVendor() {
        // A Sony device called "PS5" is a PlayStation 5, whatever the vendor table would say.
        InputDevices.Device d = InputDevices.detect("PS5", 0x080046, 4);
        assertEquals("ps5", d.key);
        assertEquals("PlayStation 5", d.name);
    }

    @Test public void appleVendorWithARoomNameIsAnAppleTv() {
        InputDevices.Device d = InputDevices.detect("Bedroom", 0x0010FA, 4);
        assertEquals("appletv", d.key);
        assertEquals("streamer", d.kind);
        assertEquals("Apple TV", d.name);
        assertEquals("appletv", InputDevices.detect(null, 0x0010FA, -1).key);
    }

    @Test public void googleAndRokuVendors() {
        InputDevices.Device g = InputDevices.detect("Den", 0x001A11, 4);
        assertEquals("chromecast", g.key);
        assertEquals("Den", g.name);
        assertEquals("Chromecast", InputDevices.detect("", 0x001A11, 4).name);
        InputDevices.Device r = InputDevices.detect("Den TV", 0x8AC72E, 4);
        assertEquals("roku", r.key);
        assertEquals("Roku", r.name);
    }

    @Test public void everyOtherVendorRowNamesTheVendorAndTakesItsKindFromTheCecType() {
        int[] ids = {0x080046, 0x0000F0, 0x00E091, 0x008045, 0x00903E, 0x000039, 0x000CE7, 0x08001F, 0x534850,
                0x00E036, 0x0009B0, 0x00A0DE, 0x0005CD, 0x000678, 0x6B746D};
        String[] names = {"Sony", "Samsung", "LG", "Panasonic", "Philips", "Toshiba", "Toshiba", "Sharp", "Sharp",
                "Pioneer", "Onkyo", "Yamaha", "Denon", "Marantz", "Vizio"};
        for (int i = 0; i < ids.length; i++) {
            InputDevices.Device d = InputDevices.detect(null, ids[i], 4);
            assertNotNull(names[i], d);
            assertEquals(names[i], d.name);
            assertEquals(names[i].toLowerCase(java.util.Locale.ROOT), d.family);
            assertEquals("hdmi", d.kind);
            assertEquals("stb", InputDevices.detect(null, ids[i], 3).kind);
            assertEquals("Own Name", InputDevices.detect("Own Name", ids[i], 4).name);
        }
    }

    @Test public void aVendorIsNotAssumedToBeAPlayStation() {
        InputDevices.Device d = InputDevices.detect(null, 0x080046, 4);
        assertEquals("sony", d.family);
        assertEquals("hdmi", d.kind);
    }

    @Test public void anUnknownVendorAndNoNameGivesNothing() {
        assertNull(InputDevices.detect(null, 0x123456, 4));
        assertNull(InputDevices.detect("", -1, -1));
    }

    @Test public void cecTypeThreeWithAnUnknownNameIsASetTopBox() {
        assertEquals("stb", InputDevices.detect("Foo", -1, 3).kind);
        assertEquals("stb", InputDevices.detect("Foo", -1, 1).kind);
        assertEquals("hdmi", InputDevices.detect("Foo", -1, 4).kind);
        assertEquals("hdmi", InputDevices.detect("Foo", -1, 5).kind);
    }

    @Test public void encodeAndDecodeRoundTrip() {
        for (String osd : new String[]{"PlayStation 5", "Xbox Series X", "Tata Play", "My PC", "Apple TV", "Bedroom"}) {
            InputDevices.Device d = InputDevices.detect(osd, -1, 4);
            InputDevices.Device back = InputDevices.decode(InputDevices.encode(d));
            assertNotNull(osd, back);
            assertEquals(d.key, back.key);
            assertEquals(d.name, back.name);
            assertEquals(d.kind, back.kind);
            assertEquals(d.family, back.family);
        }
    }

    @Test public void decodeRefusesGarbage() {
        assertNull(InputDevices.decode(null));
        assertNull(InputDevices.decode(""));
        assertNull(InputDevices.decode("ps5"));
        assertNull(InputDevices.decode("ps5\n"));
        assertNull(InputDevices.decode("\nPlayStation 5"));
        assertNull(InputDevices.decode("nonsense\nName"));
        assertNull(InputDevices.decode("ps5\n   "));
        assertNull(InputDevices.encode(null));
    }
}
