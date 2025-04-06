package com.hackathon.powerguard.domain.usecase

import com.hackathon.powerguard.data.model.Actionable
import com.hackathon.powerguard.data.model.AnalysisResponse
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
