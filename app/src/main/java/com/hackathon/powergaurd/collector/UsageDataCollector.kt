package com.hackathon.powergaurd.collector

import PromptDebug
import android.Manifest
import android.app.ActivityManager
import android.app.AlarmManager
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
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
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
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

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
    private val alarmManager by lazy {
        Log.v(TAG, "Initializing alarmManager")
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    }

    /**
     * Main entry point - collects all device data
     */
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
            it.batteryUsage
        }.take(10).forEach { PromptDebug.logAppInfo(it) }
        
        Log.d(TAG, "Top 10 data consumers:")
        deviceData.apps.sortedByDescending { 
            it.dataUsage.rxBytes + it.dataUsage.txBytes 
        }.take(10).forEach { PromptDebug.logAppInfo(it) }

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
     * Collects CPU information using Android system APIs
     */
    private fun collectCpuInfo(): CpuInfo {
        Log.d(TAG, "Collecting CPU information (using system APIs)")
        
        var cpuUsage = 0f
        var cpuTemp = 0f
        val frequencies = mutableListOf<Long>()
        
        try {
            // Use ActivityManager to get memory info which indirectly relates to CPU pressure
            val am = activityManager
            if (am != null) {
                // Get device CPU core count
                val cpuCores = Runtime.getRuntime().availableProcessors()
                Log.d(TAG, "Device has $cpuCores CPU cores")
                
                // Get current memory usage as an indirect CPU pressure indicator
                val memInfo = ActivityManager.MemoryInfo()
                am.getMemoryInfo(memInfo)
                
                // Calculate pressure based on available memory (less memory = higher CPU usage generally)
                val memoryPressure = 1f - (memInfo.availMem.toFloat() / memInfo.totalMem.toFloat())
                
                // Get device load
                val loadFactor = getSystemLoadFactor()
                
                // Calculate estimated CPU usage using a combination of factors
                cpuUsage = (memoryPressure * 50f + loadFactor * 50f).coerceIn(0f, 100f)
                Log.d(TAG, "Estimated CPU usage: $cpuUsage% (memory pressure: ${memoryPressure * 100}%, load factor: $loadFactor)")
            }
            
            // Try to read CPU temperature from thermal zones
            try {
                // This is a safer method to access thermal info which is usually less restricted
                val thermalService = context.getSystemService("thermal") // Use string literal instead of Context.THERMAL_SERVICE
                if (thermalService != null) {
                    // On Android 10+, try to use ThermalService API
                    try {
                        val getCurrentTempMethod = thermalService.javaClass.getMethod("getCurrentTemperatures")
                        val temperatures = getCurrentTempMethod.invoke(thermalService) as? Array<*>

                        if (temperatures != null && temperatures.isNotEmpty()) {
                            // Look for CPU temp in the thermal sensors
                            for (temp in temperatures) {
                                val sensorType = temp?.javaClass?.getMethod("getType")?.invoke(temp) as? Int
                                val sensorTemp = temp?.javaClass?.getMethod("getValue")?.invoke(temp) as? Float

                                // Type 3 is typically CPU in THERMAL_STATUS_*
                                if (sensorType == 3 && sensorTemp != null) {
                                    cpuTemp = sensorTemp
                                    Log.d(TAG, "Found CPU temperature from thermal service: $cpuTemp°C")
                                    break
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error using thermal service API: ${e.message}")
                    }
                }

                // If still no temperature, try the battery temp as a fallback
                if (cpuTemp <= 0f) {
                    val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val temp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
                    cpuTemp = temp / 10f
                    Log.d(TAG, "Using battery temperature as CPU proxy: $cpuTemp°C")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading thermal info: ${e.message}")
            }
            
            // Try to estimate frequencies based on device model and perf level
            try {
                // Use CPU cores to generate reasonable frequency estimates
                val cores = Runtime.getRuntime().availableProcessors()
                val devicePerformanceClass = getDevicePerformanceClass()
                
                // Base frequencies on device performance class
                val baseLowFreq = when (devicePerformanceClass) {
                    1 -> 1200L  // Low-end
                    2 -> 1600L  // Mid-range
                    3 -> 1800L  // High-end
                    else -> 1500L // Default
                }
                
                val baseHighFreq = when (devicePerformanceClass) {
                    1 -> 1800L  // Low-end
                    2 -> 2200L  // Mid-range
                    3 -> 2700L  // High-end
                    else -> 2000L // Default
                }
                
                // Generate synthetic frequencies for each core
                for (i in 0 until cores) {
                    // Create variation between cores - some at high freq, some at low
                    val randomFactor = 0.8f + (Math.random() * 0.4f).toFloat()
                    val isBigCore = i < cores / 3  // First third are "big" cores in big.LITTLE
                    
                    val freq = if (isBigCore) {
                        (baseHighFreq * randomFactor).toLong()
                    } else {
                        (baseLowFreq * randomFactor).toLong()
                    }
                    
                    frequencies.add(freq)
                }
                
                Log.d(TAG, "Estimated CPU frequencies (MHz): $frequencies")
            } catch (e: Exception) {
                Log.e(TAG, "Error estimating CPU frequencies: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting CPU info: ${e.message}")
        }
        
        return CpuInfo(
            usage = cpuUsage,
            temperature = cpuTemp,
            frequencies = frequencies
        ).also { Log.d(TAG, "Collected CPU info: $it") }
    }
    
    /**
     * Get a system load factor based on running processes
     */
    private fun getSystemLoadFactor(): Float {
        try {
            val am = activityManager ?: return 0.5f
            
            // Count running processes and their importance
            val processes = am.runningAppProcesses ?: return 0.5f
            
            var foregroundCount = 0
            var visibleCount = 0
            var backgroundCount = 0
            
            for (process in processes) {
                when (process.importance) {
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> foregroundCount++
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> visibleCount++
                    else -> backgroundCount++
                }
            }
            
            // Calculate load based on active processes (foreground has higher weight)
            val totalProcessLoad = foregroundCount * 3f + visibleCount * 1.5f + backgroundCount * 0.5f
            val loadFactor = (totalProcessLoad / processes.size).coerceIn(0f, 1f)
            
            Log.d(TAG, "System load: $loadFactor (fg: $foregroundCount, vis: $visibleCount, bg: $backgroundCount)")
            return loadFactor
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating system load: ${e.message}")
            return 0.5f
        }
    }
    
    /**
     * Estimate device performance class based on device specs
     * Returns: 1 (low-end), 2 (mid-range), 3 (high-end)
     */
    private fun getDevicePerformanceClass(): Int {
        val totalRam = activityManager?.memoryClass ?: 0
        val cpuCores = Runtime.getRuntime().availableProcessors()
        
        // Classify based on RAM and CPU cores
        return when {
            totalRam >= 8 && cpuCores >= 8 -> 3  // High-end
            totalRam >= 4 && cpuCores >= 6 -> 2  // Mid-range
            else -> 1  // Low-end
        }
    }

    /**
     * Collects network information including actual data usage statistics
     */
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
                            it.cellSignalStrengths.firstOrNull()?.level ?: -1
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
     * Collects detailed information about all apps
     */
    private fun collectAppsInfo(startTime: Long, endTime: Long): List<AppInfo> {
        Log.d(TAG, "Collecting app information")
        // Get the list of installed applications
        val packageManager = packageManager
        val totalApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        Log.v(TAG, "Found ${totalApps.size} total installed apps")

        // Filter out system apps (except popular ones like YouTube, Chrome)
        val installedApps = totalApps.filter { appInfo ->
            // Include if it's not a system app OR it's a popular pre-installed app we want to track
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || isPopularPreinstalledApp(appInfo.packageName)
        }
        
        Log.i(TAG, "Found ${installedApps.size} non-system/popular apps out of ${totalApps} total installed apps")

        // Get running processes and memory info
        val runningAppProcesses = activityManager?.runningAppProcesses ?: emptyList()
        Log.v(TAG, "Found ${runningAppProcesses.size} running processes")
        val memoryInfoMap = collectMemoryInfoPerApp(runningAppProcesses)

        // Get per-app network usage
        val networkUsageMap = collectNetworkUsagePerApp(startTime, endTime, installedApps)
        Log.v(TAG, "Collected network usage for ${networkUsageMap.size} apps")

        // Get per-app battery usage (if possible)
        val batteryUsageMap = collectBatteryUsagePerApp(installedApps)
        Log.v(TAG, "Collected battery usage for ${batteryUsageMap.size} apps")

        // Get app usage statistics
        val usageStatsMap = usageStatsManager?.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )?.associateBy { it.packageName } ?: emptyMap()
        Log.v(TAG, "Collected usage stats for ${usageStatsMap.size} apps")

        // Get per-app alarm manager wakeups
        val alarmWakeupsMap = collectAlarmManagerWakeupsPerApp(installedApps)
        Log.v(TAG, "Collected alarm wakeups for ${alarmWakeupsMap.size} apps")

        // Get per-app process priority changes
        val priorityChangesMap = collectProcessPriorityChangesPerApp(installedApps)
        Log.v(TAG, "Collected priority changes for ${priorityChangesMap.size} apps")

        // Get per-app CPU usage
        val cpuUsageMap = collectCpuUsagePerApp()
        Log.v(TAG, "Collected CPU usage for ${cpuUsageMap.size} apps")

        // Get app standby buckets
        val bucketMap = HashMap<String, String>()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
        if (usm != null && hasUsageStatsPermission()) {
            for (app in installedApps) {
                try {
                    @Suppress("DEPRECATION")
                    val bucketValue = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        // Use reflection to access the method since it's hidden in SDK
                        val method = usm.javaClass.getMethod("getAppStandbyBucket", String::class.java)
                        method.invoke(usm, app.packageName) as Int
                    } else {
                        UsageStatsManager.STANDBY_BUCKET_ACTIVE // Default for older versions
                    }

                    val bucketString = when (bucketValue) {
                        UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "ACTIVE"
                        UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "WORKING_SET"
                        UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "FREQUENT"
                        UsageStatsManager.STANDBY_BUCKET_RARE -> "RARE"
                        UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "RESTRICTED"
                        50 -> "NEVER" // This is the value for STANDBY_BUCKET_NEVER
                        else -> "UNKNOWN"
                    }
                    bucketMap[app.packageName] = bucketString
                    Log.v(TAG, "App standby bucket for ${app.packageName}: $bucketString (value: $bucketValue)")
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting standby bucket for ${app.packageName}: ${e.message}")
                    bucketMap[app.packageName] = "UNKNOWN"
                }
            }
        }
        Log.v(TAG, "Collected standby bucket information for ${bucketMap.size} apps")

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
                val packageInfo =
                    packageManager.getPackageInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)

                // Get the standard data usage
                val dataUsage = networkUsageMap[packageName] ?: DataUsage(0, 0, 0, 0)

                AppInfo(
                    packageName = packageName,
                    processName = runningAppProcesses.find { it.pkgList.contains(packageName) }?.processName
                        ?: packageName,
                    appName = packageManager.getApplicationLabel(appInfo).toString(),
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    lastUsed = usageStats?.lastTimeUsed ?: 0L,
                    foregroundTime = usageStats?.totalTimeInForeground ?: 0L,
                    backgroundTime = if (true && usageStats != null) {
                        (usageStats.totalTimeVisible - usageStats.totalTimeInForeground).coerceAtLeast(0)
                    } else 0L,
                    batteryUsage = batteryUsageMap[packageName] ?: 0f,
                    dataUsage = dataUsage,
                    memoryUsage = memoryInfoMap[packageName] ?: 0L,
                    cpuUsage = cpuUsageMap[packageName] ?: 0f,
                    notifications = 0,
                    crashes = 0,
                    versionName = packageInfo.versionName ?: "",
                    versionCode = packageInfo.longVersionCode,
                    targetSdkVersion = appInfo.targetSdkVersion,
                    installTime = packageInfo.firstInstallTime,
                    updatedTime = packageInfo.lastUpdateTime,
                    alarmWakeups = alarmWakeupsMap[packageName] ?: 0,
                    currentPriority = priorityChangesMap[packageName] ?: "Not Running",
                    bucket = bucketMap[packageName] ?: "UNKNOWN"
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

            // Get battery change since last full charge
            val batteryManager = batteryManager
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

            // Get current battery level
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level != -1 && scale != -1) (level * 100) / scale else 50

            // Estimate battery usage since last full charge
            // If we can't determine actual discharge, use a reasonable default of 20%
            val batteryConsumed = if (batteryPct > 0) 100 - batteryPct else 20
            Log.v(TAG, "Battery consumed: $batteryConsumed%")

            if (totalScreenTime > 0) {
                // Enhanced approach: weighted battery usage based on screen time AND background activity
                for (stat in usageStats) {
                    // Only include stats for apps in our filtered list
                    val app = apps.find { it.packageName == stat.packageName }
                    if (app != null && stat.totalTimeInForeground > 0) {
                        // Base weight on foreground time
                        val screenTimeProportion = stat.totalTimeInForeground.toFloat() / totalScreenTime.toFloat()

                        // Check if app has significant background activity
                        var backgroundBoost = 0f
                        if (hasBackgroundUsage(app.packageName)) {
                            backgroundBoost = 0.5f // Add 50% boost for background activity
                        }

                        // Calculate final battery usage estimate
                        val batteryUsage = (screenTimeProportion * 0.7f + backgroundBoost) * batteryConsumed

                        // Ensure we have at least a minimum value for active apps (avoid all zeros)
                        batteryUsageMap[stat.packageName] = if (batteryUsage < 0.01f) 0.1f else batteryUsage

                        Log.v(TAG, "Estimated battery usage for ${stat.packageName}: ${batteryUsageMap[stat.packageName]}%")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in battery usage approach 1: ${e.message}")
        }

        // If the first approach didn't work, try a heuristic approach
        if (batteryUsageMap.isEmpty()) {
            try {
                Log.d(TAG, "Using heuristic approach for battery usage estimation")
                
                // Get device uptime to use as baseline
                val uptimeMs = SystemClock.elapsedRealtime()
                
                // Get top apps by usage
                val usm = usageStatsManager
                if (usm != null) {
                    val usageStats = usm.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY,
                        System.currentTimeMillis() - TIME_RANGE_MS,
                        System.currentTimeMillis()
                    )
                    
                    // Sort by foreground time
                    val topApps = usageStats.filter { 
                        apps.any { app -> app.packageName == it.packageName } && it.totalTimeInForeground > 0 
                    }.sortedByDescending { it.totalTimeInForeground }
                    
                    // Assign battery usage based on rank and foreground time
                    // Top 5 apps get higher percentage
                    for ((index, stat) in topApps.take(20).withIndex()) {
                        // Use a logarithmic scale - top apps use more battery
                        val baseUsage = when (index) {
                            in 0..2 -> 8f + (2 - index) * 2f // 12%, 10%, 8% for top 3
                            in 3..5 -> 5f + (5 - index) * 1f  // 7%, 6%, 5% for next 3
                            in 6..10 -> 2f + (10 - index) * 0.5f // 4%, 3.5%, 3%, 2.5%, 2% for next 5
                            else -> 1f // 1% for others in top 20
                        }
                        
                        // Adjust based on foreground time proportion
                        val timeRatio = if (uptimeMs > 0) {
                            (stat.totalTimeInForeground.toFloat() / uptimeMs).coerceIn(0f, 1f)
                        } else 0.1f
                        
                        val finalUsage = baseUsage * (0.5f + timeRatio)
                        batteryUsageMap[stat.packageName] = finalUsage
                        
                        Log.v(TAG, "Heuristic battery usage for ${stat.packageName}: $finalUsage% (rank ${index+1})")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in heuristic battery usage approach: ${e.message}")
            }
        }

        // Approach 3: Last resort - minimal fake data for demo purposes
        if (batteryUsageMap.isEmpty()) {
            Log.d(TAG, "Using minimal fake data for battery usage")
            // Assign at least some battery usage to common apps
            for (app in apps.take(10)) {
                batteryUsageMap[app.packageName] = (1f + Math.random() * 5f).toFloat()
                Log.v(TAG, "Assigned fake battery usage for ${app.packageName}: ${batteryUsageMap[app.packageName]}%")
            }
        }

        Log.d(TAG, "Collected battery usage for ${batteryUsageMap.size} apps")
        return batteryUsageMap
    }
    
    /**
     * Helper method to check if an app has significant background activity
     */
    private fun hasBackgroundUsage(packageName: String): Boolean {
        try {
            // Check if app has network activity in background
            val netStats = networkStatsManager
            if (netStats != null) {
                val uid = try {
                    packageManager.getPackageUid(packageName, 0)
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting UID for $packageName: ${e.message}")
                    return false
                }
                
                val endTime = System.currentTimeMillis()
                val startTime = endTime - TIME_RANGE_MS
                
                // Check mobile background usage
                try {
                    val stats = netStats.querySummary(
                        ConnectivityManager.TYPE_MOBILE,
                        null,
                        startTime,
                        endTime
                    )
                    
                    val bucket = NetworkStats.Bucket()
                    while (stats.hasNextBucket()) {
                        stats.getNextBucket(bucket)
                        if (bucket.uid == uid && bucket.state == NetworkStats.Bucket.STATE_DEFAULT && 
                            (bucket.rxBytes > 1024 * 100 || bucket.txBytes > 1024 * 100)) {
                            return true
                        }
                    }
                    stats.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking mobile background usage: ${e.message}")
                }
                
                // Check WiFi background usage
                try {
                    val stats = netStats.querySummary(
                        ConnectivityManager.TYPE_WIFI,
                        null,
                        startTime,
                        endTime
                    )
                    
                    val bucket = NetworkStats.Bucket()
                    while (stats.hasNextBucket()) {
                        stats.getNextBucket(bucket)
                        if (bucket.uid == uid && bucket.state == NetworkStats.Bucket.STATE_DEFAULT && 
                            (bucket.rxBytes > 1024 * 100 || bucket.txBytes > 1024 * 100)) {
                            return true
                        }
                    }
                    stats.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking WiFi background usage: ${e.message}")
                }
            }
            
            // Check if the app is a running process
            val am = activityManager
            if (am != null) {
                val runningProcesses = am.runningAppProcesses ?: return false
                return runningProcesses.any { 
                    it.pkgList.contains(packageName) && it.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND 
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking background usage: ${e.message}")
        }
        
        return false
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

        val adaptiveBattery =
            Settings.Global.getInt(
                context.contentResolver,
                "adaptive_battery_management_enabled",
                0
            ) == 1
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
    private fun getSubscriberId(): String? {
        return try {
            // First check for regular phone state permission
            if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
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
        val mode =
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
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
     * Collects real-time CPU usage for currently running apps only.
     * Note: This will only return data for apps that are currently running.
     */
    private fun collectCpuUsagePerApp(): Map<String, Float> {
        Log.d(TAG, "Collecting real-time CPU usage for running apps")
        val cpuUsageMap = HashMap<String, Float>()

        try {
            // Get running processes
            val runningProcesses = activityManager?.runningAppProcesses ?: run {
                Log.w(TAG, "Activity manager or running processes not available")
                return emptyMap()
            }
            
            Log.i(TAG, "Found ${runningProcesses.size} running processes")
            
            // Create mapping from package names to processes
            val packageToProcess = HashMap<String, ActivityManager.RunningAppProcessInfo>()
            for (process in runningProcesses) {
                for (pkg in process.pkgList) {
                    packageToProcess[pkg] = process
                }
            }
            
            // Get CPU usage for each running process
            for ((packageName, process) in packageToProcess) {
                try {
                    // Get CPU usage for the process
                    val pid = process.pid
                    val cpuUsage = getCpuUsageForPid(pid)
                    
                    if (cpuUsage > 0f) {
                        cpuUsageMap[packageName] = cpuUsage
                        Log.v(TAG, "Real-time CPU usage for $packageName (PID $pid): $cpuUsage%")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting CPU usage for $packageName: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting CPU usage per app: ${e.message}", e)
        }

        Log.i(TAG, "Collected real-time CPU usage for ${cpuUsageMap.size} running apps")
        return cpuUsageMap
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
     * Collects AlarmManager wakeups per app
     */
    private fun collectAlarmManagerWakeupsPerApp(apps: List<ApplicationInfo>): Map<String, Int> {
        Log.d(TAG, "Collecting AlarmManager wakeups per app")
        val alarmManagerWakeupsMap = HashMap<String, Int>()

        try {
            alarmManager ?: run {
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
            alarmManager ?: run {
                Log.w(TAG, "AlarmManager not available")
                return 0
            }

            IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
            }

            val intent = Intent().apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                setPackage(packageName)
            }

            // Query for registered receivers with proper API based on SDK version
            val flags = PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            val receivers = context.packageManager.queryBroadcastReceivers(intent, flags)

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
     * Collects current process priority for each app
     */
    private fun collectProcessPriorityChangesPerApp(apps: List<ApplicationInfo>): Map<String, String> {
        Log.d(TAG, "Collecting current process priority per app")
        val priorityMap = HashMap<String, String>()

        try {
            // Get running processes
            val runningProcesses = activityManager?.runningAppProcesses ?: emptyList()
            Log.d(TAG, "Found ${runningProcesses.size} running processes")

            // Create a map of package name to process info
            val packageToProcess = HashMap<String, ActivityManager.RunningAppProcessInfo>()
            for (process in runningProcesses) {
                for (pkg in process.pkgList) {
                    packageToProcess[pkg] = process
                }
            }

            // Get priority for each app
            for (app in apps) {
                val packageName = app.packageName
                val process = packageToProcess[packageName]
                
                val priority = when (process?.importance) {
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "Foreground"
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "Foreground Service"
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "Visible"
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "Perceptible"
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "Service"
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "Cached"
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE -> "Not Running"
                    null -> "Not Running"
                    else -> "Unknown"
                }
                
                priorityMap[packageName] = priority
                Log.v(TAG, "Priority for $packageName: $priority")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting process priorities per app: ${e.message}", e)
        }

        Log.i(TAG, "Collected process priorities for ${priorityMap.size} apps")
        return priorityMap
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