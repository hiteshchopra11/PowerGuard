package com.hackathon.powerguard.data.local

import com.hackathon.powerguard.data.local.dao.DeviceInsightDao
import com.hackathon.powerguard.data.local.entity.DeviceInsightEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for accessing device insights from the database
 */
@Singleton
class InsightsDBRepository @Inject constructor(
    private val deviceInsightDao: DeviceInsightDao
) {
    /**
     * Save insights to the database
     *
     * @param insights The list of device insights to save
     */
    suspend fun saveInsights(insights: List<DeviceInsightEntity>) {
        deviceInsightDao.insertInsights(insights)
    }

    /**
     * Get all insights for a specific device
     *
     * @param deviceId The device ID
     * @return Flow of device insights ordered by timestamp (newest first)
     */
    fun getInsightsForDevice(deviceId: String): Flow<List<DeviceInsightEntity>> {
        return deviceInsightDao.getInsightsForDevice(deviceId)
    }
}