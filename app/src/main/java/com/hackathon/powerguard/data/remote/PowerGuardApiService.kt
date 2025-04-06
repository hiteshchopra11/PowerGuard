package com.hackathon.powerguard.data.remote

import com.hackathon.powerguard.data.model.AnalysisResponse
import com.hackathon.powerguard.data.model.DeviceData
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit API service interface for PowerGuard backend communication.
 */
interface PowerGuardApiService {

    /**
     * Sends device data to the backend for analysis and receives optimization recommendations.
     *
     * @param deviceData The collected device data including app usage, battery stats, etc.
     * @return Response containing actionable, insights, and scores
     */
    @POST("api/v1/analyze-device-data")
    suspend fun analyzeDeviceData(@Body deviceData: DeviceData): Response<AnalysisResponse>
}