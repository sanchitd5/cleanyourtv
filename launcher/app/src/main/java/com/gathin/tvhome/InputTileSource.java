package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Android glue for HDMI input tiles: the one enumeration of the TV's inputs that the Inputs menu
 * (MainActivity.showInputs) and the tiles share (HOME-DESIGN.md 6.3), device detection (5.2), the remembered
 * device (5.4) and the theme logo decode (5.6).
 */
final class InputTileSource {
    private static final String TAG = "InputTileSource";
    private static final String MEMORY = "input_devices";
    private static final int MAX_LOGO_BYTES = 512 * 1024;
    private static final int CEC_AUDIO_SYSTEM = 5;

    private final Context ctx;
    private final boolean showEmpty;
    private final Map<String, String> logos;
    private final List<String> inputSources;
    private final boolean cecStateOnly;

    InputTileSource(Context ctx, Config cfg) {
        this.ctx = ctx;
        this.showEmpty = cfg.inputTilesShowEmpty;
        this.logos = cfg.inputLogos;
        this.inputSources = cfg.inputSources;
        this.cecStateOnly = cfg.inputsCecStateOnly;
    }

    /** What the CEC bus says about a device; name null and numbers -1 when unknown. */
    private static final class Cec {
        String name;
        int vendor = -1;
        int type = -1;
    }

    // @SystemApi, not in the public SDK (HOME-DESIGN.md 2). Best effort: any failure means "no CEC info".
    private static Method hdmiInfo, displayName, vendorId, deviceType;   // looked up once, null after a failure
    private static boolean unavailable;
    private static boolean logoLogged;

    @SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})   // getHdmiDeviceInfo is @SystemApi: no public way to read the CEC name
    private static Cec read(TvInputInfo info) {
        Cec cec = new Cec();
        if (info == null || unavailable) return cec;
        try {
            if (hdmiInfo == null) hdmiInfo = TvInputInfo.class.getMethod("getHdmiDeviceInfo");
            Object device = hdmiInfo.invoke(info);
            if (device == null) return cec;   // an input with no CEC device: not a failure
            if (displayName == null) {
                Class<?> type = device.getClass();
                displayName = type.getMethod("getDisplayName");
                vendorId = type.getMethod("getVendorId");
                deviceType = type.getMethod("getDeviceType");
            }
            Object name = displayName.invoke(device);
            cec.name = name == null ? null : name.toString();
            cec.vendor = ((Number) vendorId.invoke(device)).intValue();
            cec.type = ((Number) deviceType.invoke(device)).intValue();
        } catch (ReflectiveOperationException | RuntimeException e) {
            unavailable = true;
            cec = new Cec();
        }
        return cec;
    }

    /**
     * Every HDMI port as a tile, visible or not, in the menu's port order. A port that is not visible is only
     * named; detection runs for the ones that are. Empty without a TvInputManager.
     */
    List<InputTiles.Tile> list() {
        List<InputTiles.Tile> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        List<Port> ports;
        try {
            ports = ports(ctx, tim, inputSources, cecStateOnly);
        } catch (RuntimeException e) {
            return out;
        }
        SharedPreferences memory = ctx.getSharedPreferences(MEMORY, Context.MODE_PRIVATE);
        File dir = Config.dir(ctx);
        for (Port port : ports) {
            if (!port.hdmi) continue;
            String portId = port.info.getId();
            boolean hasDevice = !port.devices.isEmpty();
            boolean visible = InputTiles.visible(showEmpty, hasDevice, port.stateTrusted, port.state);
            if (!visible) {
                out.add(new InputTiles.Tile(portId, portId, port.label, port.label, null, port.state, null, false));
                continue;
            }
            // The port's CEC device: not the audio system when there is another one.
            TvInputInfo child = null;
            int childAt = -1;
            Cec cec = new Cec();
            for (int i = 0; i < port.devices.size(); i++) {
                Cec c = read(port.devices.get(i));
                if (childAt < 0 || (cec.type == CEC_AUDIO_SYSTEM && c.type != CEC_AUDIO_SYSTEM)) {
                    child = port.devices.get(i);
                    childAt = i;
                    cec = c;
                }
            }
            if (cec.name == null && cec.vendor < 0) {
                Cec onPort = read(port.info);
                if (onPort.name != null || onPort.vendor >= 0) cec = onPort;
            }
            InputDevices.Device device = fromCec(cec);
            if (device == null && child != null) {
                device = InputDevices.fromLabel(label(child.loadCustomLabel(ctx)));
                if (device == null) device = InputDevices.fromLabel(label(child.loadLabel(ctx)));
            }
            if (device == null && cec.name != null && !cec.name.trim().isEmpty()) {
                device = InputDevices.detect(cec.name, -1, cec.type);
            }
            if (device != null) {
                String text = InputDevices.encode(device);
                if (!text.equals(memory.getString(portId, null))) memory.edit().putString(portId, text).apply();
            } else {
                boolean named = port.custom != null && port.custom.length() > 0 && !port.custom.toString().equals(port.label);
                if (named) {
                    device = InputDevices.fromLabel(port.custom.toString());
                    if (device == null) {
                        device = new InputDevices.Device(InputDevices.HDMI, InputDevices.HDMI, InputDevices.HDMI,
                                port.custom.toString());
                    }
                } else {
                    device = InputDevices.decode(memory.getString(portId, null));
                }
            }
            int state = port.state;
            if (childAt >= 0 && port.deviceStates.get(childAt) == TvInputManager.INPUT_STATE_CONNECTED) {
                state = TvInputManager.INPUT_STATE_CONNECTED;
            } else if (state < 0 && childAt >= 0) {
                state = port.deviceStates.get(childAt);
            }
            String logo = InputTiles.logoFor(device, logos);
            if (logo != null && (dir == null || !new File(new File(dir, ThemeFiles.INPUTS), logo).isFile())) logo = null;
            out.add(new InputTiles.Tile(portId, child != null ? child.getId() : portId,
                    device != null ? device.name : port.label, port.label, device, state, logo, true));
        }
        return out;
    }

    private static String label(CharSequence text) {
        return text == null ? null : text.toString();
    }

    /** Steps 1 and 2 of the detection: the OSD name through the table, else the vendor id. */
    private static InputDevices.Device fromCec(Cec cec) {
        InputDevices.Device byName = InputDevices.fromLabel(cec.name);
        if (byName != null) return byName;
        InputDevices.Device byVendor = InputDevices.detect("", cec.vendor, cec.type);
        if (byVendor == null) return null;
        // A vendor-only match for a maker (Sony, Samsung) takes the OSD name as the wordmark when there is one.
        boolean generic = InputDevices.HDMI.equals(byVendor.key) || InputDevices.STB.equals(byVendor.key);
        if (generic && cec.name != null && !cec.name.trim().isEmpty()) {
            return new InputDevices.Device(byVendor.key, byVendor.family, byVendor.kind, cec.name.trim());
        }
        return byVendor;
    }

    /**
     * The theme logo composed onto the kind's backdrop as a 320x180 banner (the way icon_tiles composes an icon), or
     * null when the file is missing, too large or unreadable: the tile then keeps its glyph and name.
     */
    static Drawable logoBanner(Context ctx, String file, String kind) {
        File dir = Config.dir(ctx);
        if (dir == null || !ThemeFiles.collectionImage(file)) return null;
        File f = new File(new File(dir, ThemeFiles.INPUTS), file);
        if (!f.isFile() || f.length() > MAX_LOGO_BYTES) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return noLogo(file);
            int sample = 1;
            while (Math.max(o.outWidth, o.outHeight) / sample > 640) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            Bitmap logo = BitmapFactory.decodeFile(f.getPath(), o);
            if (logo == null) return noLogo(file);
            Bitmap out = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(out);
            GradientDrawable back = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    InputTiles.kindBackdrop(kind));
            back.setBounds(0, 0, 320, 180);
            back.draw(canvas);
            float scale = Math.min(205f / logo.getWidth(), 101f / logo.getHeight());
            float w = logo.getWidth() * scale, h = logo.getHeight() * scale;
            RectF dst = new RectF((320 - w) / 2f, (180 - h) / 2f, (320 + w) / 2f, (180 + h) / 2f);
            canvas.drawBitmap(logo, null, dst, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
            logo.recycle();
            return new BitmapDrawable(ctx.getResources(), out);
        } catch (RuntimeException e) {
            return noLogo(file);
        }
    }

    private static Drawable noLogo(String file) {
        if (!logoLogged) {
            logoLogged = true;
            Log.w(TAG, "input logo unreadable: " + file);
        }
        return null;
    }

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
