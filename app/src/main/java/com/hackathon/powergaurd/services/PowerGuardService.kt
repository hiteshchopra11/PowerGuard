package com.hackathon.powergaurd.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hackathon.powergaurd.MainActivity
import com.hackathon.powergaurd.R
import com.hackathon.powergaurd.data.AppRepository
import com.hackathon.powergaurd.models.AppUsageData
import com.hackathon.powergaurd.models.BatteryOptimizationData
import com.hackathon.powergaurd.models.NetworkUsageData
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PowerGuardService : Service() {

    private val TAG = "PowerGuardService"
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "power_guard_channel"

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var isCollectingData = false

    @Inject lateinit var appRepository: AppRepository

    private lateinit var powerManager: PowerManager
    private lateinit var batteryManager: BatteryManager
    private lateinit var packageManager: PackageManager

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "PowerGuardService created")

        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        packageManager = applicationContext.packageManager

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "PowerGuardService started")

        val notification = createForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)

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
        isCollectingData = false
        Log.d(TAG, "PowerGuardService destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "PowerGuard Service"
            val descriptionText = "Monitoring battery and network usage"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel =
                    NotificationChannel(CHANNEL_ID, name, importance).apply {
                        description = descriptionText
                    }

            val notificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
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
                    collectAndAnalyzeData()
                } catch (e: Exception) {
                    Log.e(TAG, "Error collecting data: ${e.message}")
                }

                // Collect data every 15 minutes
                delay(TimeUnit.MINUTES.toMillis(15))
            }
        }
    }

    private suspend fun collectAndAnalyzeData() {
        Log.d(TAG, "Collecting usage data")

        // 1. Collect battery and app usage data
        val installedApps = getInstalledApps()
        val batteryInfo = collectBatteryInfo()
        val appUsageData = collectAppUsageData(installedApps)
        val networkUsageData = collectNetworkUsageData(installedApps)

        // 2. Store data for pattern recognition
        appRepository.saveUsageData(appUsageData, batteryInfo, networkUsageData)

        // 3. Analyze patterns and optimize (if we have system permissions)
        if (applicationContext.packageName.equals("android.uid.system")) {
            optimizeBatteryUsage(appUsageData, batteryInfo)
            optimizeNetworkUsage(networkUsageData)
        }
    }

    private fun getInstalledApps(): List<ApplicationInfo> {
        return packageManager.getInstalledApplications(PackageManager.GET_META_DATA).filter {
            // Filter out system apps if needed
            it.packageName != "android" &&
                    it.packageName != "com.android.systemui" &&
                    it.packageName != applicationContext.packageName
        }
    }

    private fun collectBatteryInfo(): BatteryOptimizationData {
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = batteryManager.isCharging
        val temperature =
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_TEMPERATURE) / 10.0
        val voltage = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_VOLTAGE)
        val currentAverage =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
                } else {
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
        // In a real implementation, we would use UsageStatsManager to get detailed app usage
        // For this example, we'll create placeholder data
        return installedApps.map { app ->
            val packageName = app.packageName
            val appName = packageManager.getApplicationLabel(app).toString()

            // In a real implementation, these values would come from UsageStatsManager
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
        // In a real implementation, we would use NetworkStatsManager to get detailed usage
        // For this example, we'll create placeholder data
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

            // In a real system app, we could force stop these apps
            highBatteryApps.forEach { app ->
                Log.d(TAG, "Would force stop high battery usage app: ${app.appName}")
                // If we have FORCE_STOP_PACKAGES permission:
                // ActivityManager.forceStopPackage(app.packageName)
            }
        }
    }

    private fun optimizeNetworkUsage(networkUsageData: List<NetworkUsageData>) {
        // For system app, we can implement direct optimization here
        // Example: Restrict background data for high-usage apps
        val highDataApps =
                networkUsageData
                        .filter { it.backgroundDataUsageBytes > 10 * 1024 * 1024 } // More than 10MB
                        .sortedByDescending { it.backgroundDataUsageBytes }
                        .take(3)

        // In a real system app, we could restrict background data:
        highDataApps.forEach { app ->
            Log.d(TAG, "Would restrict background data for: ${app.appName}")
            // If we have MANAGE_NETWORK_POLICY permission:
            // ConnectivityManager.setRestrictBackgroundData(app.packageName, true)
        }
    }
}
