package com.hackathon.powergaurd.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hackathon.PowerGuard.R
import com.hackathon.powergaurd.data.AppRepository
import com.hackathon.powergaurd.data.BackendService
import com.hackathon.powergaurd.data.DeviceStatsCollector
import com.hackathon.powergaurd.models.ActionResponse
import com.hackathon.powergaurd.models.DeviceData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DataCollectionWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @Inject
    lateinit var deviceStatsCollector: DeviceStatsCollector

    @Inject
    lateinit var backendService: BackendService

    @Inject
    lateinit var appRepository: AppRepository

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // 1. Collect device data
            val deviceData = collectDeviceData()

            // 2. Send data to backend
            val actionResponse = sendDataToBackend(deviceData)

            // 3. Store the response
            storeResponse(actionResponse)

            // 4. Apply actions if auto-optimize is enabled
            if (isAutoOptimizeEnabled()) {
                applyActions(actionResponse)
                showOptimizationNotification(actionResponse)
            }

            return@withContext Result.success()
        } catch (e: Exception) {
            // Log error and retry
            return@withContext Result.retry()
        }
    }

    private suspend fun collectDeviceData(): DeviceData {
        // This is a simulation - in a real app, we would collect actual device statistics
        return DeviceData(
            appUsage = deviceStatsCollector.collectAppUsage(),
            batteryStats = deviceStatsCollector.collectBatteryStats(),
            networkUsage = deviceStatsCollector.collectNetworkUsage(),
            wakeLocks = deviceStatsCollector.collectWakeLocks(),
            deviceId = deviceStatsCollector.getDeviceId(),
            timestamp = System.currentTimeMillis()
        )
    }

    private suspend fun sendDataToBackend(deviceData: DeviceData): ActionResponse {
        // In a real app, this would make an actual API call
        // For the hackathon, we'll simulate a response
        return simulateBackendResponse(deviceData)
    }

    private fun simulateBackendResponse(deviceData: DeviceData): ActionResponse {
        // This simulates what would come from the LLM-powered backend
        val actionables = mutableListOf<ActionResponse.Actionable>()
        val usagePatterns = mutableMapOf<String, String>()

        // Find battery-draining apps
        deviceData.appUsage.sortedByDescending { it.foregroundTimeMs + it.backgroundTimeMs }.take(3).forEach { appUsage ->
            // Add actionables based on usage
            if (appUsage.backgroundTimeMs > 3_600_000) { // More than 1 hour in background
                actionables.add(
                    ActionResponse.Actionable(
                        type = "app_mode_change",
                        app = appUsage.packageName,
                        newMode = "strict"
                    )
                )
                usagePatterns[appUsage.packageName] = "Uses significant background resources"
            }
        }

        // Check for wake locks
        deviceData.wakeLocks.filter { it.timeHeldMs > 1_800_000 }.forEach { wakeLock ->
            actionables.add(
                ActionResponse.Actionable(
                    type = "disable_wakelock",
                    app = wakeLock.packageName
                )
            )
            usagePatterns[wakeLock.packageName] = "Keeps wake locks for extended periods"
        }

        // Check network usage
        deviceData.networkUsage.appNetworkUsage
            .filter { it.dataUsageBytes > 50_000_000 } // 50MB
            .forEach { networkUsage ->
                actionables.add(
                    ActionResponse.Actionable(
                        type = "restrict_background_data",
                        app = networkUsage.packageName,
                        enabled = true
                    )
                )
                usagePatterns[networkUsage.packageName] = "Uses significant network data in background"
            }

        // Check battery temperature
        if (deviceData.batteryStats.temperature > 40) {
            actionables.add(
                ActionResponse.Actionable(
                    type = "cut_charging",
                    reason = "Battery overheating"
                )
            )
        }

        // Generate summary
        val summary = generateSummary(actionables)

        return ActionResponse(
            actionables = actionables,
            summary = summary,
            usagePatterns = usagePatterns,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun generateSummary(actionables: List<ActionResponse.Actionable>): String {
        if (actionables.isEmpty()) {
            return "No optimizations needed at this time."
        }

        val appModeChanges = actionables.filter { it.type == "app_mode_change" }
        val wakeLockDisables = actionables.filter { it.type == "disable_wakelock" }
        val dataRestrictions = actionables.filter { it.type == "restrict_background_data" }
        val chargingCuts = actionables.filter { it.type == "cut_charging" }

        val summary = StringBuilder("PowerGuard AI recommended the following optimizations: ")

        if (appModeChanges.isNotEmpty()) {
            summary.append("Restricted background activity for ${appModeChanges.size} apps. ")
        }

        if (wakeLockDisables.isNotEmpty()) {
            summary.append("Disabled wake locks for ${wakeLockDisables.size} apps. ")
        }

        if (dataRestrictions.isNotEmpty()) {
            summary.append("Limited background data usage for ${dataRestrictions.size} apps. ")
        }

        if (chargingCuts.isNotEmpty()) {
            summary.append("Recommended to pause charging due to ${chargingCuts.first().reason}. ")
        }

        summary.append("These changes could improve your battery life by up to 30%.")

        return summary.toString()
    }

    private suspend fun storeResponse(response: ActionResponse) {
        // Store in local database for UI access
        appRepository.saveActionResponse(response)
    }

    private fun isAutoOptimizeEnabled(): Boolean {
        // Check user preferences
        // For hackathon purposes, we'll default to true
        return true
    }

    private fun applyActions(response: ActionResponse) {
        // In a real app, this would apply the actions using the PowerGuardOptimizer
        // For the hackathon, this is just a stub
    }

    private fun showOptimizationNotification(response: ActionResponse) {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("PowerGuard Optimization")
            .setContentText(response.summary)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "PowerGuard Notifications"
            val descriptionText = "Notifications for PowerGuard optimization events"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "powerguard_channel"
        private const val NOTIFICATION_ID = 1
    }
}