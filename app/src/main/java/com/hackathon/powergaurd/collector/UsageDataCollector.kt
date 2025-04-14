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
import android.os.PowerManager
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
import com.hackathon.powergaurd.data.model.SocketInfo
import com.hackathon.powergaurd.data.model.WakelockInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import android.app.AlarmManager
import android.os.SystemClock

/** Collector for gathering device usage data to be sent to the backend. */
@Singleton
class UsageDataCollector @Inject constructor(@ApplicationContext private val context: Context) {
    private val TAG = "UsageDataCollector"
    private val TIME_RANGE_MS = 10 * 24 * 60 * 60 * 1000L // 10 days

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
    private val powerManager by lazy {
        Log.v(TAG, "Initializing powerManager")
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }
    private val alarmManager by lazy {
        Log.v(TAG, "Initializing alarmManager")
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
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
        var batteryInfo = collectBatteryInfo()
        val memoryInfo = collectMemoryInfo()
        val cpuInfo = collectCpuInfo()
        
        // Retry battery info collection if we get default values
        if (batteryInfo.level <= 0 || batteryInfo.temperature <= 0f) {
            Log.w(TAG, "Got default battery values, retrying collection: level=${batteryInfo.level}, temp=${batteryInfo.temperature}")
            kotlinx.coroutines.delay(300) // Short delay before retry
            batteryInfo = collectBatteryInfo() // Try again
            Log.d(TAG, "After retry: battery level=${batteryInfo.level}, temp=${batteryInfo.temperature}")
        }
        
        // Collect network info with retry for better data
        var networkInfo = collectNetworkInfo(startTime, endTime)
        // Retry network collection if we get default values
        if (networkInfo.strength < 0) {
            Log.w(TAG, "Got default network values, retrying collection: strength=${networkInfo.strength}")
            kotlinx.coroutines.delay(300) // Short delay before retry
            networkInfo = collectNetworkInfo(startTime, endTime) // Try again
            Log.d(TAG, "After retry: network strength=${networkInfo.strength}")
        }
        
        val settingsInfo = collectSettingsInfo()
        val appsInfo = collectAppsInfo(startTime, endTime)
        
        val deviceData = DeviceData(
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
        )
        
        // Log detailed information about the collected data
        Log.i(TAG, "Data collection completed: ${deviceData.apps.size} apps included, battery=${deviceData.battery.level}%, network=${deviceData.network.type}")
        
        // Use our new PromptDebug to log detailed metrics for debugging and analysis
        Log.d(TAG, "Logging detailed metrics via PromptDebug")
        PromptDebug.logDeviceData(deviceData)
        
        // Log detailed info for the top 10 battery and data consumers
        Log.d(TAG, "Top 10 battery consumers:")
        deviceData.apps.sortedByDescending { 
            it.batteryUsage + (it.wakelockInfo.totalDurationMs / 60000f) 
        }.take(10).forEach { PromptDebug.logAppInfo(it) }
        
        Log.d(TAG, "Top 10 data consumers:")
        deviceData.apps.sortedByDescending { 
            it.dataUsage.rxBytes + it.dataUsage.txBytes 
        }.take(10).forEach { PromptDebug.logAppInfo(it) }
        
        Log.d(TAG, "Apps using doze mode data:")
        deviceData.apps.filter { it.dataUsage.dozeBytes > 0 }
            .sortedByDescending { it.dataUsage.dozeBytes }
            .forEach { PromptDebug.logAppInfo(it) }
        
        return deviceData
    }

    /**
     * Collects battery information
     */
    private fun collectBatteryInfo(): BatteryInfo {
        Log.d(TAG, "Collecting battery information")
        val bm = batteryManager ?: run {
            Log.w(TAG, "Battery manager not available")
            return BatteryInfo(
                level = 10, // Hardcoded to 10% for demo
                temperature = 0f,
                voltage = 0,
                isCharging = false,
                chargingType = "unknown",
                health = 0
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
            0
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
            0L
        }

        // Get actual battery level instead of hardcoded value
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)?.let { level ->
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level != -1 && scale != -1) {
                (level * 100) / scale
            } else {
                bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            }
        } ?: bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        Log.v(TAG, "Battery level: $level%")
        
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
                totalRam = 0,
                availableRam = 0,
                lowMemory = false,
                threshold = 0
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
            usage = 0f,
            temperature = 0f,
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
                strength = 0,
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

        // Get installed apps and filter out system apps early
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { shouldCollectAppData(it) }
        Log.i(TAG, "Found ${installedApps.size} non-system/popular apps out of ${packageManager.getInstalledApplications(PackageManager.GET_META_DATA).size} total installed apps")

        // Get running processes and memory info
        val runningAppProcesses = activityManager.runningAppProcesses ?: emptyList()
        Log.v(TAG, "Found ${runningAppProcesses.size} running processes")
        val memoryInfoMap = collectMemoryInfoPerApp(runningAppProcesses)

        // Get per-app network usage
        val networkUsageMap = collectNetworkUsagePerApp(startTime, endTime, installedApps)
        Log.v(TAG, "Collected network usage for ${networkUsageMap.size} apps")

        // Get per-app battery usage (if possible)
        val batteryUsageMap = collectBatteryUsagePerApp(installedApps)
        Log.v(TAG, "Collected battery usage for ${batteryUsageMap.size} apps")

        // Get app usage statistics
        val usageStatsMap = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )?.associateBy { it.packageName } ?: emptyMap()
        Log.v(TAG, "Collected usage stats for ${usageStatsMap.size} apps")

        // New metrics collection
        // Get per-app wakelock information
        val wakelockInfoMap = collectWakelockInfoPerApp(installedApps)
        Log.v(TAG, "Collected wakelock info for ${wakelockInfoMap.size} apps")

        // Get per-app doze mode data usage
        val dozeModeUsageMap = collectDozeUsagePerApp(startTime, endTime, installedApps)
        Log.v(TAG, "Collected doze mode usage for ${dozeModeUsageMap.size} apps")

        // Get per-app socket connection counts and durations
        val socketInfoMap = collectSocketInfoPerApp(installedApps)
        Log.v(TAG, "Collected socket connection info for ${socketInfoMap.size} apps")

        // Get per-app alarm manager wakeups
        val alarmWakeupsMap = collectAlarmManagerWakeupsPerApp(installedApps)
        Log.v(TAG, "Collected alarm wakeups for ${alarmWakeupsMap.size} apps")

        // Get per-app process priority changes
        val priorityChangesMap = collectProcessPriorityChangesPerApp(installedApps)
        Log.v(TAG, "Collected priority changes for ${priorityChangesMap.size} apps")

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

                // Get the standard data usage
                val dataUsage = networkUsageMap[packageName] ?: DataUsage(0, 0, 0, 0)
                
                // Get doze mode usage and combine with standard data usage if available
                val combinedDataUsage = if (dozeModeUsageMap.containsKey(packageName)) {
                    val dozeUsage = dozeModeUsageMap[packageName]
                    DataUsage(
                        foreground = dataUsage.foreground,
                        background = dataUsage.background,
                        rxBytes = dataUsage.rxBytes,
                        txBytes = dataUsage.txBytes,
                        dozeBytes = dozeUsage?.rxBytes?.plus(dozeUsage.txBytes) ?: 0L
                    )
                } else {
                    dataUsage
                }

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
                    batteryUsage = batteryUsageMap[packageName] ?: 0f,
                    dataUsage = combinedDataUsage,
                    memoryUsage = memoryInfoMap[packageName] ?: 0L,
                    cpuUsage = 0f, // We'll update this with detailed CPU usage if available
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
                    updatedTime = packageInfo.lastUpdateTime,
                    wakelockInfo = wakelockInfoMap[packageName] ?: WakelockInfo(),
                    socketConnections = socketInfoMap[packageName] ?: SocketInfo(), 
                    alarmWakeups = alarmWakeupsMap[packageName] ?: 0,
                    priorityChanges = priorityChangesMap[packageName] ?: 0
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
    private fun collectNetworkUsagePerApp(startTime: Long, endTime: Long, apps: List<ApplicationInfo>): Map<String, DataUsage> {
        Log.d(TAG, "Collecting network usage per app")
        // First check if we have PACKAGE_USAGE_STATS permission
        if (!hasUsageStatsPermission()) {
            Log.w(TAG, "Usage stats permission not granted")
            return emptyMap()
        }

        // Check if we have network stats manager
        val networkStatsManager = networkStatsManager ?: run {
            Log.w(TAG, "Network stats manager not available, using TrafficStats fallback")
            return getNetworkUsageFromTrafficStats(apps)
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

            // Use the filtered app list
            for (app in apps) {
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
            return getNetworkUsageFromTrafficStats(apps)
        }

        Log.i(TAG, "Collected network usage for ${networkUsageMap.size} apps")
        return networkUsageMap
    }

    /**
     * Fallback method to get current session network usage per app
     * Uses TrafficStats which only provides data since boot
     */
    private fun getNetworkUsageFromTrafficStats(apps: List<ApplicationInfo>): Map<String, DataUsage> {
        Log.d(TAG, "Getting network usage from TrafficStats (fallback)")
        val networkUsageMap = HashMap<String, DataUsage>()

        try {
            // Use the filtered app list
            for (app in apps) {
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
    private fun collectBatteryUsagePerApp(apps: List<ApplicationInfo>): Map<String, Float> {
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
                        // Only include stats for apps in our filtered list
                        if (apps.any { it.packageName == stat.packageName } && stat.totalTimeInForeground > 0) {
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
                // Use the filtered app list for any manufacturer-specific implementation
                val appPackageNames = apps.map { it.packageName }.toSet()
                
                // Some manufacturers provide battery stats through their own APIs
                // This is highly device-specific and would need custom implementations

                // Example for Samsung (pseudo-code)
                if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
                    Log.v(TAG, "Samsung device detected")
                    // Samsung specific implementation would go here
                    // Only include apps in our filtered list
                }

                // Example for Huawei (pseudo-code)
                if (Build.MANUFACTURER.equals("huawei", ignoreCase = true)) {
                    Log.v(TAG, "Huawei device detected")
                    // Huawei specific implementation would go here
                    // Only include apps in our filtered list
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

    /**
     * Collects detailed per-app CPU usage (including wakelock information)
     */
    private fun collectCpuUsagePerApp(): Map<String, CpuInfo> {
        Log.d(TAG, "Collecting CPU usage per app")
        val cpuUsageMap = HashMap<String, CpuInfo>()

        try {
            val packageManager = packageManager
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

            for (app in installedApps) {
                val cpuInfo = getCpuUsageForApp(app.packageName)
                if (cpuInfo != null) {
                    cpuUsageMap[app.packageName] = cpuInfo
                    Log.v(TAG, "CPU usage for ${app.packageName}: $cpuInfo")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting CPU usage per app: ${e.message}", e)
        }

        Log.i(TAG, "Collected CPU usage for ${cpuUsageMap.size} apps")
        return cpuUsageMap
    }

    /**
     * Helper method to get CPU usage for a specific app
     * Returns a CpuInfo object or null if stats aren't available
     */
    private fun getCpuUsageForApp(packageName: String): CpuInfo? {
        return try {
            val process = activityManager?.getRunningAppProcesses()?.find { it.processName == packageName }
            if (process != null) {
                val pid = process.pid
                val cpuUsage = getCpuUsageForPid(pid)
                val wakelockInfo = getWakelockInfoForPid(pid)
                CpuInfo(
                    usage = cpuUsage,
                    temperature = 0f, // We can't get per-app CPU temperature
                    wakelockInfo = wakelockInfo
                )
            } else {
                null.also { Log.v(TAG, "No CPU usage for $packageName") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting CPU usage for $packageName: ${e.message}")
            null
        }
    }

    /**
     * Helper method to get CPU usage for a specific PID
     * Returns a float value representing the CPU usage percentage
     */
    private fun getCpuUsageForPid(pid: Int): Float {
        return try {
            val procStatFile = File("/proc/$pid/stat")
            val reader = BufferedReader(FileReader(procStatFile))
            val processStats = reader.readLine().split(" ")
            reader.close()

            val utime = processStats[13].toLong()
            val stime = processStats[14].toLong()
            val cutime = processStats[15].toLong()
            val cstime = processStats[16].toLong()
            val startTime = processStats[21].toLong()
            val totalTime = (utime + stime + cutime + cstime) / 1000
            val elapsedTime = SystemClock.elapsedRealtime() - startTime

            val cpuUsage = (totalTime.toFloat() / elapsedTime.toFloat()) * 100
            Log.v(TAG, "CPU usage for PID $pid: $cpuUsage%")
            cpuUsage
        } catch (e: Exception) {
            Log.e(TAG, "Error getting CPU usage for PID $pid: ${e.message}")
            0f
        }
    }

    /**
     * Helper method to get wakelock info for a specific PID
     * Returns a WakelockInfo object or null if wakelock info isn't available
     */
    private fun getWakelockInfoForPid(pid: Int): WakelockInfo? {
        return try {
            val wakelockFile = File("/sys/kernel/debug/wakeup_sources")
            if (wakelockFile.exists()) {
                val reader = BufferedReader(FileReader(wakelockFile))
                var line: String?
                var acquireCount = 0
                var totalDurationMs = 0L
                val wakelockTypes = HashMap<String, Int>()
                
                while (reader.readLine().also { line = it } != null) {
                    if (line?.contains("pid:$pid") == true) {
                        val parts = line?.split("\\s+".toRegex())
                        if (parts != null && parts.size > 2) {
                            val name = parts[0]
                            val count = parts.find { it.startsWith("count=") }?.split("=")?.get(1)?.toIntOrNull() ?: 0
                            val duration = parts.find { it.startsWith("total_time=") }?.split("=")?.get(1)?.toLongOrNull() ?: 0L
                            
                            if (count > 0) {
                                wakelockTypes[name] = count
                                acquireCount += count
                                totalDurationMs += duration
                            }
                        }
                    }
                }
                reader.close()
                
                if (acquireCount > 0) {
                    WakelockInfo(
                        acquireCount = acquireCount,
                        totalDurationMs = totalDurationMs,
                        wakelockTypes = wakelockTypes
                    ).also {
                        Log.v(TAG, "Wakelock info for PID $pid: $it")
                    }
                } else {
                    null.also { Log.v(TAG, "No wakelock info for PID $pid") }
                }
            } else {
                null.also { Log.v(TAG, "No wakelock file for PID $pid") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting wakelock info for PID $pid: ${e.message}")
            null
        }
    }

    /**
     * Collects per-app data usage when device is in doze mode
     */
    private fun collectDozeUsagePerApp(startTime: Long, endTime: Long, apps: List<ApplicationInfo>): Map<String, DataUsage> {
        Log.d(TAG, "Collecting doze usage per app")
        val dozeUsageMap = HashMap<String, DataUsage>()

        try {
            // Use the filtered app list
            for (app in apps) {
                val dozeUsage = getDozeUsageForApp(app.uid, startTime, endTime)
                if (dozeUsage != null) {
                    dozeUsageMap[app.packageName] = dozeUsage
                    Log.v(TAG, "Doze usage for ${app.packageName}: $dozeUsage")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting doze usage per app: ${e.message}", e)
        }

        Log.i(TAG, "Collected doze usage for ${dozeUsageMap.size} apps")
        return dozeUsageMap
    }

    /**
     * Helper method to get doze usage for a specific app
     * Returns a DataUsage object or null if stats aren't available
     */
    private fun getDozeUsageForApp(uid: Int, startTime: Long, endTime: Long): DataUsage? {
        return try {
            val networkStatsManager = networkStatsManager ?: run {
                Log.w(TAG, "Network stats manager not available")
                return null
            }

            // Check if we have the PowerManager to detect doze mode
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val supportsDoze = powerManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            
            if (!supportsDoze) {
                Log.d(TAG, "Doze mode not supported on this device")
                return null
            }

            // For mobile data
            var mobileRxBytes = 0L
            var mobileTxBytes = 0L
            
            try {
                val mobileStat = networkStatsManager.queryDetailsForUid(
                    ConnectivityManager.TYPE_MOBILE,
                    getSubscriberId() ?: "",
                    startTime,
                    endTime,
                    uid
                )
                
                val mobileBucket = NetworkStats.Bucket()
                while (mobileStat.hasNextBucket()) {
                    mobileStat.getNextBucket(mobileBucket)
                    // Ideally we would check if the device was in doze mode during this bucket's time range
                    // but this information is not directly available through the API
                    // We'll use a heuristic assuming very small data transfers during inactive periods
                    // might indicate transfers during doze (not perfectly accurate)
                    if (mobileBucket.rxBytes + mobileBucket.txBytes < 5 * 1024) { // Less than 5KB
                        mobileRxBytes += mobileBucket.rxBytes
                        mobileTxBytes += mobileBucket.txBytes
                    }
                }
                mobileStat.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting mobile doze stats: ${e.message}")
            }
            
            // For WiFi data
            var wifiRxBytes = 0L
            var wifiTxBytes = 0L
            
            try {
                val wifiStat = networkStatsManager.queryDetailsForUid(
                    ConnectivityManager.TYPE_WIFI,
                    "",
                    startTime,
                    endTime,
                    uid
                )
                
                val wifiBucket = NetworkStats.Bucket()
                while (wifiStat.hasNextBucket()) {
                    wifiStat.getNextBucket(wifiBucket)
                    // Same heuristic for WiFi
                    if (wifiBucket.rxBytes + wifiBucket.txBytes < 5 * 1024) { // Less than 5KB
                        wifiRxBytes += wifiBucket.rxBytes
                        wifiTxBytes += wifiBucket.txBytes
                    }
                }
                wifiStat.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting WiFi doze stats: ${e.message}")
            }

            val rxBytes = mobileRxBytes + wifiRxBytes
            val txBytes = mobileTxBytes + wifiTxBytes
            
            DataUsage(
                foreground = 0L, // Doze mode data is never considered foreground
                background = rxBytes + txBytes,
                rxBytes = rxBytes,
                txBytes = txBytes
            ).also {
                Log.v(TAG, "Doze usage for UID $uid: $it")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting doze usage for UID $uid: ${e.message}")
            null
        }
    }

    /**
     * Collects per-app socket connection counts and durations
     */
    private fun collectSocketInfoPerApp(apps: List<ApplicationInfo>): Map<String, SocketInfo> {
        Log.d(TAG, "Collecting socket info per app")
        val socketInfoMap = HashMap<String, SocketInfo>()

        try {
            // Use the filtered app list
            for (app in apps) {
                val socketInfo = getSocketInfoForApp(app.packageName)
                if (socketInfo != null) {
                    socketInfoMap[app.packageName] = socketInfo
                    Log.v(TAG, "Socket info for ${app.packageName}: $socketInfo")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting socket info per app: ${e.message}", e)
        }

        Log.i(TAG, "Collected socket info for ${socketInfoMap.size} apps")
        return socketInfoMap
    }

    /**
     * Helper method to get socket info for a specific app
     * Returns a SocketInfo object or null if stats aren't available
     */
    private fun getSocketInfoForApp(packageName: String): SocketInfo? {
        return try {
            val process = activityManager?.getRunningAppProcesses()?.find { it.processName == packageName }
            if (process != null) {
                val pid = process.pid
                val socketInfo = getSocketInfoForPid(pid)
                socketInfo?.also {
                    Log.v(TAG, "Socket info for $packageName: $it")
                }
            } else {
                null.also { Log.v(TAG, "No socket info for $packageName") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting socket info for $packageName: ${e.message}")
            null
        }
    }

    /**
     * Helper method to get socket info for a specific PID
     * Returns a SocketInfo object or null if stats aren't available
     */
    private fun getSocketInfoForPid(pid: Int): SocketInfo? {
        return try {
            val socketDir = File("/proc/$pid/fd")
            if (socketDir.exists() && socketDir.canRead()) {
                // Safe handling of file listing
                val socketFiles = try {
                    socketDir.listFiles { file -> 
                        try {
                            val path = file.canonicalPath
                            path.contains("socket:") || path.contains("sock")
                        } catch (e: Exception) {
                            false
                        }
                    } ?: emptyArray()
                } catch (e: Exception) {
                    Log.e(TAG, "Error listing socket files for PID $pid: ${e.message}")
                    emptyArray()
                }
                
                // Count TCP and UDP connections safely
                val tcpSocketsFile = File("/proc/$pid/net/tcp")
                val udpSocketsFile = File("/proc/$pid/net/udp")
                
                var tcpConnections = 0
                var udpConnections = 0
                
                if (tcpSocketsFile.exists() && tcpSocketsFile.canRead()) {
                    try {
                        val reader = BufferedReader(FileReader(tcpSocketsFile))
                        // Skip header line
                        reader.readLine()
                        while (reader.readLine() != null) {
                            tcpConnections++
                        }
                        reader.close()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading TCP connections for PID $pid: ${e.message}")
                    }
                }
                
                if (udpSocketsFile.exists() && udpSocketsFile.canRead()) {
                    try {
                        val reader = BufferedReader(FileReader(udpSocketsFile))
                        // Skip header line
                        reader.readLine()
                        while (reader.readLine() != null) {
                            udpConnections++
                        }
                        reader.close()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading UDP connections for PID $pid: ${e.message}")
                    }
                }
                
                // For socket duration, we need to estimate since there's no easy way
                // to determine this from the proc filesystem without root access
                // We'll use a rough estimate based on process uptime
                val uptimeFile = File("/proc/$pid/stat")
                var totalDurationMs = 0L
                if (uptimeFile.exists() && uptimeFile.canRead()) {
                    try {
                        val reader = BufferedReader(FileReader(uptimeFile))
                        val stats = reader.readLine()?.split(" ") ?: emptyList()
                        reader.close()
                        
                        // Get process start time (in clock ticks since system boot)
                        val startTimeTicks = stats.getOrNull(21)?.toLongOrNull() ?: 0L
                        if (startTimeTicks > 0) {
                            val clockTicksPerSec = 100 // Standard on most Linux systems
                            val startTimeSecs = startTimeTicks / clockTicksPerSec
                            val uptimeSecs = SystemClock.elapsedRealtime() / 1000
                            // Average socket duration - assuming sockets last ~1/3 of process lifetime
                            totalDurationMs = (uptimeSecs - startTimeSecs) * 1000 / 3
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading process stats for PID $pid: ${e.message}")
                    }
                }
                
                return SocketInfo(
                    totalConnections = socketFiles.size,
                    activeConnections = tcpConnections + udpConnections,
                    totalDurationMs = totalDurationMs,
                    tcpConnections = tcpConnections,
                    udpConnections = udpConnections
                ).also {
                    Log.v(TAG, "Socket info for PID $pid: $it")
                }
            } else {
                null.also { Log.v(TAG, "Socket directory not accessible for PID $pid") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting socket info for PID $pid: ${e.message}")
            null
        }
    }

    /**
     * Collects AlarmManager wakeups per app
     */
    private fun collectAlarmManagerWakeupsPerApp(apps: List<ApplicationInfo>): Map<String, Int> {
        Log.d(TAG, "Collecting AlarmManager wakeups per app")
        val alarmManagerWakeupsMap = HashMap<String, Int>()

        try {
            val alarmManager = alarmManager ?: run {
                Log.w(TAG, "AlarmManager not available")
                return emptyMap()
            }

            // Use the filtered app list
            for (app in apps) {
                val wakeups = getAlarmManagerWakeupsForApp(app.packageName)
                if (wakeups > 0) {
                    alarmManagerWakeupsMap[app.packageName] = wakeups
                    Log.v(TAG, "AlarmManager wakeups for ${app.packageName}: $wakeups")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting AlarmManager wakeups per app: ${e.message}", e)
        }

        Log.i(TAG, "Collected AlarmManager wakeups for ${alarmManagerWakeupsMap.size} apps")
        return alarmManagerWakeupsMap
    }

    /**
     * Helper method to get AlarmManager wakeups for a specific app
     * Returns an int value representing the number of wakeups
     */
    private fun getAlarmManagerWakeupsForApp(packageName: String): Int {
        return try {
            val alarmManager = alarmManager ?: run {
                Log.w(TAG, "AlarmManager not available")
                return 0
            }

            val intentFilter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
            }

            val intent = Intent().apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                setPackage(packageName)
            }

            // Query for registered receivers with proper API based on SDK version
            val receivers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val flags = PackageManager.ResolveInfoFlags.of(PackageManager.GET_RECEIVERS.toLong())
                context.packageManager.queryBroadcastReceivers(intent, flags)
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryBroadcastReceivers(intent, PackageManager.GET_RECEIVERS)
            }
            
            var wakeups = 0
            for (receiver in receivers) {
                try {
                    // Count only receivers that are enabled and exported
                    if (receiver.activityInfo != null && receiver.activityInfo.enabled) {
                        wakeups++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking receiver for $packageName: ${e.message}")
                }
            }

            wakeups
        } catch (e: Exception) {
            Log.e(TAG, "Error getting AlarmManager wakeups for $packageName: ${e.message}")
            0
        }
    }

    /**
     * Collects process priority changes
     */
    private fun collectProcessPriorityChangesPerApp(apps: List<ApplicationInfo>): Map<String, Int> {
        Log.d(TAG, "Collecting process priority changes")
        val priorityChangesMap = HashMap<String, Int>()

        try {
            // Use the filtered app list
            for (app in apps) {
                val priorityChanges = getProcessPriorityChangesForApp(app.packageName)
                if (priorityChanges > 0) {
                    priorityChangesMap[app.packageName] = priorityChanges
                    Log.v(TAG, "Process priority changes for ${app.packageName}: $priorityChanges")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting process priority changes: ${e.message}", e)
        }

        Log.i(TAG, "Collected process priority changes for ${priorityChangesMap.size} apps")
        return priorityChangesMap
    }

    /**
     * Helper method to get process priority changes for a specific app
     * Returns an int value representing the number of priority changes
     */
    private fun getProcessPriorityChangesForApp(packageName: String): Int {
        return try {
            val process = activityManager?.getRunningAppProcesses()?.find { it.processName == packageName }
            if (process != null) {
                val pid = process.pid
                val priorityChanges = getProcessPriorityChangesForPid(pid)
                priorityChanges
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting process priority changes for $packageName: ${e.message}")
            0
        }
    }

    /**
     * Helper method to get process priority changes for a specific PID
     * Returns an int value representing the number of priority changes
     */
    private fun getProcessPriorityChangesForPid(pid: Int): Int {
        return try {
            val procStatFile = File("/proc/$pid/stat")
            val reader = BufferedReader(FileReader(procStatFile))
            val processStats = reader.readLine().split(" ")
            reader.close()

            val priority = processStats[17].toInt()
            val nice = processStats[18].toInt()

            priority - nice
        } catch (e: Exception) {
            Log.e(TAG, "Error getting process priority changes for PID $pid: ${e.message}")
            0
        }
    }

    /**
     * Collects wakelock information per app
     */
    private fun collectWakelockInfoPerApp(apps: List<ApplicationInfo>): Map<String, WakelockInfo> {
        Log.d(TAG, "Collecting wakelock info per app")
        val wakelockInfoMap = HashMap<String, WakelockInfo>()

        try {
            // Try to get permission to read wakelock stats
            val hasPermission = context.checkSelfPermission(
                Manifest.permission.DUMP
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!hasPermission) {
                Log.w(TAG, "DUMP permission not granted for wakelock collection")
                return emptyMap()
            }

            // For devices with proper permissions, we can get detailed wakelock information
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Use the filtered app list
                for (app in apps) {
                    try {
                        val processName = app.processName ?: app.packageName
                        val pid = getProcessId(processName)
                        
                        if (pid > 0) {
                            // Read wakelock info from proc filesystem
                            val wakelockFile = File("/proc/$pid/wakelock")
                            if (wakelockFile.exists() && wakelockFile.canRead()) {
                                val reader = BufferedReader(FileReader(wakelockFile))
                                var line: String?
                                val wakelockTypes = HashMap<String, Int>()
                                var acquireCount = 0
                                var totalDurationMs = 0L
                                
                                while (reader.readLine().also { line = it } != null) {
                                    // Parse wakelock information from line
                                    // Format is usually: name count time
                                    val parts = line?.split("\\s+".toRegex())
                                    if (parts != null && parts.size >= 3) {
                                        val name = parts[0]
                                        val count = parts[1].toIntOrNull() ?: 0
                                        val time = parts[2].toLongOrNull() ?: 0L
                                        
                                        if (count > 0) {
                                            wakelockTypes[name] = count
                                            acquireCount += count
                                            totalDurationMs += time
                                        }
                                    }
                                }
                                reader.close()
                                
                                if (acquireCount > 0) {
                                    wakelockInfoMap[app.packageName] = WakelockInfo(
                                        acquireCount = acquireCount,
                                        totalDurationMs = totalDurationMs,
                                        wakelockTypes = wakelockTypes
                                    )
                                    Log.v(TAG, "Wakelock info for ${app.packageName}: ${wakelockInfoMap[app.packageName]}")
                                }
                            } else {
                                // Alternative approach using PowerManager API
                                // This requires DEVICE_POWER permission which normal apps don't have
                                // Implemented just in case the app runs with elevated permissions
                                if (context.checkSelfPermission(
                                        "android.permission.DEVICE_POWER"
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                                    
                                    // Using reflection to access hidden APIs
                                    val getWakeLockStatsMethod = PowerManager::class.java.getDeclaredMethod("getWakeLockStats")
                                    getWakeLockStatsMethod.isAccessible = true
                                    
                                    val stats = getWakeLockStatsMethod.invoke(powerManager) as? Map<*, *>
                                    if (stats != null) {
                                        var totalCount = 0
                                        var totalDuration = 0L
                                        val wakelockTypes = HashMap<String, Int>()
                                        
                                        for ((key, value) in stats) {
                                            if (key is String && key.contains(app.packageName)) {
                                                val countMethod = value!!.javaClass.getDeclaredMethod("getCount")
                                                val timeMethod = value.javaClass.getDeclaredMethod("getTotalTime")
                                                countMethod.isAccessible = true
                                                timeMethod.isAccessible = true
                                                
                                                val count = countMethod.invoke(value) as Int
                                                val time = timeMethod.invoke(value) as Long
                                                
                                                val wakelockName = getWakelockNameFromKey(key)
                                                wakelockTypes[wakelockName] = count
                                                totalCount += count
                                                totalDuration += time
                                            }
                                        }
                                        
                                        if (totalCount > 0) {
                                            wakelockInfoMap[app.packageName] = WakelockInfo(
                                                acquireCount = totalCount,
                                                totalDurationMs = totalDuration,
                                                wakelockTypes = wakelockTypes
                                            )
                                            Log.v(TAG, "Wakelock info for ${app.packageName} (alternative): ${wakelockInfoMap[app.packageName]}")
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error collecting wakelock info for ${app.packageName}: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting wakelock info per app: ${e.message}", e)
        }

        Log.i(TAG, "Collected wakelock info for ${wakelockInfoMap.size} apps")
        return wakelockInfoMap
    }
    
    /**
     * Helper method to get the process ID for a given process name
     */
    private fun getProcessId(processName: String): Int {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningApps = am.runningAppProcesses ?: return -1
            
            for (processInfo in runningApps) {
                if (processInfo.processName == processName) {
                    return processInfo.pid
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting process ID for $processName: ${e.message}")
        }
        return -1
    }

    // Fix the regex for splitting the wakelock name
    private fun getWakelockNameFromKey(key: String): String {
        return try {
            if (key.contains("*")) {
                key.split("\\*".toRegex())[1]
            } else {
                key
            }
        } catch (e: Exception) {
            key
        }
    }

    // Extension function to check if a file is a socket
    private fun File.isSocket(): Boolean {
        return try {
            this.canonicalPath.contains("socket:") || this.canonicalPath.contains("sock")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Helper method to determine if we should include this app in data collection
     * Returns true for non-system apps and popular pre-installed apps
     */
    private fun shouldCollectAppData(appInfo: ApplicationInfo): Boolean {
        val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        
        // If it's not a system app, we definitely want to include it
        if (!isSystemApp) {
            return true
        }
        
        // If it is a system app, check if it's a popular pre-installed app
        return isPopularPreinstalledApp(appInfo.packageName)
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
}

/**
 * Debug utility class for logging detailed information for prompt generation
 */
private object PromptDebug {
    private const val TAG = "PowerGuard-PromptDebug"

    fun logAppInfo(app: AppInfo) {
        Log.d(TAG, "App: ${app.appName} (${app.packageName})")
        Log.d(TAG, "  System app: ${app.isSystemApp}")
        Log.d(TAG, "  Battery usage: ${app.batteryUsage}%")
        Log.d(TAG, "  CPU usage: ${app.cpuUsage}%")
        Log.d(TAG, "  Memory: ${formatBytes(app.memoryUsage)}")
        Log.d(TAG, "  Foreground time: ${formatDuration(app.foregroundTime)}")
        Log.d(TAG, "  Background time: ${formatDuration(app.backgroundTime)}")
        
        // Log the new metrics we added
        Log.d(TAG, "  -- Additional Metrics --")
        
        // Wakelock info
        Log.d(TAG, "  Wakelocks: ${app.wakelockInfo.acquireCount} (${formatDuration(app.wakelockInfo.totalDurationMs)})")
        if (app.wakelockInfo.wakelockTypes.isNotEmpty()) {
            Log.d(TAG, "  Wakelock types: ${app.wakelockInfo.wakelockTypes.entries.joinToString { "${it.key}=${it.value}" }}")
        }
        
        // Socket connections
        Log.d(TAG, "  Socket connections: total=${app.socketConnections.totalConnections}, active=${app.socketConnections.activeConnections}")
        Log.d(TAG, "  Socket types: TCP=${app.socketConnections.tcpConnections}, UDP=${app.socketConnections.udpConnections}")
        Log.d(TAG, "  Socket duration: ${formatDuration(app.socketConnections.totalDurationMs)}")
        
        // Alarm wakeups
        Log.d(TAG, "  Alarm wakeups: ${app.alarmWakeups}")
        
        // Process priority changes
        Log.d(TAG, "  Priority changes: ${app.priorityChanges}")
        
        // Data usage with doze
        val dataUsage = app.dataUsage
        Log.d(TAG, "  Data usage: ${formatBytes(dataUsage.rxBytes + dataUsage.txBytes)}")
        Log.d(TAG, "  Foreground data: ${formatBytes(dataUsage.foreground)}")
        Log.d(TAG, "  Background data: ${formatBytes(dataUsage.background)}")
        Log.d(TAG, "  Doze mode data: ${formatBytes(dataUsage.dozeBytes)}")
    }

    fun logDeviceData(data: DeviceData) {
        Log.d(TAG, "==== Device Data Summary ====")
        Log.d(TAG, "Device: ${data.deviceInfo.manufacturer} ${data.deviceInfo.model}")
        Log.d(TAG, "OS: Android ${data.deviceInfo.osVersion} (SDK ${data.deviceInfo.sdkVersion})")
        Log.d(TAG, "Battery: ${data.battery.level}% (${data.battery.temperature}°C)")
        
        // Count apps with significant metrics
        val appsWithWakelocks = data.apps.count { it.wakelockInfo.acquireCount > 0 }
        val appsWithDozeData = data.apps.count { it.dataUsage.dozeBytes > 0 }
        val appsWithSockets = data.apps.count { it.socketConnections.totalConnections > 0 }
        val appsWithAlarms = data.apps.count { it.alarmWakeups > 0 }
        
        Log.d(TAG, "Apps with wakelocks: $appsWithWakelocks")
        Log.d(TAG, "Apps using data in doze: $appsWithDozeData")
        Log.d(TAG, "Apps with socket connections: $appsWithSockets")
        Log.d(TAG, "Apps with alarm wakeups: $appsWithAlarms")
        
        // Memory stats
        val memoryUsage = (data.memory.totalRam - data.memory.availableRam).toFloat() / data.memory.totalRam * 100
        Log.d(TAG, "Memory: ${formatBytes(data.memory.totalRam - data.memory.availableRam)} of ${formatBytes(data.memory.totalRam)} (${memoryUsage.toInt()}%)")
        
        // Top battery drainers based on combined metrics
        data.apps.sortedByDescending { 
            it.batteryUsage + (it.wakelockInfo.totalDurationMs / 60000f) + (it.alarmWakeups * 0.5f)
        }.take(5).forEach { app ->
            Log.d(TAG, "Battery drainer: ${app.appName} - ${app.batteryUsage}%, wakelocks=${app.wakelockInfo.acquireCount}, alarms=${app.alarmWakeups}")
        }
        
        // Top data users during doze
        data.apps.sortedByDescending { it.dataUsage.dozeBytes }.take(5).forEach { app ->
            if (app.dataUsage.dozeBytes > 0) {
                Log.d(TAG, "Doze data user: ${app.appName} - ${formatBytes(app.dataUsage.dozeBytes)}")
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }
}