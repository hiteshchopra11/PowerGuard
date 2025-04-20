# PowerGuard Actionables

PowerGuard leverages a set of high-impact actionables to optimize both battery and data usage on Android devices. These actionables are implementable with system privileges and target specific applications.

## Core Actionables

PowerGuard implements 5 primary actionables, each focusing on different aspects of system optimization:

### 1. Set Standby Bucket (`set_standby_bucket`)

Places apps in Android's app standby buckets to control background activity levels.

- **Implementation**: Uses `UsageStatsManager.setAppStandbyBucket()` API
- **Buckets**: ACTIVE, WORKING_SET, FREQUENT, RARE, RESTRICTED
- **Requires**: Android P (API 28) or higher for full functionality
- **Impact**: High impact on battery with low user experience disruption

### 2. Restrict Background Data (`restrict_background_data`)

Prevents specific apps from using data in the background.

- **Implementation**: Uses `NetworkPolicyManager.setUidPolicy()` API
- **Requirements**: System privileges to access NetworkPolicyManager
- **Fallback**: Guides users to data settings when direct API access isn't possible
- **Impact**: High impact on data usage with minimal UX effects

### 3. Kill App (`kill_app`)

Force stops applications that are consuming excessive resources.

- **Implementation**: Uses `ActivityManager.forceStopPackage()` API
- **Requirements**: System privileges to force stop other applications
- **Impact**: Immediate but temporary relief; apps will restart when launched

### 4. Manage Wake Locks (`manage_wake_locks`)

Controls apps keeping the device awake via wake locks.

- **Implementation**: Uses `AppOpsManager` to deny wake lock operations
- **Requirements**: Android M (API 23) or higher
- **Impact**: Targets one of the most common battery drain sources

## Architecture

PowerGuard's actionable system follows a modular architecture:

1. **ActionableTypes**: Defines available actionable types as constants
2. **ActionableHandler**: Interface implemented by all handlers
3. **ActionableExecutor**: Central service for processing and executing actionables
4. **ActionableUtils**: Utility class with common operations for all handlers
5. **Individual Handlers**: Implementations for each actionable type

## Usage

Each actionable is implemented with thorough error handling and fallback mechanisms, requiring minimal custom code to invoke:

```kotlin
// Example of using the actionable system
val actionableExecutor: ActionableExecutor = // injected via Dagger

// Create an actionable
val action = Actionable(
    id = "unique_id",
    type = ActionableTypes.KILL_APP,
    description = "Force stop Facebook to save battery",
    packageName = "com.facebook.katana"
)

// Execute the actionable
lifecycleScope.launch {
    val result = actionableExecutor.executeActionable(listOf(action))
    // Handle result
}
```

## Requirements

Most actionables require elevated system privileges to function properly:

- The app should be installed as a privileged system app
- Requires PACKAGE_USAGE_STATS permission at minimum
- Some features need additional permissions like WRITE_SECURE_SETTINGS

## Compatibility

PowerGuard actionables are designed to work across all modern Android versions (API 24+) with progressive enhancement for newer API levels.