# PowerGuard System App Installation Changes

## Overview

This document outlines the changes made to adapt PowerGuard for installation in `/system/app`
instead of `/system/priv-app`. While both locations allow the app to run with system privileges,
apps in `/system/priv-app` automatically get granted more protected permissions than those in
`/system/app`.

## Changes Made

### 1. Installation Scripts

- Modified `run_android.sh` to install the APK to `/system/app/PowerGuard/` instead of
  `/system/priv-app/PowerGuard/`
- Updated `SYSTEM_APP_INSTALLATION.md` with instructions for manual installation to `/system/app/`
- Created a new `grant_permissions.sh` script to manually grant protected permissions via ADB
  commands

### 2. PowerGuardService Enhancements

- Added permission detection logic to check which system permissions are available at runtime
- Implemented graceful fallbacks when certain permissions are not available
- Added constants for BatteryManager properties to avoid unresolved references
- Modified optimization functions to check for permission availability before attempting privileged
  operations
- Added logging for permission status to help with debugging

### 3. Documentation Updates

- Updated `README.md` to explain the difference between `/system/app` and `/system/priv-app`
- Added information about running `grant_permissions.sh` for optimal functionality

## Key Differences Between /system/app and /system/priv-app

| Feature               | /system/app                  | /system/priv-app |
|-----------------------|------------------------------|------------------|
| System UID            | Yes                          | Yes              |
| Signature permissions | Partial                      | Automatic        |
| Protected permissions | Manual grant required        | Automatic        |
| Survives updates      | Yes                          | Yes              |
| Force stop other apps | Requires explicit permission | Automatic        |
| Access battery stats  | Requires explicit permission | Automatic        |
| Manage network policy | Requires explicit permission | Automatic        |

## Permission Handling

The PowerGuardService now:

1. Checks for available permissions at startup
2. Adapts its behavior based on available permissions
3. Uses reflection to access certain system APIs
4. Provides graceful fallbacks when permissions are not available

## How to Ensure Maximum Functionality

After installing PowerGuard to `/system/app`, run the included permission granting script:

```bash
chmod +x grant_permissions.sh
./grant_permissions.sh
```

This script attempts to grant the following permissions:

- `android.permission.BATTERY_STATS`
- `android.permission.PACKAGE_USAGE_STATS`
- `android.permission.DEVICE_POWER`
- `android.permission.WRITE_SECURE_SETTINGS`
- `android.permission.WRITE_SETTINGS`
- `android.permission.MANAGE_NETWORK_POLICY`
- `android.permission.FORCE_STOP_PACKAGES`
- `android.permission.READ_LOGS`

## Known Limitations

Some features may still be limited when installed in `/system/app` due to Android's security model:

1. Force stopping other apps may require user confirmation
2. Background data restrictions might require user interaction
3. Some deep battery optimizations may not be possible

If you need full system-level control, consider installing in `/system/priv-app` instead. 