package com.hackathon.powergaurd.collector

// Imports (ensure all necessary imports are present)
import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
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

    // Define time range for data collection (e.g., last 24 hours)
    private val TIME_RANGE_MS = 24 * 60 * 60 * 1000L // 24 hours

    // System service managers (lazy initialization)
    private val batteryManager by lazy {
        context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    }

    private val usageStatsManager by lazy {
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    }

    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    private val networkStatsManager by lazy {
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
    }

    private val telephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    }

    private val packageManager: PackageManager by lazy { context.packageManager }
    private val appOpsManager: AppOpsManager by lazy {
        context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    }

    /**
     * Collects complete device data for analysis.
     * @return DeviceData object containing all relevant device statistics
     */
    suspend fun collectDeviceData(): DeviceData {
        Log.d(TAG, "Collecting device data for analysis")

        val deviceId = getDeviceId()
        val timestamp = System.currentTimeMillis()
        val startTime = timestamp - TIME_RANGE_MS

        val appUsage = collectAppUsageData(startTime, timestamp)
        val batteryStats = collectBatteryStats()
        val networkUsage = collectNetworkUsageData(startTime, timestamp)
        val wakeLocks = collectWakeLockData() // Remains limited

        return DeviceData(
            appUsage = appUsage,
            batteryStats = batteryStats,
            networkUsage = networkUsage,
            wakeLocks = wakeLocks,
            deviceId = deviceId,
            timestamp = timestamp
        )
    }

    // --- Permission Checks ---

    private fun hasUsageStatsPermission(): Boolean {
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }


    private fun hasReadPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    // --- Data Collection Methods ---

    /** Collects app usage statistics for the given time range. */
    private fun collectAppUsageData(startTime: Long, endTime: Long): List<AppUsageInfo> {
        if (!hasUsageStatsPermission()) {
            Log.w(TAG, "PACKAGE_USAGE_STATS permission not granted. Cannot collect app usage.")
            // TODO: Consider guiding the user to the settings screen here
            return emptyList()
        }

        val appUsageList = mutableListOf<AppUsageInfo>()
        val statsManager = usageStatsManager ?: run {
            Log.e(TAG, "UsageStatsManager is not available")
            return emptyList()
        }

        try {
            // Query usage stats for the specified interval
            val usageStatsList = statsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, // Or INTERVAL_BEST for more granular data
                startTime,
                endTime
            )

            if (usageStatsList.isNullOrEmpty()) {
                Log.w(TAG, "No usage stats available for the specified range.")
                return emptyList()
            }

            // Process each UsageStats entry
            for (usageStats in usageStatsList) {
                try {
                    val packageName = usageStats.packageName
                    val appInfo: ApplicationInfo? = try {
                        packageManager.getApplicationInfo(packageName, 0)
                    } catch (e: PackageManager.NameNotFoundException) {
                        Log.w(TAG, "Package not found, likely uninstalled: $packageName")
                        null // App might have been uninstalled
                    }

                    // Skip if app info not found or if it's a system app or our own app
                    if (appInfo == null || (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 || packageName == context.packageName) {
                        continue
                    }

                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    val foregroundTimeMs = usageStats.totalTimeInForeground
                    val lastTimeUsed = usageStats.lastTimeUsed

                    // *** FIX: Correct API level check for totalTimeVisible ***
                    // Background time calculation - Requires API 29 (Q) for totalTimeVisible
                    val backgroundTimeMs = when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                            // Add annotation for clarity although check is present
                            @RequiresApi(Build.VERSION_CODES.Q)
                            {
                                (usageStats.totalTimeVisible - usageStats.totalTimeInForeground).coerceAtLeast(
                                    0
                                )
                            }

                        else -> 0L // No reliable way to get this before API 29
                    }

                    // Launch count requires querying events, which is more complex.
                    val launchCount = 0 // Placeholder - real count requires event query

                    // Only add apps that were actually used (foreground or background)
                    if (foregroundTimeMs > 0 || backgroundTimeMs > 0) {
                        appUsageList.add(
                            AppUsageInfo(
                                packageName = packageName,
                                appName = appName,
                                foregroundTimeMs = foregroundTimeMs,
                                backgroundTimeMs = backgroundTimeMs,
                                lastUsed = lastTimeUsed,
                                launchCount = launchCount // Use real count if implemented
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "Error processing usage stats for package ${usageStats.packageName}",
                        e
                    )
                }
            }

            // Sort by total time (foreground + background) as a potential measure of impact
            return appUsageList.sortedByDescending { it.foregroundTimeMs + it.backgroundTimeMs }

        } catch (e: SecurityException) {
            Log.e(
                TAG,
                "SecurityException collecting app usage data. Ensure PACKAGE_USAGE_STATS is granted.",
                e
            )
            return emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting app usage data", e)
            return emptyList()
        }
    }

    /** Collects current battery statistics. */
    private fun collectBatteryStats(): BatteryStats {
        val bm = batteryManager ?: run {
            Log.e(TAG, "BatteryManager is not available")
            return createDefaultBatteryStats()
        }

        try {
            // --- Get values using BatteryManager properties (preferred on newer APIs) ---
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
            val isCharging =
                status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            // --- Fallback to Intent for older APIs or missing properties ---
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatusIntent: Intent? = context.registerReceiver(null, filter)

            val temperatureRaw =
                batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                    ?: Int.MIN_VALUE
            val temperature = if (temperatureRaw != Int.MIN_VALUE) temperatureRaw / 10f else -1f

            val voltage = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1

            val healthValue = batteryStatusIntent?.getIntExtra(
                BatteryManager.EXTRA_HEALTH,
                BatteryManager.BATTERY_HEALTH_UNKNOWN
            )
                ?: BatteryManager.BATTERY_HEALTH_UNKNOWN

            val chargePlug =
                batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1

            // --- Determine Charging Type ---
            val chargingType = when {
                !isCharging -> "not_charging"
                status == BatteryManager.BATTERY_STATUS_FULL -> "full"
                chargePlug == BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                chargePlug == BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                else -> "charging_other"
            }

            // --- Determine Health ---
            val health = when (healthValue) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "unspecified_failure"
                BatteryManager.BATTERY_HEALTH_COLD -> "cold"
                else -> "unknown"
            }

            // --- Estimate Remaining Time (Requires API 28+) ---
            val estimatedRemainingTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try { // Added for clarity
                    bm.computeChargeTimeRemaining().takeIf { it > 0 }
                } catch (e: Exception) {
                    Log.e(TAG, "Error computing charge time remaining", e)
                    null
                }
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
            return createDefaultBatteryStats()
        }
    }

    /** Creates a default BatteryStats object in case of errors. */
    private fun createDefaultBatteryStats(): BatteryStats {
        return BatteryStats(
            level = -1,
            temperature = -1f,
            isCharging = false,
            chargingType = "unknown",
            voltage = -1,
            health = "unknown",
            estimatedRemainingTime = null
        )
    }


    /** Collects network usage data using NetworkStatsManager (API 23+). */
    private fun collectNetworkUsageDataApi23(startTime: Long, endTime: Long): List<AppNetworkInfo> {
        if (!hasUsageStatsPermission()) {
            Log.w(TAG, "PACKAGE_USAGE_STATS permission not granted. Cannot collect network usage.")
            return emptyList()
        }

        val nsManager = networkStatsManager ?: run {
            Log.e(TAG, "NetworkStatsManager not available (Requires API 23+).")
            return emptyList()
        }

        // Map UID to aggregated usage
        val appNetworkUsageMap = mutableMapOf<Int, AppNetworkInfoInternal>()

        try {
            // Get subscriber ID (okay if null)
            val subscriberId = getSubscriberId()

            // --- Query Mobile Data ---
            try {
                val mobileSummary: NetworkStats? = nsManager.querySummary(
                    ConnectivityManager.TYPE_MOBILE,
                    subscriberId, // Pass null if unavailable
                    startTime,
                    endTime
                )
                mobileSummary?.use { processNetworkStats(it, appNetworkUsageMap, isWifi = false) }
                    ?: Log.w(TAG, "Mobile network summary query returned null.")
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException querying mobile network stats.", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error querying mobile network stats", e)
            }


            // --- Query WiFi Data ---
            try {
                val wifiSummary: NetworkStats? = nsManager.querySummary(
                    ConnectivityManager.TYPE_WIFI,
                    null, // Subscriber ID not needed for WiFi
                    startTime,
                    endTime
                )
                wifiSummary?.use { processNetworkStats(it, appNetworkUsageMap, isWifi = true) }
                    ?: Log.w(TAG, "WiFi network summary query returned null.")
            } catch (e: SecurityException) {
                Log.e(
                    TAG,
                    "SecurityException querying WiFi network stats. Ensure PACKAGE_USAGE_STATS is granted.",
                    e
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error querying WiFi network stats", e)
            }


            // --- Map UIDs to Packages and Finalize ---
            if (appNetworkUsageMap.isEmpty()) {
                Log.w(TAG, "No network usage data found after querying.")
            }
            return mapUidUsageToPackageUsage(appNetworkUsageMap)

        } catch (e: SecurityException) {
            Log.e(
                TAG,
                "SecurityException accessing NetworkStatsManager overall. Ensure PACKAGE_USAGE_STATS is granted.",
                e
            )
            return emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Generic error collecting network usage data", e)
            return emptyList()
        }
    }

    /** Helper to process NetworkStats results and aggregate by UID. */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun processNetworkStats(
        stats: NetworkStats,
        usageMap: MutableMap<Int, AppNetworkInfoInternal>,
        isWifi: Boolean
    ) {
        var bucketCount = 0
        try {
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val uid = bucket.uid
                val currentInfo = usageMap.getOrPut(uid) { AppNetworkInfoInternal() }
                val bytes = bucket.rxBytes + bucket.txBytes
                if (bytes > 0) {
                    if (isWifi) {
                        currentInfo.wifiUsageBytes += bytes
                    } else {
                        currentInfo.dataUsageBytes += bytes
                    }
                    bucketCount++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while iterating through NetworkStats buckets", e)
        } finally {
            Log.d(
                TAG,
                "Processed $bucketCount ${if (isWifi) "WiFi" else "Mobile"} network buckets."
            )
            // 'stats' is closed automatically by the 'use' block in the calling function
        }
    }

    /** Helper to map aggregated UID usage back to package names. */
    private fun mapUidUsageToPackageUsage(usageMap: Map<Int, AppNetworkInfoInternal>): List<AppNetworkInfo> {
        val resultList = mutableListOf<AppNetworkInfo>()
        var skippedSystem = 0
        var skippedNoPackage = 0

        for ((uid, usage) in usageMap) {
            if (uid < Process.FIRST_APPLICATION_UID) continue

            val packageNames: Array<String>? = try {
                packageManager.getPackagesForUid(uid)
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException getting packages for UID $uid", e)
                null
            } catch (e: Exception) {
                Log.e(TAG, "Error getting packages for UID $uid", e)
                null
            }

            if (!packageNames.isNullOrEmpty()) {
                val packageName = packageNames[0]
                try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 || packageName == context.packageName) {
                        skippedSystem++
                        continue
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    skippedNoPackage++
                    continue
                } catch (e: Exception) {
                    Log.w(TAG, "Could not get app info for $packageName, skipping.", e)
                    continue
                }

                if (usage.dataUsageBytes > 0 || usage.wifiUsageBytes > 0) {
                    resultList.add(
                        AppNetworkInfo(
                            packageName = packageName,
                            dataUsageBytes = usage.dataUsageBytes,
                            wifiUsageBytes = usage.wifiUsageBytes
                        )
                    )
                }
            } else {
                skippedNoPackage++
                Log.w(TAG, "Could not find package name for UID: $uid")
            }
        }
        Log.d(
            TAG,
            "Network Usage Mapping: Found ${resultList.size} apps with usage. Skipped $skippedSystem system apps, $skippedNoPackage apps with no package name."
        )
        return resultList
    }


    /** Collects network usage data. Chooses implementation based on API level. */
    private fun collectNetworkUsageData(startTime: Long, endTime: Long): NetworkUsage {
        val appNetworkUsageList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            collectNetworkUsageDataApi23(startTime, endTime)
        } else {
            Log.w(
                TAG,
                "NetworkStatsManager not available below API 23. Cannot collect per-app network usage."
            )
            emptyList()
        }

        // Get overall network state
        val connectivity = connectivityManager
        var wifiConnected = false
        var mobileDataConnected = false
        var networkType = "unknown"

        try {
            if (connectivity != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val network = connectivity.activeNetwork
                    val capabilities = network?.let { connectivity.getNetworkCapabilities(it) }
                    if (capabilities != null) {
                        wifiConnected =
                            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                        mobileDataConnected =
                            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
                    } else {
                        Log.d(TAG, "No active network or capabilities found.")
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val activeNetworkInfo = connectivity.activeNetworkInfo
                    if (activeNetworkInfo != null && activeNetworkInfo.isConnected) {
                        wifiConnected = activeNetworkInfo.type == ConnectivityManager.TYPE_WIFI
                        mobileDataConnected =
                            activeNetworkInfo.type == ConnectivityManager.TYPE_MOBILE
                    }
                }
                networkType = when {
                    wifiConnected -> "WiFi"
                    mobileDataConnected -> "Mobile"
                    connectivity.activeNetworkInfo?.isConnected == true -> "Other"
                    else -> "None"
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting connectivity state.", e)
            networkType = "Error"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network connectivity state", e)
            networkType = "Error"
        }


        return NetworkUsage(
            appNetworkUsage = appNetworkUsageList,
            wifiConnected = wifiConnected,
            mobileDataConnected = mobileDataConnected,
            networkType = networkType
        )
    }

    // Internal helper data class for aggregating network usage by UID
    private data class AppNetworkInfoInternal(
        var dataUsageBytes: Long = 0L,
        var wifiUsageBytes: Long = 0L
    )


    /**
     * Collects wake lock data. NOTE: Limited accuracy without system permissions.
     */
    private fun collectWakeLockData(): List<WakeLockInfo> {
        Log.w(
            TAG,
            "Accurate per-app wake lock data collection requires elevated permissions (BATTERY_STATS or DUMP) unavailable to this app. Returning empty list."
        )
        return emptyList()
    }


    /** Generates a unique device identifier (Android ID or fallback). */
    private fun getDeviceId(): String {
        return try {
            val androidId =
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            if (androidId.isNullOrBlank() || androidId == "9774d56d682e549c") { // Common invalid ID
                generateFallbackId("Invalid Android ID: $androidId")
            } else {
                androidId
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Android ID", e)
            generateFallbackId("Exception getting Android ID")
        }
    }

    /** Generates a fallback ID if Android ID is unavailable or invalid */
    private fun generateFallbackId(reason: String): String {
        Log.w(TAG, "$reason. Generating random UUID as fallback ID.")
        return UUID.randomUUID().toString()
    }

    /** Gets the subscriber ID for mobile network stats. Requires READ_PHONE_STATE permission. */
    @SuppressLint("MissingPermission", "HardwareIds")
    private fun getSubscriberId(): String? {
        if (!hasReadPhoneStatePermission()) {
            Log.w(TAG, "READ_PHONE_STATE permission not granted. Cannot get subscriber ID.")
            return null // Return null if permission denied
        }
        val tm = telephonyManager ?: return null

        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                // *** FIX: Add SuppressLint for permission check below Q ***
                // Suppress Lint warning for guarded call
                tm.subscriberId // This might be null even if permission is granted
            } else {
                // getSubscriberId requires READ_PRIVILEGED_PHONE_STATE from Q onwards
                Log.i(
                    TAG,
                    "Getting subscriber ID requires privileged permission on API 29+. Using null."
                )
                null // Return null as the app cannot hold the required permission
            }
        } catch (e: SecurityException) {
            // Catch SecurityException explicitly, although the check should prevent it on Q+
            Log.e(TAG, "SecurityException getting subscriber ID.", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error getting subscriber ID.", e)
            null
        }
    }
}