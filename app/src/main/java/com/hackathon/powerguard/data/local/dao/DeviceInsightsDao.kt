package com.hackathon.powerguard.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hackathon.powerguard.data.local.entity.DeviceInsightEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for device insights - core database operations
 */
@Dao
interface DeviceInsightDao {

    /**
     * Insert a list of insights into the database
     *
     * @param insights The insights to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInsights(insights: List<DeviceInsightEntity>)

    /**
     * Get all insights for a specific device
     *
     * @param deviceId The device ID to get insights for
     * @return Flow of all insights for the device, ordered by timestamp (newest first)
     */
    @Query("SELECT * FROM device_insights WHERE deviceId = :deviceId ORDER BY timestamp DESC")
    fun getInsightsForDevice(deviceId: String): Flow<List<DeviceInsightEntity>>
}