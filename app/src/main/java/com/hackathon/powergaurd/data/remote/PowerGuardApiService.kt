package com.hackathon.powergaurd.data.remote

import com.hackathon.powergaurd.data.model.AnalysisResponse
import com.hackathon.powergaurd.data.model.DeviceData
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/** API interface for PowerGuard backend */
interface PowerGuardApiService {
    
    /**
     * Analyzes device data and returns optimization recommendations
     *
     * @param deviceData Device data to analyze
     * @return Response containing analysis results
     */
    @POST("/api/analyze")
    suspend fun analyzeDeviceData(@Body deviceData: DeviceData): Response<AnalysisResponse>
}