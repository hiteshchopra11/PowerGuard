# Next Steps for PowerGuard

This document outlines what we've learned about Android system permissions and how to proceed with
running PowerGuard effectively.

## Updated Permission Findings

After thorough testing with PowerGuard installed in `/system/priv-app`, we have discovered:

1. **Successfully Granted Permissions**:
   - `BATTERY_STATS`: Direct data access to battery statistics
   - `WRITE_SECURE_SETTINGS`: Modify secure system settings
   - `READ_LOGS`: Access system logs
   - `PACKAGE_USAGE_STATS`: Access app usage statistics (via AppOps)
   - `MANAGE_NETWORK_POLICY`: Control network policies ✅ (works in `/system/priv-app` after reboot)
   - `FORCE_STOP_PACKAGES`: Force stop applications ✅ (works in `/system/priv-app` after reboot)

2. **Manual Permissions**:
   - `WRITE_SETTINGS`: Must be enabled in system settings by the user

3. **Unattainable Permissions**:
   - `DEVICE_POWER`: Managed by role, not directly grantable

## API 35 Compatibility Issues

When upgrading to API 35, we encountered the following issues:

1. **NetworkPolicyManager Not Available**: This class is not available in the public SDK for API 35.
   - **Solution**: Implemented alternative approaches using ConnectivityManager, UsageStatsManager,
     and strategic app force-stopping.

2. **Background Data Restriction**: Direct per-app background data restriction requires
   NetworkPolicyManager.
   - **Solution**: Implemented a multi-faceted approach:
      - Monitor Data Saver status and guide users to enable it
      - Mark high data usage apps as "inactive"
      - Leverage battery optimization to reduce background activity
      - Force stop extreme data consumers

## Running the App Successfully

Follow these steps to run PowerGuard with optimal permission access:

1. **Install as Privileged System App**:
   ```bash
   ./run_android.sh
   ```
   This installs PowerGuard in `/system/priv-app` and reboots the device.

2. **Grant Available Permissions**:
   ```bash
   ./grant_permissions.sh
   ```
   This grants all available permissions including the privileged system permissions that are now
   accessible.

3. **Enable Manual Permissions**:
   - Navigate to Settings → Apps → Special app access → Modify system settings
   - Find PowerGuard and toggle the permission ON

4. **Launch the App**: Open PowerGuard from the app drawer after installation.

5. **Check Logs**: Monitor the app's behavior with:
   ```bash
   adb logcat | grep PowerGuardService
   ```

## Fully Implemented Features

With our adaptations for API 35, PowerGuard now offers:

1. **Complete Battery Optimization**:
   - Direct control over force-stopping battery-draining apps
   - Full access to battery statistics
   - Intelligent decision-making based on usage patterns

2. **Adapted Network Optimization**:
   - Monitoring of Data Saver status with user guidance
   - App inactivity management for high data consumers
   - Strategic force-stopping for extreme data usage
   - Battery optimization to reduce background activities

3. **System Monitoring**:
   - Full access to app usage statistics
   - Battery status monitoring
   - System log analysis

## Workarounds for Limitations

We've implemented the following workarounds for API and permission limitations:

### NetworkPolicyManager Alternative

**Challenge**: `NetworkPolicyManager` is not available in the public SDK (API 35), preventing direct
control of background data restrictions.

**Solution**: We've implemented several alternatives:

1. **Data Saver Integration**:
   - Use `ConnectivityManager.getRestrictBackgroundStatus()` to check if Data Saver is enabled
   - Use direct integer constants instead of the `RestrictBackgroundStatus` enum:
     ```kotlin
     // RESTRICT_BACKGROUND_STATUS_DISABLED = 1
     if (connectivityManager.getRestrictBackgroundStatus() == 1) {
         // Data Saver is disabled
     }
     ```
   - Provide user guidance to enable Data Saver manually

2. **App Inactivity Management**:
   - Use reflection to call `UsageStatsManager.setAppInactive()` to mark apps as inactive
   - This reduces background activity of apps indirectly affecting data usage

3. **Last Resort Force Stop**:
   - For extreme data consumers (>50MB in background), use `FORCE_STOP_PACKAGES` permission
   - Only for critical cases where other methods are insufficient

This multi-faceted approach provides effective network management even without direct API access to
NetworkPolicyManager.

2. **Battery Management Without DEVICE_POWER**:
   - **Challenge**: Cannot directly modify device power states
   - **Solution**: Using FORCE_STOP_PACKAGES to terminate high battery consuming apps

## Conclusion

PowerGuard has been adapted to work with API 35 by replacing unavailable APIs with alternative
approaches that maintain most of the core functionality. While some features now require a
combination of programmatic actions and user guidance, the app can still deliver effective battery
and network optimization.

## Implementation Priorities

1. ~~Complete NetworkPolicyManager Integration~~: ✅ Replaced with alternative approaches for API 35
2. **Force Stop Functionality**: ✅ Implemented
3. **User Notification System**: Next priority - especially important for guiding users on Data
   Saver
4. **Usage Statistics Analytics**: Coming soon
5. **UI for Manual Settings**: Higher priority now that some actions require user involvement 