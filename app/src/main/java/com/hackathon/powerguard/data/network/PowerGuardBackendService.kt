package com.hackathon.powerguard.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.hackathon.powerguard.data.ai.PowerGuardAnalysisService
import com.hackathon.powerguard.data.model.AnalysisResponse
import com.hackathon.powerguard.data.model.DeviceData
import com.hackathon.powerguard.data.network.api.PowerGuardApi
import com.hackathon.powerguard.data.network.mapper.ModelMapper.toAnalysisResponse
import com.hackathon.powerguard.data.network.mapper.ModelMapper.toApiRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backend API implementation of PowerGuardAnalysisService using Retrofit
 */
@Singleton
class PowerGuardBackendService @Inject constructor(
    private val context: Context,
    private val api: PowerGuardApi
) : PowerGuardAnalysisService {

    companion object {
        private const val TAG = "PowerGuardBackendService"
        private const val REQUEST_TIMEOUT_MS = 30000L // 30 seconds
    }

    override suspend fun analyzeDeviceData(deviceData: DeviceData): Result<AnalysisResponse> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Making API request to backend for device: ${deviceData.deviceId}")
                
                if (!isServiceAvailable()) {
                    Log.w(TAG, "Network not available, service unavailable")
                    return@withContext Result.failure(
                        Exception("Network not available for backend API")
                    )
                }

                val apiRequest = deviceData.toApiRequest()
                Log.d(TAG, "Converted device data to API request for ${apiRequest.apps.size} apps")

                val response = withTimeout(REQUEST_TIMEOUT_MS) {
                    api.analyzeDeviceData(apiRequest)
                }

                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse != null) {
                        Log.d(TAG, "Received successful response from backend API with ${apiResponse.actionable.size} actionables and ${apiResponse.insights.size} insights")
                        val analysisResponse = apiResponse.toAnalysisResponse()
                        return@withContext Result.success(analysisResponse)
                    } else {
                        Log.e(TAG, "Response body is null despite successful response")
                        return@withContext Result.failure(
                            Exception("Empty response body from backend API")
                        )
                    }
                } else {
                    val errorMessage = "Backend API error: ${response.code()} - ${response.message()}"
                    Log.e(TAG, errorMessage)
                    return@withContext Result.failure(
                        Exception(errorMessage)
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error calling backend API", e)
                return@withContext Result.failure(e)
            }
        }

    override suspend fun isServiceAvailable(): Boolean {
        return isNetworkAvailable()
    }

    override fun getServiceType(): PowerGuardAnalysisService.ServiceType {
        return PowerGuardAnalysisService.ServiceType.BACKEND_API
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        
        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}