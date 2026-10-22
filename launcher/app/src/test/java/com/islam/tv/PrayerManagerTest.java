package com.islam.tv;

import org.junit.Test;

import java.time.chrono.HijrahDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PrayerManagerTest {

    @Test
    public void testPrayerNamesListHasSixPrayers() {
        assertEquals(6, PrayerManager.PRAYER_NAMES.length);
        assertEquals("Fajr", PrayerManager.PRAYER_NAMES[0]);
        assertEquals("Sunrise", PrayerManager.PRAYER_NAMES[1]);
        assertEquals("Dhuhr", PrayerManager.PRAYER_NAMES[2]);
        assertEquals("Asr", PrayerManager.PRAYER_NAMES[3]);
        assertEquals("Maghrib", PrayerManager.PRAYER_NAMES[4]);
        assertEquals("Isha", PrayerManager.PRAYER_NAMES[5]);
    }

    @Test
    public void testApplyAdjustmentZeroMinutes() {
        assertEquals("04:30", PrayerManager.applyAdjustment("04:30", 0));
        assertEquals("12:15", PrayerManager.applyAdjustment("12:15", 0));
    }

    @Test
    public void testApplyAdjustmentPositiveMinutes() {
        assertEquals("04:45", PrayerManager.applyAdjustment("04:30", 15));
        assertEquals("05:05", PrayerManager.applyAdjustment("04:55", 10));
        assertEquals("12:00", PrayerManager.applyAdjustment("11:58", 2));
    }

    @Test
    public void testApplyAdjustmentNegativeMinutes() {
        assertEquals("04:15", PrayerManager.applyAdjustment("04:30", -15));
        assertEquals("04:50", PrayerManager.applyAdjustment("05:05", -15));
    }

    @Test
    public void testApplyAdjustmentDayRollover() {
        assertEquals("00:05", PrayerManager.applyAdjustment("23:55", 10));
        assertEquals("23:55", PrayerManager.applyAdjustment("00:05", -10));
    }

    @Test
    public void testFormatTime24Hour() {
        assertEquals("04:30", PrayerManager.formatTime("04:30", true));
        assertEquals("12:00", PrayerManager.formatTime("12:00", true));
        assertEquals("15:45", PrayerManager.formatTime("15:45", true));
        assertEquals("23:59", PrayerManager.formatTime("23:59", true));
    }

    @Test
    public void testFormatTime12Hour() {
        assertEquals("4:30 AM", PrayerManager.formatTime("04:30", false));
        assertEquals("12:15 AM", PrayerManager.formatTime("00:15", false));
        assertEquals("12:00 PM", PrayerManager.formatTime("12:00", false));
        assertEquals("12:30 PM", PrayerManager.formatTime("12:30", false));
        assertEquals("3:45 PM", PrayerManager.formatTime("15:45", false));
        assertEquals("11:59 PM", PrayerManager.formatTime("23:59", false));
    }

    @Test
    public void testHijriDateFormatting() {
        HijrahDate date = HijrahDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.US);
        String formatted = formatter.format(date);
        assertNotNull(formatted);
        assertTrue(formatted.length() > 5);

        // Test day adjustments
        HijrahDate plusOne = date.plus(1, ChronoUnit.DAYS);
        HijrahDate minusOne = date.plus(-1, ChronoUnit.DAYS);
        assertNotNull(formatter.format(plusOne));
        assertNotNull(formatter.format(minusOne));
    }

    @Test
    public void testCleanTime() {
        assertEquals("04:19", PrayerManager.cleanTime("04:19 (+03)"));
        assertEquals("05:38", PrayerManager.cleanTime("05:38"));
        assertEquals("11:18", PrayerManager.cleanTime(" 11:18 (+03) "));
        assertEquals("00:00", PrayerManager.cleanTime("invalid"));
        assertEquals("00:00", PrayerManager.cleanTime(null));
    }

    @Test
    public void testConvertDateToDbFormat() {
        assertEquals("2026-10-26", PrayerManager.convertDateToDbFormat("26-10-2026"));
        assertEquals("2026-05-01", PrayerManager.convertDateToDbFormat("01-05-2026"));
        assertEquals("invalid", PrayerManager.convertDateToDbFormat("invalid"));
    }

    @Test
    public void testFindNextPrayer() {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        map.put("Fajr", "04:30");
        map.put("Sunrise", "05:45");
        map.put("Dhuhr", "11:35");
        map.put("Asr", "14:55");
        map.put("Maghrib", "17:15");
        map.put("Isha", "18:45");

        java.util.Calendar cal = java.util.Calendar.getInstance();

        // 10:00 AM -> next prayer is Dhuhr (11:35)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 10);
        cal.set(java.util.Calendar.MINUTE, 0);
        assertEquals("Dhuhr", PrayerManager.findNextPrayer(map, cal));

        // 12:00 PM -> next prayer is Asr (14:55)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 12);
        cal.set(java.util.Calendar.MINUTE, 0);
        assertEquals("Asr", PrayerManager.findNextPrayer(map, cal));

        // 16:00 PM -> next prayer is Maghrib (17:15)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 16);
        cal.set(java.util.Calendar.MINUTE, 0);
        assertEquals("Maghrib", PrayerManager.findNextPrayer(map, cal));

        // 18:00 PM -> next prayer is Isha (18:45)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 18);
        cal.set(java.util.Calendar.MINUTE, 0);
        assertEquals("Isha", PrayerManager.findNextPrayer(map, cal));

        // 20:00 PM -> all passed for today -> next prayer wraps to Fajr
        cal.set(java.util.Calendar.HOUR_OF_DAY, 20);
        cal.set(java.util.Calendar.MINUTE, 0);
        assertEquals("Fajr", PrayerManager.findNextPrayer(map, cal));
    }

    @Test
    public void testFindNextPrayerInfoWithCountdown() {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        map.put("Fajr", "04:30");
        map.put("Sunrise", "05:45");
        map.put("Dhuhr", "11:35");
        map.put("Asr", "14:55");
        map.put("Maghrib", "17:15");
        map.put("Isha", "18:45");

        java.util.Calendar cal = java.util.Calendar.getInstance();

        // 10:00 AM -> next is Dhuhr (11:35), diff = 95 minutes = 1h 35m
        cal.set(java.util.Calendar.HOUR_OF_DAY, 10);
        cal.set(java.util.Calendar.MINUTE, 0);
        PrayerManager.NextPrayerInfo info1 = PrayerManager.findNextPrayerInfo(map, cal);
        assertEquals("Dhuhr", info1.name);
        assertEquals(95, info1.minutesRemaining);
        assertEquals("in 1h 35m", info1.countdownText);

        // 11:20 AM -> next is Dhuhr (11:35), diff = 15 minutes
        cal.set(java.util.Calendar.HOUR_OF_DAY, 11);
        cal.set(java.util.Calendar.MINUTE, 20);
        PrayerManager.NextPrayerInfo info2 = PrayerManager.findNextPrayerInfo(map, cal);
        assertEquals("Dhuhr", info2.name);
        assertEquals(15, info2.minutesRemaining);
        assertEquals("in 15m", info2.countdownText);

        // 20:00 PM -> all passed for today -> next is Fajr tomorrow at 04:30
        // (24:00 - 20:00 = 4 hours) + 4h 30m = 8h 30m = 510 minutes
        cal.set(java.util.Calendar.HOUR_OF_DAY, 20);
        cal.set(java.util.Calendar.MINUTE, 0);
        PrayerManager.NextPrayerInfo info3 = PrayerManager.findNextPrayerInfo(map, cal);
        assertEquals("Fajr", info3.name);
        assertEquals(510, info3.minutesRemaining);
        assertEquals("in 8h 30m", info3.countdownText);
    }

    @Test
    public void testIqamaCountdownTransition() {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        map.put("Fajr", "04:30");
        map.put("Sunrise", "05:45");
        map.put("Dhuhr", "11:35");
        map.put("Asr", "14:55");
        map.put("Maghrib", "17:15");
        map.put("Isha", "18:45");

        java.util.Calendar cal = java.util.Calendar.getInstance();

        // 11:45 AM -> within Dhuhr Iqama window (11:35 + 20m offset = 11:55)
        // Remaining time is 10 minutes -> should show "Iqama in 10m"
        cal.set(java.util.Calendar.HOUR_OF_DAY, 11);
        cal.set(java.util.Calendar.MINUTE, 45);
        PrayerManager.NextPrayerInfo info = PrayerManager.findNextPrayerInfo(map, cal);
        assertTrue(info.isIqama);
        assertEquals("Dhuhr", info.name);
        assertEquals("Iqama in 10m", info.countdownText);

        // 17:20 PM -> within Maghrib Iqama window (17:15 + 10m offset = 17:25)
        // Remaining time is 5 minutes -> should show "Iqama in 5m"
        cal.set(java.util.Calendar.HOUR_OF_DAY, 17);
        cal.set(java.util.Calendar.MINUTE, 20);
        PrayerManager.NextPrayerInfo maghribInfo = PrayerManager.findNextPrayerInfo(map, cal);
        assertTrue(maghribInfo.isIqama);
        assertEquals("Maghrib", maghribInfo.name);
        assertEquals("Iqama in 5m", maghribInfo.countdownText);
    }
}
