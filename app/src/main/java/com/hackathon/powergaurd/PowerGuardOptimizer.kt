package com.hackathon.powergaurd

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hackathon.powergaurd.workers.BatteryMonitorWorker
import com.hackathon.powergaurd.workers.SyncScheduleWorker
import com.hackathon.powergaurd.workers.WakeLockTimeoutWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Main class for AI-PowerGuard optimization actions
 * Compatible with API level 24+
 */
@Singleton
class PowerGuardOptimizer @Inject constructor(private val context: Context) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val packageManager = context.packageManager
    private val workManager = WorkManager.getInstance(context)

    /**
     * 1. App Background Restriction
     * Restricts an app's ability to run in the background
     *
     * @param packageName The package name of the app to restrict
     * @param restrictionLevel "none", "moderate", or "strict"
     * @return true if the action was successful, false otherwise
     */
    fun setAppBackgroundRestriction(packageName: String, restrictionLevel: String): Boolean {
        try {
            when (restrictionLevel) {
                "none" -> {
                    // Remove background restrictions
                    // Direct the user to the settings page
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:$packageName")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    return true
                }
                "moderate" -> {
                    // Apply moderate restrictions - using battery optimization
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    return true
                }
                "strict" -> {
                    // Apply strict restrictions
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        // For Android 9+ we would use App Standby Buckets
                        // But since we're targeting API 24+, we'll use a compatible approach

                        // For devices with API 28+, we would ideally use:
                        // usageStatsManager.setAppStandbyBucket(packageName, UsageStatsManager.STANDBY_BUCKET_RARE)
                        // But this requires system permissions anyway

                        // Instead, we'll force stop the app and apply battery restrictions
                        // This requires user interaction
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.parse("package:$packageName")
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    } else {
                        // For older versions, direct to app info
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.parse("package:$packageName")
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    }
                    return true
                }
            }
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * 2. Intelligent Wake Lock Management
     * Controls wake locks for specific applications
     *
     * @param packageName The package name of the app
     * @param action "disable", "enable", or "timeout"
     * @param timeoutMs Timeout in milliseconds (only used with "timeout" action)
     * @return true if the action was successful, false otherwise
     */
    fun manageWakeLock(packageName: String, action: String, timeoutMs: Long = 0): Boolean {
        // Check if we have the permission
        if (!Settings.System.canWrite(context)) {
            // If not, just open the settings for the user to grant it
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:${context.packageName}")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            return false
        }

        try {
            when (action) {
                "disable" -> {
                    // For real implementation, this would require root access or system privileges
                    // Instead, we'll direct user to app info settings to manually force stop
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:$packageName")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)

                    return true
                }
                "enable" -> {
                    // Exclude the app from Doze mode optimization
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            intent.data = Uri.parse("package:$packageName")
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                        }
                    } else {
                        // For pre-Marshmallow, there's no direct way to manage this
                        // Just open battery settings
                        val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    }
                    return true
                }
                "timeout" -> {
                    // Schedule a task to check and force stop the app after timeout
                    val workRequest = PeriodicWorkRequestBuilder<WakeLockTimeoutWorker>(
                        timeoutMs, TimeUnit.MILLISECONDS
                    ).setInputData(
                        Data.Builder()
                            .putString("package_name", packageName)
                            .build()
                    ).build()

                    workManager.enqueueUniquePeriodicWork(
                        "wakelock_timeout_$packageName",
                        ExistingPeriodicWorkPolicy.REPLACE,
                        workRequest
                    )
                    return true
                }
                else -> return false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }


    /**
     * 3. Adaptive Network Restriction
     * Restricts background data usage for specific apps
     *
     * @param packageName The package name of the app
     * @param enabled Whether background data restriction is enabled
     * @param scheduleTimeRanges Optional time ranges when restrictions should apply
     * @return true if the action was successful, false otherwise
     */
    fun restrictBackgroundData(
        packageName: String,
        enabled: Boolean,
        scheduleTimeRanges: List<TimeRange>? = null
    ): Boolean {
        try {
            if (enabled) {
                // Direct the user to the Data Usage settings
                val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)

                // For scheduled restrictions, we'll implement a custom solution
                if (scheduleTimeRanges != null && scheduleTimeRanges.isNotEmpty()) {
                    setupScheduledNetworkRestrictions(packageName, scheduleTimeRanges)
                }

                return true
            } else {
                // Remove any scheduled restrictions
                workManager.cancelUniqueWork("network_restriction_$packageName")
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * 4. Smart Charging Control
     * Controls device charging behavior to extend battery lifespan
     * Note: Most devices don't allow programmatic control of charging without root
     *
     * @param maxChargeLevel Maximum battery level (0-100)
     * @param chargingSchedule Optional schedule for when to charge
     * @return true if the action was successful, false otherwise
     */
    fun optimizeCharging(
        maxChargeLevel: Int = 80,
        chargingSchedule: ChargingSchedule? = null
    ): Boolean {
        try {
            // Implement a monitoring service that notifies the user when battery reaches designated level
            val data = Data.Builder()
                .putInt("max_charge_level", maxChargeLevel)
                .build()

            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .setRequiresCharging(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<BatteryMonitorWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInputData(data)
                .build()

            workManager.enqueueUniquePeriodicWork(
                "battery_monitor",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )

            // If we have a charging schedule, set up scheduled monitoring
            if (chargingSchedule != null) {
                setupChargingSchedule(chargingSchedule)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * 5. Sync Frequency Optimization
     * Adjusts how often apps sync with servers and batches operations
     *
     * @param accountType Account type (e.g., "com.google")
     * @param syncFrequencyMinutes How often to allow syncing (in minutes)
     * @return true if the action was successful, false otherwise
     */
    @RequiresPermission(android.Manifest.permission.WRITE_SYNC_SETTINGS)
    fun optimizeSyncSchedule(accountType: String, syncFrequencyMinutes: Int): Boolean {
        try {
            // For real implementation, we need WRITE_SYNC_SETTINGS permission

            // 1. Check if we have the required permission
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.WRITE_SYNC_SETTINGS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // We don't have permission, so open sync settings
                val intent = Intent(Settings.ACTION_SYNC_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return false
            }

            // 2. Schedule a worker to manage sync operations
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val data = Data.Builder()
                .putString("account_type", accountType)
                .putInt("sync_frequency", syncFrequencyMinutes)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<SyncScheduleWorker>(
                syncFrequencyMinutes.toLong(), TimeUnit.MINUTES,
                // Add flex period to allow the system to batch operations
                (syncFrequencyMinutes / 4).toLong(), TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInputData(data)
                .build()

            workManager.enqueueUniquePeriodicWork(
                "sync_optimizer_$accountType",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    // Helper methods

    private fun hasUsageStatsPermission(): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    private fun setupScheduledNetworkRestrictions(packageName: String, timeRanges: List<TimeRange>) {
        // Implementation to schedule network restrictions based on time ranges
        // Using Calendar instead of LocalTime for API 24 compatibility

        for (range in timeRanges) {
            // Create a worker for each time range
            val data = Data.Builder()
                .putString("package_name", packageName)
                .putInt("start_hour", range.startHour)
                .putInt("start_minute", range.startMinute)
                .putInt("end_hour", range.endHour)
                .putInt("end_minute", range.endMinute)
                .putIntArray("days_of_week", range.daysOfWeek.toIntArray())
                .build()

            // Schedule the worker based on the time range
            // Implementation would depend on the exact requirements
        }
    }

    private fun setupChargingSchedule(schedule: ChargingSchedule) {
        // Implementation to set up charging schedule using Calendar instead of LocalTime
        val data = Data.Builder()
            .putInt("start_hour", schedule.startHour)
            .putInt("start_minute", schedule.startMinute)
            .putInt("end_hour", schedule.endHour)
            .putInt("end_minute", schedule.endMinute)
            .putIntArray("days_of_week", schedule.daysOfWeek.toIntArray())
            .build()

        // Schedule the worker based on the charging schedule
        // Implementation would depend on the exact requirements
    }

    // Data classes for the API - using Calendar-compatible values for API 24

    data class TimeRange(
        val startHour: Int,
        val startMinute: Int,
        val endHour: Int,
        val endMinute: Int,
        val daysOfWeek: List<Int> = listOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY,
            Calendar.SUNDAY
        )
    )

    data class ChargingSchedule(
        val startHour: Int,
        val startMinute: Int,
        val endHour: Int,
        val endMinute: Int,
        val daysOfWeek: List<Int> = listOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY,
            Calendar.SUNDAY
        )
    )
}