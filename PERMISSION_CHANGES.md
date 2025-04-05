# Permission Handling Improvements in PowerGuard

This document details the changes made to improve permission handling in PowerGuard, particularly
the migration from `/system/app` to `/system/priv-app`.

## Summary of Changes

PowerGuard has been modified to be installed as a privileged system app in `/system/priv-app`
instead of a standard system app in `/system/app`. This change improves permission access and
enhances the app's capabilities, but requires careful permission handling.

## Files Modified

1. **run_android.sh**: Updated to install the app in `/system/priv-app` instead of `/system/app`.
2. **grant_permissions.sh**: Completely revised to handle permissions according to best practices.
3. **SYSTEM_APP_INSTALLATION.md**: Updated to reflect the new installation location and procedures.
4. **PowerGuardService.kt**: Enhanced with improved permission checking and fallback behaviors.
5. **README.md**: Updated to include detailed information about permission handling.

## Permission Handling Approach

### Permission Groups

We've categorized permissions into four groups based on how they need to be handled:

1. **Standard Privileged Permissions**: These are granted automatically or can be granted via
   `pm grant` when installed in `/system/priv-app`:
    - `BATTERY_STATS`
    - `WRITE_SECURE_SETTINGS`
    - `READ_LOGS`

2. **Special Handling Permissions**: These require special grant methods:
    - `PACKAGE_USAGE_STATS`: Granted via AppOps with `appops set` command

3. **User Action Permissions**: These require manual enabling:
    - `WRITE_SETTINGS`: Must be toggled by the user in Settings

4. **Non-changeable Permissions**: These cannot be granted via normal means:
    - `MANAGE_NETWORK_POLICY`: Reserved for platform components, not grantable via `pm grant`
    - `FORCE_STOP_PACKAGES`: Reserved for platform components, not grantable via `pm grant`
    - `DEVICE_POWER`: Managed by role, not grantable via `pm grant`

### Permission Checking in PowerGuardService

The service now includes:

1. **Runtime Permission Checks**: The app detects which permissions are actually available at
   runtime.
2. **Adaptive Behavior**: Features are enabled/disabled based on available permissions.
3. **Fallback Mechanisms**: When certain permissions aren't available, the app provides
   recommendations instead of attempting to perform actions directly.
4. **Log Messages**: Clear logging for permission availability and denied operations.

### Key Improvements in Permission Script

The `grant_permissions.sh` script has been significantly enhanced:

1. **Structured Approach**: Permissions are now granted in a structured, logical order.
2. **AppOps Integration**: Uses AppOps for `PACKAGE_USAGE_STATS` instead of `pm grant`.
3. **Manual Instructions**: Clearly explains which permissions require manual user action.
4. **Robust Error Handling**: Better feedback on permission granting results.
5. **Documentation**: Inline documentation explaining the purpose of each permission.
6. **Skipping Non-grantable Permissions**: Avoids attempting to grant permissions like
   `MANAGE_NETWORK_POLICY`, `FORCE_STOP_PACKAGES`, and `DEVICE_POWER` that cannot be granted.

## Handling Special Cases

### PACKAGE_USAGE_STATS

This permission is now granted through AppOps rather than the package manager:

```bash
adb shell appops set com.hackathon.powergaurd android:get_usage_stats allow
```

The service checks for this permission using AppOpsManager rather than PackageManager.

### WRITE_SETTINGS

This permission requires manual user action in most Android versions. The script provides clear
instructions for users to enable this in device settings.

### MANAGE_NETWORK_POLICY, FORCE_STOP_PACKAGES, and DEVICE_POWER

We discovered that these permissions cannot be granted using `pm grant`, even for apps installed in
`/system/priv-app`. The error messages indicate:

- `MANAGE_NETWORK_POLICY` and `FORCE_STOP_PACKAGES` are "not changeable permission types" (reserved
  for platform components)
- `DEVICE_POWER` is "managed by role" (requires a specific system role)

The PowerGuardService has been updated to:

1. Skip attempting to grant these permissions in the script
2. Check if the app might still have them by virtue of being a privileged system app
3. Provide fallback behaviors for battery and network optimization when these permissions are not
   available

## Testing and Verification

After installation, the PowerGuardService performs comprehensive permission checks and logs the
results. These logs can be viewed with:

```bash
adb logcat | grep PowerGuardService
```

The permission status is displayed in the log with a clear listing of which permissions are
available and which are not.

## Additional Considerations

- **Device Variations**: Different Android versions and OEM customizations may handle permissions
  differently.
- **Graceful Degradation**: The app now functions at varying levels of capability depending on
  available permissions.
- **User Transparency**: The app clarifies which optimizations are automatic vs. those requiring
  user action.
- **Platform Limitations**: Several important permissions are permanently restricted to platform
  components or specific roles.

## Future Improvements

1. **UI Feedback**: Add a permissions status screen in the app to show which optimizations are
   available.
2. **Custom ROM Integration**: Provide specific documentation for ROM developers.
3. **Permission Monitoring**: Add runtime monitoring for permission changes.
4. **Platform Integration**: Explore deeper integration with platform components for non-changeable
   permissions. 