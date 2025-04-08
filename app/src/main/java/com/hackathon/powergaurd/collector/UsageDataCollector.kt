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
import android.util.Log
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
        Log.v(TAG, "Initializing batteryManager")
        context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    }
    private val usageStatsManager by lazy {
        Log.v(TAG, "Initializing usageStatsManager")
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    }
    private val connectivityManager by lazy {
        Log.v(TAG, "Initializing connectivityManager")
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }
    private val networkStatsManager by lazy {
        Log.v(TAG, "Initializing networkStatsManager")
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
    }
    private val telephonyManager by lazy {
        Log.v(TAG, "Initializing telephonyManager")
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    }
    private val packageManager: PackageManager by lazy {
        Log.v(TAG, "Initializing packageManager")
        context.packageManager
    }
    private val appOpsManager: AppOpsManager by lazy {
        Log.v(TAG, "Initializing appOpsManager")
        context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    }
    private val activityManager by lazy {
        Log.v(TAG, "Initializing activityManager")
        context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    }

    /**
     * Main entry point - collects all device data
     */
    @RequiresApi(Build.VERSION_CODES.P)
    suspend fun collectDeviceData(): DeviceData {
        Log.i(TAG, "Starting data collection")

        // Determine time range for stats collection
        val endTime = System.currentTimeMillis()
        val startTime = endTime - TIME_RANGE_MS

        // Get various device and usage data
        val deviceInfo = collectDeviceInfo()
        val batteryInfo = collectBatteryInfo()
        val memoryInfo = collectMemoryInfo()
        val cpuInfo = collectCpuInfo()
        val networkInfo = collectNetworkInfo(startTime, endTime) // Updated to pass start and end times
        val settingsInfo = collectSettingsInfo()
        var appsInfo = collectAppsInfo(startTime, endTime)

        // Filter out system apps as requested, but keep pre-installed non-system apps
        appsInfo = appsInfo.filter { app ->
            !app.isSystemApp || isPopularPreinstalledApp(app.packageName)
        }

        return DeviceData(
            deviceId = getDeviceId(),
            timestamp = endTime,
            battery = batteryInfo,
            memory = memoryInfo,
            cpu = cpuInfo,
            network = networkInfo,
            apps = appsInfo,
            settings = settingsInfo,
            deviceInfo = deviceInfo,
            prompt = ""
        ).also { Log.i(TAG, "Data collection completed: ${it.apps.size} apps included") }
    }

    /**
     * Collects battery information
     */
    private fun collectBatteryInfo(): BatteryInfo {
        Log.d(TAG, "Collecting battery information")
        val bm = batteryManager ?: run {
            Log.w(TAG, "Battery manager not available")
            return BatteryInfo(
                level = -1,
                temperature = -1f,
                voltage = -1,
                isCharging = false,
                chargingType = "unknown",
                health = -1
            )
        }

        val batteryIntent = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)).also {
                Log.v(TAG, "Battery intent received")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting battery intent: ${e.message}")
            null
        }

        val currentNow = try {
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000.also {
                Log.v(TAG, "Current now: $it mA")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current now: ${e.message}")
            -1
        }

        val capacity = try {
            val powerProfile = Class.forName("com.android.internal.os.PowerProfile")
                .getConstructor(Context::class.java)
                .newInstance(context)

            val batteryCapacityMethod = powerProfile.javaClass.getMethod("getBatteryCapacity")
            (batteryCapacityMethod.invoke(powerProfile) as Double).toLong().also {
                Log.v(TAG, "Battery capacity: $it mAh")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting battery capacity: ${e.message}")
            -1L
        }

        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).also {
            Log.v(TAG, "Battery level: $it%")
        }
        val temperature = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)?.div(10f) ?: -1f
        Log.v(TAG, "Battery temperature: $temperature°C")
        val voltage = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        Log.v(TAG, "Battery voltage: $voltage mV")

        val isCharging = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)?.let {
            it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL
        } ?: false
        Log.v(TAG, "Is charging: $isCharging")

        val chargingType = when (batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC".also { Log.v(TAG, "Charging type: AC") }
            BatteryManager.BATTERY_PLUGGED_USB -> "USB".also { Log.v(TAG, "Charging type: USB") }
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless".also { Log.v(TAG, "Charging type: Wireless") }
            else -> "unknown".also { Log.v(TAG, "Charging type: unknown") }
        }

        val health = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_HEALTH,
            BatteryManager.BATTERY_HEALTH_UNKNOWN
        ) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        Log.v(TAG, "Battery health: $health")

        return BatteryInfo(
            level = level,
            temperature = temperature,
            voltage = voltage,
            isCharging = isCharging,
            chargingType = chargingType,
            health = health,
            capacity = capacity,
            currentNow = currentNow
        ).also { Log.d(TAG, "Collected battery info: $it") }
    }

    /**
     * Collects memory information
     */
    private fun collectMemoryInfo(): MemoryInfo {
        Log.d(TAG, "Collecting memory information")
        val activityManager = activityManager ?: run {
            Log.w(TAG, "Activity manager not available")
            return MemoryInfo(
                totalRam = -1,
                availableRam = -1,
                lowMemory = false,
                threshold = -1
            )
        }

        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        Log.v(TAG, "Memory info - Total: ${memoryInfo.totalMem}, Available: ${memoryInfo.availMem}, " +
                "Low memory: ${memoryInfo.lowMemory}, Threshold: ${memoryInfo.threshold}")

        return MemoryInfo(
            totalRam = memoryInfo.totalMem,
            availableRam = memoryInfo.availMem,
            lowMemory = memoryInfo.lowMemory,
            threshold = memoryInfo.threshold
        ).also { Log.d(TAG, "Collected memory info: $it") }
    }

    /**
     * Collects CPU information (simplified)
     */
    private fun collectCpuInfo(): CpuInfo {
        Log.d(TAG, "Collecting CPU information (simplified)")
        // Simplified CPU info as requested to focus on network and data
        return CpuInfo(
            usage = -1f,
            temperature = -1f,
            frequencies = emptyList()
        ).also { Log.d(TAG, "Collected CPU info: $it") }
    }

    /**
     * Collects network information including actual data usage statistics
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun collectNetworkInfo(startTime: Long, endTime: Long): NetworkInfo {
        Log.d(TAG, "Collecting network information")
        val connectivity = connectivityManager ?: run {
            Log.w(TAG, "Connectivity manager not available")
            return NetworkInfo(
                type = "unknown",
                strength = -1,
                isRoaming = false,
                dataUsage = DataUsage(0, 0, 0, 0)
            )
        }

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
        Log.i(TAG, "Network type: $type")

        // Get more detailed connection info
        val activeConnectionInfo = when (type) {
            "WiFi" -> {
                val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val connectionInfo = wifiManager?.connectionInfo
                val rawSsid = connectionInfo?.ssid ?: "unknown"
                val safeSsid = rawSsid.replace(Regex("[<>]"), "") // Remove < and >
                "SSID: $safeSsid".also {
                    Log.v(TAG, "WiFi connection info: $it")
                }
            }
            "Mobile" -> {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                "Operator: ${telephonyManager?.networkOperatorName ?: "unknown"}".also {
                    Log.v(TAG, "Mobile operator: $it")
                }
            }
            else -> "".also { Log.v(TAG, "No additional connection info for network type: $type") }
        }

        // Get link speed for WiFi
        val linkSpeed = if (type == "WiFi") {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiManager?.connectionInfo?.linkSpeed ?: -1
        } else {
            -1
        }
        if (linkSpeed != -1) Log.v(TAG, "Link speed: $linkSpeed Mbps")

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
                    }.also { Log.v(TAG, "Cellular generation: $it") }
                } catch (se: SecurityException) {
                    Log.e(TAG, "Security exception getting network type: ${se.message}")
                    "unknown"
                }
            } else {
                Log.d(TAG, "READ_PHONE_STATE permission not granted")
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
                        Log.e(TAG, "Security exception getting signal strength: ${se.message}")
                        -1
                    }
                } else {
                    Log.d(TAG, "ACCESS_FINE_LOCATION permission not granted")
                    -1
                }
            }
            else -> -1
        }
        Log.v(TAG, "Signal strength: $strength")

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
        ).also { Log.d(TAG, "Collected network info: $it") }
    }

    /**
     * Collects device-wide network data usage
     */
    private fun collectNetworkDataUsage(startTime: Long, endTime: Long): DataUsage {
        Log.d(TAG, "Collecting network data usage: start=$startTime, end=$endTime")
        var foregroundBytes = 0L
        var backgroundBytes = 0L
        var rxBytes = 0L
        var txBytes = 0L

        try {
            val networkStatsManager = networkStatsManager ?: run {
                Log.w(TAG, "Network stats manager not available")
                return DataUsage(0, 0, 0, 0)
            }

            // Try to get historical stats first
            if (context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                Log.v(TAG, "READ_PHONE_STATE permission granted, attempting to get historical stats")

                // Mobile usage
                val subscriberId = getSubscriberId()
                Log.v(TAG, "Subscriber ID: ${subscriberId ?: "null"}")

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
                Log.v(TAG, "Mobile stats: rx=$rxBytes, tx=$txBytes, fg=$foregroundBytes, bg=$backgroundBytes")

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
                Log.v(TAG, "WiFi stats: rx=$rxBytes, tx=$txBytes, fg=$foregroundBytes, bg=$backgroundBytes")
            } else {
                Log.d(TAG, "READ_PHONE_STATE permission not granted for historical stats")
            }

            // If we couldn't get historical stats, fallback to current session stats
            if (foregroundBytes == 0L && backgroundBytes == 0L) {
                Log.d(TAG, "Falling back to current session stats using TrafficStats")
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
                Log.v(TAG, "TrafficStats: totalRx=$totalRx, totalTx=$totalTx, mobileRx=$mobileRx, mobileTx=$mobileTx")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error collecting network data usage: ${e.message}", e)
        }

        return DataUsage(
            foreground = foregroundBytes,
            background = backgroundBytes,
            rxBytes = rxBytes,
            txBytes = txBytes
        ).also { Log.d(TAG, "Collected network data usage: $it") }
    }

    /**
     * Collects detailed information about all apps
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun collectAppsInfo(startTime: Long, endTime: Long): List<AppInfo> {
        Log.d(TAG, "Collecting apps information")
        if (!hasUsageStatsPermission()) {
            Log.w(TAG, "Usage stats permission not granted")
            return emptyList()
        }

        val usageStatsManager = usageStatsManager ?: run {
            Log.w(TAG, "Usage stats manager not available")
            return emptyList()
        }
        val packageManager = packageManager
        val activityManager = activityManager ?: run {
            Log.w(TAG, "Activity manager not available")
            return emptyList()
        }

        // Get running processes and memory info
        val runningAppProcesses = activityManager.runningAppProcesses ?: emptyList()
        Log.v(TAG, "Found ${runningAppProcesses.size} running processes")
        val memoryInfoMap = collectMemoryInfoPerApp(runningAppProcesses)

        // Get per-app network usage
        val networkUsageMap = collectNetworkUsagePerApp(startTime, endTime)
        Log.v(TAG, "Collected network usage for ${networkUsageMap.size} apps")

        // Get per-app battery usage (if possible)
        val batteryUsageMap = collectBatteryUsagePerApp()
        Log.v(TAG, "Collected battery usage for ${batteryUsageMap.size} apps")

        // Get app usage statistics
        val usageStatsMap = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )?.associateBy { it.packageName } ?: emptyMap()
        Log.v(TAG, "Collected usage stats for ${usageStatsMap.size} apps")

        // Collection of apps that are installed and potentially running
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        Log.i(TAG, "Found ${installedApps.size} installed apps")

        return installedApps.mapNotNull { appInfo ->
            try {
                val packageName = appInfo.packageName
                val usageStats = usageStatsMap[packageName]

                // Skip apps with no usage data if we're only interested in active apps
                if (usageStats == null && networkUsageMap[packageName] == null) {
                    Log.v(TAG, "Skipping app $packageName - no usage or network data")
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
                        (usageStats.totalTimeVisible - usageStats.totalTimeInForeground).coerceAtLeast(0)
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
                ).also { Log.v(TAG, "Collected info for app $packageName: $it") }
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting info for app ${appInfo.packageName}: ${e.message}")
                null
            }
        }.also { Log.i(TAG, "Successfully collected information for ${it.size} apps") }
    }

    /**
     * Collects memory usage per app
     */
    private fun collectMemoryInfoPerApp(processes: List<ActivityManager.RunningAppProcessInfo>): Map<String, Long> {
        Log.d(TAG, "Collecting memory info per app for ${processes.size} processes")
        val memoryMap = HashMap<String, Long>()
        val activityManager = activityManager ?: run {
            Log.w(TAG, "Activity manager not available")
            return emptyMap()
        }

        try {
            // Get PIDs for all running processes
            val pids = processes.map { it.pid }.toIntArray()
            if (pids.isEmpty()) {
                Log.d(TAG, "No running processes found")
                return emptyMap()
            }

            // Get memory information for these processes
            val memoryInfo = activityManager.getProcessMemoryInfo(pids)
            Log.v(TAG, "Got memory info for ${memoryInfo.size} processes")

            // Map process memory to package names
            processes.forEachIndexed { index, process ->
                if (index < memoryInfo.size) {
                    val info = memoryInfo[index]
                    // Total PSS is a good measure of app's memory footprint
                    val memoryUsage = info?.totalPss?.toLong() ?: 0L

                    // Associate memory with each package in this process
                    process.pkgList?.forEach { packageName ->
                        memoryMap[packageName] = memoryUsage
                        Log.v(TAG, "Memory usage for $packageName: ${memoryUsage}KB")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting memory info per app: ${e.message}", e)
        }

        Log.d(TAG, "Collected memory info for ${memoryMap.size} apps")
        return memoryMap
    }

    /**
     * Collects per-app network usage statistics with proper permission handling
     */
    private fun collectNetworkUsagePerApp(startTime: Long, endTime: Long): Map<String, DataUsage> {
        Log.d(TAG, "Collecting network usage per app")
        // First check if we have PACKAGE_USAGE_STATS permission
        if (!hasUsageStatsPermission()) {
            Log.w(TAG, "Usage stats permission not granted")
            return emptyMap()
        }

        // Check if we have network stats manager
        val networkStatsManager = networkStatsManager ?: run {
            Log.w(TAG, "Network stats manager not available, using TrafficStats fallback")
            return getNetworkUsageFromTrafficStats()
        }

        val networkUsageMap = HashMap<String, DataUsage>()

        val hasPhoneStatePermission = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        Log.v(TAG, "READ_PHONE_STATE permission granted: $hasPhoneStatePermission")

        try {
            val subscriberId = if (hasPhoneStatePermission) getSubscriberId() else null
            Log.v(TAG, "Subscriber ID: ${subscriberId ?: "null"}")

            // Get all installed apps with their UIDs
            val packageManager = packageManager
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

            for (app in installedApps) {
                var foregroundBytes = 0L
                var backgroundBytes = 0L
                var rxBytes = 0L
                var txBytes = 0L

                try {
                    // Mobile data usage - only if we have proper permissions
                    if (hasPhoneStatePermission && subscriberId != null) {
                        try {
                            Log.v(TAG, "Querying mobile data for ${app.packageName} (UID: ${app.uid})")
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
                            Log.v(TAG, "Mobile data for ${app.packageName}: rx=$rxBytes, tx=$txBytes")
                        } catch (se: SecurityException) {
                            Log.e(TAG, "Security exception getting mobile stats for ${app.packageName}: ${se.message}")
                        }
                    }

                    // WiFi data usage - less restrictive
                    try {
                        Log.v(TAG, "Querying WiFi data for ${app.packageName} (UID: ${app.uid})")
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
                        Log.v(TAG, "WiFi data for ${app.packageName}: rx=$rxBytes, tx=$txBytes")
                    } catch (se: SecurityException) {
                        Log.e(TAG, "Security exception getting WiFi stats for ${app.packageName}: ${se.message}")
                        // Try TrafficStats fallback for this app
                        tryTrafficStatsForApp(app.uid)?.let { trafficStats ->
                            rxBytes = trafficStats.first
                            txBytes = trafficStats.second

                            // Estimate foreground/background
                            foregroundBytes = (rxBytes + txBytes) * 7 / 10
                            backgroundBytes = (rxBytes + txBytes) - foregroundBytes
                            Log.v(TAG, "Using TrafficStats fallback for ${app.packageName}")
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error getting network stats for ${app.packageName}: ${e.message}")
                    // Try TrafficStats fallback for this app
                    tryTrafficStatsForApp(app.uid)?.let { trafficStats ->
                        rxBytes = trafficStats.first
                        txBytes = trafficStats.second

                        // Estimate foreground/background
                        foregroundBytes = (rxBytes + txBytes) * 7 / 10
                        backgroundBytes = (rxBytes + txBytes) - foregroundBytes
                        Log.v(TAG, "Using TrafficStats fallback for ${app.packageName}")
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
                    Log.v(TAG, "Network usage for ${app.packageName}: ${networkUsageMap[app.packageName]}")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error collecting network usage per app: ${e.message}", e)
            // Try fallback using TrafficStats only
            return getNetworkUsageFromTrafficStats()
        }

        Log.i(TAG, "Collected network usage for ${networkUsageMap.size} apps")
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
                Pair(rxBytes, txBytes).also {
                    Log.v(TAG, "TrafficStats for UID $uid: rx=${it.first}, tx=${it.second}")
                }
            } else {
                null.also { Log.v(TAG, "No traffic stats for UID $uid") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting traffic stats for UID $uid: ${e.message}")
            null
        }
    }

    /**
     * Fallback method to get current session network usage per app
     * Uses TrafficStats which only provides data since boot
     */
    private fun getNetworkUsageFromTrafficStats(): Map<String, DataUsage> {
        Log.d(TAG, "Getting network usage from TrafficStats (fallback)")
        val networkUsageMap = HashMap<String, DataUsage>()

        try {
            val packageManager = packageManager
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

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
                    Log.v(TAG, "TrafficStats for ${app.packageName}: ${networkUsageMap[app.packageName]}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network usage from TrafficStats: ${e.message}", e)
        }

        Log.i(TAG, "Collected network usage for ${networkUsageMap.size} apps using TrafficStats")
        return networkUsageMap
    }

    /**
     * Collects battery usage per app (if available)
     * Note: Battery usage is not always accessible on all devices
     */
    private fun collectBatteryUsagePerApp(): Map<String, Float> {
        Log.d(TAG, "Collecting battery usage per app")
        val batteryUsageMap = HashMap<String, Float>()

        // Multiple approaches for collecting battery usage

        // Approach 1: Using BatteryManager and UsageStatsManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val usm = usageStatsManager ?: run {
                    Log.w(TAG, "Usage stats manager not available")
                    return emptyMap()
                }
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
                Log.v(TAG, "Total screen time: $totalScreenTime ms")

                if (totalScreenTime > 0) {
                    // Simplistic approach: estimate battery usage based on screen time proportion
                    val batteryManager = batteryManager
                    val batteryLevel = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
                    val batteryConsumed = 100 - batteryLevel
                    Log.v(TAG, "Battery consumed: $batteryConsumed%")

                    for (stat in usageStats) {
                        if (stat.totalTimeInForeground > 0) {
                            val proportion = stat.totalTimeInForeground.toFloat() / totalScreenTime.toFloat()
                            // Rough estimation
                            batteryUsageMap[stat.packageName] = proportion * batteryConsumed
                            Log.v(TAG, "Estimated battery usage for ${stat.packageName}: ${batteryUsageMap[stat.packageName]}%")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in battery usage approach 1: ${e.message}")
        }

        // Approach 2: Using manufacturer-specific APIs (simplified example)
        if (batteryUsageMap.isEmpty()) {
            try {
                Log.v(TAG, "Trying manufacturer-specific battery usage APIs")
                // Some manufacturers provide battery stats through their own APIs
                // This is highly device-specific and would need custom implementations

                // Example for Samsung (pseudo-code)
                if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
                    Log.v(TAG, "Samsung device detected")
                    // Samsung specific implementation would go here
                }

                // Example for Huawei (pseudo-code)
                if (Build.MANUFACTURER.equals("huawei", ignoreCase = true)) {
                    Log.v(TAG, "Huawei device detected")
                    // Huawei specific implementation would go here
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in manufacturer-specific battery usage approach: ${e.message}")
            }
        }

        Log.d(TAG, "Collected battery usage for ${batteryUsageMap.size} apps")
        return batteryUsageMap
    }

    /**
     * Collects settings information
     */
    private fun collectSettingsInfo(): SettingsInfo {
        Log.d(TAG, "Collecting settings information")
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager

        val batteryOptimization = powerManager?.isIgnoringBatteryOptimizations(context.packageName)?.not() ?: false
        Log.v(TAG, "Battery optimization enabled: $batteryOptimization")

        val dataSaver = (context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
            ?.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        Log.v(TAG, "Data saver enabled: $dataSaver")

        val powerSaveMode = powerManager?.isPowerSaveMode ?: false
        Log.v(TAG, "Power save mode enabled: $powerSaveMode")

        val adaptiveBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Settings.Global.getInt(
                context.contentResolver,
                "adaptive_battery_management_enabled",
                0
            ) == 1
        } else false
        Log.v(TAG, "Adaptive battery enabled: $adaptiveBattery")

        val autoSync = ContentResolver.getMasterSyncAutomatically()
        Log.v(TAG, "Auto sync enabled: $autoSync")

        return SettingsInfo(
            batteryOptimization = batteryOptimization,
            dataSaver = dataSaver,
            powerSaveMode = powerSaveMode,
            adaptiveBattery = adaptiveBattery,
            autoSync = autoSync
        ).also { Log.d(TAG, "Collected settings info: $it") }
    }

    /**
     * Collects device information
     */
    private fun collectDeviceInfo(): DeviceInfo {
        Log.d(TAG, "Collecting device information")
        val screenOnTime = try {
            Settings.System.getLong(
                context.contentResolver,
                "screen_on_time",  // Use string literal instead of constant
                0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting screen on time: ${e.message}")
            0L
        }
        Log.v(TAG, "Screen on time: $screenOnTime ms")

        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            osVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            screenOnTime = screenOnTime
        ).also { Log.d(TAG, "Collected device info: $it") }
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
                    telephonyManager?.subscriberId.also {
                        Log.v(TAG, "Got subscriber ID: ${it ?: "null"}")
                    }
                } catch (se: SecurityException) {
                    // If it fails with SecurityException, we don't have the privileged permission
                    Log.e(TAG, "Security exception getting subscriber ID: ${se.message}")
                    null
                }
            } else {
                Log.d(TAG, "READ_PHONE_STATE permission not granted")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting subscriber ID: ${e.message}")
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
        val hasPermission = mode == AppOpsManager.MODE_ALLOWED
        Log.v(TAG, "Usage stats permission granted: $hasPermission")
        return hasPermission
    }

    /**
     * Gets the device ID for identifying this device
     */
    private fun getDeviceId(): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: UUID.randomUUID().toString().also {
                    Log.w(TAG, "Couldn't get ANDROID_ID, generating random UUID")
                }
        } catch (e: Exception) {
            UUID.randomUUID().toString().also {
                Log.e(TAG, "Error getting device ID: ${e.message}, generating random UUID")
            }
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
        Log.d(TAG, "Collecting network data usage with NetworkCapabilities")
        // First try using TrafficStats directly - this is the most reliable method that doesn't require special permissions
        val totalStats = collectBasicTrafficStats()
        Log.v(TAG, "Basic traffic stats: $totalStats")

        // If we have the networkStatsManager and proper permissions, try to get more detailed stats
        if (networkStatsManager != null) {
            try {
                val detailedStats = collectDetailedNetworkStats(startTime, endTime, networkType)
                Log.v(TAG, "Detailed network stats: $detailedStats")
                return detailedStats
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting detailed network stats: ${e.message}, falling back to basic stats")
            }
        } else {
            Log.w(TAG, "NetworkStatsManager not available, using basic stats")
        }

        return totalStats
    }

    /**
     * Collects basic traffic stats that are available without special permissions
     */
    private fun collectBasicTrafficStats(): DataUsage {
        Log.d(TAG, "Collecting basic traffic stats")
        val rxBytes = TrafficStats.getTotalRxBytes()
        val txBytes = TrafficStats.getTotalTxBytes()
        val mobileRxBytes = TrafficStats.getMobileRxBytes()
        val mobileTxBytes = TrafficStats.getMobileTxBytes()

        Log.v(TAG, "Total rx: $rxBytes, tx: $txBytes, mobile rx: $mobileRxBytes, tx: $mobileTxBytes")

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
        ).also { Log.d(TAG, "Collected basic traffic stats: $it") }
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
        Log.d(TAG, "Collecting detailed network stats")
        var foregroundBytes = 0L
        var backgroundBytes = 0L
        var rxBytes = 0L
        var txBytes = 0L

        // First check phone state permission
        val hasPhoneStatePermission = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        Log.v(TAG, "READ_PHONE_STATE permission granted: $hasPhoneStatePermission")

        val networkStatsManager = networkStatsManager ?: run {
            Log.w(TAG, "NetworkStatsManager not available")
            return DataUsage(0, 0, 0, 0)
        }

        try {
            // Mobile stats (requires READ_PHONE_STATE permission)
            if (hasPhoneStatePermission && networkType == "Mobile") {
                try {
                    val subscriberId = getSubscriberId()
                    if (subscriberId != null) {
                        Log.v(TAG, "Querying mobile network stats")
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
                        Log.v(TAG, "Mobile stats: rx=$rxBytes, tx=$txBytes, fg=$foregroundBytes, bg=$backgroundBytes")
                    } else {
                        Log.w(TAG, "Subscriber ID is null")
                    }
                } catch (se: SecurityException) {
                    Log.e(TAG, "Security exception getting mobile stats: ${se.message}")
                }
            }

            // WiFi stats (doesn't require special permissions)
            if (networkType == "WiFi" || networkType == "unknown") {
                try {
                    Log.v(TAG, "Querying WiFi network stats")
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
                    Log.v(TAG, "WiFi stats: rx=$rxBytes, tx=$txBytes, fg=$foregroundBytes, bg=$backgroundBytes")
                } catch (se: SecurityException) {
                    Log.e(TAG, "Security exception getting WiFi stats: ${se.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting detailed network stats: ${e.message}", e)
            // Fall back to basic stats if detailed collection fails
            return collectBasicTrafficStats()
        }

        // If we failed to collect any stats, fall back to basic stats
        if (foregroundBytes == 0L && backgroundBytes == 0L) {
            Log.w(TAG, "No stats collected, falling back to basic stats")
            return collectBasicTrafficStats()
        }

        return DataUsage(
            foreground = foregroundBytes,
            background = backgroundBytes,
            rxBytes = rxBytes,
            txBytes = txBytes
        ).also { Log.d(TAG, "Collected detailed network stats: $it") }
    }

    /**
     * Check if package is a popular pre-installed app that should be included despite being system app
     * Some pre-installed apps like YouTube, Gmail are useful to track even if they're system apps
     */
    private fun isPopularPreinstalledApp(packageName: String): Boolean {
        val popularPreinstalledApps = listOf(
            "com.google.android.youtube",
            "com.google.android.gm", // Gmail
            "com.google.android.apps.maps",
            "com.google.android.apps.photos",
            "com.android.chrome",
            "com.google.android.music",
            "com.google.android.videos"
        )
        return packageName in popularPreinstalledApps
    }
}