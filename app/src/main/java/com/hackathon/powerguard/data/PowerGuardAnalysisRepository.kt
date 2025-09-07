package com.hackathon.powerguard.data

import android.util.Log
import com.hackathon.powerguard.data.ai.AiRepository
import com.hackathon.powerguard.data.model.AnalysisResponse
import com.hackathon.powerguard.data.model.DeviceData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for device data analysis using on-device ML inference via AiInference.
 * This repository serves as the main entry point for AI-powered device analysis.
 */
@Singleton
class PowerGuardAnalysisRepository @Inject constructor(
    private val aiRepository: AiRepository
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
                Log.d(TAG, "Starting on-device analysis with AiInferenceSDK")
                val result = aiRepository.analyzeDeviceData(deviceData)
                Log.d(TAG, "Analysis completed successfully")
                result
            } catch (e: Exception) {
                Log.e(TAG, "On-device analysis failed: ${e.message}", e)
                Result.failure(e)
            }
        }
} 