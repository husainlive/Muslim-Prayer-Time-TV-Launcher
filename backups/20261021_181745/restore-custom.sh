#!/usr/bin/env bash
# Restore script to re-enable all debloated packages and revert settings
set -euo pipefail

TV_HOST="${TV_HOST:-192.168.18.9}"
TV_PORT="${TV_PORT:-5555}"
TARGET="$TV_HOST:$TV_PORT"

echo "Re-enabling disabled packages on $TARGET..."
PACKAGES=(
    "com.smartdevice.recordactivation"
    "com.google.android.feedback"
    "com.android.tv.feedbackconsent"
    "com.android.ondevicepersonalization.services"
    "com.android.federatedcompute.services"
    "com.android.healthconnect.controller"
    "com.android.health.connect.backuprestore"
    "com.smartdevice.leanbacklauncher.customizer"
    "com.google.android.katniss"
    "com.android.hotwordenrollment.okgoogle"
    "com.android.hotwordenrollment.xgoogle"
    "com.google.android.apps.mediashell"
    "com.google.android.apps.tv.launcherx"
    "com.google.android.tungsten.setupwraith"
)

for pkg in "${PACKAGES[@]}"; do
    echo "Enabling $pkg..."
    adb -s "$TARGET" shell pm enable "$pkg" || true
done

echo "Resetting Private DNS to default (opportunistic)..."
adb -s "$TARGET" shell settings put global private_dns_mode opportunistic || true

echo "Restore finished."
