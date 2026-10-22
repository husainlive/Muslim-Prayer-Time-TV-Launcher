package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
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

    @Override
    public void onReceive(Context context, Intent intent) {
        String prayerName = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_NAME);
        String prayerTime = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_TIME);
        if (prayerName == null) prayerName = "Prayer";
        if (prayerTime == null) prayerTime = "";

        Log.d(TAG, "onReceive prayer alarm: " + prayerName + " at " + prayerTime);

        PrayerManager pm = PrayerManager.getInstance(context);

        // Hold static CPU wake lock so device stays awake until service finishes
        acquireStaticWakeLock(context);

        // If sleep mode is enabled, wake the TV screen
        if (pm.isAzanSleepModeEnabled()) {
            try {
                PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
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
                }
            } catch (Exception e) {
                Log.w(TAG, "Screen wake lock on receive failed: " + e.getMessage());
            }
        }

        try {
            Intent svcIntent = new Intent(context, PrayerAlarmService.class);
            svcIntent.putExtra(PrayerAlarmScheduler.EXTRA_PRAYER_NAME, prayerName);
            svcIntent.putExtra(PrayerAlarmScheduler.EXTRA_PRAYER_TIME, prayerTime);

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
