package com.gathin.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;

public class PrayerAlarmScheduler {
    private static final String TAG = "PrayerAlarmScheduler";
    public static final String ACTION_PRAYER_ALARM = "com.gathin.tvhome.ACTION_PRAYER_ALARM";
    public static final String EXTRA_PRAYER_NAME = "prayer_name";
    public static final String EXTRA_PRAYER_TIME = "prayer_time";

    public static void scheduleAlarms(Context context) {
        PrayerManager pm = PrayerManager.getInstance(context);
        if (!pm.isNotificationOverlayEnabled() && !pm.isAzanSoundEnabled()) {
            Log.d(TAG, "Both Azan and prayer notifications are disabled. Skipping alarm scheduling.");
            return;
        }

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Calendar now = Calendar.getInstance();
        PrayerManager.PrayerSchedule schedule = pm.getTodaySchedule();
        if (schedule == null || schedule.items == null) return;

        int reqCode = 101;
        for (PrayerManager.PrayerItem item : schedule.items) {
            // Sunrise is astronomical, not a prayer with azan
            if ("Sunrise".equalsIgnoreCase(item.name)) continue;
            if (!pm.isPrayerNotificationEnabled(item.name)) continue;

            String timeStr = item.adjustedTime; // "HH:mm"
            String[] parts = timeStr.split(":");
            if (parts.length != 2) continue;

            try {
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);

                Calendar target = (Calendar) now.clone();
                target.set(Calendar.HOUR_OF_DAY, hour);
                target.set(Calendar.MINUTE, minute);
                target.set(Calendar.SECOND, 0);
                target.set(Calendar.MILLISECOND, 0);

                if (target.before(now)) {
                    target.add(Calendar.DAY_OF_YEAR, 1);
                }

                Intent intent = new Intent(context, PrayerAlarmReceiver.class);
                intent.setAction(ACTION_PRAYER_ALARM);
                intent.putExtra(EXTRA_PRAYER_NAME, item.name);
                intent.putExtra(EXTRA_PRAYER_TIME, item.formattedTime);

                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }

                PendingIntent pi = PendingIntent.getBroadcast(context, reqCode++, intent, flags);

                long triggerMillis = target.getTimeInMillis();
                boolean scheduled = false;
                if (pm.isAzanSleepModeEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        Intent showIntent = new Intent(context, PrayerSettingsActivity.class);
                        PendingIntent showPi = PendingIntent.getActivity(context, reqCode + 1000, showIntent, flags);
                        AlarmManager.AlarmClockInfo clockInfo = new AlarmManager.AlarmClockInfo(triggerMillis, showPi);
                        am.setAlarmClock(clockInfo, pi);
                        scheduled = true;
                        Log.d(TAG, "Scheduled AlarmClock for " + item.name + " at " + target.getTime());
                    } catch (Exception e) {
                        Log.w(TAG, "setAlarmClock failed, falling back", e);
                    }
                }
                if (!scheduled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (am.canScheduleExactAlarms()) {
                            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi);
                        } else {
                            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi);
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi);
                    } else {
                        am.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, pi);
                    }
                    Log.d(TAG, "Scheduled exact alarm for " + item.name + " at " + target.getTime());
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed scheduling alarm for " + item.name, e);
            }
        }
    }
}
