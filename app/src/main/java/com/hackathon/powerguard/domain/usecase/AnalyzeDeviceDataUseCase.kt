package com.hackathon.powerguard.domain.usecase

import com.hackathon.powerguard.data.model.AnalysisResponse
import com.hackathon.powerguard.data.model.DeviceData
import com.hackathon.powerguard.data.remote.PowerGuardRepository
import javax.inject.Inject

/**
 * Use case for analyzing device data and returning optimization recommendations
 * Also automatically saves insights to the database
 */
class AnalyzeDeviceDataUseCase @Inject constructor(
    private val repository: PowerGuardRepository,
    private val saveInsightsUseCase: SaveInsightsUseCase
) {
    /**
     * Executes the use case with the provided device data
     *
     * @param deviceData The collected device data to analyze
     * @return Result containing analysis response with actionable
     */
    suspend operator fun invoke(deviceData: DeviceData): Result<AnalysisResponse> {
        val result = repository.analyzeDeviceData(deviceData)

        // If the analysis was successful, save the insights
        if (result.isSuccess) {
            result.getOrNull()?.let { response ->
                saveInsightsUseCase(deviceData.deviceId, response)
            }
        }

        return result
    }
}