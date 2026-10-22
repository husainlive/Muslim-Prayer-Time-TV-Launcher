package com.islam.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public final class ScreenSaverSettingsActivity extends Activity {

    private ScreenSaverManager screenSaverManager;

    private TextView screenSaverEnabledChoice;
    private TextView screenSaverTimeoutChoice;
    private TextView screenSaverAppChoice;
    private TextView screenSaverPreviewChoice;
    private TextView screenSaverSystemChoice;

    private static final int[] TIMEOUT_OPTIONS = {1, 2, 5, 10, 15, 20, 30, 60};

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_screensaver_settings);

        screenSaverManager = ScreenSaverManager.getInstance(this);

        screenSaverEnabledChoice = findViewById(R.id.screenSaverEnabledChoice);
        screenSaverTimeoutChoice = findViewById(R.id.screenSaverTimeoutChoice);
        screenSaverAppChoice = findViewById(R.id.screenSaverAppChoice);
        screenSaverPreviewChoice = findViewById(R.id.screenSaverPreviewChoice);
        screenSaverSystemChoice = findViewById(R.id.screenSaverSystemChoice);

        setupClickListeners();
        screenSaverManager.syncWithSystem();
        refreshUI();
        screenSaverEnabledChoice.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUI();
    }

    private void setupClickListeners() {
        screenSaverEnabledChoice.setOnClickListener(v -> {
            boolean current = screenSaverManager.isScreenSaverEnabled();
            screenSaverManager.setScreenSaverEnabled(!current);
            setResult(RESULT_OK);
            refreshUI();
        });

        screenSaverTimeoutChoice.setOnClickListener(v -> showTimeoutPicker());

        screenSaverAppChoice.setOnClickListener(v -> showAppPicker());

        screenSaverPreviewChoice.setOnClickListener(v -> {
            screenSaverManager.startScreenSaver(this);
        });

        screenSaverSystemChoice.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS));
            } catch (ActivityNotFoundException | SecurityException e) {
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (Exception ex) {
                    Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showTimeoutPicker() {
        String[] labels = new String[TIMEOUT_OPTIONS.length];
        int currentTimeout = screenSaverManager.getTimeoutMinutes();
        int selectedIndex = 0;

        for (int i = 0; i < TIMEOUT_OPTIONS.length; i++) {
            int mins = TIMEOUT_OPTIONS[i];
            labels[i] = mins == 1 ? getString(R.string.screensaver_minute_singular)
                    : getString(R.string.screensaver_minutes_format, mins);
            if (mins == currentTimeout) {
                selectedIndex = i;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.screensaver_select_timeout)
                .setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> {
                    dialog.dismiss();
                    screenSaverManager.setTimeoutMinutes(TIMEOUT_OPTIONS[which]);
                    setResult(RESULT_OK);
                    refreshUI();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showAppPicker() {
        List<ScreenSaverManager.ScreenSaverOption> options = screenSaverManager.getAvailableScreenSavers();
        String currentId = screenSaverManager.getScreenSaverApp();

        // Items: available dream/clock options + 1 option for "Choose from installed apps..."
        String[] labels = new String[options.size() + 1];
        int selectedIndex = -1;

        for (int i = 0; i < options.size(); i++) {
            ScreenSaverManager.ScreenSaverOption opt = options.get(i);
            labels[i] = opt.label;
            if (opt.id.equals(currentId)) {
                selectedIndex = i;
            }
        }
        labels[labels.length - 1] = getString(R.string.screensaver_choose_from_apps);

        new AlertDialog.Builder(this)
                .setTitle(R.string.screensaver_select_app)
                .setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> {
                    dialog.dismiss();
                    if (which < options.size()) {
                        ScreenSaverManager.ScreenSaverOption chosen = options.get(which);
                        screenSaverManager.setScreenSaverApp(chosen.id, chosen.label);
                        setResult(RESULT_OK);
                        refreshUI();
                    } else {
                        showInstalledAppsPicker();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showInstalledAppsPicker() {
        List<ScreenSaverManager.AppChoice> apps = screenSaverManager.getInstalledApps();
        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels = new String[apps.size()];
        String currentId = screenSaverManager.getScreenSaverApp();
        int selectedIndex = -1;

        for (int i = 0; i < apps.size(); i++) {
            ScreenSaverManager.AppChoice app = apps.get(i);
            labels[i] = app.label;
            if (("app:" + app.packageName).equals(currentId)) {
                selectedIndex = i;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.screensaver_select_installed_app)
                .setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> {
                    dialog.dismiss();
                    ScreenSaverManager.AppChoice chosen = apps.get(which);
                    screenSaverManager.setScreenSaverApp("app:" + chosen.packageName, chosen.label);
                    setResult(RESULT_OK);
                    refreshUI();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void refreshUI() {
        boolean enabled = screenSaverManager.isScreenSaverEnabled();
        String stateStr = enabled ? getString(R.string.on) : getString(R.string.screensaver_off_turn_off_screen);
        screenSaverEnabledChoice.setText(getString(R.string.screensaver_enable, stateStr));

        int timeout = screenSaverManager.getTimeoutMinutes();
        String timeoutStr = timeout == 1 ? getString(R.string.screensaver_minute_singular)
                : getString(R.string.screensaver_minutes_format, timeout);
        screenSaverTimeoutChoice.setText(getString(R.string.screensaver_timeout, timeoutStr));

        screenSaverAppChoice.setText(getString(R.string.screensaver_app,
                screenSaverManager.getScreenSaverAppLabel()));

        // Inactivity timeout applies both for screensaver and turning off the screen!
        screenSaverTimeoutChoice.setEnabled(true);
        screenSaverTimeoutChoice.setAlpha(1.0f);

        screenSaverAppChoice.setEnabled(enabled);
        screenSaverAppChoice.setAlpha(enabled ? 1.0f : 0.45f);

        screenSaverPreviewChoice.setEnabled(enabled);
        screenSaverPreviewChoice.setAlpha(enabled ? 1.0f : 0.45f);

        if (screenSaverSystemChoice != null) {
            boolean hasAdbSync = screenSaverManager.hasSecureSettingsPermission();
            String sysLabel = getString(R.string.screensaver_system_settings);
            if (hasAdbSync) {
                sysLabel += " · " + getString(R.string.screensaver_adb_synced);
            }
            screenSaverSystemChoice.setText(sysLabel);
        }
    }
}
