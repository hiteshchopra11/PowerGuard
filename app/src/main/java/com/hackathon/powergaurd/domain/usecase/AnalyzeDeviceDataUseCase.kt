package com.hackathon.powergaurd.domain.usecase

import com.hackathon.powergaurd.data.PowerGuardAnalysisRepository
import com.hackathon.powergaurd.data.model.AnalysisResponse
import com.hackathon.powergaurd.data.model.DeviceData
import javax.inject.Inject

/**
 * Use case for analyzing device data.
 */
class AnalyzeDeviceDataUseCase @Inject constructor(
    private val repository: PowerGuardAnalysisRepository,
    private val saveInsightsUseCase: SaveInsightsUseCase
) {
    /**
     * Analyzes the provided device data.
     *
     * @param deviceData The device data to analyze
     * @return Result containing analysis response or error
     */
    suspend operator fun invoke(deviceData: DeviceData): Result<AnalysisResponse> {
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
            }
        }
        
        return result
    }
}