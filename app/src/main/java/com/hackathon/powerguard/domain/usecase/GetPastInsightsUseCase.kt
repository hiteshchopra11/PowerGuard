package com.hackathon.powerguard.domain.usecase

import com.hackathon.powerguard.data.local.InsightsDBRepository
import com.hackathon.powerguard.data.local.entity.DeviceInsightEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for retrieving past insights for a device from the database
 */
class GetPastInsightsUseCase @Inject constructor(
    private val insightsDBRepository: InsightsDBRepository
) {
    /**
     * Get all stored insights for a specific device
     *
     * @param deviceId The device ID
     * @return Flow of all past insights for the device
     */
    operator fun invoke(deviceId: String): Flow<List<DeviceInsightEntity>> {
        return insightsDBRepository.getInsightsForDevice(deviceId)
    }
}