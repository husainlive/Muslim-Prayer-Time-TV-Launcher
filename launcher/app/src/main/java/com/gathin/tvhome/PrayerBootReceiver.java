package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class PrayerBootReceiver extends BroadcastReceiver {
    private static final String TAG = "PrayerBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Boot/Time change event received, rescheduling prayer alarms");
        PrayerAlarmScheduler.scheduleAlarms(context);
    }
}
