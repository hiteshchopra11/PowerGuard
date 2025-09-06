package com.hackathon.powerguard.domain.usecase

import com.hackathon.powerguard.data.local.InsightsDBRepository
import com.hackathon.powerguard.data.local.entity.DeviceInsightEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case to retrieve past insights for a specific device
 */
class GetPastInsightsUseCase @Inject constructor(
    private val insightsDBRepository: InsightsDBRepository
) {
    /**
     * Operator method to support direct invocation of the use case
     *
     * @param deviceId Device ID to retrieve insights for
     * @return Flow of device insights ordered by timestamp
     */
    operator fun invoke(deviceId: String): Flow<List<DeviceInsightEntity>> {
        return insightsDBRepository.getInsightsForDevice(deviceId)
    }
}