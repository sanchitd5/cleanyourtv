package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private static final String[] CLOCK_FORMATS = {"system", "12", "24"};
    private final TextView[] clockChoices = new TextView[CLOCK_FORMATS.length];
    private TextView wallpaperChoice;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private TextView openOnStartChoice;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private String clockFormat;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        View reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        clockFormat = config.clockFormat;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] dockIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < dockIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(dockIds[i]);
            dockChoices[i].setOnClickListener(v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] clockIds = {R.id.clockSystem, R.id.clock12, R.id.clock24};
        for (int i = 0; i < clockIds.length; i++) {
            final String value = CLOCK_FORMATS[i];
            clockChoices[i] = findViewById(clockIds[i]);
            clockChoices[i].setOnClickListener(v -> {
                if (value.equals(clockFormat)) return;
                clockFormat = value;
                choices.edit().putString(Config.CLOCK_FORMAT_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // Mirror text alignment without globally opting the launcher into RTL. Numeric rows
        // still need explicit ordering in Android's no-supportsRtl compatibility mode.
        if (getResources().getBoolean(R.bool.rtl_menu_bar))
            findViewById(android.R.id.content).setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
            reverseRow(dockChoices);
            reverseRow(clockChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        openOnStartChoice = findViewById(R.id.openOnStartChoice);
        if (config.openOnStartChoice) {
            openOnStartChoice.setVisibility(View.VISIBLE);
            openOnStartChoice.setOnClickListener(v -> showOpenOnStartPicker());
        }
        buildHomeLook((LinearLayout) findViewById(R.id.homeLookRows));
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            Config.clearHorizonChoices(choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove(Config.CLOCK_FORMAT_KEY).remove(OpenOnStart.KEY)).commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            dockOpacity = current.dockOpacity;
            clockFormat = current.clockFormat;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            changed();
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v ->
                open(new Intent(this, HomeSettingsActivity.class)));
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        refresh();
        if (config.launcherSettingsButton) reorderChoice.requestFocus();
        else columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    /** How a Home look row shows and lists its values. */
    private enum Fmt { NUMBER, SWITCH, PERCENT, SECONDS_OFF, MINUTES_OFF, SECONDS_NEVER, MINUTES_NEVER, PARALLAX, GREETING, PHOTOS }

    private static final class HomeRow {
        final String key;
        final int label;
        final int[] values;
        final Fmt fmt;
        TextView view;

        HomeRow(String key, int label, int[] values, Fmt fmt) {
            this.key = key;
            this.label = label;
            this.values = values;
            this.fmt = fmt;
        }
    }

    private final List<HomeRow> homeRows = new ArrayList<>();

    /**
     * The "Home look" rows: one table, one row view each, in the settings-row style. OK opens that row's list of
     * values; the list's "Follow theme" forgets just that row. A row's choice is saved under its theme key
     * (Config.*_KEY) and read back by Config.load after the theme, so it survives a theme push.
     */
    private void buildHomeLook(LinearLayout container) {
        homeRows.add(new HomeRow(Config.DOCK_TILES_KEY, R.string.dock_tiles_label, Config.DOCK_TILE_CHOICES, Fmt.NUMBER));
        homeRows.add(new HomeRow(Config.TOP_SHELF_KEY, R.string.top_shelf_label, Config.SWITCH_CHOICES, Fmt.SWITCH));
        homeRows.add(new HomeRow(Config.TOP_SHELF_SCRIM_KEY, R.string.shelf_scrim_label, Config.TOP_SHELF_SCRIM_CHOICES, Fmt.PERCENT));
        homeRows.add(new HomeRow(Config.INPUT_TILES_KEY, R.string.input_tiles_label, Config.SWITCH_CHOICES, Fmt.SWITCH));
        homeRows.add(new HomeRow(Config.INPUT_TILES_EMPTY_KEY, R.string.input_tiles_empty_label, Config.SWITCH_CHOICES, Fmt.SWITCH));
        homeRows.add(new HomeRow(Config.GREETING_MODE_KEY, R.string.greeting_label, Config.GREETING_CHOICES, Fmt.GREETING));
        homeRows.add(new HomeRow(Config.FOCUS_PARALLAX_KEY, R.string.focus_parallax_label, Config.FOCUS_PARALLAX_CHOICES, Fmt.PARALLAX));
        homeRows.add(new HomeRow(Config.FOCUS_SHEEN_KEY, R.string.focus_sheen_label, Config.SWITCH_CHOICES, Fmt.SWITCH));
        homeRows.add(new HomeRow(Config.WALLPAPER_VIDEO_KEY, R.string.video_wallpaper_label, Config.SWITCH_CHOICES, Fmt.SWITCH));
        homeRows.add(new HomeRow(Config.VIDEO_MINUTES_KEY, R.string.clip_change_label, Config.VIDEO_MINUTES_CHOICES, Fmt.MINUTES_NEVER));
        homeRows.add(new HomeRow(Config.CAPTION_SECONDS_KEY, R.string.caption_fades_label, Config.CAPTION_SECONDS_CHOICES, Fmt.SECONDS_NEVER));
        homeRows.add(new HomeRow(Config.HOME_IDLE_SHIFT_KEY, R.string.idle_shift_label, Config.SWITCH_CHOICES, Fmt.SWITCH));
        homeRows.add(new HomeRow(Config.HOME_IDLE_CALM_KEY, R.string.home_calm_label, Config.HOME_IDLE_CALM_CHOICES, Fmt.SECONDS_OFF));
        homeRows.add(new HomeRow(Config.HOME_IDLE_DIM_KEY, R.string.home_dim_label, Config.HOME_IDLE_DIM_CHOICES, Fmt.SECONDS_OFF));
        homeRows.add(new HomeRow(Config.CLOCK_DIM_AFTER_KEY, R.string.clock_dim_label, Config.CLOCK_DIM_AFTER_CHOICES, Fmt.MINUTES_OFF));
        homeRows.add(new HomeRow(Config.CLOCK_BLANK_AFTER_KEY, R.string.clock_blank_label, Config.CLOCK_BLANK_AFTER_CHOICES, Fmt.MINUTES_OFF));
        homeRows.add(new HomeRow(Config.SCREENSAVER_PHOTOS_KEY, R.string.screensaver_photos_label, Config.SWITCH_CHOICES, Fmt.PHOTOS));
        homeRows.add(new HomeRow(Config.REDUCED_MOTION_KEY, R.string.reduced_motion_label, Config.SWITCH_CHOICES, Fmt.SWITCH));
        float dp = getResources().getDisplayMetrics().density;
        for (HomeRow row : homeRows) {
            TextView view = new TextView(this, null, 0, R.style.SettingsRow);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Math.round(52 * dp));
            params.topMargin = Math.round(10 * dp);
            view.setLayoutParams(params);
            view.setOnClickListener(v -> pickHomeRow(row));
            container.addView(view);
            row.view = view;
        }
    }

    private void pickHomeRow(HomeRow row) {
        String[] labels = new String[row.values.length];
        int saved = choices.getInt(row.key, -1);
        int checked = -1;
        for (int i = 0; i < labels.length; i++) {
            labels[i] = homeValue(row.fmt, row.values[i]);
            if (row.values[i] == saved) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(row.label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    choices.edit().putInt(row.key, row.values[which]).apply();
                    homeChanged();
                })
                .setNeutralButton(R.string.follow_theme_row, (dialog, which) -> {
                    Config.clearHorizonChoice(choices.edit(), row.key).apply();
                    homeChanged();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void homeChanged() {
        columns = Config.load(this).columns;   // dock tiles win over the columns row
        changed();
        refresh();
    }

    private String homeValue(Fmt fmt, int value) {
        switch (fmt) {
            case SWITCH: return getString(value == 1 ? R.string.on : R.string.off);
            case PERCENT: return getString(R.string.timing_percent, value);
            case SECONDS_OFF: return value == 0 ? getString(R.string.off) : getString(R.string.timing_seconds, value);
            case MINUTES_OFF: return value == 0 ? getString(R.string.off) : getString(R.string.timing_minutes, value);
            case SECONDS_NEVER: return value == 0 ? getString(R.string.never) : getString(R.string.timing_seconds, value);
            case MINUTES_NEVER: return value == 0 ? getString(R.string.never) : getString(R.string.timing_minutes, value);
            case PARALLAX: return getString(value == 0 ? R.string.off : value <= 2 ? R.string.level_low
                    : value <= 4 ? R.string.level_medium : R.string.level_high);
            case GREETING: return getString(value == Config.GREETING_OFF ? R.string.off
                    : value == Config.GREETING_ALWAYS ? R.string.greeting_always : R.string.greeting_once);
            case PHOTOS: return getString(value == 1 ? R.string.photos_folder : R.string.photos_home);
            default: return String.valueOf(value);
        }
    }

    /** A row's state text: its saved value, or Theme default; the screensaver photos row also says when its folder is empty. */
    private String homeState(HomeRow row) {
        int saved = choices.getInt(row.key, -1);
        if (saved < 0) return getString(R.string.wallpaper_theme_default);
        String text = homeValue(row.fmt, saved);
        if (row.fmt == Fmt.PHOTOS && saved == 1 && ScreensaverStills.images(Config.dir(this), ScreensaverStills.FOLDER).isEmpty()) {
            text += getString(R.string.photos_empty);
        }
        return text;
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size()
                : indexOf(images, current);
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size()) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which).getName()).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Off, then every app with a TV entry point by name. Saved at once; it is read on the next boot. */
    private void showOpenOnStartPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.off);
        String current = choices.getString(OpenOnStart.KEY, "");
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
                    else {
                        OpenOnStart.skipThisBoot(this);
                        choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < dockChoices.length; i++) {
            dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        }
        for (int i = 0; i < clockChoices.length; i++) {
            clockChoices[i].setSelected(CLOCK_FORMATS[i].equals(clockFormat));
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        openOnStartChoice.setText(getString(R.string.open_on_start_state, openOnStartLabel()));
        for (HomeRow row : homeRows) {
            row.view.setText(getString(R.string.home_row_state, getString(row.label), homeState(row)));
        }
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean anyHorizonChoice() {
        for (String key : Config.HORIZON_KEYS) if (choices.contains(key)) return true;
        return false;
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY)
                || choices.contains(Config.CLOCK_FORMAT_KEY) || choices.contains(OpenOnStart.KEY)
                || anyHorizonChoice();
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
