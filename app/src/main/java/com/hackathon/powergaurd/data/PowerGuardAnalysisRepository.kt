package com.hackathon.powergaurd.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.hackathon.powergaurd.data.gemma.GemmaRepository
import com.hackathon.powergaurd.data.model.AnalysisRepository
import com.hackathon.powergaurd.data.model.AnalysisResponse
import com.hackathon.powergaurd.data.model.DeviceData
import com.hackathon.powergaurd.data.remote.PowerGuardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository facade that can use either backend API or on-device ML inference
 * for device data analysis.
 */
@Singleton
class PowerGuardAnalysisRepository @Inject constructor(
    private val context: Context,
    private val remoteRepository: PowerGuardRepository,
    private val gemmaRepository: GemmaRepository
) {
    companion object {
        private const val TAG = "PowerGuardAnalysisRepo"
        
        // Enable Gemma SDK by default
        private var useGemma = true
        
        // Track if we've detected network issues
        private var hasNetworkConnectivity = true
    }

    init {
        Log.d(TAG, "Initialized with useGemma = $useGemma")
    }

    /**
     * Analyzes device data using either backend API or on-device ML inference.
     *
     * @param deviceData The collected device data
     * @return Result with AnalysisResponse or error information
     */
    suspend fun analyzeDeviceData(deviceData: DeviceData): Result<AnalysisResponse> =
        withContext(Dispatchers.IO) {
            // Check network connectivity
            hasNetworkConnectivity = checkNetworkConnectivity()
            
            try {
                if (useGemma) {
                    Log.d(TAG, "Using Gemma for analysis")
                    gemmaRepository.analyzeDeviceData(deviceData)
                } else if (!hasNetworkConnectivity) {
                    // If using remote API but no network, fall back to Gemma
                    Log.d(TAG, "No network connectivity, falling back to Gemma")
                    gemmaRepository.analyzeDeviceData(deviceData) 
                } else {
                    Log.d(TAG, "Using backend API for analysis")
                    remoteRepository.analyzeDeviceData(deviceData)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Analysis failed, attempting fallback", e)
                
                // If one approach fails, try the other as fallback
                return@withContext try {
                    if (useGemma) {
                        Log.d(TAG, "Gemma failed, falling back to backend API")
                        // Only try remote if we have network connectivity
                        if (hasNetworkConnectivity) {
                            remoteRepository.analyzeDeviceData(deviceData)
                        } else {
                            Log.d(TAG, "No network connectivity for fallback, using simulated response")
                            gemmaRepository.analyzeDeviceData(deviceData)
                        }
                    } else {
                        Log.d(TAG, "Backend API failed, falling back to Gemma")
                        gemmaRepository.analyzeDeviceData(deviceData)
                    }
                } catch (fallbackEx: Exception) {
                    Log.e(TAG, "Fallback also failed", fallbackEx)
                    // Create a basic error response
                    Result.failure(fallbackEx)
                }
            }
        }

    /**
     * Check if the device has network connectivity
     */
    private fun checkNetworkConnectivity(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null && 
               (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || 
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
    }

    /**
     * Sets which implementation to use for analysis.
     *
     * @param useGemmaImplementation True to use local Gemma SDK, false to use backend API
     */
    fun setUseGemma(useGemmaImplementation: Boolean) {
        useGemma = useGemmaImplementation
        Log.d(TAG, "Switched to ${if (useGemma) "Gemma" else "backend API"} implementation")
    }

    /**
     * Returns which implementation is currently active.
     */
    fun isUsingGemma(): Boolean = useGemma

    /**
     * Initializes the Gemma SDK.
     * Should be called early in the app lifecycle.
     */
    suspend fun initializeGemma(): Boolean {
        return gemmaRepository.initialize()
    }

    /**
     * Releases resources used by the Gemma SDK.
     */
    fun shutdownGemma() {

    }
} 