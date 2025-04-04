#!/bin/bash

# Fail script on any error
set -e

# Define the package and main activity
PACKAGE_NAME="com.hackathon.powergaurd"
MAIN_ACTIVITY="com.hackathon.powergaurd.MainActivity"

# Define the module and variant
MODULE="app"
FLAVOR="Debug"

# Go to your project directory
cd /Users/hitesh.chopra/AndroidStudioProjects/PowerGaurd || exit

# Check if Gradle wrapper exists, otherwise fallback to system Gradle
if [ -f "./gradlew" ]; then
    GRADLE_CMD="./gradlew"
else
    GRADLE_CMD="gradle"
fi

# Build and install the app
echo "🔄 Building and installing $MODULE:$FLAVOR..."
$GRADLE_CMD ":app:assembleDebug"

# Check if adb is available
ADB_CMD="adb"
if ! command -v $ADB_CMD &> /dev/null; then
    echo "❌ adb not found. Please ensure Android SDK platform-tools are installed."
    exit 1
fi

# Check if a device or emulator is connected
DEVICE=$($ADB_CMD devices | awk 'NR==2 {print $1}')
if [ -z "$DEVICE" ]; then
    echo "❌ No device/emulator detected. Please connect a device or start an emulator."
    exit 1
fi

# Check if the device is rooted or an emulator
IS_EMULATOR=$($ADB_CMD -s $DEVICE shell getprop ro.boot.qemu 2>/dev/null)
IS_ROOTED=$($ADB_CMD -s $DEVICE shell "su -c 'echo rooted'" 2>/dev/null | grep -q "rooted" && echo "true" || echo "false")

# Location of the built APK
APK_PATH="$MODULE/build/outputs/apk/$FLAVOR/debug/$MODULE-debug.apk"

# Install as system app if device is rooted or emulator
if [ "$IS_EMULATOR" == "1" ] || [ "$IS_ROOTED" == "true" ]; then
    echo "🔒 Detected rooted device or emulator. Installing as privileged system app..."
    
    # Remount system partition as read-write
    $ADB_CMD -s $DEVICE shell "su -c 'mount -o rw,remount /system'"
    
    # Push APK to system/priv-app directory instead of system/app
    SYSTEM_APP_DIR="/system/priv-app/PowerGuard"
    $ADB_CMD -s $DEVICE shell "su -c 'mkdir -p $SYSTEM_APP_DIR'"
    $ADB_CMD -s $DEVICE push $APK_PATH $SYSTEM_APP_DIR/PowerGuard.apk
    
    # Set correct permissions
    $ADB_CMD -s $DEVICE shell "su -c 'chmod 644 $SYSTEM_APP_DIR/PowerGuard.apk'"
    $ADB_CMD -s $DEVICE shell "su -c 'chown root:root $SYSTEM_APP_DIR/PowerGuard.apk'"
    
    # Remount system as read-only
    $ADB_CMD -s $DEVICE shell "su -c 'mount -o ro,remount /system'"
    
    # Reboot device to apply changes
    echo "🔄 Rebooting device to apply system app installation..."
    $ADB_CMD -s $DEVICE reboot
    
    echo "⏱️ Waiting for device to reboot..."
    $ADB_CMD wait-for-device
    
    echo "✅ Privileged system app installation complete!"
    echo "ℹ️ After reboot, run the grant_permissions.sh script to ensure all necessary permissions are granted."
else
    # Regular installation for non-rooted devices
    echo "📱 Installing as regular app (non-rooted device)..."
    $ADB_CMD -s $DEVICE install -r $APK_PATH
    
    # Launch the app
    echo "🚀 Launching the app on device: $DEVICE"
    $ADB_CMD -s $DEVICE shell am start -n "$PACKAGE_NAME/$MAIN_ACTIVITY"
    
    echo "⚠️ NOTE: Most system features will not work as this is not installed as a system app."
    echo "✅ App successfully installed and launched!"
fi