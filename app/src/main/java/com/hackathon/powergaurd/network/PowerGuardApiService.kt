package com.hackathon.powergaurd.network

import com.hackathon.powergaurd.models.ActionResponse
import com.hackathon.powergaurd.models.DeviceData
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/** Retrofit API service interface for PowerGuard backend communication. */
interface PowerGuardApiService {

    /**
     * Sends device usage data to the backend and receives optimization actionables.
     *
     * @param deviceData The collected device data including app usage, battery stats, etc.
     * @return Response containing actionables and usage patterns
     */
    @POST("api/analyze")
    suspend fun analyzeDeviceData(@Body deviceData: DeviceData): Response<ActionResponse>
}
