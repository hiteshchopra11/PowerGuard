package com.hackathon.powergaurd.data

import android.util.Log
import com.hackathon.powergaurd.actionable.ActionableTypes
import com.hackathon.powergaurd.models.ActionResponse
import com.hackathon.powergaurd.models.AppUsageInfo
import com.hackathon.powergaurd.models.DeviceData
import com.hackathon.powergaurd.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackendService @Inject constructor(
    private val apiService: ApiService
) {
    companion object {
        private const val TAG = "BackendService"
    }

    /**
     * Sends device data to the backend for analysis and returns actionable insights.
     * Falls back to simulated data if there's a network error.
     */
    suspend fun sendDataForAnalysis(deviceData: DeviceData): ActionResponse {
        return try {
            Log.d(TAG, "Sending data to backend for device: ${deviceData.deviceId}")
            val response = withContext(Dispatchers.IO) {
                apiService.analyzeDeviceData(deviceData)
            }
            Log.d(TAG, "Received response with ${response.actionables.size} actionables")
            response
        } catch (e: Exception) {
            Log.e(TAG, "Error sending data to backend, using fallback", e)
            // Fallback to simulated response if network call fails
            simulateBackendResponse(deviceData)
        }
    }

    /**
     * Gets usage patterns for a specific device from the backend.
     */
    suspend fun getUsagePatterns(deviceId: String): Map<String, String> {
        return try {
            Log.d(TAG, "Fetching usage patterns for device: $deviceId")
            withContext(Dispatchers.IO) {
                apiService.getUsagePatterns(deviceId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching usage patterns, using fallback", e)
            // Fallback to empty patterns
            emptyMap()
        }
    }

    // Fallback method to simulate backend response if network call fails
    private fun simulateBackendResponse(deviceData: DeviceData): ActionResponse {
        val actionables = mutableListOf<ActionResponse.Actionable>()
        val usagePatterns = mutableMapOf<String, String>()

        // Find battery-draining apps
        deviceData.appUsage
            .sortedByDescending { it.foregroundTimeMs + it.backgroundTimeMs }
            .take(3)
            .forEach { appUsage ->
                if (appUsage.backgroundTimeMs > 3_600_000) { // More than 1 hour in background
                    // Use our standby bucket handler
                    actionables.add(
                        ActionResponse.Actionable(
                            type = ActionableTypes.SET_STANDBY_BUCKET,
                            app = appUsage.packageName,
                            newMode = "restricted"
                        )
                    )
                    usagePatterns[appUsage.packageName] = "Uses significant background resources"

                    // For extremely high battery drain apps, also suggest killing them
                    if (appUsage.backgroundTimeMs > 7_200_000) { // More than 2 hours in background
                        actionables.add(
                            ActionResponse.Actionable(
                                type = ActionableTypes.KILL_APP,
                                app = appUsage.packageName,
                                reason = "Excessive background activity"
                            )
                        )
                    }

                    // Mark apps as inactive
                    actionables.add(
                        ActionResponse.Actionable(
                            type = ActionableTypes.MARK_APP_INACTIVE,
                            app = appUsage.packageName,
                            enabled = true
                        )
                    )
                }
            }

        // Check network usage
        deviceData.networkUsage.appNetworkUsage
            .filter { it.dataUsageBytes > 50_000_000 } // 50MB
            .forEach { networkUsage ->
                actionables.add(
                    ActionResponse.Actionable(
                        type = ActionableTypes.ENABLE_DATA_SAVER,
                        app = networkUsage.packageName,
                        enabled = true
                    )
                )
                usagePatterns[networkUsage.packageName] =
                    "Uses significant network data in background"
            }

        // Check battery temperature
        if (deviceData.batteryStats.temperature > 40) {
            actionables.add(
                ActionResponse.Actionable(
                    type = ActionableTypes.ENABLE_BATTERY_SAVER,
                    enabled = true,
                    reason = "Battery overheating"
                )
            )
        }

        // Check for sync-heavy apps
        deviceData.appUsage
            .filter { app -> hasHighSyncCount(app) }
            .forEach { appUsage ->
                actionables.add(
                    ActionResponse.Actionable(
                        type = ActionableTypes.ADJUST_SYNC_SETTINGS,
                        app = appUsage.packageName,
                        enabled = false
                    )
                )
                usagePatterns[appUsage.packageName] = "Performs excessive sync operations"
            }

        return ActionResponse(
            actionables = actionables,
            summary = "PowerGuard detected opportunities to save battery and data usage",
            usagePatterns = usagePatterns,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Checks if an app has a high number of sync operations
     * This is a helper function to avoid direct syncCount access which may not be available
     */
    private fun hasHighSyncCount(appUsage: AppUsageInfo): Boolean {
        // We can use app.backgroundTime or other available metrics as a proxy for sync activity
        // For example, apps that run a lot in the background might be syncing frequently

        // Check if the app has substantial background usage which could indicate sync activity
        return appUsage.backgroundTimeMs > 10 * 60 * 1000 // More than 10 minutes in background
    }
}