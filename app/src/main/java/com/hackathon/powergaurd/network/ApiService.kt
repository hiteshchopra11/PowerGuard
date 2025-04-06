package com.hackathon.powergaurd.network

import com.hackathon.powergaurd.models.ActionResponse
import com.hackathon.powergaurd.models.DeviceData
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import javax.inject.Inject
import javax.inject.Singleton

/** Retrofit service for the PowerGuard API */
interface ApiService {
    /**
     * Analyze device data and get optimization recommendations
     */
    @POST("api/analyze")
    suspend fun analyzeDeviceData(@Body data: DeviceData): ActionResponse

    /**
     * Get usage patterns for a specific device
     */
    @GET("api/patterns/{device_id}")
    suspend fun getUsagePatterns(@Path("device_id") deviceId: String): Map<String, String>
}

/** Mock implementation of ApiService for testing */
@Singleton
class MockApiService @Inject constructor() : ApiService {
    override suspend fun analyzeDeviceData(data: DeviceData): ActionResponse {
        // For demonstration purposes, return some sample actionables
        val actionables = mutableListOf<ActionResponse.Actionable>()
        val usagePatterns = mutableMapOf<String, String>()

        // Add sample actionables
        actionables.add(
            ActionResponse.Actionable(
                type = "SET_STANDBY_BUCKET",
                app = "com.example.highbatterydrain",
                newMode = "restricted",
                reason = "Excessive background battery usage"
            )
        )

        actionables.add(
            ActionResponse.Actionable(
                type = "KILL_APP",
                app = "com.example.crashprone",
                reason = "Frequent crashes detected"
            )
        )

        actionables.add(
            ActionResponse.Actionable(
                type = "ENABLE_DATA_SAVER",
                app = "com.example.datahungry",
                enabled = true,
                reason = "High data usage in background"
            )
        )

        // Add sample usage patterns
        usagePatterns["com.example.highbatterydrain"] = "Uses significant battery in background"
        usagePatterns["com.example.datahungry"] = "Consumes excessive data when not in use"

        return ActionResponse(
            actionables = actionables,
            summary = "Found 3 optimization opportunities that could improve battery life by up to 2 hours",
            usagePatterns = usagePatterns,
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun getUsagePatterns(deviceId: String): Map<String, String> {
        return mapOf(
            "com.example.app1" to "Heavy battery drain at night",
            "com.example.app2" to "Uses camera in background",
            "com.example.app3" to "Excessive sync operations during low battery"
        )
    }
}
