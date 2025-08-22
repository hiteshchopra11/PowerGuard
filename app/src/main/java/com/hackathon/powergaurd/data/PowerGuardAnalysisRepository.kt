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
 * Repository for device data analysis using on-device ML inference via GemmaInferenceSDK.
 * This repository serves as the main entry point for AI-powered device analysis.
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
                Log.d(TAG, "Starting on-device analysis with GemmaInferenceSDK")
                val result = gemmaRepository.analyzeDeviceData(deviceData)
                Log.d(TAG, "Analysis completed successfully")
                result
            } catch (e: Exception) {
                Log.e(TAG, "On-device analysis failed: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * Initializes the GemmaInferenceSDK for on-device inference.
     */
    suspend fun initializeGemma(): Boolean {
        return try {
            Log.d(TAG, "Initializing GemmaInferenceSDK")
            val result = gemmaRepository.initialize()
            Log.d(TAG, "GemmaInferenceSDK initialization result: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize GemmaInferenceSDK: ${e.message}", e)
            false
        }
    }

    /**
     * Releases resources used by the GemmaInferenceSDK.
     * Note: The SDK handles its own lifecycle management through lifecycle observers.
     */
    fun shutdownGemma() {
        Log.d(TAG, "GemmaInferenceSDK shutdown requested - handled by SDK lifecycle management")
        // The GemmaInferenceSDK handles its own resource cleanup through lifecycle observers
        // No explicit cleanup needed here
    }
} 