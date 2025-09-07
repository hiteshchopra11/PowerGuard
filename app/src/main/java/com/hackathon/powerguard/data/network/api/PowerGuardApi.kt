package com.hackathon.powerguard.data.network.api

import com.hackathon.powerguard.data.network.model.ApiRequest
import com.hackathon.powerguard.data.network.model.ApiResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit interface for PowerGuard backend API
 */
interface PowerGuardApi {
    
    @POST("/api/analyze")
    suspend fun analyzeDeviceData(@Body request: ApiRequest): Response<ApiResponse>
}