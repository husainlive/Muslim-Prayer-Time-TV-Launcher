package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

public class PrayerAlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "PrayerAlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String prayerName = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_NAME);
        String prayerTime = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_TIME);
        if (prayerName == null) prayerName = "Prayer";
        if (prayerTime == null) prayerTime = "";

        Log.d(TAG, "onReceive prayer alarm: " + prayerName + " at " + prayerTime);

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = null;
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TvHome:PrayerAlarmWakeLock");
            wakeLock.acquire(2 * 60 * 1000L); // 2 minutes
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
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) {
                try {
                    wakeLock.release();
                } catch (Exception ignored) {}
            }
        }

        // Keep future prayer alarms scheduled
        PrayerAlarmScheduler.scheduleAlarms(context);
    }
}
