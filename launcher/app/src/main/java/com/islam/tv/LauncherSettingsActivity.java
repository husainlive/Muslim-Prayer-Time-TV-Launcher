package com.islam.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private TextView wallpaperChoice;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private TextView prayerSettingsChoice;
    private TextView weatherSettingsChoice;
    private TextView screenSaverSettingsChoice;
    private WeatherManager weatherManager;
    private PrayerManager prayerManager;
    private ScreenSaverManager screenSaverManager;
    private int columns;
    private int rowsBelowDock;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
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
                setResult(RESULT_OK);
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
                setResult(RESULT_OK);
                refresh();
            });
        }
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            setResult(RESULT_OK);
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
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY).commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            setResult(RESULT_OK);
            refresh();
        });
        weatherManager = WeatherManager.getInstance(this);
        prayerManager = PrayerManager.getInstance(this);
        screenSaverManager = ScreenSaverManager.getInstance(this);
        prayerSettingsChoice = findViewById(R.id.prayerSettingsChoice);
        prayerSettingsChoice.setOnClickListener(v ->
                startActivityForResult(new Intent(this, PrayerSettingsActivity.class), 43));
        weatherSettingsChoice = findViewById(R.id.weatherSettingsChoice);
        weatherSettingsChoice.setOnClickListener(v ->
                startActivityForResult(new Intent(this, WeatherSettingsActivity.class), 44));
        screenSaverSettingsChoice = findViewById(R.id.screenSaverSettingsChoice);
        screenSaverSettingsChoice.setOnClickListener(v ->
                startActivityForResult(new Intent(this, ScreenSaverSettingsActivity.class), 45));
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(new Intent(Settings.ACTION_SETTINGS)));
        refresh();
        columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == 43 || requestCode == 44 || requestCode == 45) && resultCode == RESULT_OK) {
            setResult(RESULT_OK);
            refresh();
        }
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showWallpaperPicker() {
        List<String> values = new java.util.ArrayList<>();
        List<String> labels = new java.util.ArrayList<>();

        // 1. Dynamic Aurora and React-Bits Themes
        values.add(AuroraView.THEME_AURORA);
        labels.add("Aurora (Animated)");

        values.add(AuroraView.THEME_GHOST_FIBERS);
        labels.add("GhostFibers");

        values.add(AuroraView.THEME_GRADIENT_ASCII);
        labels.add("GradientASCII");

        values.add(AuroraView.THEME_GRAINIENT);
        labels.add("Grainient");

        values.add(AuroraView.THEME_IRIDESCENCE);
        labels.add("Iridescence");

        // 2. Static image files (if any in theme directory)
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        for (File img : images) {
            values.add(img.getName());
            labels.add(img.getName() + " (Static)");
        }

        // 3. None option
        values.add("none");
        labels.add(getString(R.string.wallpaper_none));

        // Determine currently checked item
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = 0; // Default is Aurora (Animated)
        if (current != null) {
            for (int i = 0; i < values.size(); i++) {
                if (values.get(i).equals(current)) {
                    checked = i;
                    break;
                }
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels.toArray(new String[0]), checked, (dialog, which) -> {
                    dialog.dismiss();
                    String selected = values.get(which);
                    choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, selected).apply();
                    setResult(RESULT_OK);
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    setResult(RESULT_OK);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        setResult(RESULT_OK);
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        String displayLabel;
        if ("none".equals(wallpaperCurrent)) {
            displayLabel = getString(R.string.wallpaper_none);
        } else if (wallpaperCurrent != null) {
            displayLabel = wallpaperCurrent;
        } else {
            displayLabel = getString(R.string.wallpaper_theme_default) + " (Aurora)";
        }
        wallpaperChoice.setText(getString(R.string.wallpaper_state, displayLabel));
        followThemeChoice.setSelected(!hasOnTvChoices());
        if (prayerSettingsChoice != null && prayerManager != null) {
            String azanStatus = getString(prayerManager.isAzanSoundEnabled() ? R.string.on : R.string.off);
            String sleepStatus = getString(prayerManager.isAzanSleepModeEnabled() ? R.string.on : R.string.off);
            prayerSettingsChoice.setText(getString(R.string.prayer_settings_state, azanStatus, sleepStatus));
        }
        if (weatherSettingsChoice != null && weatherManager != null && prayerManager != null) {
            weatherSettingsChoice.setText(getString(R.string.weather_settings_state,
                    getString(weatherManager.isWeatherEnabled() ? R.string.on : R.string.off),
                    prayerManager.getCity()));
        }
        if (screenSaverSettingsChoice != null && screenSaverManager != null) {
            boolean ssEnabled = screenSaverManager.isScreenSaverEnabled();
            int timeout = screenSaverManager.getTimeoutMinutes();
            String timeoutStr = timeout == 1 ? getString(R.string.screensaver_minute_singular)
                    : getString(R.string.screensaver_minutes_format, timeout);
            screenSaverSettingsChoice.setText(getString(R.string.screensaver_settings_state,
                    getString(ssEnabled ? R.string.on : R.string.off),
                    timeoutStr,
                    screenSaverManager.getScreenSaverAppLabel()));
        }
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
