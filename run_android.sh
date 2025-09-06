#!/bin/bash

# Fail script on any error
set -e

# Define the package and main activity
PACKAGE_NAME="com.hackathon.powerguard"
MAIN_ACTIVITY="com.hackathon.powerguard.MainActivity"

# Define the module (lowercase for directory path)
MODULE="app"

# Go to your project directory
cd /Users/hitesh.chopra/AndroidStudioProjects/PowerGuard || exit

# Check if Gradle wrapper exists, otherwise fallback to system Gradle
if [ -f "./gradlew" ]; then
    GRADLE_CMD="./gradlew"
else
    GRADLE_CMD="gradle"
fi

# Check if --install-only flag is provided
if [ "$1" = "--install-only" ]; then
    echo "🔄 Skipping build, installing existing APK..."
else
    # Build the app without clean for faster builds
    echo "🔄 Building $MODULE..."
    $GRADLE_CMD :app:assembleDebug
fi

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

# Location of the built APK (notice lowercase 'debug')
APK_PATH="$MODULE/build/outputs/apk/debug/$MODULE-debug.apk"

# Verify APK exists
if [ ! -f "$APK_PATH" ]; then
    echo "❌ APK not found at: $APK_PATH"
    echo "Check build output for errors."
    exit 1
fi

# Uninstall existing app first
echo "🗑️ Uninstalling existing app..."
$ADB_CMD -s $DEVICE uninstall $PACKAGE_NAME || true

# Install the app
echo "📱 Installing app from $APK_PATH..."
$ADB_CMD -s $DEVICE install -d -r $APK_PATH

# Launch the app
echo "🚀 Launching the app on device: $DEVICE"
$ADB_CMD -s $DEVICE shell am start -n "$PACKAGE_NAME/$MAIN_ACTIVITY"

echo "✅ App successfully installed and launched!"