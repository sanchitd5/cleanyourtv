package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * The panel the remote's Settings button opens when {@code settings_key_panel} is on (HomeKeyService), and
 * Home itself with {@code quick_panel_shortcuts}: a few glass rows under the Settings pill, or the tiles of
 * {@code quick_panel}, over Home or whatever app is playing. Translucent and in a task of its own, so the
 * app behind keeps running and Back returns to it. Pressing Settings again closes the panel.
 */
public final class QuickSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }


    /** A quick_panel entry that this TV can open. */
    private static final class Tile {
        final String label, target, glyph;
        final Intent intent;
        final int kind;

        Tile(String label, String target, Intent intent) {
            this.label = label;
            this.target = target;
            this.intent = intent;
            this.glyph = QuickPanelMap.glyph(target);
            this.kind = QuickPanelMap.kind(target);
        }
    }

    private GlassMotionController motion;
    private View firstFocus;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Config cfg = Config.load(this);
        Glass.configure(cfg);   // this activity can be the first of the process to run; the reduce-transparency row is read here too
        setContentView(R.layout.activity_quick_settings);
        View panel = findViewById(R.id.panel);
        LinearLayout rows = findViewById(R.id.rows);
        List<Tile> tiles = resolve(cfg.quickPanel);
        // Control Center layout (glass on, with quick_panel tiles): a tall sheet docked right. Anything else keeps the old
        // compact panel under the Settings pill, exactly as before.
        boolean center = Glass.on() && !tiles.isEmpty();
        if (!center) {
            float d = getResources().getDisplayMetrics().density;
            android.widget.FrameLayout.LayoutParams old = (android.widget.FrameLayout.LayoutParams) panel.getLayoutParams();
            old.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            old.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            old.topMargin = (int) (105 * d);
            old.bottomMargin = 0;
            old.setMarginEnd((int) (56 * d));
            panel.setLayoutParams(old);
        }
        if (getResources().getBoolean(R.bool.rtl_menu_bar)) {
            android.widget.FrameLayout.LayoutParams position =
                    (android.widget.FrameLayout.LayoutParams) panel.getLayoutParams();
            position.gravity = android.view.Gravity.TOP | android.view.Gravity.LEFT;
            int inset = position.getMarginEnd();
            position.setMarginEnd(0);
            position.setMargins(inset, position.topMargin, 0, position.bottomMargin);
            panel.setLayoutParams(position);
        }
        findViewById(R.id.scrim).setOnClickListener(v -> finish());
        if (center) {
            rows.setVisibility(View.GONE);
            buildCenter(tiles);
        } else if (!tiles.isEmpty()) {
            addTiles(rows, tiles);
        } else {
            addRow(rows, R.drawable.ic_settings, R.string.tv_settings, 0, SystemSettings.intent(this, Settings.ACTION_SETTINGS));
            addRow(rows, R.drawable.ic_picture, R.string.quick_picture, 0, SystemSettings.displayIntent(this));
            addRow(rows, R.drawable.ic_wifi, R.string.quick_network, 0, SystemSettings.intent(this, Settings.ACTION_WIFI_SETTINGS));
            addRow(rows, R.drawable.ic_apps, R.string.quick_apps, R.string.quick_apps_hint,
                    SystemSettings.intent(this, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
            // Through Home, so it rebuilds with the new choices when Launcher settings closes.
            addRow(rows, R.drawable.ic_home_settings, R.string.launcher_settings, 0,
                    new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS));
        }
        // Glass (19.6): over Home (GlassBackdrop.homeVisible, Home is only paused under this window) the pre-blurred backdrop;
        // over an app or an HDMI input the fixed 78 % smoked tint (no cross-window blur on this TV); Solid with reduce
        // transparency. homeHosted=false so the material follows homeVisible. Live backdrop: Home keeps the clip playing
        // under this panel and samples it at 1 Hz (MainActivity.onPause, "video under panel"); this Activity needs no handle on it.
        // The Picture, Sound and other modules open the vendor's MediaTek activities, whose own screens cannot be restyled.
        if (Glass.apply(panel, center ? CENTER_RADIUS_DP : GlassSurface.POPUP_RADIUS_DP, false)) {
            Glass.rows((ViewGroup) panel);
            if (center) restyleCenter();
            // Open spring and exit fade from the sheet's right edge (pivot at the right, middle: it grows from the edge it is docked to).
            motion = new GlassMotionController(panel, Glass.animations(), Glass.reducedMotion());
            motion.watch((ViewGroup) panel, center);
            motion.prepareOpen(1f, center ? 0.5f : 0f);
            motion.runOpen();
        }
        // The first module (Picture), else the first control, else the first row.
        View first = firstFocus != null ? firstFocus : rows.getChildAt(0);
        if (first != null) first.requestFocus();
    }

    /** Back fades the panel out first, with glass; a chosen tile still closes at once. */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (motion != null) motion.close(this::finish);
        else super.onBackPressed();
    }

    /**
     * Settings pressed again while the panel is up, or the system toggling its notification panel
     * (QuickPanel in the manifest).
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    /** Anything else taking the screen (Home, an app, a screensaver) closes the panel. */
    @Override
    @SuppressWarnings("deprecation")
    protected void onPause() {
        super.onPause();
        finish();
        overridePendingTransition(0, 0);
    }

    private void addRow(LinearLayout rows, int icon, int title, int status, Intent target) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        TextView hint = row.findViewById(R.id.status);
        if (status != 0) hint.setText(status);
        else hint.setVisibility(View.GONE);
        opens(row, target);
        rows.addView(row);
    }

    /** OK on a row or a tile opens its target and closes the panel. */
    private void opens(View view, Intent target) {
        view.setOnClickListener(v -> {
            try {
                startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException | SecurityException e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
            finish();
        });
    }

    /** The quick_panel entries this TV can open, in order: one that nothing answers, or whose activity is not exported, is left out. */
    private List<Tile> resolve(List<String> entries) {
        List<Tile> out = new ArrayList<>();
        for (String entry : entries) {
            String[] tile = tile(entry);
            if (tile == null) continue;
            Intent target = target(tile[1]);
            ResolveInfo found = getPackageManager().resolveActivity(target, PackageManager.MATCH_DEFAULT_ONLY);
            if (found == null || !found.activityInfo.exported) continue;
            out.add(new Tile(tile[0], tile[1], target));
        }
        return out;
    }

    /** Without glass: the old grid of the top bar's pills, two to a row. */
    private void addTiles(LinearLayout rows, List<Tile> tiles) {
        float density = getResources().getDisplayMetrics().density;
        int gap = (int) (4 * density);
        LinearLayout pair = null;
        for (Tile tile : tiles) {
            TextView view = new TextView(this, null, 0, R.style.TopButton);
            view.setText(tile.label);
            view.setSingleLine();
            opens(view, tile.intent);
            if (pair == null || pair.getChildCount() > 1) rows.addView(pair = new LinearLayout(this));
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams((int) (150 * density), (int) (42 * density));
            size.setMargins(gap, gap, gap, gap);
            pair.addView(view, size);
        }
    }

    private static final float CENTER_RADIUS_DP = 22f;   // 44 px: concentric with the 14 dp modules and the 8 dp padding

    /** Header, round controls and module grid, in quick_panel order within each group. Typography through UiFont. */
    private void buildCenter(List<Tile> tiles) {
        float d = getResources().getDisplayMetrics().density;
        findViewById(R.id.header).setVisibility(View.VISIBLE);
        TextView time = findViewById(R.id.qp_time), date = findViewById(R.id.qp_date);
        time.setTypeface(UiFont.get(this, R.string.font_light));
        date.setTypeface(UiFont.get(this, R.string.font_medium));
        LinearLayout controls = findViewById(R.id.controls), grid = findViewById(R.id.grid);
        LinearLayout pair = null;
        for (Tile tile : tiles) {
            if (tile.kind == QuickPanelMap.CONTROL) {
                controls.setVisibility(View.VISIBLE);
                controls.addView(control(tile, d), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            } else {
                grid.setVisibility(View.VISIBLE);
                if (pair == null || pair.getChildCount() > 1) {
                    pair = new LinearLayout(this);
                    LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (66 * d));
                    row.bottomMargin = (int) (8 * d);
                    grid.addView(pair, row);
                }
                View module = module(tile, d);
                LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                at.setMargins((int) (4 * d), 0, (int) (4 * d), 0);   // 8 dp (16 px) between modules
                pair.addView(module, at);
                if (firstFocus == null) firstFocus = module;
            }
        }
        // An odd module keeps its column width: an invisible twin takes the other half.
        if (pair != null && pair.getChildCount() == 1) pair.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        if (firstFocus == null && !circles.isEmpty()) firstFocus = circles.get(0);
    }

    private final List<View> circles = new ArrayList<>();
    private final List<View> modules = new ArrayList<>();

    /** A 44 dp circle (88 px) with a 12 sp label under it; only the circle takes focus. */
    private View control(Tile tile, float d) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        ImageView circle = new ImageView(this);
        circle.setImageResource(glyph(tile.glyph));
        circle.setScaleType(ImageView.ScaleType.CENTER);
        circle.setFocusable(true);
        circle.setClickable(true);
        circle.setContentDescription(tile.label);
        opens(circle, tile.intent);
        circles.add(circle);
        cell.addView(circle, new LinearLayout.LayoutParams((int) (44 * d), (int) (44 * d)));
        TextView label = new TextView(this);
        label.setText(tile.label);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        label.setTextColor(0xC7FFFFFF);
        label.setGravity(android.view.Gravity.CENTER);
        label.setMaxLines(2);
        label.setTypeface(UiFont.get(this, R.string.font_medium));
        LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        at.topMargin = (int) (6 * d);
        cell.addView(label, at);
        return cell;
    }

    /** A rounded module: glyph top-left, 17 sp label bottom-left (shrinks to fit one line). */
    private View module(Tile tile, float d) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * d);
        box.setPadding(pad, (int) (10 * d), pad, (int) (10 * d));
        box.setFocusable(true);
        box.setClickable(true);
        ImageView icon = new ImageView(this);
        icon.setImageResource(glyph(tile.glyph));
        icon.setDuplicateParentStateEnabled(true);
        box.addView(icon, new LinearLayout.LayoutParams((int) (22 * d), (int) (22 * d)));
        box.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
        TextView label = new TextView(this);
        label.setText(tile.label);
        label.setSingleLine();
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setTypeface(UiFont.get(this, R.string.font_medium));
        label.setDuplicateParentStateEnabled(true);
        label.setAutoSizeTextTypeUniformWithConfiguration(13, 17, 1, TypedValue.COMPLEX_UNIT_SP);
        box.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        opens(box, tile.intent);
        modules.add(box);
        return box;
    }

    /** After Glass.rows: the circle and module backgrounds in place of the row capsule, and a tile's text sizes. */
    private void restyleCenter() {
        for (View c : circles) c.setBackgroundResource(R.drawable.glass_circle_bg);
        for (View m : modules) m.setBackgroundResource(R.drawable.glass_tile_bg);
    }

    private static int glyph(String key) {
        switch (key) {
            case QuickPanelMap.INPUTS: return R.drawable.ic_input;
            case QuickPanelMap.WIFI: return R.drawable.ic_wifi;
            case QuickPanelMap.BLUETOOTH: return R.drawable.ic_qp_bluetooth;
            case QuickPanelMap.SETTINGS: return R.drawable.ic_settings;
            case QuickPanelMap.PICTURE: return R.drawable.ic_picture;
            case QuickPanelMap.SOUND: return R.drawable.ic_qp_sound;
            case QuickPanelMap.SCREEN: return R.drawable.ic_qp_screen;
            case QuickPanelMap.AUDIO: return R.drawable.ic_qp_audio;
            default: return R.drawable.ic_apps;
        }
    }

    /** A quick_panel entry, "Label=target", as {label, target}; null when a half is missing. */
    static String[] tile(String entry) {
        int eq = entry.indexOf('=');
        if (eq < 0) return null;
        String label = entry.substring(0, eq).trim(), target = entry.substring(eq + 1).trim();
        return label.isEmpty() || target.isEmpty() ? null : new String[]{label, target};
    }

    /** A tile's target: Home's Inputs menu for the word inputs, an activity for "package/.Activity", else an action. */
    private Intent target(String target) {
        if (target.equals("inputs")) {
            return new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_VIEW_INPUTS);
        }
        ComponentName component = ComponentName.unflattenFromString(target);
        return component != null ? new Intent().setComponent(component) : new Intent(target);
    }
}
