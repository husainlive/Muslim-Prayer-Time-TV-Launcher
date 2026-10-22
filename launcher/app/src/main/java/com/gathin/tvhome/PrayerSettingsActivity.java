package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PrayerSettingsActivity extends Activity {

    private PrayerManager prayerManager;
    private WeatherManager weatherManager;

    private TextView showPrayerBarChoice;
    private TextView lockPrayerBarChoice;
    private TextView locationChoice;
    private TextView timeFormatChoice;
    private TextView methodChoice;

    private TextView weatherShowChoice;
    private TextView weatherRefreshChoice;

    private TextView azanSoundEnabledChoice;
    private TextView azanSoundChoice;
    private TextView testAzanChoice;
    private TextView overlayNotifChoice;
    private TextView testAlertChoice;
    private TextView permissionsChoice;

    private TextView adjFajr;
    private TextView adjSunrise;
    private TextView adjDhuhr;
    private TextView adjAsr;
    private TextView adjMaghrib;
    private TextView adjIsha;

    private final TextView[] hijriChoices = new TextView[5];
    private static final int[] HIJRI_VALUES = {-2, -1, 0, 1, 2};

    private MediaPlayer testMediaPlayer;

    private static final String[] PRESET_CITIES = {
            "Doha, Qatar",
            "Riyadh, Saudi Arabia",
            "Makkah, Saudi Arabia",
            "Madinah, Saudi Arabia",
            "Dubai, United Arab Emirates",
            "Abu Dhabi, United Arab Emirates",
            "Kuwait City, Kuwait",
            "Manama, Bahrain",
            "Muscat, Oman",
            "Cairo, Egypt",
            "Istanbul, Turkey",
            "London, United Kingdom",
            "New York, United States"
    };

    private static final String[] METHOD_NAMES = {
            "Umm Al-Qura (Qatar, Saudi Arabia, Gulf)", // 4
            "Muslim World League (MWL)",               // 3
            "Egyptian General Authority",              // 5
            "Univ. of Islamic Sciences, Karachi",      // 1
            "Islamic Society of North America (ISNA)", // 2
            "Kuwait",                                  // 9
            "Dubai (UAE)"                              // 16
    };
    private static final int[] METHOD_IDS = {4, 3, 5, 1, 2, 9, 16};

    private static final String[] AZAN_SOUND_NAMES = {
            "Azan 1 (Makkah)",
            "Azan 2 (Madinah)",
            "Azan 3 (Al-Aqsa)"
    };
    private static final String[] AZAN_SOUND_KEYS = {
            "azan_1",
            "azan_2",
            "azan_3"
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_prayer_settings);

        prayerManager = PrayerManager.getInstance(this);
        weatherManager = WeatherManager.getInstance(this);

        showPrayerBarChoice = findViewById(R.id.showPrayerBarChoice);
        lockPrayerBarChoice = findViewById(R.id.lockPrayerBarChoice);
        locationChoice = findViewById(R.id.locationChoice);
        timeFormatChoice = findViewById(R.id.timeFormatChoice);
        methodChoice = findViewById(R.id.methodChoice);

        weatherShowChoice = findViewById(R.id.weatherShowChoice);
        weatherRefreshChoice = findViewById(R.id.weatherRefreshChoice);

        azanSoundEnabledChoice = findViewById(R.id.azanSoundEnabledChoice);
        azanSoundChoice = findViewById(R.id.azanSoundChoice);
        testAzanChoice = findViewById(R.id.testAzanChoice);
        overlayNotifChoice = findViewById(R.id.overlayNotifChoice);
        testAlertChoice = findViewById(R.id.testAlertChoice);
        permissionsChoice = findViewById(R.id.permissionsChoice);

        adjFajr = findViewById(R.id.adjFajr);
        adjSunrise = findViewById(R.id.adjSunrise);
        adjDhuhr = findViewById(R.id.adjDhuhr);
        adjAsr = findViewById(R.id.adjAsr);
        adjMaghrib = findViewById(R.id.adjMaghrib);
        adjIsha = findViewById(R.id.adjIsha);

        hijriChoices[0] = findViewById(R.id.hijriMinus2);
        hijriChoices[1] = findViewById(R.id.hijriMinus1);
        hijriChoices[2] = findViewById(R.id.hijriZero);
        hijriChoices[3] = findViewById(R.id.hijriPlus1);
        hijriChoices[4] = findViewById(R.id.hijriPlus2);

        setupClickListeners();
        refreshUI();
        showPrayerBarChoice.requestFocus();
    }

    private void setupClickListeners() {
        showPrayerBarChoice.setOnClickListener(v -> {
            boolean current = prayerManager.isShowPrayerBar();
            prayerManager.setShowPrayerBar(!current);
            setResult(RESULT_OK);
            refreshUI();
        });

        lockPrayerBarChoice.setOnClickListener(v -> {
            boolean current = prayerManager.isLockBarEnabled();
            prayerManager.setLockBarEnabled(!current);
            setResult(RESULT_OK);
            refreshUI();
        });

        locationChoice.setOnClickListener(v -> showLocationPicker());

        timeFormatChoice.setOnClickListener(v -> {
            boolean current = prayerManager.is24HourFormat();
            prayerManager.set24HourFormat(!current);
            setResult(RESULT_OK);
            refreshUI();
        });

        methodChoice.setOnClickListener(v -> showMethodPicker());

        weatherShowChoice.setOnClickListener(v -> {
            boolean current = weatherManager.isWeatherEnabled();
            weatherManager.setWeatherEnabled(!current);
            setResult(RESULT_OK);
            refreshUI();
        });

        weatherRefreshChoice.setOnClickListener(v -> {
            Toast.makeText(this, "Updating weather...", Toast.LENGTH_SHORT).show();
            weatherManager.loadWeather(true, text -> {
                Toast.makeText(PrayerSettingsActivity.this, R.string.weather_refreshed, Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                refreshUI();
            });
        });

        azanSoundEnabledChoice.setOnClickListener(v -> {
            boolean current = prayerManager.isAzanSoundEnabled();
            prayerManager.setAzanSoundEnabled(!current);
            PrayerAlarmScheduler.scheduleAlarms(this);
            setResult(RESULT_OK);
            refreshUI();
        });

        azanSoundChoice.setOnClickListener(v -> showAzanSoundPicker());

        testAzanChoice.setOnClickListener(v -> toggleTestAzanSound());

        overlayNotifChoice.setOnClickListener(v -> {
            boolean current = prayerManager.isNotificationOverlayEnabled();
            if (!current) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.permission_overlay_title)
                            .setMessage(R.string.permission_overlay_message)
                            .setPositiveButton(R.string.enable, (dialog, which) -> {
                                try {
                                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:" + getPackageName()));
                                    startActivity(intent);
                                } catch (Exception e) {
                                    Toast.makeText(this, "Could not open overlay settings", Toast.LENGTH_SHORT).show();
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                }
            }
            prayerManager.setNotificationOverlayEnabled(!current);
            PrayerAlarmScheduler.scheduleAlarms(this);
            setResult(RESULT_OK);
            refreshUI();
        });

        testAlertChoice.setOnClickListener(v -> {
            Intent testIntent = new Intent(this, PrayerAlarmService.class);
            testIntent.putExtra(PrayerAlarmScheduler.EXTRA_PRAYER_NAME, "Isha");
            testIntent.putExtra(PrayerAlarmScheduler.EXTRA_PRAYER_TIME, "6:32 PM");
            startService(testIntent);
        });

        permissionsChoice.setOnClickListener(v -> showPermissionsStatusDialog());

        adjFajr.setOnClickListener(v -> showAdjustmentPicker("Fajr"));
        adjSunrise.setOnClickListener(v -> showAdjustmentPicker("Sunrise"));
        adjDhuhr.setOnClickListener(v -> showAdjustmentPicker("Dhuhr"));
        adjAsr.setOnClickListener(v -> showAdjustmentPicker("Asr"));
        adjMaghrib.setOnClickListener(v -> showAdjustmentPicker("Maghrib"));
        adjIsha.setOnClickListener(v -> showAdjustmentPicker("Isha"));

        for (int i = 0; i < hijriChoices.length; i++) {
            final int days = HIJRI_VALUES[i];
            hijriChoices[i].setOnClickListener(v -> {
                prayerManager.setHijriAdjustment(days);
                setResult(RESULT_OK);
                refreshUI();
            });
        }

        findViewById(R.id.refreshChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.prayer_times, Toast.LENGTH_SHORT).show();
            prayerManager.fetchPrayerCalendarAsync(() -> {
                Toast.makeText(PrayerSettingsActivity.this, R.string.prayer_refreshed, Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                refreshUI();
            });
        });

        findViewById(R.id.resetChoice).setOnClickListener(v -> {
            for (String name : PrayerManager.PRAYER_NAMES) {
                prayerManager.setAdjustment(name, 0);
            }
            prayerManager.setHijriAdjustment(0);
            setResult(RESULT_OK);
            refreshUI();
            Toast.makeText(this, R.string.reset_adjustments, Toast.LENGTH_SHORT).show();
        });
    }

    private void toggleTestAzanSound() {
        if (testMediaPlayer != null && testMediaPlayer.isPlaying()) {
            stopTestAzanSound();
            testAzanChoice.setText(R.string.prayer_test_azan_play);
            return;
        }

        stopTestAzanSound();
        try {
            String soundChoice = prayerManager.getSelectedAzanSound();
            int rawRes = R.raw.azan_1;
            if ("azan_2".equals(soundChoice)) rawRes = R.raw.azan_2;
            else if ("azan_3".equals(soundChoice)) rawRes = R.raw.azan_3;

            Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + rawRes);
            testMediaPlayer = new MediaPlayer();
            testMediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            testMediaPlayer.setDataSource(this, uri);
            testMediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            testMediaPlayer.prepare();
            testMediaPlayer.start();
            testAzanChoice.setText(R.string.prayer_test_azan_stop);

            testMediaPlayer.setOnCompletionListener(mp -> {
                stopTestAzanSound();
                testAzanChoice.setText(R.string.prayer_test_azan_play);
            });
        } catch (Exception e) {
            Toast.makeText(this, "Error playing audio", Toast.LENGTH_SHORT).show();
            stopTestAzanSound();
        }
    }

    private void stopTestAzanSound() {
        if (testMediaPlayer != null) {
            try {
                if (testMediaPlayer.isPlaying()) {
                    testMediaPlayer.stop();
                }
                testMediaPlayer.release();
            } catch (Exception ignored) {}
            testMediaPlayer = null;
        }
    }

    private void showAzanSoundPicker() {
        String currentKey = prayerManager.getSelectedAzanSound();
        int selectedIndex = 0;
        for (int i = 0; i < AZAN_SOUND_KEYS.length; i++) {
            if (AZAN_SOUND_KEYS[i].equals(currentKey)) {
                selectedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.select_azan_sound)
                .setSingleChoiceItems(AZAN_SOUND_NAMES, selectedIndex, (dialog, which) -> {
                    dialog.dismiss();
                    prayerManager.setSelectedAzanSound(AZAN_SOUND_KEYS[which]);
                    setResult(RESULT_OK);
                    refreshUI();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String getSoundDisplayName(String key) {
        for (int i = 0; i < AZAN_SOUND_KEYS.length; i++) {
            if (AZAN_SOUND_KEYS[i].equals(key)) return AZAN_SOUND_NAMES[i];
        }
        return AZAN_SOUND_NAMES[0];
    }

    private void triggerAutoDetect() {
        Toast.makeText(this, R.string.weather_detecting, Toast.LENGTH_SHORT).show();
        weatherManager.autoDetectLocation((success, city, country, lat, lon) -> {
            if (success && city != null && !city.isEmpty()) {
                prayerManager.setCity(city);
                if (country != null) prayerManager.setCountry(country);
                Toast.makeText(PrayerSettingsActivity.this,
                        getString(R.string.weather_detected, city, country != null ? country : ""),
                        Toast.LENGTH_LONG).show();
                setResult(RESULT_OK);
                refreshUI();
                fetchUpdatedTimes();
                weatherManager.loadWeather(true, null);
            } else {
                Toast.makeText(PrayerSettingsActivity.this, R.string.weather_detect_failed, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showLocationPicker() {
        String[] options = new String[PRESET_CITIES.length + 2];
        options[0] = getString(R.string.weather_auto_detect);
        System.arraycopy(PRESET_CITIES, 0, options, 1, PRESET_CITIES.length);
        options[options.length - 1] = getString(R.string.custom_location);

        String currentLoc = prayerManager.getCity() + ", " + prayerManager.getCountry();
        int selectedIndex = -1;
        for (int i = 0; i < PRESET_CITIES.length; i++) {
            if (PRESET_CITIES[i].equalsIgnoreCase(currentLoc)) {
                selectedIndex = i + 1;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.select_location)
                .setSingleChoiceItems(options, selectedIndex, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) {
                        triggerAutoDetect();
                    } else if (which < options.length - 1) {
                        String[] parts = PRESET_CITIES[which - 1].split(",");
                        String city = parts[0].trim();
                        String country = parts[1].trim();
                        prayerManager.setCity(city);
                        prayerManager.setCountry(country);
                        weatherManager.setCity(city);
                        weatherManager.setCountry(country);
                        setResult(RESULT_OK);
                        refreshUI();
                        fetchUpdatedTimes();
                        weatherManager.loadWeather(true, null);
                    } else {
                        showCustomLocationDialog();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showCustomLocationDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        final EditText editCity = new EditText(this);
        editCity.setHint("City (e.g. Doha)");
        editCity.setText(prayerManager.getCity());
        editCity.setTextColor(0xFFFFFFFF);
        editCity.setHintTextColor(0x80FFFFFF);
        layout.addView(editCity);

        final EditText editCountry = new EditText(this);
        editCountry.setHint("Country (e.g. Qatar)");
        editCountry.setText(prayerManager.getCountry());
        editCountry.setTextColor(0xFFFFFFFF);
        editCountry.setHintTextColor(0x80FFFFFF);
        layout.addView(editCountry);

        new AlertDialog.Builder(this)
                .setTitle(R.string.custom_location)
                .setView(layout)
                .setPositiveButton(R.string.enable, (dialog, which) -> {
                    String city = editCity.getText().toString().trim();
                    String country = editCountry.getText().toString().trim();
                    if (!city.isEmpty() && !country.isEmpty()) {
                        prayerManager.setCity(city);
                        prayerManager.setCountry(country);
                        setResult(RESULT_OK);
                        refreshUI();
                        fetchUpdatedTimes();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showMethodPicker() {
        int currentMethod = prayerManager.getMethod();
        int selectedIndex = 0;
        for (int i = 0; i < METHOD_IDS.length; i++) {
            if (METHOD_IDS[i] == currentMethod) {
                selectedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.select_method)
                .setSingleChoiceItems(METHOD_NAMES, selectedIndex, (dialog, which) -> {
                    dialog.dismiss();
                    prayerManager.setMethod(METHOD_IDS[which]);
                    setResult(RESULT_OK);
                    refreshUI();
                    fetchUpdatedTimes();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showAdjustmentPicker(String prayerName) {
        final int[] values = {-15, -10, -5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 10, 15};
        String[] labels = new String[values.length];
        int currentAdj = prayerManager.getAdjustment(prayerName);
        int selectedIndex = 7; // 0 index
        for (int i = 0; i < values.length; i++) {
            labels[i] = String.format(Locale.US, "%+d minutes", values[i]);
            if (values[i] == currentAdj) selectedIndex = i;
        }

        new AlertDialog.Builder(this)
                .setTitle(prayerName + " Adjustment")
                .setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> {
                    dialog.dismiss();
                    prayerManager.setAdjustment(prayerName, values[which]);
                    setResult(RESULT_OK);
                    refreshUI();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void fetchUpdatedTimes() {
        Toast.makeText(this, R.string.prayer_times, Toast.LENGTH_SHORT).show();
        prayerManager.fetchPrayerCalendarAsync(() -> {
            Toast.makeText(PrayerSettingsActivity.this, R.string.prayer_refreshed, Toast.LENGTH_SHORT).show();
            PrayerAlarmScheduler.scheduleAlarms(this);
            setResult(RESULT_OK);
            refreshUI();
        });
    }

    private void refreshUI() {
        boolean showBar = prayerManager.isShowPrayerBar();
        showPrayerBarChoice.setText(getString(R.string.prayer_show_bar,
                getString(showBar ? R.string.on : R.string.off)));

        lockPrayerBarChoice.setText(getString(R.string.prayer_lock_bar,
                getString(prayerManager.isLockBarEnabled() ? R.string.on : R.string.off)));

        locationChoice.setText(getString(R.string.prayer_location,
                prayerManager.getCity() + ", " + prayerManager.getCountry()));

        timeFormatChoice.setText(getString(R.string.prayer_time_format,
                prayerManager.is24HourFormat() ? "24-hour" : "12-hour (AM/PM)"));

        int method = prayerManager.getMethod();
        String methodName = METHOD_NAMES[0];
        for (int i = 0; i < METHOD_IDS.length; i++) {
            if (METHOD_IDS[i] == method) {
                methodName = METHOD_NAMES[i];
                break;
            }
        }
        methodChoice.setText(getString(R.string.prayer_method, methodName));

        weatherShowChoice.setText(getString(R.string.weather_show,
                getString(weatherManager.isWeatherEnabled() ? R.string.on : R.string.off)));

        azanSoundEnabledChoice.setText(getString(R.string.prayer_azan_sound_enabled,
                getString(prayerManager.isAzanSoundEnabled() ? R.string.on : R.string.off)));

        azanSoundChoice.setText(getString(R.string.prayer_select_azan,
                getSoundDisplayName(prayerManager.getSelectedAzanSound())));

        overlayNotifChoice.setText(getString(R.string.prayer_overlay_notif,
                getString(prayerManager.isNotificationOverlayEnabled() ? R.string.on : R.string.off)));

        adjFajr.setText(getString(R.string.prayer_adj_item, getString(R.string.prayer_fajr), prayerManager.getAdjustment("Fajr")));
        adjSunrise.setText(getString(R.string.prayer_adj_item, getString(R.string.prayer_sunrise), prayerManager.getAdjustment("Sunrise")));
        adjDhuhr.setText(getString(R.string.prayer_adj_item, getString(R.string.prayer_dhuhr), prayerManager.getAdjustment("Dhuhr")));
        adjAsr.setText(getString(R.string.prayer_adj_item, getString(R.string.prayer_asr), prayerManager.getAdjustment("Asr")));
        adjMaghrib.setText(getString(R.string.prayer_adj_item, getString(R.string.prayer_maghrib), prayerManager.getAdjustment("Maghrib")));
        adjIsha.setText(getString(R.string.prayer_adj_item, getString(R.string.prayer_isha), prayerManager.getAdjustment("Isha")));

        int currentHijriAdj = prayerManager.getHijriAdjustment();
        for (int i = 0; i < hijriChoices.length; i++) {
            hijriChoices[i].setSelected(currentHijriAdj == HIJRI_VALUES[i]);
        }

        boolean notifGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        boolean locationGranted = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean overlayGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
        boolean allGranted = notifGranted && locationGranted && overlayGranted;

        permissionsChoice.setText(getString(R.string.prayer_permissions_check) + " · " +
                (allGranted ? "✅ All Granted" : "⚠️ Check Needed"));
    }

    private void showPermissionsStatusDialog() {
        boolean notifGranted = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifGranted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        boolean locationGranted = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean overlayGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
        boolean alarmGranted = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            alarmGranted = am == null || am.canScheduleExactAlarms();
        }

        StringBuilder sb = new StringBuilder();
        sb.append(notifGranted ? "✅ Notifications (Adhan alerts): Allowed\n\n" : "❌ Notifications: Missing (Adhan banners blocked)\n\n");
        sb.append(locationGranted ? "✅ Location (Wi-Fi SSID & Weather): Allowed\n\n" : "⚠️ Location: Missing (Wi-Fi SSID name unavailable)\n\n");
        sb.append(overlayGranted ? "✅ Display Over Other Apps: Allowed\n\n" : "❌ Display Over Other Apps: Missing (needed over YouTube/TV)\n\n");
        sb.append(alarmGranted ? "✅ Exact Alarms (Adhan): Allowed" : "⚠️ Exact Alarms: Restricted");

        boolean needsAction = !notifGranted || !locationGranted || !overlayGranted;

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.permissions_dialog_title)
                .setMessage(sb.toString())
                .setPositiveButton(needsAction ? R.string.enable : android.R.string.ok, (dialog, which) -> {
                    if (needsAction) {
                        requestMissingPermissions();
                    }
                })
                .setNegativeButton(needsAction ? android.R.string.cancel : null, null);
        builder.show();
    }

    private void requestMissingPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> runtimeList = new ArrayList<>();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    runtimeList.add(android.Manifest.permission.POST_NOTIFICATIONS);
                }
            }
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                runtimeList.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
            }
            if (checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                runtimeList.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);
            }
            if (!runtimeList.isEmpty()) {
                requestPermissions(runtimeList.toArray(new String[0]), 501);
                return;
            }
            if (!Settings.canDrawOverlays(this)) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Please allow overlay in system settings", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshUI();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onStop() {
        stopTestAzanSound();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        stopTestAzanSound();
        super.onDestroy();
    }
}
