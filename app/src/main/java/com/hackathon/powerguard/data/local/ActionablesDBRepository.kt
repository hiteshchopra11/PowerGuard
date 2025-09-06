package com.hackathon.powerguard.data.local

import com.hackathon.powerguard.data.local.dao.DeviceActionableDao
import com.hackathon.powerguard.data.local.entity.DeviceActionableEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for accessing device actionables from the database
 */
@Singleton
class ActionablesDBRepository @Inject constructor(
    private val deviceActionableDao: DeviceActionableDao
) {
    /**
     * Save actionables to the database
     *
     * @param actionables The list of device actionables to save
     */
    suspend fun saveActionables(actionables: List<DeviceActionableEntity>) {
        deviceActionableDao.insertActionables(actionables)
    }

    /**
     * Save a single actionable to the database
     *
     * @param actionable The device actionable to save
     * @return The ID of the inserted actionable
     */
    suspend fun saveActionable(actionable: DeviceActionableEntity): Long {
        return deviceActionableDao.insertActionable(actionable)
    }

    /**
     * Get all actionables sorted by timestamp (newest first)
     *
     * @return List of device actionables ordered by timestamp
     */
    suspend fun getAllActionablesSortedByTimestamp(): List<DeviceActionableEntity> {
        return deviceActionableDao.getAllActionablesSortedByTimestamp()
    }

    /**
     * Get actionables within a specific time range
     *
     * @param startTime The start timestamp
     * @param endTime The end timestamp
     * @return List of device actionables within the specified time range
     */
    suspend fun getActionablesInTimeRange(startTime: Long, endTime: Long): List<DeviceActionableEntity> {
        return deviceActionableDao.getActionablesInTimeRange(startTime, endTime)
    }
    
    /**
     * Delete actionables older than a specified timestamp
     *
     * @param olderThan The timestamp before which actionables should be deleted
     */
    suspend fun deleteOlderActionables(olderThan: Long) {
        deviceActionableDao.deleteOlderActionables(olderThan)
    }
    
    /**
     * Delete all actionables from the database
     */
    suspend fun deleteAllActionables() {
        deviceActionableDao.deleteAllActionables()
    }
} 