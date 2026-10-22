package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Manages screen saver state: enabled/disabled, inactivity timeout, and target screensaver app.
 */
public class ScreenSaverManager {
    private static final String TAG = "ScreenSaverManager";
    public static final String KEY_SCREENSAVER_ENABLED = "screensaver_enabled";
    public static final String KEY_SCREENSAVER_TIMEOUT_MINUTES = "screensaver_timeout_minutes";
    public static final String KEY_SCREENSAVER_APP = "screensaver_app";
    public static final String KEY_SCREENSAVER_APP_LABEL = "screensaver_app_label";

    public static final String ID_BUILTIN_CLOCK = "builtin:clock";

    private static ScreenSaverManager instance;
    private final Context context;
    private final SharedPreferences prefs;

    public static class ScreenSaverOption {
        public final String id;
        public final String label;
        public final String packageName;
        public final boolean isBuiltIn;

        public ScreenSaverOption(String id, String label, String packageName, boolean isBuiltIn) {
            this.id = id;
            this.label = label;
            this.packageName = packageName;
            this.isBuiltIn = isBuiltIn;
        }
    }

    public static class AppChoice {
        public final String packageName;
        public final String label;

        public AppChoice(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }
    }

    private ScreenSaverManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized ScreenSaverManager getInstance(Context context) {
        if (instance == null) {
            instance = new ScreenSaverManager(context);
        }
        return instance;
    }

    public boolean isScreenSaverEnabled() {
        return prefs.getBoolean(KEY_SCREENSAVER_ENABLED, true);
    }

    public void setScreenSaverEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SCREENSAVER_ENABLED, enabled).apply();
    }

    public int getTimeoutMinutes() {
        return prefs.getInt(KEY_SCREENSAVER_TIMEOUT_MINUTES, 5);
    }

    public void setTimeoutMinutes(int minutes) {
        prefs.edit().putInt(KEY_SCREENSAVER_TIMEOUT_MINUTES, minutes).apply();
    }

    public long getTimeoutMillis() {
        if (!isScreenSaverEnabled()) return 0;
        int mins = getTimeoutMinutes();
        if (mins <= 0) return 0;
        return mins * 60 * 1000L;
    }

    public String getScreenSaverApp() {
        return prefs.getString(KEY_SCREENSAVER_APP, ID_BUILTIN_CLOCK);
    }

    public String getScreenSaverAppLabel() {
        return prefs.getString(KEY_SCREENSAVER_APP_LABEL, context.getString(R.string.screensaver_builtin_clock));
    }

    public void setScreenSaverApp(String id, String label) {
        prefs.edit()
                .putString(KEY_SCREENSAVER_APP, id)
                .putString(KEY_SCREENSAVER_APP_LABEL, label)
                .apply();
    }

    public List<ScreenSaverOption> getAvailableScreenSavers() {
        List<ScreenSaverOption> list = new ArrayList<>();
        list.add(new ScreenSaverOption(ID_BUILTIN_CLOCK, context.getString(R.string.screensaver_builtin_clock), context.getPackageName(), true));

        PackageManager pm = context.getPackageManager();

        // Query Android Dream Services
        try {
            Intent dreamIntent = new Intent("android.service.dreams.DreamService");
            List<ResolveInfo> dreamServices = pm.queryIntentServices(dreamIntent, PackageManager.GET_META_DATA);
            if (dreamServices != null) {
                for (ResolveInfo ri : dreamServices) {
                    ServiceInfo si = ri.serviceInfo;
                    if (si == null) continue;
                    if (context.getPackageName().equals(si.packageName)) continue; // Built-in clock is already first

                    CharSequence labelSeq = ri.loadLabel(pm);
                    String label = labelSeq != null ? labelSeq.toString() : si.packageName;
                    String id = "dream:" + si.packageName + "/" + si.name;
                    list.add(new ScreenSaverOption(id, label, si.packageName, false));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error querying dream services: " + e.getMessage());
        }

        return list;
    }

    public List<AppChoice> getInstalledApps() {
        List<AppChoice> apps = new ArrayList<>();
        PackageManager pm = context.getPackageManager();

        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(mainIntent, 0);

        // Fallback or union with standard LAUNCHER
        mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> standardList = pm.queryIntentActivities(mainIntent, 0);
        if (standardList != null) {
            if (list == null) list = new ArrayList<>();
            list.addAll(standardList);
        }

        if (list != null) {
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            for (ResolveInfo ri : list) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (context.getPackageName().equals(pkg)) continue;
                if (seen.contains(pkg)) continue;
                seen.add(pkg);

                CharSequence labelSeq = ri.loadLabel(pm);
                String label = labelSeq != null ? labelSeq.toString().trim() : pkg;
                apps.add(new AppChoice(pkg, label));
            }
        }

        Collections.sort(apps, Comparator.comparing(a -> a.label.toLowerCase()));
        return apps;
    }

    public void startScreenSaver(Context ctx) {
        if (!isScreenSaverEnabled()) return;
        String id = getScreenSaverApp();
        if (id == null || ID_BUILTIN_CLOCK.equals(id)) {
            Intent clockIntent = new Intent(ctx, ClockActivity.class);
            clockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(clockIntent);
            return;
        }

        try {
            if (id.startsWith("dream:")) {
                String comp = id.substring("dream:".length());
                String[] parts = comp.split("/");
                if (parts.length == 2) {
                    Intent launchIntent = ctx.getPackageManager().getLaunchIntentForPackage(parts[0]);
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(launchIntent);
                        return;
                    }
                }
            } else if (id.startsWith("app:")) {
                String pkg = id.substring("app:".length());
                Intent launchIntent = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(launchIntent);
                    return;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed launching custom screensaver app: " + id, e);
        }

        // Fallback to built-in clock
        Intent fallback = new Intent(ctx, ClockActivity.class);
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(fallback);
    }
}
