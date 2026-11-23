package com.gathin.tvhome;

import java.util.Locale;

/**
 * Names an HDMI device from what the TV says about it: its CEC OSD name, its CEC vendor id and its CEC device type
 * (HOME-DESIGN.md 5.2). Pure; the Android glue that reads those values is InputTileSource. The names are plain
 * product names set in the launcher's own font: no logo artwork is involved.
 */
final class InputDevices {
    static final String CONSOLE = "console", STREAMER = "streamer", STB = "stb", PC = "pc", HDMI = "hdmi";

    /** A detected device. {@code key} and {@code family} look up a theme logo; {@code name} is the tile's wordmark. */
    static final class Device {
        final String key, family, kind, name;

        Device(String key, String family, String kind, String name) {
            this.key = key;
            this.family = family;
            this.kind = kind;
            this.name = name;
        }
    }

    private InputDevices() {}

    /** One OSD-table row: matches when the squashed name contains any of {@code contains}, or a token equals any of {@code tokens}. */
    private static final class Row {
        final String key, family, kind, wordmark;   // wordmark null: the name as sent
        final String[] contains, tokens;

        Row(String key, String family, String kind, String wordmark, String[] contains, String[] tokens) {
            this.key = key;
            this.family = family;
            this.kind = kind;
            this.wordmark = wordmark;
            this.contains = contains;
            this.tokens = tokens;
        }
    }

    private static Row row(String key, String family, String kind, String wordmark, String[] contains, String... tokens) {
        return new Row(key, family, kind, wordmark, contains, tokens);
    }

    private static String[] c(String... values) {
        return values;
    }

    /** Tested top to bottom. */
    private static final Row[] ROWS = {
            row("ps5", "playstation", CONSOLE, "PlayStation 5", c("playstation5"), "ps5"),
            row("ps4", "playstation", CONSOLE, "PlayStation 4", c("playstation4"), "ps4"),
            row("ps3", "playstation", CONSOLE, "PlayStation 3", c("playstation3"), "ps3"),
            row("playstation", "playstation", CONSOLE, "PlayStation", c("playstation")),
            row("xbox-series-x", "xbox", CONSOLE, "Xbox Series X", c("xboxseriesx")),
            row("xbox-series-s", "xbox", CONSOLE, "Xbox Series S", c("xboxseriess")),
            row("xbox-one", "xbox", CONSOLE, "Xbox One", c("xboxone")),
            row("xbox", "xbox", CONSOLE, "Xbox", c("xbox")),
            row("switch", "nintendo", CONSOLE, "Nintendo Switch", c("nintendoswitch")),
            row("nintendo", "nintendo", CONSOLE, "Nintendo", c("nintendo")),
            row("steamdeck", "steam", CONSOLE, "Steam Deck", c("steamdeck")),
            row("appletv", "apple", STREAMER, "Apple TV", c("appletv")),
            row("firetv", "amazon", STREAMER, "Fire TV", c("firetv", "firestick")),
            row("googletv", "google", STREAMER, "Google TV Streamer", c("googletvstreamer")),
            row("chromecast", "google", STREAMER, "Chromecast", c("chromecast")),
            row("roku", "roku", STREAMER, "Roku", c("roku")),
            row("shield", "nvidia", STREAMER, "SHIELD", c("shield")),
            row("stb", "stb", STB, null, c("settop", "tataplay", "tatasky", "airtel", "dishtv", "d2h", "sundirect",
                    "jiostb", "skyq", "cablebox", "satellite", "decoder", "tivo"), "stb", "dth"),
            row("pc", "pc", PC, null, c("computer", "laptop", "desktop", "macbook", "macmini", "imac", "windows"),
                    "pc", "mac"),
    };

    /** CEC vendor id (a 24-bit OUI), vendor name, and the result when the OSD name matched nothing. */
    private static final int[] VENDOR_ID = {
            0x0010FA, 0x001A11, 0x8AC72E, 0x080046, 0x0000F0, 0x00E091, 0x008045, 0x00903E, 0x000039, 0x000CE7,
            0x08001F, 0x534850, 0x00E036, 0x0009B0, 0x00A0DE, 0x0005CD, 0x000678, 0x6B746D};
    private static final String[] VENDOR_NAME = {
            "Apple", "Google", "Roku", "Sony", "Samsung", "LG", "Panasonic", "Philips", "Toshiba", "Toshiba",
            "Sharp", "Sharp", "Pioneer", "Onkyo", "Yamaha", "Denon", "Marantz", "Vizio"};

    /** CEC device types that refine the kind when nothing else does: recorder and tuner are set-top boxes. */
    static final int CEC_RECORDER = 1, CEC_TUNER = 3;

    /**
     * The device behind a CEC OSD name, else its vendor id, else its CEC type. {@code vendorId} is -1 when unknown and
     * {@code cecType} is -1 when unknown. Null when there is nothing to name the device by.
     */
    static Device detect(String osdName, int vendorId, int cecType) {
        String name = osdName == null ? "" : osdName.trim();
        Device byName = fromLabel(name);
        if (byName != null) return byName;
        int v = indexOfVendor(vendorId);
        if (v >= 0) {
            String vendor = VENDOR_NAME[v];
            if ("Apple".equals(vendor)) return new Device("appletv", "apple", STREAMER, "Apple TV");
            if ("Google".equals(vendor)) {
                return new Device("chromecast", "google", STREAMER, name.isEmpty() ? "Chromecast" : name);
            }
            if ("Roku".equals(vendor)) return new Device("roku", "roku", STREAMER, "Roku");
            String kind = kindFromCec(cecType);
            return new Device(kind, vendor.toLowerCase(Locale.ROOT), kind, name.isEmpty() ? vendor : name);
        }
        if (name.isEmpty()) return null;
        String kind = kindFromCec(cecType);
        return new Device(kind, kind, kind, name);
    }

    /** The device an OSD-style name stands for (a CEC name, a child input's label, the user's label), or null when it matches no row. */
    static Device fromLabel(String label) {
        if (label == null) return null;
        String name = label.trim();
        if (name.isEmpty()) return null;
        String lower = name.toLowerCase(Locale.ROOT);
        StringBuilder squashed = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) squashed.append(ch);
        }
        String n = squashed.toString();
        String[] tokens = lower.split("[^a-z0-9]+");
        for (Row r : ROWS) {
            if (matches(r, n, tokens)) {
                return new Device(r.key, r.family, r.kind, r.wordmark != null ? r.wordmark : name);
            }
        }
        return null;
    }

    private static boolean matches(Row r, String n, String[] tokens) {
        for (String s : r.contains) if (n.contains(s)) return true;
        for (String t : r.tokens) for (String token : tokens) if (token.equals(t)) return true;
        return false;
    }

    private static int indexOfVendor(int id) {
        for (int i = 0; i < VENDOR_ID.length; i++) if (VENDOR_ID[i] == id) return i;
        return -1;
    }

    private static String kindFromCec(int cecType) {
        return cecType == CEC_RECORDER || cecType == CEC_TUNER ? STB : HDMI;
    }

    /** The form kept in preferences: the key, a newline, the name. */
    static String encode(Device d) {
        return d == null ? null : d.key + "\n" + d.name;
    }

    /** Reads {@link #encode} back; null for anything that is not that form. */
    static Device decode(String text) {
        if (text == null) return null;
        int nl = text.indexOf('\n');
        if (nl <= 0 || nl == text.length() - 1) return null;
        String key = text.substring(0, nl), name = text.substring(nl + 1).trim();
        if (name.isEmpty() || key.indexOf('\n') >= 0) return null;
        for (Row r : ROWS) if (r.key.equals(key)) return new Device(r.key, r.family, r.kind, name);
        if (key.equals(STB) || key.equals(PC) || key.equals(HDMI) || key.equals(CONSOLE) || key.equals(STREAMER)) {
            return new Device(key, key, key, name);
        }
        return null;
    }
}
