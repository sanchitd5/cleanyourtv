package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
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


    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Config cfg = Config.load(this);
        Glass.configure(cfg);   // this activity can be the first of the process to run; the reduce-transparency row is read here too
        setContentView(R.layout.activity_quick_settings);
        if (getResources().getBoolean(R.bool.rtl_menu_bar)) {
            View panel = findViewById(R.id.panel);
            android.widget.FrameLayout.LayoutParams position =
                    (android.widget.FrameLayout.LayoutParams) panel.getLayoutParams();
            position.gravity = android.view.Gravity.TOP | android.view.Gravity.LEFT;
            int inset = position.getMarginEnd();
            position.setMarginEnd(0);
            position.setMargins(inset, position.topMargin, 0, position.bottomMargin);
            panel.setLayoutParams(position);
        }
        findViewById(R.id.scrim).setOnClickListener(v -> finish());
        LinearLayout rows = findViewById(R.id.rows);
        if (!addTiles(rows, cfg.quickPanel)) {
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
        // transparency. homeHosted=false so the material follows homeVisible. No live sample is requested here: Home's own
        // onPause already took one of the playing clip (MainActivity.onPause), and this Activity has no handle on the
        // WallpaperPlayer, so the panel shows that frame, or the still's copy.
        // TODO(G4): open and dismiss springs and highlight parallax: wire GlassMotionController-style calls on @id/panel here.
        if (Glass.apply(findViewById(R.id.panel), GlassSurface.POPUP_RADIUS_DP, false)) {
            Glass.rows(rows);
            for (View tile : tiles) chip(tile, cfg);
        }
        rows.getChildAt(0).requestFocus();   // the first row, or the first tile of the first pair
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

    private final List<View> tiles = new ArrayList<>();

    /**
     * A tile as a glass chip: faint fill at rest, the white capsule with dark ink when focused (Glass.rows has already set the
     * ink), a 1.04 zoom on focus. The Picture, Sound and other tiles open the vendor's MediaTek activities; those screens are
     * another app's and cannot be restyled from here.
     */
    private void chip(View tile, Config cfg) {
        tile.setBackgroundResource(R.drawable.glass_tile_bg);
        ((TextView) tile).setTypeface(UiFont.get(this, R.string.font_medium));   // follows a later font switch
        final boolean animate = cfg.animations && !cfg.reducedMotion;
        tile.setOnFocusChangeListener((v, focused) -> {
            float scale = focused ? GlassMotion.CHIP_FOCUS_SCALE : 1f;
            if (animate) v.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
            else { v.setScaleX(scale); v.setScaleY(scale); }
        });
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

    /**
     * quick_panel: the panel as a grid of the top bar's glass pills, two to a row, one per "Label=target" entry.
     * A tile this TV cannot open is left out: nothing answers its target, or the activity is not exported.
     * False when no tile is left (or the list is empty): the rows show then.
     */
    private boolean addTiles(LinearLayout rows, List<String> entries) {
        float density = getResources().getDisplayMetrics().density;
        int gap = (int) (4 * density);
        LinearLayout pair = null;
        for (String entry : entries) {
            String[] tile = tile(entry);
            if (tile == null) continue;
            Intent target = target(tile[1]);
            ResolveInfo found = getPackageManager().resolveActivity(target, PackageManager.MATCH_DEFAULT_ONLY);
            if (found == null || !found.activityInfo.exported) continue;
            TextView view = new TextView(this, null, 0, R.style.TopButton);
            view.setText(tile[0]);
            view.setSingleLine();
            opens(view, target);
            tiles.add(view);
            if (pair == null || pair.getChildCount() > 1) rows.addView(pair = new LinearLayout(this));
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams((int) (150 * density), (int) (42 * density));
            size.setMargins(gap, gap, gap, gap);
            pair.addView(view, size);
        }
        return pair != null;
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
