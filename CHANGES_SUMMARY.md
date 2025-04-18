# PowerGuard System App Implementation Changes

## Overview

The following changes were made to implement PowerGuard as a system app for enhanced battery and
network optimization capabilities.

## 1. AndroidManifest.xml Changes

- Added system-level permissions:
  - `android.permission.BATTERY_STATS`
  - `android.permission.DEVICE_POWER`
  - `android.permission.CHANGE_NETWORK_STATE`
  - `android.permission.MODIFY_PHONE_STATE`
  - `android.permission.MANAGE_NETWORK_POLICY`
  - `android.permission.READ_PHONE_STATE`
  - `android.permission.FORCE_STOP_PACKAGES`
  - `android.permission.KILL_BACKGROUND_PROCESSES`
  - `android.permission.WAKE_LOCK`
  - `android.permission.READ_LOGS`

- Added `android:sharedUserId="android.uid.system"` to the application tag to run with system
  privileges
- Added a foreground service declaration for PowerGuardService

## 2. Implementation of PowerGuardService

Created a new system service that:

- Runs as a foreground service to avoid being killed by the system
- Collects data about battery usage, app usage, and network usage
- Implements optimization strategies based on the collected data
- Uses system permissions to perform privileged operations like force stopping apps

## 3. Enhanced Data Models

Added new data models to store and analyze:

- `AppUsageData`: App usage statistics including foreground and background time
- `BatteryOptimizationData`: Battery level, charging status, temperature, and other metrics
- `NetworkUsageData`: Network usage statistics for mobile and WiFi data

## 4. Updated AppRepository

Extended the AppRepository to:

- Store and retrieve app usage, battery, and network data
- Provide methods to identify top battery consumers and network users
- Track usage patterns over time for AI-powered optimizations

## 6. MainActivity Updates

Modified the MainActivity to:

- Start the PowerGuardService when the app is launched
- Request necessary permissions
- Provide UI for user to see optimization recommendations

## 7. Installation and Build Tools

- Updated run_android.sh to detect rooted devices and install PowerGuard as a system app
- Created a detailed SYSTEM_APP_INSTALLATION.md guide for manual installation
- Updated README.md with information about system app requirements

## 8. System-Level Capabilities

With system app privileges, PowerGuard can now:

- Force stop battery-draining apps when battery is critically low
- Restrict background data for apps that consume excessive data
- Manage wakelocks to prevent unnecessary battery drain
- Control sync frequency for better data management
- Provide smart charging control notifications
- Apply app background restrictions based on usage patterns

## Next Steps

The following items could be implemented to further enhance PowerGuard's system capabilities:

1. **AI Pattern Analysis**: Implement the AI component that analyzes usage patterns and recommends
   optimizations
2. **More System Controls**: Add additional system-level controls like CPU frequency scaling
3. **Custom ROMs Integration**: Create packages for easy integration into custom ROMs
4. **Whitelist Management**: Implement a system for users to whitelist critical apps
5. **Dynamic Optimization Rules**: Allow users to define custom optimization rules
6. **System UI Integration**: Provide a Quick Settings tile for fast access to optimization features 