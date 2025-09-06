package com.hackathon.powerguard.domain.usecase

import com.hackathon.powerguard.data.model.AnalysisResponse
import com.hackathon.powerguard.data.model.Insight
import javax.inject.Inject


/**
 * Use case to get all insights from analysis
 */
class GetCurrentInsightsUseCase @Inject constructor() {
    /**
     * Executes the use case with the provided analysis response
     *
     * @param analysisResponse The full analysis response
     * @return List of all insights
     */
    operator fun invoke(analysisResponse: AnalysisResponse): List<Insight> {
        return analysisResponse.insights
    }
}