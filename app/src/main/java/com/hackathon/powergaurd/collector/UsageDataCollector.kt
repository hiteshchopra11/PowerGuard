package com.hackathon.powergaurd.collector

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageStatsManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.hackathon.powergaurd.data.model.AppInfo
import com.hackathon.powergaurd.data.model.BatteryInfo
import com.hackathon.powergaurd.data.model.CpuInfo
import com.hackathon.powergaurd.data.model.DataUsage
import com.hackathon.powergaurd.data.model.DeviceData
import com.hackathon.powergaurd.data.model.DeviceInfo
import com.hackathon.powergaurd.data.model.MemoryInfo
import com.hackathon.powergaurd.data.model.NetworkInfo
import com.hackathon.powergaurd.data.model.SettingsInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Collector for gathering device usage data to be sent to the backend. */
@Singleton
class UsageDataCollector @Inject constructor(@ApplicationContext private val context: Context) {
    private val TAG = "UsageDataCollector"
    private val TIME_RANGE_MS = 24 * 60 * 60 * 1000L // 24 hours

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
    private val activityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    }

    /**
     * Main entry point - collects all device data
     */
    @RequiresApi(Build.VERSION_CODES.P)
    fun collectDeviceData(): DeviceData {
        val timestamp = System.currentTimeMillis()
        val startTime = timestamp - TIME_RANGE_MS

        return DeviceData(
            deviceId = getDeviceId(),
            timestamp = timestamp,
            battery = collectBatteryInfo(),
            memory = collectMemoryInfo(),
            cpu = collectCpuInfo(),
            network = collectNetworkInfo(startTime, timestamp),
            apps = collectAppsInfo(startTime, timestamp),
            settings = collectSettingsInfo(),
            deviceInfo = collectDeviceInfo()
        )
    }

    /**
     * Collects battery information
     */
    private fun collectBatteryInfo(): BatteryInfo {
        val bm = batteryManager ?: return BatteryInfo(
            level = -1,
            temperature = -1f,
            voltage = -1,
            isCharging = false,
            chargingType = "unknown",
            health = -1
        )

        val batteryIntent =
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val currentNow =
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000 // Convert to mA

        val capacity =
            // Try to get design capacity if available
            try {
                val powerProfile = Class.forName("com.android.internal.os.PowerProfile")
                    .getConstructor(Context::class.java)
                    .newInstance(context)

                val batteryCapacityMethod = powerProfile.javaClass.getMethod("getBatteryCapacity")
                (batteryCapacityMethod.invoke(powerProfile) as Double).toLong()
            } catch (e: Exception) {
                -1L
            }

        return BatteryInfo(
            level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            temperature = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)?.div(10f)
                ?: -1f,
            voltage = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1,
            isCharging = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)?.let {
                it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL
            } ?: false,
            chargingType = when (batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                else -> "unknown"
            },
            health = batteryIntent?.getIntExtra(
                BatteryManager.EXTRA_HEALTH,
                BatteryManager.BATTERY_HEALTH_UNKNOWN
            )
                ?: BatteryManager.BATTERY_HEALTH_UNKNOWN,
            capacity = capacity,
            currentNow = currentNow
        )
    }

    /**
     * Collects memory information
     */
    private fun collectMemoryInfo(): MemoryInfo {
        val activityManager = activityManager ?: return MemoryInfo(
            totalRam = -1,
            availableRam = -1,
            lowMemory = false,
            threshold = -1
        )

        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        return MemoryInfo(
            totalRam = memoryInfo.totalMem,
            availableRam = memoryInfo.availMem,
            lowMemory = memoryInfo.lowMemory,
            threshold = memoryInfo.threshold
        )
    }

    /**
     * Collects CPU information (simplified)
     */
    private fun collectCpuInfo(): CpuInfo {
        // Simplified CPU info as requested to focus on network and data
        return CpuInfo(
            usage = -1f,
            temperature = -1f,
            frequencies = emptyList()
        )
    }

    /**
     * Collects network information including actual data usage statistics
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun collectNetworkInfo(startTime: Long, endTime: Long): NetworkInfo {
        val connectivity = connectivityManager ?: return NetworkInfo(
            type = "unknown",
            strength = -1,
            isRoaming = false,
            dataUsage = DataUsage(0, 0, 0, 0)
        )

        // Get current active network and its capabilities
        val activeNetwork = connectivity.activeNetwork
        val networkCapabilities = connectivity.getNetworkCapabilities(activeNetwork)

        // Determine network type using NetworkCapabilities instead of deprecated constants
        val type = when {
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile"
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) == true -> "Bluetooth"
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN"
            else -> "unknown"
        }

        // Get more detailed connection info
        val activeConnectionInfo = when (type) {
            "WiFi" -> {
                val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val connectionInfo = wifiManager?.connectionInfo
                "SSID: ${connectionInfo?.ssid ?: "unknown"}"
            }

            "Mobile" -> {
                val telephonyManager =
                    context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                "Operator: ${telephonyManager?.networkOperatorName ?: "unknown"}"
            }

            else -> ""
        }

        // Get link speed for WiFi
        val linkSpeed = if (type == "WiFi") {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiManager?.connectionInfo?.linkSpeed ?: -1
        } else {
            -1
        }

        // Get mobile network generation with proper permission check
        val cellularGeneration = if (type == "Mobile") {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    when (telephonyManager?.dataNetworkType) {
                        TelephonyManager.NETWORK_TYPE_GPRS,
                        TelephonyManager.NETWORK_TYPE_EDGE,
                        TelephonyManager.NETWORK_TYPE_CDMA,
                        TelephonyManager.NETWORK_TYPE_1xRTT,
                        TelephonyManager.NETWORK_TYPE_IDEN -> "2G"

                        TelephonyManager.NETWORK_TYPE_UMTS,
                        TelephonyManager.NETWORK_TYPE_EVDO_0,
                        TelephonyManager.NETWORK_TYPE_EVDO_A,
                        TelephonyManager.NETWORK_TYPE_HSDPA,
                        TelephonyManager.NETWORK_TYPE_HSUPA,
                        TelephonyManager.NETWORK_TYPE_HSPA,
                        TelephonyManager.NETWORK_TYPE_EVDO_B,
                        TelephonyManager.NETWORK_TYPE_EHRPD,
                        TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"

                        TelephonyManager.NETWORK_TYPE_LTE,
                        TelephonyManager.NETWORK_TYPE_IWLAN -> "4G"

                        TelephonyManager.NETWORK_TYPE_NR -> "5G"

                        else -> "unknown"
                    }
                } catch (se: SecurityException) {
                    "unknown"
                }
            } else {
                "unknown"
            }
        } else {
            ""
        }

        // Get signal strength
        val strength = when (type) {
            "WiFi" -> {
                val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                wifiManager?.connectionInfo?.rssi ?: -1
            }

            "Mobile" -> {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    try {
                        telephonyManager?.signalStrength?.let {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                it.cellSignalStrengths.firstOrNull()?.level ?: -1
                            } else {
                                @Suppress("DEPRECATION")
                                it.gsmSignalStrength
                            }
                        } ?: -1
                    } catch (se: SecurityException) {
                        -1
                    }
                } else {
                    -1
                }
            }

            else -> -1
        }

        // Get network usage stats using non-deprecated methods when possible
        val dataUsage = collectNetworkDataUsageWithNetworkCapabilities(startTime, endTime, type)

        return NetworkInfo(
            type = type,
            strength = strength,
            isRoaming = telephonyManager?.isNetworkRoaming ?: false,
            dataUsage = dataUsage,
            activeConnectionInfo = activeConnectionInfo,
            linkSpeed = linkSpeed,
            cellularGeneration = cellularGeneration
        )
    }

    /**
     * Collects device-wide network data usage
     */
    private fun collectNetworkDataUsage(startTime: Long, endTime: Long): DataUsage {
        var foregroundBytes = 0L
        var backgroundBytes = 0L
        var rxBytes = 0L
        var txBytes = 0L

        try {
            val networkStatsManager = networkStatsManager ?: return DataUsage(0, 0, 0, 0)

            // Try to get historical stats first
            if (context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED
            ) {

                // Mobile usage
                val subscriberId = getSubscriberId()
                val mobileStats = networkStatsManager.querySummary(
                    ConnectivityManager.TYPE_MOBILE,
                    subscriberId,
                    startTime,
                    endTime
                )

                val bucket = NetworkStats.Bucket()
                while (mobileStats.hasNextBucket()) {
                    mobileStats.getNextBucket(bucket)
                    if (bucket.state == NetworkStats.Bucket.STATE_FOREGROUND) {
                        foregroundBytes += bucket.rxBytes + bucket.txBytes
                    } else {
                        backgroundBytes += bucket.rxBytes + bucket.txBytes
                    }
                    rxBytes += bucket.rxBytes
                    txBytes += bucket.txBytes
                }
                mobileStats.close()

                // WiFi usage
                val wifiStats = networkStatsManager.querySummary(
                    ConnectivityManager.TYPE_WIFI,
                    "",
                    startTime,
                    endTime
                )

                while (wifiStats.hasNextBucket()) {
                    wifiStats.getNextBucket(bucket)
                    if (bucket.state == NetworkStats.Bucket.STATE_FOREGROUND) {
                        foregroundBytes += bucket.rxBytes + bucket.txBytes
                    } else {
                        backgroundBytes += bucket.rxBytes + bucket.txBytes
                    }
                    rxBytes += bucket.rxBytes
                    txBytes += bucket.txBytes
                }
                wifiStats.close()
            }

            // If we couldn't get historical stats, fallback to current session stats
            if (foregroundBytes == 0L && backgroundBytes == 0L) {
                // Get current session stats using TrafficStats
                val totalRx = TrafficStats.getTotalRxBytes()
                val totalTx = TrafficStats.getTotalTxBytes()
                val mobileRx = TrafficStats.getMobileRxBytes()
                val mobileTx = TrafficStats.getMobileTxBytes()

                rxBytes = totalRx
                txBytes = totalTx

                // Assume mobile is background, WiFi is foreground (simplified approach)
                backgroundBytes = mobileRx + mobileTx
                foregroundBytes = (totalRx - mobileRx) + (totalTx - mobileTx)
            }

        } catch (e: Exception) {
            // Log exception
        }

        return DataUsage(
            foreground = foregroundBytes,
            background = backgroundBytes,
            rxBytes = rxBytes,
            txBytes = txBytes
        )
    }

    /**
     * Collects detailed information about all apps
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun collectAppsInfo(startTime: Long, endTime: Long): List<AppInfo> {
        if (!hasUsageStatsPermission()) return emptyList()

        val usageStatsManager = usageStatsManager ?: return emptyList()
        val packageManager = packageManager
        val activityManager = activityManager ?: return emptyList()

        // Get running processes and memory info
        val runningAppProcesses = activityManager.runningAppProcesses ?: emptyList()
        val memoryInfoMap = collectMemoryInfoPerApp(runningAppProcesses)

        // Get per-app network usage
        val networkUsageMap = collectNetworkUsagePerApp(startTime, endTime)

        // Get per-app battery usage (if possible)
        val batteryUsageMap = collectBatteryUsagePerApp()

        // Get app usage statistics
        val usageStatsMap = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )?.associateBy { it.packageName } ?: emptyMap()

        // Collection of apps that are installed and potentially running
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        return installedApps.mapNotNull { appInfo ->
            try {
                val packageName = appInfo.packageName
                val usageStats = usageStatsMap[packageName]

                // Skip apps with no usage data if we're only interested in active apps
                if (usageStats == null && networkUsageMap[packageName] == null) {
                    return@mapNotNull null
                }

                // Get app version info
                val packageInfo = packageManager.getPackageInfo(packageName, 0)

                AppInfo(
                    packageName = packageName,
                    processName = runningAppProcesses.find { it.pkgList.contains(packageName) }?.processName
                        ?: packageName,
                    appName = packageManager.getApplicationLabel(appInfo).toString(),
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    lastUsed = usageStats?.lastTimeUsed ?: 0L,
                    foregroundTime = usageStats?.totalTimeInForeground ?: 0L,
                    backgroundTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && usageStats != null) {
                        (usageStats.totalTimeVisible - usageStats.totalTimeInForeground).coerceAtLeast(
                            0
                        )
                    } else 0L,
                    batteryUsage = batteryUsageMap[packageName] ?: -1f,
                    dataUsage = networkUsageMap[packageName] ?: DataUsage(0L, 0L, 0L, 0L),
                    memoryUsage = memoryInfoMap[packageName] ?: -1L,
                    cpuUsage = -1f, // Ignoring CPU usage as requested
                    notifications = 0, // Requires notification listener permissions
                    crashes = 0, // Would require system logs access
                    versionName = packageInfo.versionName ?: "",
                    versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode.toLong()
                    },
                    targetSdkVersion = appInfo.targetSdkVersion,
                    installTime = packageInfo.firstInstallTime,
                    updatedTime = packageInfo.lastUpdateTime
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Collects memory usage per app
     */
    private fun collectMemoryInfoPerApp(processes: List<ActivityManager.RunningAppProcessInfo>): Map<String, Long> {
        val memoryMap = HashMap<String, Long>()
        val activityManager = activityManager ?: return emptyMap()

        try {
            // Get PIDs for all running processes
            val pids = processes.map { it.pid }.toIntArray()
            if (pids.isEmpty()) return emptyMap()

            // Get memory information for these processes
            val memoryInfo = activityManager.getProcessMemoryInfo(pids)

            // Map process memory to package names
            processes.forEachIndexed { index, process ->
                if (index < memoryInfo.size) {
                    val info = memoryInfo[index]
                    // Total PSS is a good measure of app's memory footprint
                    val memoryUsage = info?.totalPss?.toLong() ?: 0L

                    // Associate memory with each package in this process
                    process.pkgList?.forEach { packageName ->
                        memoryMap[packageName] = memoryUsage
                    }
                }
            }
        } catch (e: Exception) {
            // Log exception
        }

        return memoryMap
    }

    /**
     * Collects per-app network usage statistics with proper permission handling
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun collectNetworkUsagePerApp(startTime: Long, endTime: Long): Map<String, DataUsage> {
        // First check if we have PACKAGE_USAGE_STATS permission
        if (!hasUsageStatsPermission()) {
            return emptyMap()
        }

        // Check if we have network stats manager
        val networkStatsManager = networkStatsManager ?: return getNetworkUsageFromTrafficStats()

        val networkUsageMap = HashMap<String, DataUsage>()
        val hasPhoneStatePermission = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        try {
            val subscriberId = if (hasPhoneStatePermission) getSubscriberId() else null

            // Get all installed apps with their UIDs
            val packageManager = packageManager
            val installedApps =
                packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

            for (app in installedApps) {
                var foregroundBytes = 0L
                var backgroundBytes = 0L
                var rxBytes = 0L
                var txBytes = 0L

                try {
                    // Mobile data usage - only if we have proper permissions
                    if (hasPhoneStatePermission && subscriberId != null) {
                        try {
                            // Using direct constant instead of deprecated constant
                            val mobileStats = networkStatsManager.queryDetailsForUid(
                                0, // ConnectivityManager.TYPE_MOBILE = 0
                                subscriberId,
                                startTime,
                                endTime,
                                app.uid
                            )

                            val bucket = NetworkStats.Bucket()
                            while (mobileStats.hasNextBucket()) {
                                mobileStats.getNextBucket(bucket)
                                if (bucket.state == NetworkStats.Bucket.STATE_FOREGROUND) {
                                    foregroundBytes += bucket.rxBytes + bucket.txBytes
                                } else {
                                    backgroundBytes += bucket.rxBytes + bucket.txBytes
                                }
                                rxBytes += bucket.rxBytes
                                txBytes += bucket.txBytes
                            }
                            mobileStats.close()
                        } catch (se: SecurityException) {
                            // Skip mobile stats if we don't have proper permissions
                        }
                    }

                    // WiFi data usage - less restrictive
                    try {
                        // Using direct constant instead of deprecated constant
                        val wifiStats = networkStatsManager.queryDetailsForUid(
                            1, // ConnectivityManager.TYPE_WIFI = 1
                            "",
                            startTime,
                            endTime,
                            app.uid
                        )

                        val bucket = NetworkStats.Bucket()
                        while (wifiStats.hasNextBucket()) {
                            wifiStats.getNextBucket(bucket)
                            if (bucket.state == NetworkStats.Bucket.STATE_FOREGROUND) {
                                foregroundBytes += bucket.rxBytes + bucket.txBytes
                            } else {
                                backgroundBytes += bucket.rxBytes + bucket.txBytes
                            }
                            rxBytes += bucket.rxBytes
                            txBytes += bucket.txBytes
                        }
                        wifiStats.close()
                    } catch (se: SecurityException) {
                        // Try TrafficStats fallback for this app
                        tryTrafficStatsForApp(app.uid)?.let { trafficStats ->
                            rxBytes = trafficStats.first
                            txBytes = trafficStats.second

                            // Estimate foreground/background
                            foregroundBytes = (rxBytes + txBytes) * 7 / 10
                            backgroundBytes = (rxBytes + txBytes) - foregroundBytes
                        }
                    }

                } catch (e: Exception) {
                    // Try TrafficStats fallback for this app
                    tryTrafficStatsForApp(app.uid)?.let { trafficStats ->
                        rxBytes = trafficStats.first
                        txBytes = trafficStats.second

                        // Estimate foreground/background
                        foregroundBytes = (rxBytes + txBytes) * 7 / 10
                        backgroundBytes = (rxBytes + txBytes) - foregroundBytes
                    }
                }

                // Only add apps that have used network
                if (rxBytes > 0 || txBytes > 0) {
                    networkUsageMap[app.packageName] = DataUsage(
                        foreground = foregroundBytes,
                        background = backgroundBytes,
                        rxBytes = rxBytes,
                        txBytes = txBytes
                    )
                }
            }

        } catch (e: Exception) {
            // Try fallback using TrafficStats only
            return getNetworkUsageFromTrafficStats()
        }

        return networkUsageMap
    }

    /**
     * Helper method to get traffic stats for a specific UID
     * Returns a Pair of (rxBytes, txBytes) or null if stats aren't available
     */
    private fun tryTrafficStatsForApp(uid: Int): Pair<Long, Long>? {
        return try {
            val rxBytes = TrafficStats.getUidRxBytes(uid)
            val txBytes = TrafficStats.getUidTxBytes(uid)

            if (rxBytes > 0 || txBytes > 0) {
                Pair(rxBytes, txBytes)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fallback method to get current session network usage per app
     * Uses TrafficStats which only provides data since boot
     */
    private fun getNetworkUsageFromTrafficStats(): Map<String, DataUsage> {
        val networkUsageMap = HashMap<String, DataUsage>()

        try {
            val packageManager = packageManager
            val installedApps =
                packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

            for (app in installedApps) {
                tryTrafficStatsForApp(app.uid)?.let { (rxBytes, txBytes) ->
                    // With TrafficStats, we can't differentiate foreground vs background
                    // Assuming 70% foreground, 30% background for simplicity
                    val foregroundBytes = (rxBytes + txBytes) * 7 / 10
                    val backgroundBytes = (rxBytes + txBytes) - foregroundBytes

                    networkUsageMap[app.packageName] = DataUsage(
                        foreground = foregroundBytes,
                        background = backgroundBytes,
                        rxBytes = rxBytes,
                        txBytes = txBytes
                    )
                }
            }
        } catch (e: Exception) {
            // Log the exception
        }

        return networkUsageMap
    }

    /**
     * Collects battery usage per app (if available)
     * Note: Battery usage is not always accessible on all devices
     */
    private fun collectBatteryUsagePerApp(): Map<String, Float> {
        val batteryUsageMap = HashMap<String, Float>()

        // Multiple approaches for collecting battery usage

        // Approach 1: Using BatteryManager and UsageStatsManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val usm = usageStatsManager ?: return emptyMap()
                val usageStats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    System.currentTimeMillis() - TIME_RANGE_MS,
                    System.currentTimeMillis()
                )

                // Extract total screen time as a baseline
                var totalScreenTime = 0L
                for (stat in usageStats) {
                    totalScreenTime += stat.totalTimeInForeground
                }

                if (totalScreenTime > 0) {
                    // Simplistic approach: estimate battery usage based on screen time proportion
                    val batteryManager = batteryManager
                    val batteryLevel =
                        batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                            ?: 100
                    val batteryConsumed = 100 - batteryLevel

                    for (stat in usageStats) {
                        if (stat.totalTimeInForeground > 0) {
                            val proportion =
                                stat.totalTimeInForeground.toFloat() / totalScreenTime.toFloat()
                            // Rough estimation
                            batteryUsageMap[stat.packageName] = proportion * batteryConsumed
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback to next approach
        }

        // Approach 2: Using manufacturer-specific APIs (simplified example)
        if (batteryUsageMap.isEmpty()) {
            try {
                // Some manufacturers provide battery stats through their own APIs
                // This is highly device-specific and would need custom implementations

                // Example for Samsung (pseudo-code)
                if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
                    // Samsung specific implementation would go here
                }

                // Example for Huawei (pseudo-code)
                if (Build.MANUFACTURER.equals("huawei", ignoreCase = true)) {
                    // Huawei specific implementation would go here
                }
            } catch (e: Exception) {
                // Log exception
            }
        }

        return batteryUsageMap
    }

    /**
     * Collects settings information
     */
    private fun collectSettingsInfo(): SettingsInfo {
        val powerManager =
            context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager

        return SettingsInfo(
            batteryOptimization = powerManager?.isIgnoringBatteryOptimizations(context.packageName)
                ?.not() ?: false,
            dataSaver =
            (context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
                ?.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED,
            powerSaveMode = powerManager?.isPowerSaveMode ?: false,
            adaptiveBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Settings.Global.getInt(
                    context.contentResolver,
                    "adaptive_battery_management_enabled",
                    0
                ) == 1
            } else false,
            autoSync = ContentResolver.getMasterSyncAutomatically()
        )
    }

    /**
     * Collects device information
     */
    private fun collectDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            osVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            screenOnTime = try {
                Settings.System.getLong(
                    context.contentResolver,
                    "screen_on_time",  // Use string literal instead of constant
                    0
                )
            } catch (e: Exception) {
                0L
            }
        )
    }

    /**
     * Gets the subscriber ID for mobile network statistics
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun getSubscriberId(): String? {
        return try {
            // First check for regular phone state permission
            if (context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    // Try to get subscriberId
                    telephonyManager?.subscriberId
                } catch (se: SecurityException) {
                    // If it fails with SecurityException, we don't have the privileged permission
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    // Permission check helpers
    private fun hasUsageStatsPermission(): Boolean {
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Gets the device ID for identifying this device
     */
    private fun getDeviceId(): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: UUID.randomUUID().toString()
        } catch (e: Exception) {
            UUID.randomUUID().toString()
        }
    }

    /**
     * Collects network data usage using NetworkCapabilities when possible, falling back to traditional methods
     */
    private fun collectNetworkDataUsageWithNetworkCapabilities(
        startTime: Long,
        endTime: Long,
        networkType: String
    ): DataUsage {
        // First try using TrafficStats directly - this is the most reliable method that doesn't require special permissions
        val totalStats = collectBasicTrafficStats()

        // If we have the networkStatsManager and proper permissions, try to get more detailed stats
        if (networkStatsManager != null) {
            try {
                return collectDetailedNetworkStats(startTime, endTime, networkType)
            } catch (e: Exception) {
                // Log the exception and fall back to basic stats
            }
        }

        return totalStats
    }

    /**
     * Collects basic traffic stats that are available without special permissions
     */
    private fun collectBasicTrafficStats(): DataUsage {
        val rxBytes = TrafficStats.getTotalRxBytes()
        val txBytes = TrafficStats.getTotalTxBytes()
        val mobileRxBytes = TrafficStats.getMobileRxBytes()
        val mobileTxBytes = TrafficStats.getMobileTxBytes()

        val totalBytes = rxBytes + txBytes
        val mobileBytes = mobileRxBytes + mobileTxBytes
        val wifiBytes = totalBytes - mobileBytes

        // Estimate foreground/background split
        // Assume WiFi is mostly foreground (80%) and mobile is mostly background (70%)
        val foregroundBytes = (wifiBytes * 0.8).toLong() + (mobileBytes * 0.3).toLong()
        val backgroundBytes = totalBytes - foregroundBytes

        return DataUsage(
            foreground = foregroundBytes,
            background = backgroundBytes,
            rxBytes = rxBytes,
            txBytes = txBytes
        )
    }

    /**
     * Collects detailed network stats using NetworkStatsManager
     * Requires proper permissions
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun collectDetailedNetworkStats(
        startTime: Long,
        endTime: Long,
        networkType: String
    ): DataUsage {
        var foregroundBytes = 0L
        var backgroundBytes = 0L
        var rxBytes = 0L
        var txBytes = 0L

        // First check phone state permission
        val hasPhoneStatePermission = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val networkStatsManager = networkStatsManager ?: return DataUsage(0, 0, 0, 0)

        try {
            // Mobile stats (requires READ_PHONE_STATE permission)
            if (hasPhoneStatePermission && networkType == "Mobile") {
                try {
                    val subscriberId = getSubscriberId()
                    if (subscriberId != null) {
                        // IMPORTANT: Using the constant directly since TYPE_MOBILE is deprecated
                        // But NetworkStatsManager API still needs it
                        val mobileStats = networkStatsManager.querySummary(
                            0, // ConnectivityManager.TYPE_MOBILE = 0
                            subscriberId,
                            startTime,
                            endTime
                        )

                        val bucket = NetworkStats.Bucket()
                        while (mobileStats.hasNextBucket()) {
                            mobileStats.getNextBucket(bucket)
                            if (bucket.state == NetworkStats.Bucket.STATE_FOREGROUND) {
                                foregroundBytes += bucket.rxBytes + bucket.txBytes
                            } else {
                                backgroundBytes += bucket.rxBytes + bucket.txBytes
                            }
                            rxBytes += bucket.rxBytes
                            txBytes += bucket.txBytes
                        }
                        mobileStats.close()
                    }
                } catch (se: SecurityException) {
                    // Handle permission issues
                }
            }

            // WiFi stats (doesn't require special permissions)
            if (networkType == "WiFi" || networkType == "unknown") {
                try {
                    // IMPORTANT: Using the constant directly since TYPE_WIFI is deprecated
                    // But NetworkStatsManager API still needs it
                    val wifiStats = networkStatsManager.querySummary(
                        1, // ConnectivityManager.TYPE_WIFI = 1
                        "",
                        startTime,
                        endTime
                    )

                    val bucket = NetworkStats.Bucket()
                    while (wifiStats.hasNextBucket()) {
                        wifiStats.getNextBucket(bucket)
                        if (bucket.state == NetworkStats.Bucket.STATE_FOREGROUND) {
                            foregroundBytes += bucket.rxBytes + bucket.txBytes
                        } else {
                            backgroundBytes += bucket.rxBytes + bucket.txBytes
                        }
                        rxBytes += bucket.rxBytes
                        txBytes += bucket.txBytes
                    }
                    wifiStats.close()
                } catch (se: SecurityException) {
                    // Handle permission issues
                }
            }
        } catch (e: Exception) {
            // Fall back to basic stats if detailed collection fails
            return collectBasicTrafficStats()
        }

        // If we failed to collect any stats, fall back to basic stats
        if (foregroundBytes == 0L && backgroundBytes == 0L) {
            return collectBasicTrafficStats()
        }

        return DataUsage(
            foreground = foregroundBytes,
            background = backgroundBytes,
            rxBytes = rxBytes,
            txBytes = txBytes
        )
    }

}