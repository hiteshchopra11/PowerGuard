# PowerGuard Permission Status

This document outlines the current status of system permissions for PowerGuard when installed as a privileged system app in `/system/priv-app`.

## Permission Status

| Permission | Status | Notes |
|------------|--------|-------|
| `BATTERY_STATS` | ✅ Available | Granted via `pm grant` |
| `WRITE_SECURE_SETTINGS` | ✅ Available | Granted via `pm grant` |
| `READ_LOGS` | ✅ Available | Granted via `pm grant` |
| `PACKAGE_USAGE_STATS` | ✅ Available | Granted via AppOps |
| `MANAGE_NETWORK_POLICY` | ✅ Available | Available when installed in `/system/priv-app` |
| `FORCE_STOP_PACKAGES` | ✅ Available | Available when installed in `/system/priv-app` |
| `WRITE_SETTINGS` | ⚠️ Manual | Requires user action in Settings |
| `DEVICE_POWER` | ❌ Unavailable | Managed by role, not grantable |

## Implementation Details

### Battery Optimization

**Primary Permissions Used**: `BATTERY_STATS`, `FORCE_STOP_PACKAGES`

**Implementation**:
- PowerGuard monitors battery status and app usage
- When battery level drops below 20% and device is not charging:
  - High battery consuming apps are identified
  - Using `FORCE_STOP_PACKAGES` permission, background apps are force stopped
  - A notification is shown to inform the user of the action taken

**Feature Status**: ✅ Fully implemented with direct system control

### Network Optimization

**Primary Permissions Used**: `MANAGE_NETWORK_POLICY`, `FORCE_STOP_PACKAGES`, `PACKAGE_USAGE_STATS`

**Implementation**:
- PowerGuard monitors network usage by apps
- High data consuming apps are identified
- Since `NetworkPolicyManager` is not available in the public SDK (API 35), we use a multi-faceted approach:
  1. **Data Saver Integration**: Monitor Data Saver status and prompt the user to enable it if necessary
  2. **App Inactivity Management**: Mark high data consuming apps as "inactive" via UsageStatsManager
  3. **Battery Optimization**: Leverage battery optimization features to reduce background activities
  4. **Last Resort Force Stop**: For extremely high data usage apps, use `FORCE_STOP_PACKAGES` to terminate them
- A notification is shown to inform the user of any actions taken

**Feature Status**: ✅ Implemented with alternative approaches

### System Monitoring

**Primary Permissions Used**: `BATTERY_STATS`, `PACKAGE_USAGE_STATS`, `READ_LOGS`

**Implementation**:
- PowerGuard uses `BATTERY_STATS` to monitor detailed battery usage
- `PACKAGE_USAGE_STATS` provides app usage patterns
- `READ_LOGS` allows analyzing system logs for issues

**Feature Status**: ✅ Fully implemented with direct system access

### System Settings Modification

**Primary Permissions Used**: `WRITE_SECURE_SETTINGS`, `WRITE_SETTINGS`

**Implementation**:
- PowerGuard uses `WRITE_SECURE_SETTINGS` for protected system settings
- For general settings requiring `WRITE_SETTINGS`, user must enable manually

**Feature Status**: ⚠️ Partially implemented (some settings require user action)

## Device Power Management

**Workaround for Missing `DEVICE_POWER`**:

Since we do not have `DEVICE_POWER` permission, PowerGuard implements a workaround:
- Instead of directly manipulating device power states
- We use `FORCE_STOP_PACKAGES` to stop battery-draining background apps
- This achieves similar battery savings without the need for direct power management
- For critical power management, we display notifications guiding users to take manual action

**Feature Status**: ⚠️ Implemented with workarounds

## API Level Adaptations (API 35)

For compatibility with Android API 35, we've made these adaptations:
- Removed dependency on `NetworkPolicyManager` which is not part of the public SDK
- Implemented alternative approaches for background data restriction
- Used reflection for specific system actions while providing graceful fallbacks
- Added user notification prompts for actions that require user intervention

## Conclusion

PowerGuard successfully implements its core functionality for battery and network optimization through the permissions available to it as a privileged system app. When API limitations are encountered, the app degrades gracefully by providing alternative approaches or user guidance.

## Next Steps

1. Add notification system for user visibility into automated actions
2. Create user-friendly UI for manually optimizing areas requiring user action
3. Implement advanced monitoring with the available permissions
4. Add Data Saver configuration guidance for users 