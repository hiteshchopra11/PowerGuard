package com.hackathon.powergaurd.network

import android.util.Log
import com.hackathon.powergaurd.models.ActionResponse
import com.hackathon.powergaurd.models.DeviceData
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Repository for handling API communication with the PowerGuard backend. */
@Singleton
class ApiRepository @Inject constructor() {

    private val apiService = RetrofitClient.apiService
    private val TAG = "ApiRepository"

    /**
     * Sends device data to the backend for analysis and receives actionable recommendations.
     *
     * @param deviceData The collected device data
     * @return ActionResponse with actionables or null on error
     */
    suspend fun analyzeDeviceData(deviceData: DeviceData): Result<ActionResponse> =
            withContext(Dispatchers.IO) {
                try {
                    val response = apiService.analyzeDeviceData(deviceData)

                    if (response.isSuccessful && response.body() != null) {
                        Log.d(TAG, "Successfully received actionables from backend")
                        Result.success(response.body()!!)
                    } else {
                        Log.e(TAG, "Error analyzing device data: ${response.errorBody()?.string()}")
                        Result.failure(
                                Exception("API error: ${response.code()} ${response.message()}")
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception while analyzing device data", e)
                    Result.failure(e)
                }
            }
}
