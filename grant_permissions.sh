#!/bin/bash

# Fail script on any error
set -e

# Define the package name
PACKAGE_NAME="com.hackathon.powergaurd"

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

echo "🔒 Granting permissions for PowerGuard privileged system app..."
echo "📋 API 35 compatibility: Some permissions are handled differently in API 35."

echo "📱 Step 1: Granting permissions using pm grant..."

# Standard permissions that can be granted via pm grant
echo "  - Granting BATTERY_STATS permission..."
$ADB_CMD -s $DEVICE shell pm grant $PACKAGE_NAME android.permission.BATTERY_STATS

echo "  - Granting WRITE_SECURE_SETTINGS permission..."
$ADB_CMD -s $DEVICE shell pm grant $PACKAGE_NAME android.permission.WRITE_SECURE_SETTINGS

echo "  - Granting READ_LOGS permission..."
$ADB_CMD -s $DEVICE shell pm grant $PACKAGE_NAME android.permission.READ_LOGS

# Privileged permissions - cannot be granted with pm grant but are available automatically
echo "  - Note: FORCE_STOP_PACKAGES permission is automatically granted to privileged system apps"
echo "    This permission cannot be granted via pm grant but is available when installed in /system/priv-app"

echo "  - Note: MANAGE_NETWORK_POLICY permission is automatically granted to privileged system apps"
echo "    This permission cannot be granted via pm grant but is available when installed in /system/priv-app"
echo "    API 35 Note: NetworkPolicyManager is not available in the public SDK. Alternative approaches are used."

# QUERY_ALL_PACKAGES as a privileged permission
echo "  - Note: QUERY_ALL_PACKAGES is a normal permission but restricted via Play policy."
echo "    It cannot be granted via pm grant. If your app is a privileged system app, ensure this is in:"
echo "      /system/etc/permissions/privapp-permissions-<yourapp>.xml"
echo ""
echo "    Example:"
echo "    <permissions>"
echo "        <privapp-permissions package=\"$PACKAGE_NAME\">"
echo "            <permission name=\"android.permission.QUERY_ALL_PACKAGES\" />"
echo "        </privapp-permissions>"
echo "    </permissions>"
echo ""
echo "    After pushing the XML and placing the APK under /system/priv-app/, reboot the device."

# Device power cannot be granted
echo "  - Note: DEVICE_POWER permission cannot be granted via pm grant"
echo "    This permission is managed by role and not directly grantable."
echo "    PowerGuard will use alternative approaches to power management."

echo ""
echo "📱 Step 2: Granting special permissions using AppOps..."

echo "  - Granting PACKAGE_USAGE_STATS via AppOps..."
$ADB_CMD -s $DEVICE shell appops set $PACKAGE_NAME android:get_usage_stats allow

echo ""
echo "📱 Step 3: Manual permission steps required by user..."
echo "  - WRITE_SETTINGS permission requires manual user action:"
echo "    1. Go to device Settings -> Apps -> Special app access -> Modify system settings"
echo "    2. Find 'PowerGuard' in the list and toggle the switch ON"
echo "    3. If this menu is not available in your system, use this adb command to attempt setup:"
echo "       adb -s $DEVICE shell pm grant $PACKAGE_NAME android.permission.WRITE_SETTINGS"

echo "  - DATA SAVER configuration (API 35):"
echo "    To maximize network optimization effectiveness in API 35, manual Data Saver configuration is recommended:"
echo "    1. Go to device Settings -> Network & internet -> Data Saver"
echo "    2. Enable Data Saver"
echo "    3. In 'Unrestricted data' list, ensure critical apps are allowed"

echo ""
echo "✅ Permission granting completed!"
echo ""
echo "⚠️ IMPORTANT: Manual permissions require user action as described above."
echo "⚠️ Some system permissions are automatically granted to apps in /system/priv-app."
echo "⚠️ QUERY_ALL_PACKAGES must be declared in a privapp-permissions XML if you're a system app."
echo ""
echo "ℹ️ If you experience permission issues, please restart your device after running this script."