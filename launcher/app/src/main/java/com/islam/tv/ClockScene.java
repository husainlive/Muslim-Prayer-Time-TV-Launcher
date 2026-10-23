package com.islam.tv;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.util.Random;

/**
 * The clock screensaver's scene (layout dream_clock): the thin clock, Hijri date, next prayer time,
 * and weather on black over a slow ambient glow. Every minute the clock drifts to a new spot and the
 * glow shifts colour, so nothing burns in.
 * Shared by ClockDream (the system screensaver) and ClockActivity (Home left idle).
 */
final class ClockScene {

    private static final String TAG = "ClockScene";
    private static final long MOVE_MS = 60_000L;
    /** Deep, low-brightness glow colours; the glow is drawn at partial alpha on black. */
    private static final int[] PALETTE = {0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final View root;
    private final View content;
    private final GradientDrawable glow;
    private int colorIndex;
    private int glowColor;

    private final TextView hijriDateTv;
    private final View dateDivider;
    private final View prayerContainer;
    private final TextView nextPrayerNameTv;
    private final TextView nextPrayerTimeTv;
    private final TextView nextPrayerCountdownTv;
    private final TextView weatherTv;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateInfo();
            moveClock();
            shiftGlow();
            handler.postDelayed(this, MOVE_MS);
        }
    };

    /** root is the inflated dream_clock layout. */
    ClockScene(View root) {
        this.root = root;
        content = root.findViewById(R.id.dreamContent);
        View ambient = root.findViewById(R.id.dreamAmbient);
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambient.setBackground(glow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius((r - l) * 0.55f));
        colorIndex = random.nextInt(PALETTE.length);
        setGlow(PALETTE[colorIndex]);

        hijriDateTv = root.findViewById(R.id.dreamHijriDate);
        dateDivider = root.findViewById(R.id.dreamDateDivider);
        prayerContainer = root.findViewById(R.id.dreamPrayerContainer);
        nextPrayerNameTv = root.findViewById(R.id.dreamNextPrayerName);
        nextPrayerTimeTv = root.findViewById(R.id.dreamNextPrayerTime);
        nextPrayerCountdownTv = root.findViewById(R.id.dreamNextPrayerCountdown);
        weatherTv = root.findViewById(R.id.dreamWeather);

        if (prayerContainer != null) {
            prayerContainer.setSelected(true);
        }

        updateInfo();
    }

    void start() {
        updateInfo();
        content.setAlpha(0f);
        content.post(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        });
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, MOVE_MS);
    }

    void stop() {
        handler.removeCallbacks(tick);
    }

    private void updateInfo() {
        Context ctx = root != null ? root.getContext() : null;
        if (ctx == null) return;
        try {
            PrayerManager pm = PrayerManager.getInstance(ctx);
            if (hijriDateTv != null) {
                String hijri = pm.getHijriDateString();
                if (hijri != null && !hijri.isEmpty()) {
                    hijriDateTv.setText(hijri);
                    hijriDateTv.setVisibility(View.VISIBLE);
                    if (dateDivider != null) dateDivider.setVisibility(View.VISIBLE);
                } else {
                    hijriDateTv.setVisibility(View.GONE);
                    if (dateDivider != null) dateDivider.setVisibility(View.GONE);
                }
            }

            if (nextPrayerNameTv != null && prayerContainer != null) {
                PrayerManager.PrayerSchedule schedule = pm.getTodaySchedule();
                PrayerManager.PrayerItem nextItem = null;
                if (schedule != null && schedule.items != null) {
                    for (PrayerManager.PrayerItem item : schedule.items) {
                        if (item.isNext) {
                            nextItem = item;
                            break;
                        }
                    }
                }
                if (nextItem != null) {
                    prayerContainer.setVisibility(View.VISIBLE);
                    nextPrayerNameTv.setText(nextItem.name);
                    if (nextPrayerTimeTv != null) nextPrayerTimeTv.setText(nextItem.formattedTime);
                    if (nextPrayerCountdownTv != null) {
                        if (nextItem.countdownText != null && !nextItem.countdownText.isEmpty()) {
                            nextPrayerCountdownTv.setText("• " + nextItem.countdownText);
                            nextPrayerCountdownTv.setVisibility(View.VISIBLE);
                        } else {
                            nextPrayerCountdownTv.setVisibility(View.GONE);
                        }
                    }
                } else {
                    prayerContainer.setVisibility(View.GONE);
                }
            }

            if (weatherTv != null) {
                WeatherManager wm = WeatherManager.getInstance(ctx);
                if (wm.isWeatherEnabled()) {
                    String cached = wm.getCachedWeather();
                    if (cached != null && !cached.isEmpty()) {
                        weatherTv.setText(cached);
                        weatherTv.setVisibility(View.VISIBLE);
                    } else {
                        weatherTv.setVisibility(View.GONE);
                    }
                    wm.loadWeather(false, text -> {
                        if (weatherTv != null && text != null && !text.isEmpty()) {
                            weatherTv.post(() -> {
                                weatherTv.setText(text);
                                weatherTv.setVisibility(View.VISIBLE);
                            });
                        }
                    });
                } else {
                    weatherTv.setVisibility(View.GONE);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error updating prayer or weather info in clock screensaver: " + t.getMessage());
        }
    }

    private void moveClock() {
        content.animate().alpha(0f).setDuration(900).withEndAction(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        }).start();
    }

    /** A random spot that keeps the whole clock on screen, with the glow centred behind it. */
    private void placeClock() {
        View parent = (View) content.getParent();
        if (parent == null) return;
        if (parent.getWidth() <= 0 || parent.getHeight() <= 0) {
            content.post(this::placeClock);
            return;
        }
        int maxX = Math.max(0, parent.getWidth() - content.getWidth());
        int maxY = Math.max(0, parent.getHeight() - content.getHeight());
        float x = maxX * (0.1f + 0.8f * random.nextFloat());
        float y = maxY * (0.1f + 0.8f * random.nextFloat());
        content.setX(x);
        content.setY(y);
        glow.setGradientCenter((x + content.getWidth() / 2f) / parent.getWidth(),
                (y + content.getHeight() / 2f) / parent.getHeight());
    }

    private void shiftGlow() {
        int from = glowColor;
        colorIndex = (colorIndex + 1) % PALETTE.length;
        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), from, PALETTE[colorIndex]);
        anim.setDuration(8000);
        anim.addUpdateListener(a -> setGlow((int) a.getAnimatedValue()));
        anim.start();
    }

    private void setGlow(int color) {
        glowColor = color;
        int c = color & 0x00FFFFFF;
        glow.setColors(new int[]{0x40000000 | c, 0x14000000 | c, 0x00000000 | c});
    }
}
