package com.gathin.tvhome;

import android.media.tv.TvInputManager;

import java.util.List;
import java.util.Map;

/** Pure rules for HDMI input tiles (HOME-DESIGN.md section 5 and owner decision 4). The Android glue is InputTileSource. */
final class InputTiles {
    private InputTiles() {}

    /** One HDMI port as a tile. Immutable; {@code state} is a TvInputManager INPUT_STATE_* value or -1 when unknown. */
    static final class Tile {
        final String portId;       // the port's input id; the tile's key is built from it
        final String launchId;     // what openInput gets: the CEC child's id when the port has one, else portId
        final String name;         // the wordmark
        final String portName;     // "HDMI 1"
        final InputDevices.Device device;   // null when nothing identified the device
        final int state;
        final String logoFile;     // a file name under inputs/ the theme maps this device to, or null
        final boolean visible;     // the result of InputTiles.visible for this port

        Tile(String portId, String launchId, String name, String portName, InputDevices.Device device, int state,
             String logoFile, boolean visible) {
            this.portId = portId;
            this.launchId = launchId;
            this.name = name;
            this.portName = portName;
            this.device = device;
            this.state = state;
            this.logoFile = logoFile;
            this.visible = visible;
        }
    }

    /** The tile key ("input:<port id>"); it goes in dock_apps and is the entry's package. */
    static String key(String portId) {
        return LaunchActions.inputKey(portId);
    }

    /**
     * Whether a port gets a tile. Naming a port in dock_apps decides its position only, never its visibility: a port
     * with no device (nothing on the CEC bus, nothing reported connected) is hidden and the tile comes back when a
     * device is seen again. {@code showEmpty} is input_tiles_show_empty, {@code hasDevice} that the port has a CEC
     * child input, {@code stateTrusted} that the TV's port states can be believed. CONNECTED_STANDBY alone does not
     * show a tile, as in InputFold.
     */
    static boolean visible(boolean showEmpty, boolean hasDevice, boolean stateTrusted, int state) {
        return showEmpty || hasDevice || (stateTrusted && state == TvInputManager.INPUT_STATE_CONNECTED);
    }

    /**
     * What changes the set of tiles or how they look, not whether a device is on: port id, device key, name, logo file
     * and the visible flag, in list order. A change of connection state alone leaves it as it was.
     */
    static String signature(List<Tile> tiles) {
        StringBuilder out = new StringBuilder();
        for (Tile t : tiles) {
            out.append(t.portId).append('|').append(t.device == null ? "" : t.device.key).append('|')
                    .append(t.name).append('|').append(t.logoFile == null ? "" : t.logoFile).append('|')
                    .append(t.visible ? '1' : '0').append('\n');
        }
        return out.toString();
    }

    /**
     * The theme logo file for a device: the entry for its key, else its family, else its kind. An entry whose value is
     * not a name ThemeFiles accepts is skipped. The caller checks the file exists.
     */
    static String logoFor(InputDevices.Device device, Map<String, String> logos) {
        if (device == null || logos == null || logos.isEmpty()) return null;
        for (String k : new String[]{device.key, device.family, device.kind}) {
            String file = k == null ? null : logos.get(k);
            if (ThemeFiles.collectionImage(file)) return file;
        }
        return null;
    }

    /** The shelf's meta line: the port, then the state when there is one ("HDMI 1 · Connected"). */
    static String meta(String portName, String stateText) {
        if (stateText == null || stateText.isEmpty()) return portName;
        return portName + " · " + stateText;
    }

    /** The kind's glass and shelf accent, ARGB. Generic hues, not brand colours. */
    static int kindAccent(String kind) {
        if (InputDevices.CONSOLE.equals(kind)) return 0xFF4C8DFF;
        if (InputDevices.STREAMER.equals(kind)) return 0xFFB07CFF;
        if (InputDevices.STB.equals(kind)) return 0xFF3FD0C0;
        if (InputDevices.PC.equals(kind)) return 0xFFA9B4C6;
        return 0xFF9AA6B8;
    }

    /** The kind's backdrop, top then bottom, ARGB. */
    static int[] kindBackdrop(String kind) {
        if (InputDevices.CONSOLE.equals(kind)) return new int[]{0xFF1C2740, 0xFF0A0E18};
        if (InputDevices.STREAMER.equals(kind)) return new int[]{0xFF2B1D3A, 0xFF100A17};
        if (InputDevices.STB.equals(kind)) return new int[]{0xFF14302E, 0xFF081312};
        if (InputDevices.PC.equals(kind)) return new int[]{0xFF2A2C31, 0xFF0E0F12};
        return new int[]{0xFF20242C, 0xFF0C0E12};
    }
}
