package com.hackathon.powergaurd.data.remote

import android.util.Log
import com.hackathon.powergaurd.data.model.Actionable
import com.hackathon.powergaurd.data.model.AnalysisResponse
import com.hackathon.powergaurd.data.model.DeviceData
import com.hackathon.powergaurd.data.model.Insight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for handling API communication with the PowerGuard backend.
 */
@Singleton
class PowerGuardRepository @Inject constructor(
    private val apiService: PowerGuardApiService
) {
    companion object {
        private const val TAG = "PowerGuardRepository"
    }

    /**
     * Sends device data to the backend for analysis and receives optimization recommendations.
     * Falls back to simulated data if there's a network error.
     *
     * @param deviceData The collected device data
     * @return Result with AnalysisResponse or error information
     */
    suspend fun analyzeDeviceData(deviceData: DeviceData): Result<AnalysisResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.analyzeDeviceData(deviceData)

                if (response.isSuccessful && response.body() != null) {
                    Log.d(TAG, "Successfully received analysis from backend")
                    Result.success(response.body()!!)
                } else {
                    Log.e(TAG, "Error analyzing device data: ${response.errorBody()?.string()}")
                    // Fall back to simulated response instead of returning an error
                    Result.success(simulateAnalysisResponse(deviceData))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while analyzing device data", e)
                // Fall back to simulated response
                Result.success(simulateAnalysisResponse(deviceData))
            }
        }

    /**
     * Generates a simulated backend response for offline fallback
     */
    private fun simulateAnalysisResponse(deviceData: DeviceData): AnalysisResponse {
        val actionable = mutableListOf<Actionable>()
        val insights = mutableListOf<Insight>()

        // Initialize scores
        var batteryScore = 85
        var dataScore = 90
        var performanceScore = 80

        // Find battery-draining apps
        deviceData.apps
            .sortedByDescending { it.backgroundTime + it.foregroundTime }
            .take(3)
            .forEach { app ->
                if (app.backgroundTime > 3_600_000) { // More than 1 hour in background
                    actionable.add(
                        Actionable(
                            id = generateRandomId(),
                            type = "OPTIMIZE_BATTERY",
                            packageName = app.packageName,
                            description = "This app is using excessive battery in the background",
                            reason = "High background usage detected",
                            parameters = mapOf(
                                "restrictBackground" to "true",
                                "optimizeBatteryUsage" to "true"
                            ),
                            newMode = "restricted"
                        )
                    )

                    batteryScore -= 5 // Reduce battery score for each problematic app

                    insights.add(
                        Insight(
                            type = "BATTERY",
                            title = "High Battery Drain: ${app.appName}",
                            description = "${app.appName} is using significant battery in the background (${app.backgroundTime / 60000} minutes)",
                            severity = "HIGH"
                        )
                    )
                }
            }

        // Check for high data usage apps
        deviceData.apps
            .sortedByDescending { it.dataUsage.background }
            .take(3)
            .forEach { app ->
                if (app.dataUsage.background > 50 * 1024 * 1024) { // More than 50MB
                    actionable.add(
                        Actionable(
                            id = generateRandomId(),
                            type = "RESTRICT_DATA",
                            packageName = app.packageName,
                            description = "This app is using a lot of data",
                            reason = "High data usage detected",
                            parameters = mapOf(
                                "restrictBackgroundData" to "true"
                            ),
                            newMode = "restricted"
                        )
                    )

                    dataScore -= 5 // Reduce data score for each problematic app

                    insights.add(
                        Insight(
                            type = "DATA",
                            title = "High Data Usage: ${app.appName}",
                            description = "${app.appName} used ${app.dataUsage.background / (1024 * 1024)}MB of data recently",
                            severity = "MEDIUM"
                        )
                    )
                }
            }

        // Check for low memory
        if (deviceData.memory.availableRam < deviceData.memory.totalRam * 0.2) { // Less than 20% free
            performanceScore -= 15
            
            insights.add(
                Insight(
                    type = "PERFORMANCE",
                    title = "Low Available Memory",
                    description = "Your device is running low on available memory (${deviceData.memory.availableRam / (1024 * 1024)}MB free)",
                    severity = "HIGH"
                )
            )
            
            // Find memory-hungry apps
            deviceData.apps
                .sortedByDescending { it.foregroundTime }
                .take(2)
                .forEach { app ->
                    actionable.add(
                        Actionable(
                            id = generateRandomId(),
                            type = "OPTIMIZE_MEMORY",
                            packageName = app.packageName,
                            description = "This app is using a lot of memory",
                            reason = "High memory usage detected",
                            parameters = mapOf(
                                "forceStop" to "true"
                            ),
                            newMode = "stopped"
                        )
                    )
                }
        }

        return AnalysisResponse(
            id = deviceData.deviceId,
            timestamp = System.currentTimeMillis().toFloat(),
            batteryScore = batteryScore.toFloat(),
            dataScore = dataScore.toFloat(),
            performanceScore = performanceScore.toFloat(),
            insights = insights,
            actionable = actionable,
            success = true,
            message = "Analysis",
            estimatedSavings = AnalysisResponse.EstimatedSavings(
                batteryMinutes = 17f,
                dataMB = 1200f
            )
        )
    }

    private fun generateRandomId(): String {
        return UUID.randomUUID().toString()
    }
}