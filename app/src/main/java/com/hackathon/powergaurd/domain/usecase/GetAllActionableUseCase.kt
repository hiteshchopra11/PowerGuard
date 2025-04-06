package com.hackathon.powergaurd.domain.usecase

import com.hackathon.powergaurd.data.local.entity.DeviceInsightEntity
import com.hackathon.powergaurd.data.model.Actionable
import com.hackathon.powergaurd.data.model.AnalysisResponse
import com.hackathon.powergaurd.data.model.Insight
import javax.inject.Inject

/**
 * Use case to get all actionable from analysis results
 */
class GetAllActionableUseCase @Inject constructor() {
    /**
     * Executes the use case with the provided analysis response
     *
     * @param analysisResponse The full analysis response
     * @return List of all actionable
     */
    operator fun invoke(analysisResponse: AnalysisResponse): List<Actionable> {
        return analysisResponse.actionable
    }
}

fun Insight.toEntity(): DeviceInsightEntity {
    return DeviceInsightEntity(
        insightType = this.type,
        insightTitle = this.title,
        insightDescription = this.description,
        severity = this.severity,
        timestamp = System.currentTimeMillis(),
    )
}
