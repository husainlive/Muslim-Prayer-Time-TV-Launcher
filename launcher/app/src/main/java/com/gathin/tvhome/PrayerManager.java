package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.time.chrono.HijrahDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PrayerManager {

    private static final String TAG = "PrayerManager";
    public static final String PREFS_NAME = "prayer_prefs";

    public static final String KEY_CITY = "city";
    public static final String KEY_COUNTRY = "country";
    public static final String KEY_IS_24H = "is_24hour_format";
    public static final String KEY_METHOD = "method";
    public static final String KEY_SHOW_BAR = "show_prayer_bar";
    public static final String KEY_HIJRI_ADJUSTMENT = "hijri_adjustment";
    public static final String KEY_ADJUSTMENT_PREFIX = "adjustment_";
    public static final String KEY_LOCK_BAR = "lock_prayer_bar";
    public static final String KEY_AZAN_ENABLED = "azan_sound_enabled";
    public static final String KEY_NOTIF_OVERLAY = "notification_overlay_enabled";
    public static final String KEY_AZAN_SOUND = "selected_azan_sound";
    public static final String KEY_NOTIF_PREFIX = "notif_prayer_";

    public static final String[] PRAYER_NAMES = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};

    private static PrayerManager sInstance;

    private final SharedPreferences prefs;
    private final PrayerDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isFetching = false;

    public static class PrayerItem {
        public final String name;
        public final String rawTime;
        public final String adjustedTime;
        public final String formattedTime;
        public final boolean isNext;
        public final String countdownText;

        public PrayerItem(String name, String rawTime, String adjustedTime, String formattedTime, boolean isNext, String countdownText) {
            this.name = name;
            this.rawTime = rawTime;
            this.adjustedTime = adjustedTime;
            this.formattedTime = formattedTime;
            this.isNext = isNext;
            this.countdownText = countdownText != null ? countdownText : "";
        }

        public PrayerItem(String name, String rawTime, String adjustedTime, String formattedTime, boolean isNext) {
            this(name, rawTime, adjustedTime, formattedTime, isNext, "");
        }
    }

    public static int getIqamaOffsetMinutes(String prayerName) {
        if ("Fajr".equalsIgnoreCase(prayerName)) return 25;
        if ("Dhuhr".equalsIgnoreCase(prayerName)) return 20;
        if ("Asr".equalsIgnoreCase(prayerName)) return 25;
        if ("Maghrib".equalsIgnoreCase(prayerName)) return 10;
        if ("Isha".equalsIgnoreCase(prayerName)) return 20;
        return 0;
    }

    public static class NextPrayerInfo {
        public final String name;
        public final long minutesRemaining;
        public final String countdownText;
        public final boolean isIqama;

        public NextPrayerInfo(String name, long minutesRemaining, String countdownText) {
            this(name, minutesRemaining, countdownText, false);
        }

        public NextPrayerInfo(String name, long minutesRemaining, String countdownText, boolean isIqama) {
            this.name = name;
            this.minutesRemaining = minutesRemaining;
            this.countdownText = countdownText;
            this.isIqama = isIqama;
        }
    }

    public static class PrayerSchedule {
        public final List<PrayerItem> items;
        public final String nextPrayerName;
        public final String hijriDate;
        public final String countdownText;

        public PrayerSchedule(List<PrayerItem> items, String nextPrayerName, String hijriDate, String countdownText) {
            this.items = items;
            this.nextPrayerName = nextPrayerName;
            this.hijriDate = hijriDate;
            this.countdownText = countdownText != null ? countdownText : "";
        }

        public PrayerSchedule(List<PrayerItem> items, String nextPrayerName, String hijriDate) {
            this(items, nextPrayerName, hijriDate, "");
        }
    }

    public static synchronized PrayerManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new PrayerManager(context.getApplicationContext());
        }
        return sInstance;
    }

    private PrayerManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.db = new PrayerDatabase(context);
    }

    public String getCity() {
        return prefs.getString(KEY_CITY, "Doha");
    }

    public void setCity(String city) {
        prefs.edit().putString(KEY_CITY, city).apply();
    }

    public String getCountry() {
        return prefs.getString(KEY_COUNTRY, "Qatar");
    }

    public void setCountry(String country) {
        prefs.edit().putString(KEY_COUNTRY, country).apply();
    }

    public boolean is24HourFormat() {
        return prefs.getBoolean(KEY_IS_24H, true);
    }

    public void set24HourFormat(boolean is24H) {
        prefs.edit().putBoolean(KEY_IS_24H, is24H).apply();
    }

    public int getMethod() {
        return prefs.getInt(KEY_METHOD, 4); // Default 4: Umm al-Qura
    }

    public void setMethod(int method) {
        prefs.edit().putInt(KEY_METHOD, method).apply();
    }

    public boolean isShowPrayerBar() {
        return prefs.getBoolean(KEY_SHOW_BAR, true);
    }

    public void setShowPrayerBar(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_BAR, show).apply();
    }

    public int getHijriAdjustment() {
        return prefs.getInt(KEY_HIJRI_ADJUSTMENT, 0);
    }

    public void setHijriAdjustment(int days) {
        prefs.edit().putInt(KEY_HIJRI_ADJUSTMENT, days).apply();
    }

    public int getAdjustment(String prayerName) {
        return prefs.getInt(KEY_ADJUSTMENT_PREFIX + prayerName, 0);
    }

    public void setAdjustment(String prayerName, int minutes) {
        prefs.edit().putInt(KEY_ADJUSTMENT_PREFIX + prayerName, minutes).apply();
    }

    public boolean isLockBarEnabled() {
        return prefs.getBoolean(KEY_LOCK_BAR, true);
    }

    public void setLockBarEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_LOCK_BAR, enabled).apply();
    }

    public boolean isAzanSoundEnabled() {
        return prefs.getBoolean(KEY_AZAN_ENABLED, true);
    }

    public void setAzanSoundEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AZAN_ENABLED, enabled).apply();
    }

    public boolean isNotificationOverlayEnabled() {
        return prefs.getBoolean(KEY_NOTIF_OVERLAY, true);
    }

    public void setNotificationOverlayEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIF_OVERLAY, enabled).apply();
    }

    public String getSelectedAzanSound() {
        return prefs.getString(KEY_AZAN_SOUND, "azan_1");
    }

    public void setSelectedAzanSound(String sound) {
        prefs.edit().putString(KEY_AZAN_SOUND, sound).apply();
    }

    public boolean isPrayerNotificationEnabled(String prayerName) {
        return prefs.getBoolean(KEY_NOTIF_PREFIX + prayerName, true);
    }

    public void setPrayerNotificationEnabled(String prayerName, boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIF_PREFIX + prayerName, enabled).apply();
    }

    public String getHijriDateString() {
        try {
            int adjustment = getHijriAdjustment();
            HijrahDate hijrahDate = HijrahDate.now();
            if (adjustment != 0) {
                hijrahDate = hijrahDate.plus(adjustment, ChronoUnit.DAYS);
            }
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault());
            return formatter.format(hijrahDate) + " AH";
        } catch (Exception e) {
            Log.e(TAG, "Error calculating Hijri date", e);
            return "";
        }
    }

    public PrayerSchedule getTodaySchedule() {
        return getTodaySchedule(null);
    }

    public PrayerSchedule getTodaySchedule(Runnable onUpdated) {
        String city = getCity();
        String country = getCountry();
        boolean is24H = is24HourFormat();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String todayStr = sdf.format(Calendar.getInstance().getTime());

        PrayerDatabase.PrayerDayTimes cached = db.getPrayerTimes(todayStr, city, country);

        if (cached == null) {
            // Need to fetch from network
            fetchPrayerCalendarAsync(onUpdated);
            // Return fallback schedule while fetching
            return buildScheduleFromMap(getFallbackTimes(), is24H);
        }

        Map<String, String> rawMap = new LinkedHashMap<>();
        rawMap.put("Fajr", cached.fajr);
        rawMap.put("Sunrise", cached.sunrise);
        rawMap.put("Dhuhr", cached.dhuhr);
        rawMap.put("Asr", cached.asr);
        rawMap.put("Maghrib", cached.maghrib);
        rawMap.put("Isha", cached.isha);

        return buildScheduleFromMap(rawMap, is24H);
    }

    private Map<String, String> getFallbackTimes() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("Fajr", "04:30");
        map.put("Sunrise", "05:45");
        map.put("Dhuhr", "11:35");
        map.put("Asr", "14:55");
        map.put("Maghrib", "17:15");
        map.put("Isha", "18:45");
        return map;
    }

    private PrayerSchedule buildScheduleFromMap(Map<String, String> rawMap, boolean is24H) {
        Calendar now = Calendar.getInstance();
        List<PrayerItem> items = new ArrayList<>();

        Map<String, String> adjustedMap = new LinkedHashMap<>();
        for (String name : PRAYER_NAMES) {
            String raw = rawMap.get(name);
            if (raw == null || raw.isEmpty()) raw = "00:00";
            int adj = getAdjustment(name);
            String adjusted = applyAdjustment(raw, adj);
            adjustedMap.put(name, adjusted);
        }

        // Determine which prayer is next along with countdown
        NextPrayerInfo nextInfo = findNextPrayerInfo(adjustedMap, now);

        for (String name : PRAYER_NAMES) {
            String raw = rawMap.get(name);
            String adjusted = adjustedMap.get(name);
            String formatted = formatTime(adjusted, is24H);
            boolean isNext = name.equalsIgnoreCase(nextInfo.name);
            String countdown = isNext ? nextInfo.countdownText : "";
            items.add(new PrayerItem(name, raw, adjusted, formatted, isNext, countdown));
        }

        return new PrayerSchedule(items, nextInfo.name, getHijriDateString(), nextInfo.countdownText);
    }

    public static NextPrayerInfo findNextPrayerInfo(Map<String, String> adjustedMap, Calendar now) {
        Calendar nowClean = (Calendar) now.clone();
        nowClean.set(Calendar.SECOND, 0);
        nowClean.set(Calendar.MILLISECOND, 0);

        // Dynamic Prayer & Iqama Countdown:
        // As soon as a prayer time is reached, dynamically replace the "Next Prayer" counter
        // in the exact same spot with an "Iqama Counter" counting down remaining minutes.
        // Fixed offsets: Fajr +25m, Dhuhr +20m, Asr +25m, Maghrib +10m, Isha +20m.
        for (String name : PRAYER_NAMES) {
            int offset = getIqamaOffsetMinutes(name);
            if (offset <= 0) continue;
            String timeStr = adjustedMap.get(name);
            if (timeStr == null) continue;
            String[] parts = timeStr.split(":");
            if (parts.length != 2) continue;

            try {
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);

                Calendar calStart = (Calendar) nowClean.clone();
                calStart.set(Calendar.HOUR_OF_DAY, hour);
                calStart.set(Calendar.MINUTE, minute);

                Calendar calEnd = (Calendar) calStart.clone();
                calEnd.add(Calendar.MINUTE, offset);

                if (!nowClean.before(calStart) && nowClean.before(calEnd)) {
                    long remainingMs = calEnd.getTimeInMillis() - nowClean.getTimeInMillis();
                    long remainingMins = Math.max(1, (remainingMs + 59999) / (60 * 1000L));
                    String iqamaCountdown = "Iqama in " + remainingMins + "m";
                    return new NextPrayerInfo(name, remainingMins, iqamaCountdown, true);
                }
            } catch (NumberFormatException ignored) {}
        }

        long minDiff = Long.MAX_VALUE;
        String next = "Fajr";

        for (String name : PRAYER_NAMES) {
            String timeStr = adjustedMap.get(name);
            if (timeStr == null) continue;
            String[] parts = timeStr.split(":");
            if (parts.length != 2) continue;

            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);

            Calendar cal = (Calendar) nowClean.clone();
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, minute);

            long diff = cal.getTimeInMillis() - nowClean.getTimeInMillis();
            if (diff > 0 && diff < minDiff) {
                minDiff = diff;
                next = name;
            }
        }

        // If all prayers today have passed, next prayer is Fajr tomorrow
        if (minDiff == Long.MAX_VALUE) {
            String fajrStr = adjustedMap.get("Fajr");
            if (fajrStr != null) {
                String[] parts = fajrStr.split(":");
                if (parts.length == 2) {
                    Calendar cal = (Calendar) nowClean.clone();
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
                    cal.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
                    minDiff = Math.max(0, cal.getTimeInMillis() - nowClean.getTimeInMillis());
                }
            }
            next = "Fajr";
        }

        long totalMinutes = minDiff / (60 * 1000L);
        long hours = totalMinutes / 60;
        long mins = totalMinutes % 60;
        String countdown;
        if (totalMinutes <= 1) {
            countdown = "Now";
        } else if (hours > 0) {
            countdown = "in " + hours + "h " + mins + "m";
        } else {
            countdown = "in " + mins + "m";
        }

        return new NextPrayerInfo(next, totalMinutes, countdown);
    }

    static String findNextPrayer(Map<String, String> adjustedMap, Calendar now) {
        return findNextPrayerInfo(adjustedMap, now).name;
    }

    static String cleanTime(String time) {
        if (time == null) return "00:00";
        // Extract HH:mm from strings like "04:19 (+03)" or "04:19"
        String[] parts = time.trim().split(" ");
        String hhmm = parts[0];
        if (hhmm.matches("\\d{2}:\\d{2}")) {
            return hhmm;
        }
        return "00:00";
    }

    static String convertDateToDbFormat(String rawDate) {
        // From DD-MM-YYYY to YYYY-MM-DD
        try {
            String[] parts = rawDate.split("-");
            if (parts.length == 3) {
                return parts[2] + "-" + parts[1] + "-" + parts[0];
            }
        } catch (Exception ignored) {
        }
        return rawDate;
    }

    public static String applyAdjustment(String time24, int adjustmentMinutes) {
        if (adjustmentMinutes == 0) return time24;
        try {
            String[] parts = time24.split(":");
            if (parts.length != 2) return time24;
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, hours);
            cal.set(Calendar.MINUTE, minutes);
            cal.add(Calendar.MINUTE, adjustmentMinutes);

            return String.format(Locale.US, "%02d:%02d",
                    cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
        } catch (Exception e) {
            return time24;
        }
    }

    public static String formatTime(String time24, boolean is24H) {
        if (time24 == null || is24H) return time24 != null ? time24 : "00:00";
        try {
            String[] parts = time24.split(":");
            if (parts.length != 2) return time24;
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);

            int hours12 = (hours == 0) ? 12 : (hours > 12) ? hours - 12 : hours;
            String amPm = (hours < 12) ? "AM" : "PM";
            return String.format(Locale.US, "%d:%02d %s", hours12, minutes, amPm);
        } catch (Exception e) {
            return time24;
        }
    }

    public void fetchPrayerCalendarAsync(Runnable onUpdated) {
        if (isFetching) return;
        isFetching = true;

        executor.execute(() -> {
            try {
                Calendar cal = Calendar.getInstance();
                int currentMonth = cal.get(Calendar.MONTH) + 1;
                int currentYear = cal.get(Calendar.YEAR);

                fetchAndSaveMonth(getCity(), getCountry(), getMethod(), currentMonth, currentYear);

                // If within last 7 days of the month, fetch next month too
                Calendar checkCal = Calendar.getInstance();
                checkCal.add(Calendar.DAY_OF_YEAR, 7);
                int nextMonth = checkCal.get(Calendar.MONTH) + 1;
                int nextYear = checkCal.get(Calendar.YEAR);
                if (nextMonth != currentMonth) {
                    fetchAndSaveMonth(getCity(), getCountry(), getMethod(), nextMonth, nextYear);
                }

                if (onUpdated != null) {
                    mainHandler.post(onUpdated);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to fetch prayer calendar", e);
            } finally {
                isFetching = false;
            }
        });
    }

    private void fetchAndSaveMonth(String city, String country, int method, int month, int year) {
        try {
            String urlStr = "https://api.aladhan.com/v1/calendarByCity?city="
                    + URLEncoder.encode(city, "UTF-8")
                    + "&country=" + URLEncoder.encode(country, "UTF-8")
                    + "&method=" + method
                    + "&month=" + month
                    + "&year=" + year;

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                InputStream is = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONObject root = new JSONObject(sb.toString());
                int code = root.optInt("code", 0);
                if (code == 200) {
                    JSONArray data = root.getJSONArray("data");
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject dayObj = data.getJSONObject(i);
                        JSONObject timings = dayObj.getJSONObject("timings");
                        JSONObject dateObj = dayObj.getJSONObject("date").getJSONObject("gregorian");
                        String rawDate = dateObj.getString("date"); // DD-MM-YYYY

                        String dbDate = convertDateToDbFormat(rawDate);
                        String fajr = cleanTime(timings.optString("Fajr"));
                        String sunrise = cleanTime(timings.optString("Sunrise"));
                        String dhuhr = cleanTime(timings.optString("Dhuhr"));
                        String asr = cleanTime(timings.optString("Asr"));
                        String maghrib = cleanTime(timings.optString("Maghrib"));
                        String isha = cleanTime(timings.optString("Isha"));

                        db.insertPrayerTimes(dbDate, city, country, fajr, sunrise, dhuhr, asr, maghrib, isha);
                    }
                    Log.d(TAG, "Successfully cached month " + month + "/" + year + " for " + city + ", " + country);
                }
            } else {
                Log.w(TAG, "AlAdhan API returned HTTP " + responseCode);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching/saving month " + month + "/" + year, e);
        }
    }
}
