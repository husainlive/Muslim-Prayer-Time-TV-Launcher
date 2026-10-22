package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages fetching, caching, and formatting weather data in Celsius.
 * Uses Open-Meteo API with wttr.in fallback. Requires no API keys.
 */
public class WeatherManager {
    private static final String TAG = "WeatherManager";
    private static final String PREFS_NAME = "prayer_prefs";
    private static final String KEY_WEATHER_TEXT = "weather_text";
    private static final String KEY_WEATHER_TEMP = "weather_temp";
    private static final String KEY_WEATHER_TIME = "weather_time";
    private static final String KEY_WEATHER_ENABLED = "weather_enabled";
    private static final String KEY_TEMP_UNIT = "temp_unit"; // "C" or "F"
    private static final long CACHE_DURATION_MS = 30 * 60 * 1000L; // 30 minutes

    private static WeatherManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Coordinate cache for common Islamic/global cities
    private static final Map<String, double[]> CITY_COORDS = new HashMap<>();
    static {
        CITY_COORDS.put("doha", new double[]{25.2854, 51.5310});
        CITY_COORDS.put("riyadh", new double[]{24.7136, 46.6753});
        CITY_COORDS.put("makkah", new double[]{21.3891, 39.8579});
        CITY_COORDS.put("madinah", new double[]{24.5247, 39.5692});
        CITY_COORDS.put("dubai", new double[]{25.2048, 55.2708});
        CITY_COORDS.put("abu dhabi", new double[]{24.4539, 54.3773});
        CITY_COORDS.put("cairo", new double[]{30.0444, 31.2357});
        CITY_COORDS.put("kuwait", new double[]{29.3759, 47.9774});
        CITY_COORDS.put("muscat", new double[]{23.5859, 58.4059});
        CITY_COORDS.put("manama", new double[]{26.2285, 50.5860});
        CITY_COORDS.put("istanbul", new double[]{41.0082, 28.9784});
        CITY_COORDS.put("amman", new double[]{31.9454, 35.9284});
        CITY_COORDS.put("beirut", new double[]{33.8938, 35.5018});
        CITY_COORDS.put("baghdad", new double[]{33.3152, 44.3661});
        CITY_COORDS.put("karachi", new double[]{24.8607, 67.0011});
        CITY_COORDS.put("lahore", new double[]{31.5204, 74.3587});
        CITY_COORDS.put("islamabad", new double[]{33.6844, 73.0479});
        CITY_COORDS.put("dhaka", new double[]{23.8103, 90.4125});
        CITY_COORDS.put("jakarta", new double[]{-6.2088, 106.8456});
        CITY_COORDS.put("kuala lumpur", new double[]{3.1390, 101.6869});
        CITY_COORDS.put("london", new double[]{51.5074, -0.1278});
        CITY_COORDS.put("new york", new double[]{40.7128, -74.0060});
        CITY_COORDS.put("paris", new double[]{48.8566, 2.3522});
        CITY_COORDS.put("toronto", new double[]{43.6532, -79.3832});
        CITY_COORDS.put("sydney", new double[]{-33.8688, 151.2093});
    }

    public interface WeatherCallback {
        void onWeatherLoaded(String weatherText);
    }

    public interface LocationDetectCallback {
        void onLocationDetected(boolean success, String city, String country, double lat, double lon);
    }

    private WeatherManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized WeatherManager getInstance(Context context) {
        if (instance == null) {
            instance = new WeatherManager(context);
        }
        return instance;
    }

    public boolean isWeatherEnabled() {
        return prefs.getBoolean(KEY_WEATHER_ENABLED, true);
    }

    public void setWeatherEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_WEATHER_ENABLED, enabled).apply();
    }

    public String getCity() {
        return prefs.getString("city", "Doha");
    }

    public void setCity(String city) {
        prefs.edit().putString("city", city).remove(KEY_WEATHER_TIME).apply();
    }

    public String getCountry() {
        return prefs.getString("country", "Qatar");
    }

    public void setCountry(String country) {
        prefs.edit().putString("country", country).apply();
    }

    public void autoDetectLocation(LocationDetectCallback callback) {
        executor.execute(() -> {
            boolean success = false;
            String detectedCity = null;
            String detectedCountry = null;
            double lat = 0;
            double lon = 0;

            // Primary: ipwho.is (HTTPS, fast, accurate)
            try {
                URL url = new URL("https://ipwho.is/");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setRequestMethod("GET");
                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    conn.disconnect();

                    JSONObject root = new JSONObject(sb.toString());
                    if (root.optBoolean("success", false)) {
                        detectedCity = root.optString("city", "");
                        detectedCountry = root.optString("country", "");
                        lat = root.optDouble("latitude", 0);
                        lon = root.optDouble("longitude", 0);
                        if (!detectedCity.isEmpty()) {
                            success = true;
                        }
                    }
                } else {
                    conn.disconnect();
                }
            } catch (Exception e) {
                Log.w(TAG, "ipwho.is detection failed: " + e.getMessage());
            }

            // Fallback: ip-api.com
            if (!success) {
                try {
                    URL url = new URL("http://ip-api.com/json/");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(6000);
                    conn.setReadTimeout(6000);
                    conn.setRequestMethod("GET");
                    if (conn.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                        reader.close();
                        conn.disconnect();

                        JSONObject root = new JSONObject(sb.toString());
                        if ("success".equalsIgnoreCase(root.optString("status", ""))) {
                            detectedCity = root.optString("city", "");
                            detectedCountry = root.optString("country", "");
                            lat = root.optDouble("lat", 0);
                            lon = root.optDouble("lon", 0);
                            if (!detectedCity.isEmpty()) {
                                success = true;
                            }
                        }
                    } else {
                        conn.disconnect();
                    }
                } catch (Exception e) {
                    Log.w(TAG, "ip-api fallback failed: " + e.getMessage());
                }
            }

            final boolean finalSuccess = success;
            final String finalCity = detectedCity;
            final String finalCountry = detectedCountry;
            final double finalLat = lat;
            final double finalLon = lon;

            if (finalSuccess && finalCity != null) {
                prefs.edit()
                        .putString("city", finalCity)
                        .putString("country", finalCountry != null ? finalCountry : "")
                        .remove(KEY_WEATHER_TIME)
                        .apply();

                if (finalLat != 0 && finalLon != 0) {
                    CITY_COORDS.put(finalCity.toLowerCase(Locale.US), new double[]{finalLat, finalLon});
                }
            }

            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onLocationDetected(finalSuccess, finalCity, finalCountry, finalLat, finalLon);
                }
            });
        });
    }

    public String getCachedWeather() {
        if (!isWeatherEnabled()) return "";
        String text = prefs.getString(KEY_WEATHER_TEXT, "");
        if (!text.isEmpty() && !text.contains("•")) {
            String city = getCity();
            if (text.startsWith("☀️ ")) {
                return "☀️ " + city + " • " + text.substring(2).trim();
            } else {
                return "☀️ " + city + " • " + text;
            }
        }
        return text;
    }

    public void loadWeather(boolean forceRefresh, WeatherCallback callback) {
        if (!isWeatherEnabled()) {
            if (callback != null) callback.onWeatherLoaded("");
            return;
        }

        String cached = prefs.getString(KEY_WEATHER_TEXT, "");
        long lastTime = prefs.getLong(KEY_WEATHER_TIME, 0);
        long now = System.currentTimeMillis();

        if (!forceRefresh && !cached.isEmpty() && cached.contains("•") && (now - lastTime < CACHE_DURATION_MS)) {
            if (callback != null) callback.onWeatherLoaded(cached);
            return;
        }

        executor.execute(() -> {
            String city = prefs.getString("city", "Doha");
            String result = fetchFromOpenMeteo(city);
            if (result == null || result.isEmpty()) {
                result = fetchFromWttrIn(city);
            }

            if (result != null && !result.isEmpty()) {
                prefs.edit()
                        .putString(KEY_WEATHER_TEXT, result)
                        .putLong(KEY_WEATHER_TIME, System.currentTimeMillis())
                        .apply();
            } else if (!cached.isEmpty()) {
                result = cached;
            } else {
                result = "☀️ " + city + " • 30°C Clear";
            }

            final String finalResult = result;
            mainHandler.post(() -> {
                if (callback != null) callback.onWeatherLoaded(finalResult);
            });
        });
    }

    private String fetchFromOpenMeteo(String city) {
        try {
            double[] coords = getCoordinatesForCity(city);
            if (coords == null) return null;

            String urlStr = String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code",
                    coords[0], coords[1]);

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                return null;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            conn.disconnect();

            JSONObject json = new JSONObject(sb.toString());
            JSONObject current = json.getJSONObject("current");
            double tempC = current.getDouble("temperature_2m");
            int weatherCode = current.optInt("weather_code", 0);

            String iconAndCondition = parseWeatherCode(weatherCode);
            int roundTemp = (int) Math.round(tempC);
            return String.format(Locale.US, "%s %s • %d°C %s", iconAndCondition.split(" ")[0], city, roundTemp,
                    iconAndCondition.substring(iconAndCondition.indexOf(" ") + 1));
        } catch (Exception e) {
            Log.w(TAG, "Open-Meteo fetch failed: " + e.getMessage());
            return null;
        }
    }

    private double[] getCoordinatesForCity(String city) {
        if (city == null) return CITY_COORDS.get("doha");
        String key = city.trim().toLowerCase(Locale.US);
        if (CITY_COORDS.containsKey(key)) {
            return CITY_COORDS.get(key);
        }

        // Geocoding fallback via Open-Meteo
        try {
            String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name="
                    + URLEncoder.encode(city, "UTF-8") + "&count=1";
            URL url = new URL(geoUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                conn.disconnect();

                JSONObject root = new JSONObject(sb.toString());
                JSONArray results = root.optJSONArray("results");
                if (results != null && results.length() > 0) {
                    JSONObject first = results.getJSONObject(0);
                    double lat = first.getDouble("latitude");
                    double lon = first.getDouble("longitude");
                    double[] c = new double[]{lat, lon};
                    CITY_COORDS.put(key, c);
                    return c;
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            Log.w(TAG, "Geocoding failed for " + city + ": " + e.getMessage());
        }

        return CITY_COORDS.get("doha");
    }

    private String fetchFromWttrIn(String city) {
        try {
            String urlStr = "https://wttr.in/" + URLEncoder.encode(city, "UTF-8") + "?format=%t+%C";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("User-Agent", "curl/7.68.0");

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line = reader.readLine();
                reader.close();
                conn.disconnect();
                if (line != null && !line.trim().isEmpty()) {
                    String clean = line.trim().replace("+", "");
                    return String.format(Locale.US, "☀️ %s • %s", city, clean);
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            Log.w(TAG, "wttr.in fetch failed: " + e.getMessage());
        }
        return null;
    }

    private String parseWeatherCode(int code) {
        switch (code) {
            case 0:
                return "☀️ Clear";
            case 1:
            case 2:
            case 3:
                return "🌤 Partly Cloudy";
            case 45:
            case 48:
                return "🌫 Foggy";
            case 51:
            case 53:
            case 55:
            case 61:
            case 63:
            case 65:
                return "🌧 Rain";
            case 71:
            case 73:
            case 75:
            case 77:
                return "❄️ Snow";
            case 80:
            case 81:
            case 82:
                return "🌦 Showers";
            case 95:
            case 96:
            case 99:
                return "⛈ Thunderstorm";
            default:
                return "🌤 Fair";
        }
    }
}
