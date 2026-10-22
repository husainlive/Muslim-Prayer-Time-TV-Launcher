package com.gathin.tvhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class PrayerAlarmService extends Service {
    private static final String TAG = "PrayerAlarmService";
    public static final String ACTION_DISMISS = "com.gathin.tvhome.ACTION_DISMISS_ADHAN";
    private static final String CHANNEL_ID = "prayer_channel";
    private static final int NOTIFICATION_ID = 2001;

    private MediaPlayer mediaPlayer;
    private PowerManager.WakeLock wakeLock;
    private PowerManager.WakeLock screenWakeLock;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_DISMISS.equals(intent.getAction())) {
            Log.d(TAG, "Dismiss action received, stopping Adhan");
            stopSelf();
            return START_NOT_STICKY;
        }

        String prayerName = intent != null ? intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_NAME) : "Prayer";
        String prayerTime = intent != null ? intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_TIME) : "";
        if (prayerName == null) prayerName = "Prayer";
        if (prayerTime == null) prayerTime = "";

        PrayerManager pm = PrayerManager.getInstance(this);

        createNotificationChannel();
        acquireWakeLock();

        // Build Dismiss action PendingIntent
        Intent dismissIntent = new Intent(this, PrayerAlarmService.class);
        dismissIntent.setAction(ACTION_DISMISS);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent dismissPending = PendingIntent.getService(this, 10, dismissIntent, piFlags);

        // Build overlay intent
        Intent alertIntent = new Intent(this, PrayerAlertActivity.class);
        alertIntent.putExtra(PrayerAlarmScheduler.EXTRA_PRAYER_NAME, prayerName);
        alertIntent.putExtra(PrayerAlarmScheduler.EXTRA_PRAYER_TIME, prayerTime);
        alertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent fullScreenPending = PendingIntent.getActivity(this, 11, alertIntent, piFlags);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Time for " + prayerName + " Prayer")
                .setContentText(prayerTime + " • Adhan")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullScreenPending, true)
                .setContentIntent(fullScreenPending)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPending)
                .setAutoCancel(true)
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);

        // Display overlay or wake screen if enabled or in sleep mode
        if (pm.isNotificationOverlayEnabled() || pm.isAzanSleepModeEnabled()) {
            try {
                startActivity(alertIntent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch PrayerAlertActivity: " + e.getMessage());
            }
        }

        // Play Azan sound if enabled
        if (pm.isAzanSoundEnabled()) {
            playAzanSound(pm.getSelectedAzanSound());
        } else {
            // If sound is disabled and overlay auto-dismisses, stop service when alert finishes
            Log.d(TAG, "Azan sound disabled, service running for alert");
        }

        return START_NOT_STICKY;
    }

    private void playAzanSound(String soundChoice) {
        try {
            int rawRes = R.raw.azan_1;
            if ("azan_2".equals(soundChoice)) {
                rawRes = R.raw.azan_2;
            } else if ("azan_3".equals(soundChoice)) {
                rawRes = R.raw.azan_3;
            }

            Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + rawRes);

            // Ensure alarm audio stream is audible if TV was muted
            try {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    int vol = am.getStreamVolume(AudioManager.STREAM_ALARM);
                    int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                    if (vol == 0 && max > 0) {
                        am.setStreamVolume(AudioManager.STREAM_ALARM, (int) (max * 0.7f), 0);
                    }
                }
            } catch (Exception ignored) {}

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mediaPlayer.setDataSource(this, uri);
            mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            mediaPlayer.prepare();
            mediaPlayer.start();

            mediaPlayer.setOnCompletionListener(mp -> {
                Log.d(TAG, "Adhan finished playing");
                stopSelf();
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
                stopSelf();
                return true;
            });
        } catch (Exception e) {
            Log.e(TAG, "Error playing Azan audio", e);
            stopSelf();
        }
    }

    private void acquireWakeLock() {
        try {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TvHome:AdhanServiceWakeLock");
                wakeLock.acquire(5 * 60 * 1000L); // 5 min max

                PrayerManager pm = PrayerManager.getInstance(this);
                if (pm.isAzanSleepModeEnabled()) {
                    @SuppressWarnings("deprecation")
                    PowerManager.WakeLock sLock = powerManager.newWakeLock(
                            PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                            PowerManager.ACQUIRE_CAUSES_WAKEUP |
                            PowerManager.ON_AFTER_RELEASE,
                            "TvHome:AdhanServiceScreenWakeLock");
                    sLock.acquire(3 * 60 * 1000L);
                    screenWakeLock = sLock;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed acquiring wake lock", e);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Prayer Notifications & Adhan",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Alerts and Adhan for prayer times");
            channel.enableVibration(true);
            channel.setShowBadge(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "PrayerAlarmService onDestroy");
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {}
            wakeLock = null;
        }

        if (screenWakeLock != null && screenWakeLock.isHeld()) {
            try {
                screenWakeLock.release();
            } catch (Exception ignored) {}
            screenWakeLock = null;
        }

        // Release static CPU wake lock held since receiver triggered
        PrayerAlarmReceiver.releaseStaticWakeLock();
    }
}
