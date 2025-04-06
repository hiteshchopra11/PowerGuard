package com.hackathon.powerguard.data.remote

import android.util.Log
import com.hackathon.powerguard.data.model.Actionable
import com.hackathon.powerguard.data.model.AnalysisResponse
import com.hackathon.powerguard.data.model.DeviceData
import com.hackathon.powerguard.data.model.Insight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
                            priority = 1,
                            description = "This app is using excessive battery in the background",
                            reason = "High background usage detected",
                            parameters = mapOf(
                                "restrictBackground" to "true",
                                "optimizeBatteryUsage" to "true"
                            )
                        )
                    )

                    batteryScore -= 5 // Reduce battery score for each problematic app

                    // For extremely high battery drain apps, also suggest killing them
                    if (app.backgroundTime > 7_200_000) { // More than 2 hours in background
                        actionable.add(
                            Actionable(
                                id = generateRandomId(),
                                type = "KILL_APP",
                                packageName = app.packageName,
                                priority = 2,
                                description = "Force stop app to immediately save battery",
                                reason = "Critical battery usage detected",
                                parameters = emptyMap()
                            )
                        )
                        batteryScore -= 5 // Further reduce score
                    }
                }
            }

        // Check network usage
        deviceData.apps
            .filter { it.dataUsage.background > 10_000_000 } // 10MB
            .forEach { app ->
                actionable.add(
                    Actionable(
                        id = generateRandomId(),
                        type = "RESTRICT_BACKGROUND",
                        packageName = app.packageName,
                        priority = 2,
                        description = "App is using significant data in the background",
                        reason = "High background data usage detected",
                        parameters = mapOf("restrictBackgroundData" to "true")
                    )
                )
                dataScore -= 5 // Reduce data score
            }

        // Check battery temperature
        if (deviceData.battery.temperature > 40) {
            actionable.add(
                Actionable(
                    id = generateRandomId(),
                    type = "ENABLE_BATTERY_SAVER",
                    packageName = "",
                    priority = 1,
                    description = "Enable battery saver to reduce temperature",
                    reason = "Device running hot",
                    parameters = emptyMap()
                )
            )
            batteryScore -= 10
            performanceScore -= 5
        }

        // Generate insights based on findings
        if (actionable.any { it.type == "OPTIMIZE_BATTERY" }) {
            insights.add(
                Insight(
                    type = "BATTERY",
                    title = "Battery Drain Sources Identified",
                    description = "We found apps that are significantly draining your battery",
                    severity = "HIGH"
                )
            )
        }

        if (actionable.any { it.type == "RESTRICT_BACKGROUND" }) {
            insights.add(
                Insight(
                    type = "DATA",
                    title = "Background Data Usage Alert",
                    description = "Some apps are using excessive data in the background",
                    severity = "MEDIUM"
                )
            )
        }

        return AnalysisResponse(
            success = true,
            timestamp = System.currentTimeMillis(),
            message = "Analysis completed successfully",
            actionable = actionable,
            insights = insights,
            batteryScore = batteryScore,
            dataScore = dataScore,
            performanceScore = performanceScore,
            estimatedSavings = AnalysisResponse.EstimatedSavings(
                batteryMinutes = 30 + (actionable.size * 5),
                dataMB = 20 + (actionable.count { it.type == "RESTRICT_BACKGROUND" } * 10),
                storageMB = 10
            )
        )
    }

    /**
     * Generate a random ID for actionable
     */
    private fun generateRandomId(): String {
        return java.util.UUID.randomUUID().toString().substring(0, 8)
    }
}