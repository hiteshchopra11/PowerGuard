package com.hackathon.powergaurd.collector

import android.app.usage.UsageStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.hackathon.powergaurd.models.AppNetworkInfo
import com.hackathon.powergaurd.models.AppUsageInfo
import com.hackathon.powergaurd.models.BatteryStats
import com.hackathon.powergaurd.models.DeviceData
import com.hackathon.powergaurd.models.NetworkUsage
import com.hackathon.powergaurd.models.WakeLockInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Collector for gathering device usage data to be sent to the backend. */
@Singleton
class UsageDataCollector @Inject constructor(@ApplicationContext private val context: Context) {
    private val TAG = "UsageDataCollector"

    // System service managers
    private val batteryManager by lazy {
        context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    }

    private val usageStatsManager by lazy {
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    }

    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    private val packageManager by lazy { context.packageManager }

    /**
     * Collects complete device data for analysis.
     * @return DeviceData object containing all relevant device statistics
     */
    suspend fun collectDeviceData(): DeviceData {
        Log.d(TAG, "Collecting device data for analysis")

        val deviceId = getDeviceId()
        val timestamp = System.currentTimeMillis()

        val appUsage = collectAppUsageData()
        val batteryStats = collectBatteryStats()
        val networkUsage = collectNetworkUsageData()
        val wakeLocks = collectWakeLockData()

        return DeviceData(
            appUsage = appUsage,
            batteryStats = batteryStats,
            networkUsage = networkUsage,
            wakeLocks = wakeLocks,
            deviceId = deviceId,
            timestamp = timestamp
        )
    }

    /** Collects app usage statistics. */
    private fun collectAppUsageData(): List<AppUsageInfo> {
        val appUsageList = mutableListOf<AppUsageInfo>()

        try {
            val statsManager = usageStatsManager
            if (statsManager == null) {
                Log.e(TAG, "UsageStatsManager is not available")
                return appUsageList
            }

            // Get usage stats for the last 24 hours
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 24 * 60 * 60 * 1000 // 24 hours in milliseconds

            val usageStatsList =
                statsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    endTime
                )

            if (usageStatsList.isNullOrEmpty()) {
                Log.w(TAG, "No usage stats available")
                return appUsageList
            }

            for (usageStats in usageStatsList) {
                try {
                    val packageName = usageStats.packageName

                    // Skip system packages and our own app
                    if (isSystemPackage(packageName) || packageName == context.packageName) {
                        continue
                    }

                    val appName =
                        try {
                            val appInfo = packageManager.getApplicationInfo(packageName, 0)
                            packageManager.getApplicationLabel(appInfo).toString()
                        } catch (e: Exception) {
                            packageName // Fallback to package name if app label can't be
                            // retrieved
                        }

                    val foregroundTimeMs = usageStats.totalTimeInForeground
                    val lastTimeUsed = usageStats.lastTimeUsed

                    // Background time is estimated as total time minus foreground time
                    // This is a rough approximation
                    val backgroundTimeMs =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            (usageStats.totalTimeVisible - usageStats.totalTimeInForeground)
                                .coerceAtLeast(0)
                        } else {
                            0 // Not available for older Android versions
                        }

                    val launchCount =
                        usageStats.totalTimeInForeground / (60 * 1000) // Estimate based on time

                    appUsageList.add(
                        AppUsageInfo(
                            packageName = packageName,
                            appName = appName,
                            foregroundTimeMs = foregroundTimeMs,
                            backgroundTimeMs = backgroundTimeMs,
                            lastUsed = lastTimeUsed,
                            launchCount = launchCount.toInt().coerceAtLeast(1)
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing usage stats for package", e)
                }
            }

            // Sort by foreground time (most used first)
            return appUsageList.sortedByDescending { it.foregroundTimeMs }
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting app usage data", e)
            return appUsageList
        }
    }

    /** Collects battery statistics. */
    private fun collectBatteryStats(): BatteryStats {
        try {
            val battery = batteryManager
            if (battery == null) {
                Log.e(TAG, "BatteryManager is not available")
                return BatteryStats(
                    level = 0,
                    temperature = 0f,
                    isCharging = false,
                    chargingType = "unknown",
                    voltage = 0,
                    health = "unknown",
                    estimatedRemainingTime = null
                )
            }

            val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

            // Handle potential errors with specific battery properties
            val temperatureRaw =
                try {
                    // BATTERY_PROPERTY_TEMPERATURE is not available in all API levels
                    // Using a different approach for temperature
                    val intent =
                        context.registerReceiver(
                            null,
                            android.content.IntentFilter(
                                android.content.Intent.ACTION_BATTERY_CHANGED
                            )
                        )
                    intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting battery temperature", e)
                    0
                }
            val temperature = temperatureRaw / 10f // Convert to celsius

            val voltage =
                try {
                    // BATTERY_PROPERTY_VOLTAGE is not available in all API levels
                    // Using a different approach for voltage
                    val intent =
                        context.registerReceiver(
                            null,
                            android.content.IntentFilter(
                                android.content.Intent.ACTION_BATTERY_CHANGED
                            )
                        )
                    intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting battery voltage", e)
                    0
                }

            val isCharging = battery.isCharging

            val status =
                try {
                    battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting battery status", e)
                    BatteryManager.BATTERY_STATUS_UNKNOWN
                }

            val chargingType =
                when {
                    !isCharging -> "not_charging"
                    status == BatteryManager.BATTERY_STATUS_FULL -> "full"
                    else -> "charging"
                }

            val healthValue =
                try {
                    // BATTERY_HEALTH_PROPERTY is not available
                    // Using a different approach for health
                    val intent =
                        context.registerReceiver(
                            null,
                            android.content.IntentFilter(
                                android.content.Intent.ACTION_BATTERY_CHANGED
                            )
                        )
                    intent?.getIntExtra(
                        BatteryManager.EXTRA_HEALTH,
                        BatteryManager.BATTERY_HEALTH_UNKNOWN
                    )
                        ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting battery health", e)
                    BatteryManager.BATTERY_HEALTH_UNKNOWN
                }

            val health =
                when (healthValue) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "good"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
                    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "unspecified_failure"
                    BatteryManager.BATTERY_HEALTH_COLD -> "cold"
                    else -> "unknown"
                }

            // Calculate estimated remaining time (rough estimate)
            val estimatedRemainingTime =
                if (!isCharging && level > 0) {
                    // Rough estimate: assuming 24 hours for 100% battery
                    (level / 100.0 * 24 * 60 * 60 * 1000).toLong()
                } else {
                    null
                }

            return BatteryStats(
                level = level,
                temperature = temperature,
                isCharging = isCharging,
                chargingType = chargingType,
                voltage = voltage,
                health = health,
                estimatedRemainingTime = estimatedRemainingTime
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting battery stats", e)
            return BatteryStats(
                level = 0,
                temperature = 0f,
                isCharging = false,
                chargingType = "unknown",
                voltage = 0,
                health = "unknown",
                estimatedRemainingTime = null
            )
        }
    }

    /** Collects network usage data. */
    private fun collectNetworkUsageData(): NetworkUsage {
        val appNetworkUsageList = mutableListOf<AppNetworkInfo>()

        try {
            val connectivity = connectivityManager
            if (connectivity == null) {
                Log.e(TAG, "ConnectivityManager is not available")
                return NetworkUsage(
                    appNetworkUsage = appNetworkUsageList,
                    wifiConnected = false,
                    mobileDataConnected = false,
                    networkType = "unknown"
                )
            }

            // Check network connectivity
            val activeNetwork = connectivity.activeNetworkInfo
            val wifiConnected =
                activeNetwork?.type == ConnectivityManager.TYPE_WIFI &&
                        activeNetwork.isConnected
            val mobileDataConnected =
                activeNetwork?.type == ConnectivityManager.TYPE_MOBILE &&
                        activeNetwork.isConnected

            val networkType =
                when {
                    wifiConnected -> "wifi"
                    mobileDataConnected -> "mobile"
                    else -> "none"
                }

            // App network usage collection would require the READ_NETWORK_USAGE_HISTORY permission
            // which is not available to third-party apps. We'll use dummy data for the prototype.

            // Get our app usage data and estimate network usage based on usage time
            val appUsage = collectAppUsageData()

            for (app in appUsage) {
                // Simple heuristic: estimate data usage based on app usage time
                // This is just a placeholder for demonstration purposes
                val estimatedDataUsage =
                    app.foregroundTimeMs / 1000 * 10 * 1024 // 10KB per second as example
                val estimatedWifiUsage = if (wifiConnected) estimatedDataUsage else 0L
                val estimatedMobileUsage = if (mobileDataConnected) estimatedDataUsage else 0L

                appNetworkUsageList.add(
                    AppNetworkInfo(
                        packageName = app.packageName,
                        dataUsageBytes = estimatedMobileUsage,
                        wifiUsageBytes = estimatedWifiUsage
                    )
                )
            }

            return NetworkUsage(
                appNetworkUsage = appNetworkUsageList,
                wifiConnected = wifiConnected,
                mobileDataConnected = mobileDataConnected,
                networkType = networkType
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting network usage data", e)
            return NetworkUsage(
                appNetworkUsage = appNetworkUsageList,
                wifiConnected = false,
                mobileDataConnected = false,
                networkType = "unknown"
            )
        }
    }

    /** Collects wake lock data. */
    private fun collectWakeLockData(): List<WakeLockInfo> {
        // Wake lock collection would require system permissions
        // For the prototype, we'll return an empty list

        return emptyList()
    }

    /** Check if a package is a system package. */
    private fun isSystemPackage(packageName: String): Boolean {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) {
            false
        }
    }

    /** Generates a unique device identifier. */
    private fun getDeviceId(): String {
        val androidId =
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )

        return androidId ?: UUID.randomUUID().toString()
    }
}
