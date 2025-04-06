package com.hackathon.powerguard.domain.usecase

import com.hackathon.powerguard.data.local.InsightsDBRepository
import com.hackathon.powerguard.data.local.entity.DeviceInsightEntity
import com.hackathon.powerguard.data.model.AnalysisResponse
import javax.inject.Inject

/**
 * Use case for saving insights from analysis results to the database
 */
class SaveInsightsUseCase @Inject constructor(
    private val insightsDBRepository: InsightsDBRepository
) {
    /**
     * Save insights from analysis response to the database
     *
     * @param deviceId The device ID
     * @param analysisResponse The analysis response containing insights
     */
    suspend operator fun invoke(deviceId: String, analysisResponse: AnalysisResponse) {
        // Convert Insight objects to DeviceInsightEntity objects
        val insightEntities = analysisResponse.insights.map { insight ->
            DeviceInsightEntity(
                deviceId = deviceId,
                insightType = insight.type,
                insightTitle = insight.title,
                insightDescription = insight.description,
                severity = insight.severity,
                timestamp = analysisResponse.timestamp
            )
        }

        // Only save if there are insights to save
        if (insightEntities.isNotEmpty()) {
            insightsDBRepository.saveInsights(insightEntities)
        }
    }
}