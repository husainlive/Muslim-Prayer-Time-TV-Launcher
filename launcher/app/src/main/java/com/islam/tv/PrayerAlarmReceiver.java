package com.islam.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

public class PrayerAlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "PrayerAlarmReceiver";

    private static PowerManager.WakeLock sCpuWakeLock;
    private static PowerManager.WakeLock sScreenWakeLock;

    public static synchronized void acquireStaticWakeLock(Context context) {
        try {
            if (sCpuWakeLock == null) {
                PowerManager pm = (PowerManager) context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    sCpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TvHome:PrayerAlarmCpuWakeLock");
                    sCpuWakeLock.setReferenceCounted(false);
                }
            }
            if (sCpuWakeLock != null) {
                sCpuWakeLock.acquire(6 * 60 * 1000L); // 6 min safety timeout
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed acquiring static wake lock", e);
        }
    }

    public static synchronized void releaseStaticWakeLock() {
        try {
            if (sCpuWakeLock != null && sCpuWakeLock.isHeld()) {
                sCpuWakeLock.release();
            }
        } catch (Exception ignored) {}
        try {
            if (sScreenWakeLock != null && sScreenWakeLock.isHeld()) {
                sScreenWakeLock.release();
            }
        } catch (Exception ignored) {}
    }

    public static final String EXTRA_WAS_SCREEN_OFF = "was_screen_off";
    public static final String ACTION_TEST_PRAYER_ALARM = "com.islam.tv.ACTION_TEST_PRAYER_ALARM";
    public static final String ACTION_TEST_SLEEP = "com.islam.tv.ACTION_TEST_SLEEP";

    public static final String ACTION_TEST_SLEEP_AND_ALARM = "com.islam.tv.ACTION_TEST_SLEEP_AND_ALARM";
    public static final String ACTION_SET_ROWS_BELOW_DOCK = "com.islam.tv.ACTION_SET_ROWS_BELOW_DOCK";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && ACTION_SET_ROWS_BELOW_DOCK.equals(intent.getAction())) {
            int rows = intent.getIntExtra("rows", 1);
            SharedPreferences choices = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
            choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, rows).apply();
            Log.d(TAG, "ACTION_SET_ROWS_BELOW_DOCK: set rows_below_dock to " + rows);
            return;
        }

        if (intent != null && ACTION_TEST_SLEEP.equals(intent.getAction())) {
            Log.d(TAG, "ACTION_TEST_SLEEP received, turning screen off");
            HomeKeyService.turnScreenOff();
            return;
        }

        if (intent != null && ACTION_TEST_SLEEP_AND_ALARM.equals(intent.getAction())) {
            Log.d(TAG, "Scheduling test alarm in 8s and putting screen to sleep now");
            android.app.AlarmManager am = (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                Intent alarmIntent = new Intent(context, PrayerAlarmReceiver.class);
                alarmIntent.setAction(PrayerAlarmScheduler.ACTION_PRAYER_ALARM);
                alarmIntent.putExtra(PrayerAlarmScheduler.EXTRA_PRAYER_NAME, "Dhuhr");
                alarmIntent.putExtra(PrayerAlarmScheduler.EXTRA_PRAYER_TIME, "11:19 AM");
                int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= android.app.PendingIntent.FLAG_IMMUTABLE;
                android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(context, 9999, alarmIntent, flags);

                long trigger = System.currentTimeMillis() + 8000L;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Intent showIntent = new Intent(context, PrayerAlertActivity.class);
                    android.app.PendingIntent showPi = android.app.PendingIntent.getActivity(context, 9998, showIntent, flags);
                    am.setAlarmClock(new android.app.AlarmManager.AlarmClockInfo(trigger, showPi), pi);
                } else {
                    am.setExact(android.app.AlarmManager.RTC_WAKEUP, trigger, pi);
                }
            }
            HomeKeyService.turnScreenOff();
            return;
        }

        String prayerName = intent != null ? intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_NAME) : null;
        String prayerTime = intent != null ? intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_TIME) : null;
        if (prayerName == null) prayerName = "Prayer";
        if (prayerTime == null) prayerTime = "";

        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean wasScreenOff = false;
        if (intent != null) {
            wasScreenOff = intent.getBooleanExtra(EXTRA_WAS_SCREEN_OFF, false)
                    || "true".equalsIgnoreCase(intent.getStringExtra(EXTRA_WAS_SCREEN_OFF));
        }
        if (!wasScreenOff && powerManager != null) {
            wasScreenOff = !powerManager.isInteractive();
        }

        Log.d(TAG, "onReceive prayer alarm: " + prayerName + " at " + prayerTime + ", wasScreenOff=" + wasScreenOff);

        PrayerManager pm = PrayerManager.getInstance(context);

        // Hold static CPU wake lock so device stays awake until service finishes
        acquireStaticWakeLock(context);

        // If sleep mode is enabled or screen is off, wake the TV screen immediately
        if (wasScreenOff || pm.isAzanSleepModeEnabled()) {
            try {
                if (powerManager != null) {
                    @SuppressWarnings("deprecation")
                    PowerManager.WakeLock screenWl = powerManager.newWakeLock(
                            PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                            PowerManager.ACQUIRE_CAUSES_WAKEUP |
                            PowerManager.ON_AFTER_RELEASE,
                            "TvHome:PrayerAlarmScreenWake");
                    screenWl.setReferenceCounted(false);
                    screenWl.acquire(60 * 1000L); // 60s
                    sScreenWakeLock = screenWl;

                    // Also try PowerManager.wakeUp via reflection for devices requiring direct wake signal
                    try {
                        java.lang.reflect.Method wakeUpMethod = PowerManager.class.getMethod("wakeUp", long.class);
                        wakeUpMethod.invoke(powerManager, android.os.SystemClock.uptimeMillis());
                    } catch (Throwable ignored) {}
                }
            } catch (Exception e) {
                Log.w(TAG, "Screen wake lock on receive failed: " + e.getMessage());
            }

            // Launch PrayerAlertActivity directly to turn on display hardware and show overlay
            Intent alertIntent = new Intent(context, PrayerAlertActivity.class);
            alertIntent.putExtra(PrayerAlarmScheduler.EXTRA_PRAYER_NAME, prayerName);
            alertIntent.putExtra(PrayerAlarmScheduler.EXTRA_PRAYER_TIME, prayerTime);
            alertIntent.putExtra(EXTRA_WAS_SCREEN_OFF, wasScreenOff);
            alertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

            boolean started = HomeKeyService.startAlertActivity(alertIntent);
            if (!started) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        android.app.ActivityOptions opts = android.app.ActivityOptions.makeBasic();
                        opts.setPendingIntentBackgroundActivityStartMode(
                                android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                        context.startActivity(alertIntent, opts.toBundle());
                    } else {
                        context.startActivity(alertIntent);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed launching PrayerAlertActivity from receiver: " + e.getMessage());
                }
            }
        }

        try {
            Intent svcIntent = new Intent(context, PrayerAlarmService.class);
            svcIntent.putExtra(PrayerAlarmScheduler.EXTRA_PRAYER_NAME, prayerName);
            svcIntent.putExtra(PrayerAlarmScheduler.EXTRA_PRAYER_TIME, prayerTime);
            svcIntent.putExtra(EXTRA_WAS_SCREEN_OFF, wasScreenOff);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svcIntent);
            } else {
                context.startService(svcIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting PrayerAlarmService", e);
            releaseStaticWakeLock();
        }

        // Keep future prayer alarms scheduled
        PrayerAlarmScheduler.scheduleAlarms(context);
    }
}
