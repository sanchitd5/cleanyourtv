package com.gathin.tvhome;

import android.content.Context;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Android glue for HDMI input tiles. For now it holds the one enumeration of the TV's inputs that the Inputs menu
 * (MainActivity.showInputs) and the tiles share (HOME-DESIGN.md 6.3); device detection, the remembered device and
 * the logo decode are added here by the tiles step.
 */
final class InputTileSource {
    private InputTileSource() {}

    /** One passthrough input the menu lists: an HDMI port or another jack, with the CEC devices on it. */
    static final class Port {
        TvInputInfo info;
        /** CEC child inputs on this port (a device takes its port's row), possibly empty. */
        List<TvInputInfo> devices = Collections.emptyList();
        /** Each device's input state, or -1 when the TV's HDMI states cannot be trusted; parallel to {@link #devices}. */
        List<Integer> deviceStates = Collections.emptyList();
        /** The input's label, "HDMI " first when it is an HDMI port whose label lacks it ("HDMI 1"). */
        String label;
        /** The name given in the TV's Settings > Inputs, tidied, or null. */
        CharSequence custom;
        boolean hdmi;
        /** The port's own input state, or -1 when it cannot be trusted or the input is not HDMI. */
        int state;
        /** False when the TV puts port states on the wrong port (inputs_cec_state_only, or every port reports connected). */
        boolean stateTrusted;
    }

    /** The TV's inputs, HDMI ports first, then the rest; each group by label. */
    static List<TvInputInfo> sortedInputs(Context ctx, TvInputManager tim) {
        List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
        // HDMI ports first in port order, then AV and the rest.
        Collections.sort(inputs, (a, b) -> {
            boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
            if (ha != hb) return ha ? -1 : 1;
            return String.valueOf(a.loadLabel(ctx)).compareToIgnoreCase(String.valueOf(b.loadLabel(ctx)));
        });
        return inputs;
    }

    static List<Port> ports(Context ctx, TvInputManager tim, List<String> inputSources, boolean cecStateOnly) {
        return ports(ctx, tim, sortedInputs(ctx, tim), inputSources, cecStateOnly);
    }

    /**
     * The passthrough inputs in menu order, with the rules the Inputs menu has always applied: an input hidden in the
     * TV's Settings, or left out by input_sources ("HW8=-"), is skipped; a CEC device's input is folded into its
     * port; and the port states are dropped when the TV cannot be trusted with them.
     */
    static List<Port> ports(Context ctx, TvInputManager tim, List<TvInputInfo> inputs, List<String> inputSources,
                            boolean cecStateOnly) {
        // An HDMI-CEC device (an Apple TV, a soundbar) gets an input of its own whose parent is the
        // port it sits on, so the port and the device would each get a row (Vestel MT7332: "HDMI 1"
        // and "HDMI Apple TV"). The device takes its port's row instead, and a bare port is listed
        // only when nothing on it speaks CEC.
        Set<String> inputIds = new HashSet<>();
        for (TvInputInfo info : inputs) inputIds.add(info.getId());
        Map<String, List<TvInputInfo>> cecDevices = new HashMap<>();
        for (TvInputInfo info : inputs) {
            String parent = info.getParentId();
            if (parent == null || !inputIds.contains(parent)) continue;
            if (!info.isPassthroughInput() || info.isHidden(ctx)) continue;
            List<TvInputInfo> onPort = cecDevices.get(parent);
            if (onPort == null) cecDevices.put(parent, onPort = new ArrayList<>());
            onPort.add(info);
        }
        // Some TVs (Sony) report every HDMI port as connected whether or not anything is plugged in,
        // so drop the HDMI status then. A TV with every port really in use loses it too.
        int hdmiPorts = 0, hdmiConnected = 0;
        for (TvInputInfo info : inputs) {
            if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
            if (info.getParentId() != null && inputIds.contains(info.getParentId())) continue;
            hdmiPorts++;
            if (tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED) hdmiConnected++;
        }
        // inputs_cec_state_only: the TV puts port states on the wrong port (TCL UnionTV, Android 9), so
        // ports show none, and a CEC device row shows its own state, which the CEC bus keeps right.
        boolean hdmiStateBogus = cecStateOnly || (hdmiPorts > 1 && hdmiConnected == hdmiPorts);
        List<Port> ports = new ArrayList<>();
        for (TvInputInfo info : inputs) {
            // Inputs hidden in the TV's Settings > Inputs stay out of the menu too.
            if (!info.isPassthroughInput() || info.isHidden(ctx)) continue;
            // input_sources can leave out ports the board reports but the set does not have ("HW8=-").
            if (Integer.valueOf(-1).equals(InputSources.mapped(inputSources, info.getId()))) continue;
            // A CEC device's row is added with its port, below.
            if (info.getParentId() != null && inputIds.contains(info.getParentId())) continue;
            Port port = new Port();
            port.info = info;
            CharSequence l = info.loadLabel(ctx);
            String label = l == null ? info.getId() : l.toString();
            port.hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
            if (port.hdmi && !label.toUpperCase(Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
            port.label = label;
            port.custom = InputNames.tidy(info.loadCustomLabel(ctx));
            port.stateTrusted = !hdmiStateBogus;
            List<TvInputInfo> devices = cecDevices.get(info.getId());
            if (devices != null) {
                port.devices = devices;
                List<Integer> states = new ArrayList<>(devices.size());
                for (TvInputInfo device : devices) {
                    states.add(hdmiStateBogus && !cecStateOnly ? -1 : tim.getInputState(device.getId()));
                }
                port.deviceStates = states;
            }
            // Analog AV jacks have no hotplug detection, and some TVs report them as always connected
            // (TCL Smart TV Pro G03 says "Connected" with nothing plugged in), so AV rows show no state.
            port.state = !port.hdmi || hdmiStateBogus ? -1 : tim.getInputState(info.getId());
            ports.add(port);
        }
        return ports;
    }
}
