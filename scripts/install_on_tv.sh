#!/usr/bin/env bash
set -e

APK_PATH="launcher/app/build/outputs/apk/release/app-release.apk"
TV_IP="192.168.18.9"

echo "=== Vibe Code TV Installer ==="
echo "Checking for TV connection ($TV_IP)..."

# Check if already connected in adb devices
DEVICE=$(adb devices | grep -E "192\.168\.18\.9|adb-0000AC1794DB8296" | awk '{print $1}' | head -n 1)

MAX_RETRIES=600
RETRY_COUNT=0

while [ -z "$DEVICE" ] && [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    RETRY_COUNT=$((RETRY_COUNT + 1))
    
    # 1. Direct device check in adb devices
    DEVICE=$(adb devices | grep -E "192\.168\.18\.9|adb-0000AC1794DB8296" | awk '{print $1}' | head -n 1)
    if [ -n "$DEVICE" ]; then break; fi

    # 2. Check mDNS for wireless debugging TLS port
    MDNS_PORT=$(adb mdns services 2>/dev/null | grep -E "adb-0000AC1794DB8296.*_adb-tls-connect" | awk '{print $3}' | cut -d':' -f2 | head -n 1)
    if [ -n "$MDNS_PORT" ]; then
        echo "Found TV mDNS port: $MDNS_PORT. Connecting..."
        adb connect "$TV_IP:$MDNS_PORT" || true
        DEVICE=$(adb devices | grep -E "192\.168\.18\.9|adb-0000AC1794DB8296" | awk '{print $1}' | head -n 1)
        if [ -n "$DEVICE" ]; then break; fi
    fi

    # 3. Try standard port 5555
    adb connect "$TV_IP:5555" >/dev/null 2>&1 || true
    DEVICE=$(adb devices | grep -E "192\.168\.18\.9|adb-0000AC1794DB8296" | awk '{print $1}' | head -n 1)
    if [ -n "$DEVICE" ]; then break; fi

    if [ $((RETRY_COUNT % 10)) -eq 0 ]; then
        echo "[$RETRY_COUNT/$MAX_RETRIES] Still waiting for TV ($TV_IP) to wake up..."
    fi

    sleep 3
done

if [ -z "$DEVICE" ]; then
    echo "ERROR: TV is currently unreachable or in sleep mode."
    echo "Please wake the TV using the remote control and ensure Wireless Debugging / ADB is ON."
    exit 1
fi

echo "Connected to device: $DEVICE"
echo "Installing $APK_PATH..."
adb -s "$DEVICE" install -r "$APK_PATH"

echo "Granting permissions..."
adb -s "$DEVICE" shell pm grant com.islam.tv android.permission.WRITE_SECURE_SETTINGS 2>/dev/null || true

echo "Setting as home activity..."
adb -s "$DEVICE" shell cmd package set-home-activity --user 0 com.islam.tv/.MainActivity 2>/dev/null || true

echo "Starting Launcher Activity..."
adb -s "$DEVICE" shell am start -W -n com.islam.tv/.MainActivity

echo "=== Installation & Launch Complete! ==="
