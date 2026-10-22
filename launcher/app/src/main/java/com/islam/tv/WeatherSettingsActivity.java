package com.islam.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class WeatherSettingsActivity extends Activity {

    private WeatherManager weatherManager;
    private PrayerManager prayerManager;

    private TextView weatherShowChoice;
    private TextView weatherLocationChoice;
    private TextView weatherAutoDetectChoice;
    private TextView weatherRefreshChoice;

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

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_weather_settings);

        weatherManager = WeatherManager.getInstance(this);
        prayerManager = PrayerManager.getInstance(this);

        weatherShowChoice = findViewById(R.id.weatherShowChoice);
        weatherLocationChoice = findViewById(R.id.weatherLocationChoice);
        weatherAutoDetectChoice = findViewById(R.id.weatherAutoDetectChoice);
        weatherRefreshChoice = findViewById(R.id.weatherRefreshChoice);

        setupClickListeners();
        refreshUI();
        weatherShowChoice.requestFocus();
    }

    private void setupClickListeners() {
        weatherShowChoice.setOnClickListener(v -> {
            boolean current = weatherManager.isWeatherEnabled();
            weatherManager.setWeatherEnabled(!current);
            setResult(RESULT_OK);
            refreshUI();
        });

        weatherLocationChoice.setOnClickListener(v -> showLocationPicker());

        weatherAutoDetectChoice.setOnClickListener(v -> triggerAutoDetect());

        weatherRefreshChoice.setOnClickListener(v -> {
            Toast.makeText(this, "Updating weather…", Toast.LENGTH_SHORT).show();
            weatherManager.loadWeather(true, text -> {
                Toast.makeText(WeatherSettingsActivity.this, R.string.weather_refreshed, Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                refreshUI();
            });
        });
    }

    private void triggerAutoDetect() {
        Toast.makeText(this, R.string.weather_detecting, Toast.LENGTH_SHORT).show();
        weatherManager.autoDetectLocation((success, city, country, lat, lon) -> {
            if (success && city != null && !city.isEmpty()) {
                prayerManager.setCity(city);
                if (country != null) prayerManager.setCountry(country);
                Toast.makeText(WeatherSettingsActivity.this,
                        getString(R.string.weather_detected, city, country != null ? country : ""),
                        Toast.LENGTH_LONG).show();
                setResult(RESULT_OK);
                refreshUI();
                weatherManager.loadWeather(true, null);
                prayerManager.fetchPrayerCalendarAsync(() -> PrayerAlarmScheduler.scheduleAlarms(this));
            } else {
                Toast.makeText(WeatherSettingsActivity.this, R.string.weather_detect_failed, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showLocationPicker() {
        // Options: 0 = Auto-detect, 1..N = Preset cities, Last = Custom location
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
                        updateWeatherData();
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
                        weatherManager.setCity(city);
                        weatherManager.setCountry(country);
                        setResult(RESULT_OK);
                        refreshUI();
                        updateWeatherData();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateWeatherData() {
        weatherManager.loadWeather(true, text -> {
            Toast.makeText(WeatherSettingsActivity.this, R.string.weather_refreshed, Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            refreshUI();
        });
        prayerManager.fetchPrayerCalendarAsync(() -> PrayerAlarmScheduler.scheduleAlarms(this));
    }

    private void refreshUI() {
        weatherShowChoice.setText(getString(R.string.weather_show,
                getString(weatherManager.isWeatherEnabled() ? R.string.on : R.string.off)));

        weatherLocationChoice.setText(getString(R.string.weather_location,
                prayerManager.getCity() + ", " + prayerManager.getCountry()));
    }
}
