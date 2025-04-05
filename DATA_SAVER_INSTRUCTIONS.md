# Data Saver Configuration for PowerGuard

This guide explains how to configure Data Saver on your Android device to work optimally with
PowerGuard's network optimization features.

## Why Configure Data Saver?

In Android API 35, the `NetworkPolicyManager` class is not available in the public SDK, which limits
PowerGuard's ability to directly restrict background data for specific applications. Instead,
PowerGuard leverages Android's built-in Data Saver feature to achieve similar network optimization
results.

## Benefits of Using Data Saver with PowerGuard

1. **System-level data restrictions**: When Data Saver is enabled, Android restricts background data
   usage for all apps except those you specifically allow.
2. **Enhanced battery life**: Reducing background data usage helps improve battery performance.
3. **More control over data usage**: You can choose which apps are allowed unrestricted data access.

## How to Configure Data Saver

### Step 1: Enable Data Saver

1. Open your device's **Settings** app
2. Navigate to **Network & internet** → **Data Saver**
   - Note: On some devices, this may be located in **Settings** → **Connections** → **Data usage
     ** → **Data Saver**
3. Toggle the switch to **ON**

### Step 2: Configure Unrestricted Data Access

When Data Saver is on, you'll need to allow unrestricted access to essential apps:

1. In the Data Saver screen, tap on **Unrestricted data**
2. You'll see a list of all installed apps
3. Toggle ON the switch for apps that should have unrestricted access:
   - Messaging apps you use regularly
   - Email clients that need background sync
   - Any critical apps that need real-time updates

### Step 3: Configure PowerGuard (Optional)

PowerGuard can help you identify which apps should be restricted:

1. Open the PowerGuard app
2. Navigate to the **Network Usage** section
3. Review the list of apps with high background data usage
4. Consider leaving these high-usage apps restricted in Data Saver

## How PowerGuard Works with Data Saver

PowerGuard interacts with Data Saver in the following ways:

1. **Detection**: PowerGuard checks the current Data Saver status using the
   `ConnectivityManager.getRestrictBackgroundStatus()` method to determine if Data Saver is enabled.

2. **System Integration**: If Data Saver is enabled, PowerGuard leverages the system-level
   restrictions already in place.

3. **Complementary Approach**: When Data Saver is disabled, PowerGuard logs this status and can
   notify users to enable it manually for optimal network management.

4. **Multi-layered Optimization**: Even when Data Saver is unavailable or disabled, PowerGuard
   employs additional strategies such as marking apps as inactive and, in extreme cases, using the
   force stop capability for apps with excessive data consumption.

## Troubleshooting

If you're experiencing issues with Data Saver and PowerGuard:

1. **Data Saver not restricting apps**: Some system apps may ignore Data Saver restrictions. This is
   normal Android behavior.
2. **Battery drain despite Data Saver**: Some apps may still use significant battery even when data
   is restricted. PowerGuard will help identify and manage these.
3. **Missing notifications**: If you're not receiving important notifications, add that app to the
   unrestricted data list.

## Additional Notes

- Data Saver settings are preserved across device reboots
- When connected to Wi-Fi, Data Saver restrictions are typically not applied
- PowerGuard logs Data Saver status changes and will notify you of any issues 