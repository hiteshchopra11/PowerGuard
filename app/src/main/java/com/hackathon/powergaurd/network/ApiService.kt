package com.hackathon.powergaurd.network

import com.hackathon.powergaurd.data.model.Actionable
import com.hackathon.powergaurd.data.model.DeviceData
import com.hackathon.powergaurd.data.model.Insight
import com.hackathon.powergaurd.data.model.SavingsEstimate
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.http.Body
import retrofit2.http.POST

/** Retrofit service for the PowerGuard API */
interface ApiService {
        @POST("api/v1/analyze-device-data")
        suspend fun analyzeDeviceData(@Body data: DeviceData): ApiResponse
}

/** Response from the device data analysis API */
data class ApiResponse(
        val success: Boolean,
        val timestamp: Long,
        val message: String?,
        val actionables: List<Actionable> = emptyList(),
        val insights: List<Insight> = emptyList(),
        val batteryScore: Int = 0,
        val dataScore: Int = 0,
        val performanceScore: Int = 0,
        val estimatedSavings: SavingsEstimate? = null
)

/** Mock implementation of ApiService for testing */
@Singleton
class MockApiService @Inject constructor() : ApiService {
        override suspend fun analyzeDeviceData(data: DeviceData): ApiResponse {
                // For demonstration purposes, return some sample actionables
                return ApiResponse(
                        success = true,
                        timestamp = System.currentTimeMillis(),
                        message = "Analysis completed successfully",
                        actionables =
                                listOf(
                                        Actionable(
                                                id = "1",
                                                type = "OPTIMIZE_BATTERY",
                                                packageName = "com.example.highbatterydrain",
                                                priority = 1,
                                                description =
                                                        "This app is consuming excessive battery power in the background",
                                                parameters = mapOf("restrictBackground" to true)
                                        ),
                                        Actionable(
                                                id = "2",
                                                type = "RESTRICT_BACKGROUND",
                                                packageName = "com.example.highdatausage",
                                                priority = 2,
                                                description =
                                                        "App is using significant data in the background",
                                                parameters = mapOf("restrictBackgroundData" to true)
                                        )
                                ),
                        insights =
                                listOf(
                                        Insight(
                                                type = "BATTERY",
                                                title = "Battery Drain Sources Identified",
                                                description =
                                                        "We found 2 apps that are significantly draining your battery",
                                                severity = "HIGH"
                                        ),
                                        Insight(
                                                type = "DATA",
                                                title = "Background Data Usage Alert",
                                                description =
                                                        "1 app is using excessive data in the background",
                                                severity = "MEDIUM"
                                        )
                                ),
                        batteryScore = 65,
                        dataScore = 78,
                        performanceScore = 82,
                        estimatedSavings =
                                SavingsEstimate(batteryMinutes = 45, dataMB = 50, storageMB = 20)
                )
        }
}
