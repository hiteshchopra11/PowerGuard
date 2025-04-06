package com.hackathon.powergaurd.domain.usecase

import com.hackathon.powergaurd.data.local.InsightsDBRepository
import com.hackathon.powergaurd.data.local.entity.DeviceInsightEntity
import javax.inject.Inject

/**
 * Use case to save insights to the database
 */
class SaveInsightsUseCase @Inject constructor(
    private val insightsDBRepository: InsightsDBRepository
) {
    /**
     * Save a list of insights to the database
     *
     * @param insights List of insights to save
     */
    suspend operator fun invoke(insights: List<DeviceInsightEntity>) {
        insightsDBRepository.saveInsights(insights)
    }
}