package com.islam.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

public class PrayerAlertActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoDismissRunnable = this::dismissAlert;
    private android.content.BroadcastReceiver closeReceiver;
    private boolean wasScreenOff = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        wasScreenOff = getIntent() != null && (getIntent().getBooleanExtra(PrayerAlarmReceiver.EXTRA_WAS_SCREEN_OFF, false)
                || "true".equalsIgnoreCase(getIntent().getStringExtra(PrayerAlarmReceiver.EXTRA_WAS_SCREEN_OFF)));

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        // TV overlay flags & screen wakeup
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        );

        try {
            android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(android.content.Context.KEYGUARD_SERVICE);
            if (km != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                km.requestDismissKeyguard(this, null);
            }
        } catch (Exception ignored) {}

        setContentView(R.layout.activity_prayer_alert);

        String prayerName = getIntent().getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_NAME);
        String prayerTime = getIntent().getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_TIME);
        if (prayerName == null) prayerName = "Prayer";
        if (prayerTime == null) prayerTime = "";

        TextView title = findViewById(R.id.alertTitle);
        TextView time = findViewById(R.id.alertTime);
        Button btnDismiss = findViewById(R.id.btnDismissAlert);

        title.setText("Time for " + prayerName + " Prayer");
        time.setText(prayerTime);

        btnDismiss.setOnClickListener(v -> dismissAlert());
        btnDismiss.requestFocus();

        // Listen for close signal from PrayerAlarmService when Azan finishes
        closeReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, Intent intent) {
                finishAndClean();
            }
        };
        android.content.IntentFilter filter = new android.content.IntentFilter(PrayerAlarmService.ACTION_CLOSE_ALERT);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(closeReceiver, filter);
        }

        // If Azan sound is enabled, stay visible during full playback (~3.5 to 4.5 min) with a 5-min safety timeout.
        // If sound is disabled, auto-dismiss after 35 seconds.
        PrayerManager pm = PrayerManager.getInstance(this);
        long timeoutMs = pm.isAzanSoundEnabled() ? 300_000L : 35_000L;
        handler.postDelayed(autoDismissRunnable, timeoutMs);
    }

    private void dismissAlert() {
        handler.removeCallbacks(autoDismissRunnable);
        Intent dismissIntent = new Intent(this, PrayerAlarmService.class);
        dismissIntent.setAction(PrayerAlarmService.ACTION_DISMISS);
        dismissIntent.putExtra(PrayerAlarmReceiver.EXTRA_WAS_SCREEN_OFF, wasScreenOff);
        startService(dismissIntent);
        finishAndClean();
        if (wasScreenOff) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                HomeKeyService.turnScreenOff();
            }, 600);
        }
    }

    private void finishAndClean() {
        handler.removeCallbacks(autoDismissRunnable);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask();
        } else {
            finish();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            dismissAlert();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(autoDismissRunnable);
        if (closeReceiver != null) {
            try {
                unregisterReceiver(closeReceiver);
            } catch (Exception ignored) {}
            closeReceiver = null;
        }
        super.onDestroy();
    }
}
