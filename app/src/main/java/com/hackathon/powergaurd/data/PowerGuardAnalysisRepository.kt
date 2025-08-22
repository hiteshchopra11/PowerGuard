package com.hackathon.powergaurd.data

import android.util.Log
import com.hackathon.powergaurd.data.gemma.GemmaRepository
import com.hackathon.powergaurd.data.model.AnalysisResponse
import com.hackathon.powergaurd.data.model.DeviceData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for device data analysis using on-device ML inference.
 * Simplified to only use Gemma SDK since remote API is not used.
 */
@Singleton
class PowerGuardAnalysisRepository @Inject constructor(
    private val gemmaRepository: GemmaRepository
) {
    companion object {
        private const val TAG = "PowerGuardAnalysisRepo"
    }

    /**
     * Analyzes device data using on-device ML inference.
     *
     * @param deviceData The collected device data
     * @return Result with AnalysisResponse or error information
     */
    suspend fun analyzeDeviceData(deviceData: DeviceData): Result<AnalysisResponse> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Using Gemma for analysis")
                gemmaRepository.analyzeDeviceData(deviceData)
            } catch (e: Exception) {
                Log.e(TAG, "Analysis failed", e)
                Result.failure(e)
            }
        }

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
        // Gemma SDK resource cleanup if needed
    }
} 