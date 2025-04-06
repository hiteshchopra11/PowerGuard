package com.hackathon.powergaurd.domain.usecase

import com.hackathon.powergaurd.data.model.AnalysisResponse
import com.hackathon.powergaurd.data.model.DeviceData
import com.hackathon.powergaurd.data.remote.PowerGuardRepository
import javax.inject.Inject

/**
 * Use case to analyze device data and save insights
 */
class AnalyzeDeviceDataUseCase @Inject constructor(
    private val repository: PowerGuardRepository,
    private val saveInsightsUseCase: SaveInsightsUseCase
) {
    /**
     * Analyze device data and save insights
     *
     * @param deviceData The device data to analyze
     * @return Result of the analysis operation
     */
    suspend operator fun invoke(deviceData: DeviceData): Result<AnalysisResponse> {
        return try {
            // Call the API service via repository to analyze device data
            val result = repository.analyzeDeviceData(deviceData)

            // If analysis is successful, save insights
            if (result.isSuccess) {
                val response = result.getOrNull()
                if (response != null) {
                    // Convert insights to entities and save them
                    val insightEntities = response.insights.map { it.toEntity() }

                    // Only save if there are insights
                    if (insightEntities.isNotEmpty()) {
                        saveInsightsUseCase(insightEntities)
                    }

                    Result.success(response)
                } else {
                    Result.failure(Exception("No response received from analysis"))
                }
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Analysis failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}