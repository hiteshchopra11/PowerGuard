package com.hackathon.powergaurd

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.BatteryManager
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
import com.hackathon.powergaurd.models.ChargingSchedule
import com.hackathon.powergaurd.models.TimeRange
import com.hackathon.powergaurd.workers.BatteryMonitorWorker
import com.hackathon.powergaurd.workers.SyncScheduleWorker
import com.hackathon.powergaurd.workers.WakeLockTimeoutWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Main class for AI-PowerGuard optimization actions
 * Compatible with API level 24+
 */
class PowerGuardOptimizer(private val context: Context) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val packageManager = context.packageManager
    private val workManager = WorkManager.getInstance(context)

    fun setAppBackgroundRestriction(packageName: String, restrictionLevel: String): Boolean {
        try {
            when (restrictionLevel) {
                "none" -> {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:$packageName")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    return true
                }
                "moderate" -> {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    return true
                }
                "strict" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.parse("package:$packageName")
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    } else {
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

    @RequiresPermission(android.Manifest.permission.WRITE_SETTINGS)
    fun manageWakeLock(packageName: String, action: String, timeoutMs: Long = 0): Boolean {
        try {
            when (action) {
                "disable" -> {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:$packageName")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    return true
                }
                "enable" -> {
                    if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        intent.data = Uri.parse("package:$packageName")
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    }
                    return true
                }
                "timeout" -> {
                    val workRequest = PeriodicWorkRequestBuilder<WakeLockTimeoutWorker>(
                        timeoutMs, TimeUnit.MILLISECONDS
                    ).setInputData(
                        Data.Builder()
                            .putString("package_name", packageName)
                            .build()
                    ).build()

                    workManager.enqueueUniquePeriodicWork(
                        "wakelock_timeout_$packageName",
                        ExistingPeriodicWorkPolicy.UPDATE,
                        workRequest
                    )
                    return true
                }
            }
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun restrictBackgroundData(
        packageName: String,
        enabled: Boolean,
        scheduleTimeRanges: List<TimeRange>? = null
    ): Boolean {
        try {
            if (enabled) {
                val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)

                if (scheduleTimeRanges != null && scheduleTimeRanges.isNotEmpty()) {
                    setupScheduledNetworkRestrictions(packageName, scheduleTimeRanges)
                }
                return true
            } else {
                workManager.cancelUniqueWork("network_restriction_$packageName")
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun optimizeCharging(
        maxChargeLevel: Int = 80,
        chargingSchedule: ChargingSchedule? = null
    ): Boolean {
        try {
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
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

            if (chargingSchedule != null) {
                setupChargingSchedule(chargingSchedule)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    @RequiresPermission(android.Manifest.permission.WRITE_SYNC_SETTINGS)
    fun optimizeSyncSchedule(accountType: String, syncFrequencyMinutes: Int): Boolean {
        try {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.WRITE_SYNC_SETTINGS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                val intent = Intent(Settings.ACTION_SYNC_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return false
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val data = Data.Builder()
                .putString("account_type", accountType)
                .putInt("sync_frequency", syncFrequencyMinutes)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<SyncScheduleWorker>(
                syncFrequencyMinutes.toLong(), TimeUnit.MINUTES,
                (syncFrequencyMinutes / 4).toLong(), TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInputData(data)
                .build()

            workManager.enqueueUniquePeriodicWork(
                "sync_optimizer_$accountType",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

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
        for (range in timeRanges) {
            val data = Data.Builder()
                .putString("package_name", packageName)
                .putInt("start_hour", range.startHour)
                .putInt("start_minute", range.startMinute)
                .putInt("end_hour", range.endHour)
                .putInt("end_minute", range.endMinute)
                .putIntArray("days_of_week", range.daysOfWeek.toIntArray())
                .build()
        }
    }

    private fun setupChargingSchedule(schedule: ChargingSchedule) {
        val data = Data.Builder()
            .putInt("start_hour", schedule.startHour)
            .putInt("start_minute", schedule.startMinute)
            .putInt("end_hour", schedule.endHour)
            .putInt("end_minute", schedule.endMinute)
            .putIntArray("days_of_week", schedule.daysOfWeek.toIntArray())
            .build()
    }
} 