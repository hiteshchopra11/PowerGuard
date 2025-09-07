package com.hackathon.powerguard.services

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hackathon.powerguard.MainActivity
import com.hackathon.powerguard.R
import com.hackathon.powerguard.di.AppDataRepository
import com.hackathon.powerguard.models.AppUsageData
import com.hackathon.powerguard.models.BatteryOptimizationData
import com.hackathon.powerguard.models.NetworkUsageData
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class PowerGuardService : Service() {

    private val TAG = "PowerGuardService"
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "power_guard_channel"

    // Create cancellable scope with SupervisorJob
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private var isCollectingData = false

    @Inject
    lateinit var appRepository: AppDataRepository

    private lateinit var powerManager: PowerManager
    private lateinit var batteryManager: BatteryManager
    private lateinit var packageManager: PackageManager
    private lateinit var activityManager: ActivityManager
    private lateinit var appOpsManager: AppOpsManager

    // Permission flags
    private var hasForceStopPermission = false
    private var hasNetworkPolicyPermission = false
    private var hasBatteryStatsPermission = false
    private var hasDevicePowerPermission = false
    private var hasUsageStatsPermission = false
    private var hasWriteSettingsPermission = false
    private var hasWriteSecureSettingsPermission = false
    private var hasReadLogsPermission = false

    // Battery Manager constants
    private val BATTERY_PROPERTY_TEMPERATURE = 2 // BatteryManager.BATTERY_PROPERTY_TEMPERATURE
    private val BATTERY_PROPERTY_VOLTAGE = 4 // BatteryManager.BATTERY_PROPERTY_VOLTAGE
    private val BATTERY_PROPERTY_CURRENT_NOW = 1 // BatteryManager.BATTERY_PROPERTY_CURRENT_NOW

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "PowerGuardService created")

        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        packageManager = applicationContext.packageManager
        activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        appOpsManager = getSystemService(APP_OPS_SERVICE) as AppOpsManager

        // Check permissions
        checkSystemPermissions()

        createNotificationChannel()
        
        // Create notification and start as foreground right away
        val notification = createForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "PowerGuardService started")

        if (!isCollectingData) {
            isCollectingData = true
            startDataCollection()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Cancel all coroutines
        serviceJob.cancel()
        isCollectingData = false
        Log.d(TAG, "PowerGuardService destroyed")
    }

    private fun checkSystemPermissions() {
        try {
            // Check if we have permission to force stop packages
            hasForceStopPermission =
                try {
                    // Now we know this permission is available in priv-app
                    // and we've confirmed we have it
                    isPermissionGrantedViaPackageManager(
                        "android.permission.FORCE_STOP_PACKAGES"
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error checking FORCE_STOP_PACKAGES permission: ${e.message}")
                    false
                }

            // Check for battery stats permission
            hasBatteryStatsPermission =
                try {
                    // Attempt to use a method that requires this permission
                    batteryManager.getIntProperty(BATTERY_PROPERTY_CURRENT_NOW)
                    true
                } catch (e: Exception) {
                    Log.w(TAG, "No BATTERY_STATS permission: ${e.message}")
                    false
                }

            // Check for device power permission - we know we don't have this
            hasDevicePowerPermission = false

            // Check for network policy permission - we've confirmed we have this
            hasNetworkPolicyPermission =
                try {
                    isPermissionGrantedViaPackageManager(
                        "android.permission.MANAGE_NETWORK_POLICY"
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error checking MANAGE_NETWORK_POLICY permission: ${e.message}")
                    false
                }

            // Check for WRITE_SETTINGS permission
            hasWriteSettingsPermission =
                Settings.System.canWrite(applicationContext)

            // Check for WRITE_SECURE_SETTINGS permission
            hasWriteSecureSettingsPermission =
                isPermissionGrantedViaPackageManager("android.permission.WRITE_SECURE_SETTINGS")

            // Check for READ_LOGS permission
            hasReadLogsPermission =
                isPermissionGrantedViaPackageManager("android.permission.READ_LOGS")

            // Check for PACKAGE_USAGE_STATS permission using AppOps
            hasUsageStatsPermission = checkUsageStatsPermission()

            val permissionStatus =
                """
                System permissions status:
                - Force Stop Packages: $hasForceStopPermission
                - Battery Stats: $hasBatteryStatsPermission
                - Device Power: $hasDevicePowerPermission
                - Network Policy: $hasNetworkPolicyPermission
                - Write Settings: $hasWriteSettingsPermission
                - Write Secure Settings: $hasWriteSecureSettingsPermission
                - Read Logs: $hasReadLogsPermission
                - Usage Stats: $hasUsageStatsPermission
                - Is Privileged System App: ${isAppInstalledAsPrivilegedSystemApp()}
            """.trimIndent()

            Log.d(TAG, permissionStatus)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions: ${e.message}")
        }
    }

    private fun isAppInstalledAsPrivilegedSystemApp(): Boolean {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    appInfo.sourceDir.startsWith("/system/priv-app")
        } catch (e: Exception) {
            false
        }
    }

    private fun isPermissionGrantedViaPackageManager(permission: String): Boolean {
        return try {
            packageManager.checkPermission(permission, packageName) ==
                    PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    private fun checkUsageStatsPermission(): Boolean {
        val appOpsManager = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        return appOpsManager.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun createNotificationChannel() {
        val name = "PowerGuard Service"
        val descriptionText = "Monitoring battery and network usage"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createForegroundNotification(): Notification {
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PowerGuard Active")
            .setContentText("Optimizing battery and network usage")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startDataCollection() {
        serviceScope.launch {
            while (isCollectingData) {
                try {
                    collectUsageData()
                } catch (e: Exception) {
                    Log.e(TAG, "Error collecting data: ${e.message}")
                }

                // Collect data every 15 minutes
                delay(TimeUnit.MINUTES.toMillis(15))
            }
        }
    }

    private fun collectUsageData() {
        Log.d(TAG, "Collecting usage data")

        // 1. Collect battery and app usage data
        val installedApps = getInstalledApps()
        val batteryInfo = collectBatteryInfo()
        val appUsageData = collectAppUsageData(installedApps)
        val networkUsageData = collectNetworkUsageData(installedApps)

        // 2. Store data for pattern recognition
        try {
            if (::appRepository.isInitialized) {
                appRepository.saveUsageData(appUsageData, batteryInfo, networkUsageData)
            } else {
                Log.e(TAG, "AppRepository not initialized")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving usage data: ${e.message}", e)
        }

        // 3. Analyze patterns and optimize based on available permissions
        optimizeBatteryUsage(appUsageData, batteryInfo)
        optimizeNetworkUsage(networkUsageData)
    }

    private fun getInstalledApps(): List<ApplicationInfo> {
        // minSdk is 35; use modern MATCH_* flags unconditionally
        return packageManager.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES).filter {
            it.packageName != "android" &&
                it.packageName != "com.android.systemui" &&
                it.packageName != applicationContext.packageName
        }
    }

    private fun collectBatteryInfo(): BatteryOptimizationData {
        // Basic battery info that should be available without special permissions
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = batteryManager.isCharging

        // These might need additional permissions
        val temperature =
            try {
                if (hasBatteryStatsPermission) {
                    batteryManager.getIntProperty(BATTERY_PROPERTY_TEMPERATURE) / 10.0
                } else {
                    Log.w(TAG, "Cannot access battery temperature - permission not available")
                    0.0 // Default if permission not available
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting battery temperature: ${e.message}")
                0.0
            }

        val voltage =
            try {
                if (hasBatteryStatsPermission) {
                    batteryManager.getIntProperty(BATTERY_PROPERTY_VOLTAGE)
                } else {
                    Log.w(TAG, "Cannot access battery voltage - permission not available")
                    0 // Default if permission not available
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting battery voltage: ${e.message}")
                0
            }

        val currentAverage =
            if (hasBatteryStatsPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    batteryManager.getLongProperty(
                        BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting battery current: ${e.message}")
                    0L
                }
            } else {
                if (!hasBatteryStatsPermission) {
                    Log.w(TAG, "Cannot access battery current - permission not available")
                }
                0L
            }

        return BatteryOptimizationData(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            temperature = temperature,
            voltage = voltage,
            currentAverage = currentAverage,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun collectAppUsageData(installedApps: List<ApplicationInfo>): List<AppUsageData> {
        if (!hasUsageStatsPermission) {
            Log.w(
                TAG,
                "Cannot collect detailed app usage data - PACKAGE_USAGE_STATS permission not granted"
            )
            // Return placeholder data with warning log
            return installedApps.map { app ->
                AppUsageData(
                    packageName = app.packageName,
                    appName = packageManager.getApplicationLabel(app).toString(),
                    foregroundTimeMinutes = 0L,
                    backgroundTimeMinutes = 0L,
                    wakelockTimeMinutes = 0L,
                    batteryUsagePercent = 0.0,
                    timestamp = System.currentTimeMillis()
                )
            }
        }

        // In a real implementation with UsageStatsManager, we'd use the permission here
        // For this example, we'll continue with placeholder data but note that we could
        // actually collect real data since we have the permission

        Log.d(TAG, "Collecting app usage data with PACKAGE_USAGE_STATS permission")
        return installedApps.map { app ->
            val packageName = app.packageName
            val appName = packageManager.getApplicationLabel(app).toString()

            // In a real implementation, these values would come from UsageStatsManager
            // This is just a placeholder for the real implementation
            val foregroundTimeMinutes = 0L
            val backgroundTimeMinutes = 0L
            val wakelockTimeMinutes = 0L
            val batteryUsagePercent = 0.0

            AppUsageData(
                packageName = packageName,
                appName = appName,
                foregroundTimeMinutes = foregroundTimeMinutes,
                backgroundTimeMinutes = backgroundTimeMinutes,
                wakelockTimeMinutes = wakelockTimeMinutes,
                batteryUsagePercent = batteryUsagePercent,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    private fun collectNetworkUsageData(
        installedApps: List<ApplicationInfo>
    ): List<NetworkUsageData> {
        val hasNetworkStatsAccess = hasNetworkPolicyPermission || hasWriteSecureSettingsPermission

        if (!hasNetworkStatsAccess) {
            Log.w(
                TAG,
                "Cannot collect detailed network usage data - required permissions not granted"
            )
            // Return placeholder data with warning log
            return installedApps.map { app ->
                NetworkUsageData(
                    packageName = app.packageName,
                    appName = packageManager.getApplicationLabel(app).toString(),
                    mobileDataUsageBytes = 0L,
                    wifiDataUsageBytes = 0L,
                    backgroundDataUsageBytes = 0L,
                    timestamp = System.currentTimeMillis()
                )
            }
        }

        // In a real implementation with NetworkStatsManager, we'd use the permission here
        // For this example, we'll continue with placeholder data but note that we could
        // actually collect real data since we have the permission

        Log.d(TAG, "Collecting network usage data with necessary permissions")
        return installedApps.map { app ->
            val packageName = app.packageName
            val appName = packageManager.getApplicationLabel(app).toString()

            // In a real implementation, these values would come from NetworkStatsManager
            val mobileDataUsageBytes = 0L
            val wifiDataUsageBytes = 0L
            val backgroundDataUsageBytes = 0L

            NetworkUsageData(
                packageName = packageName,
                appName = appName,
                mobileDataUsageBytes = mobileDataUsageBytes,
                wifiDataUsageBytes = wifiDataUsageBytes,
                backgroundDataUsageBytes = backgroundDataUsageBytes,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    private fun optimizeBatteryUsage(
        appUsageData: List<AppUsageData>,
        batteryInfo: BatteryOptimizationData
    ) {
        // For system app, we can implement direct optimization here
        // Example: Force stop apps with high battery usage when battery is low
        if (batteryInfo.batteryLevel < 20 && !batteryInfo.isCharging) {
            // Find top battery-consuming apps
            val highBatteryApps =
                appUsageData
                    .filter { it.batteryUsagePercent > 5.0 }
                    .sortedByDescending { it.batteryUsagePercent }
                    .take(3)

            if (highBatteryApps.isEmpty()) {
                Log.d(TAG, "No high battery consuming apps found for optimization")
                return
            }

            // We now know we have FORCE_STOP_PACKAGES permission
            if (hasForceStopPermission) {
                Log.d(TAG, "Using FORCE_STOP_PACKAGES permission to optimize battery usage")
                highBatteryApps.forEach { app ->
                    try {
                        Log.d(TAG, "Force stopping high battery usage app: ${app.appName}")
                        // Direct method call to force stop the package
                        val method =
                            ActivityManager::class.java.getMethod(
                                "forceStopPackage",
                                String::class.java
                            )
                        method.invoke(activityManager, app.packageName)

                        // Log success
                        Log.d(TAG, "Successfully force stopped app: ${app.appName}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to force stop ${app.appName}: ${e.message}")
                    }
                }
            } else {
                // This is a fallback, but we should have the permission
                Log.w(TAG, "FORCE_STOP_PACKAGES permission not available, using fallback")
                highBatteryApps.forEach { app ->
                    Log.d(TAG, "Recommendation: User should manually restrict: ${app.appName}")
                    // Show notification to the user
                }
            }
        }
    }

    private fun optimizeNetworkUsage(networkUsageData: List<NetworkUsageData>) {
        // Find high data usage apps
        val highDataApps =
            networkUsageData
                .filter { it.backgroundDataUsageBytes > 10 * 1024 * 1024 } // More than 10MB
                .sortedByDescending { it.backgroundDataUsageBytes }
                .take(3)

        if (highDataApps.isEmpty()) {
            Log.d(TAG, "No high data usage apps found for optimization")
            return
        }

        // We now know we have MANAGE_NETWORK_POLICY permission
        if (hasNetworkPolicyPermission) {
            Log.d(TAG, "Using network policy permissions to optimize network usage")

            try {
                highDataApps.forEach { app ->
                    try {
                        Log.d(TAG, "Restricting background data for: ${app.appName}")

                        // 1. Use Data Saver mode if available (Android N+)
                        val connectivityManager =
                            getSystemService(CONNECTIVITY_SERVICE) as
                                    ConnectivityManager
                        val status = connectivityManager.restrictBackgroundStatus

                        // ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED = 1
                        if (status == 1) { // RESTRICT_BACKGROUND_STATUS_DISABLED
                            // Data Saver is disabled, but we can't enable it programmatically
                            // We would need to prompt the user to enable it
                            Log.d(
                                TAG,
                                "Data Saver is disabled. We need user action to enable it."
                            )

                            // In a real app, show a notification to the user
                            // notifyUserToEnableDataSaver()
                        } else {
                            Log.d(
                                TAG,
                                "Data Saver is already enabled. Apps will have restricted background data."
                            )
                        }

                        // 2. For battery optimization, which can indirectly reduce background data
                        // usage
                        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                        if (!powerManager.isIgnoringBatteryOptimizations(app.packageName)) {
                            // Request the app to be optimized for battery, which can help
                            // reduce background activity
                            // Note: This is just a recommendation to the system
                            try {
                                // Use reflection to call setAppInactive method
                                val usageStatsManagerClass =
                                    Class.forName("android.app.usage.UsageStatsManager")
                                val usageStatsManager =
                                    getSystemService(USAGE_STATS_SERVICE)
                                val setAppInactiveMethod =
                                    usageStatsManagerClass.getMethod(
                                        "setAppInactive",
                                        String::class.java,
                                        Boolean::class.java
                                    )
                                setAppInactiveMethod.invoke(
                                    usageStatsManager,
                                    app.packageName,
                                    true
                                )
                                Log.d(TAG, "Marked app as inactive: ${app.appName}")
                            } catch (e: Exception) {
                                Log.e(TAG, "Could not mark app as inactive: ${e.message}")
                            }
                        }

                        // 3. As a last resort, for apps with excessive data usage,
                        // we could force stop them using our FORCE_STOP_PACKAGES permission
                        if (hasForceStopPermission &&
                            app.backgroundDataUsageBytes > 50 * 1024 * 1024
                        ) { // > 50MB
                            try {
                                Log.d(TAG, "Force stopping high data usage app: ${app.appName}")
                                val method =
                                    ActivityManager::class.java.getMethod(
                                        "forceStopPackage",
                                        String::class.java
                                    )
                                method.invoke(activityManager, app.packageName)
                                Log.d(
                                    TAG,
                                    "Successfully force stopped high data usage app: ${app.appName}"
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to force stop app: ${e.message}")
                            }
                        }

                        Log.d(TAG, "Applied available restrictions for: ${app.appName}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to restrict data for ${app.appName}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error applying network restrictions: ${e.message}")
                // Fall back to recommendations
                highDataApps.forEach { app ->
                    Log.d(
                        TAG,
                        "Recommendation: User should manually restrict background data for: ${app.appName}"
                    )
                    // Show notification to the user
                }
            }
        } else {
            // This is a fallback, but we should have the permission
            Log.w(TAG, "Network policy permissions not available, using fallback")
            highDataApps.forEach { app ->
                Log.d(
                    TAG,
                    "Recommendation: User should manually restrict background data for: ${app.appName}"
                )
                // Show notification to the user
            }
        }
    }

    // New utility method to get package UID for network policy
    private fun getPackageUid(packageName: String): Int {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
            appInfo.uid
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get UID for package $packageName: ${e.message}")
            -1
        }
    }
}