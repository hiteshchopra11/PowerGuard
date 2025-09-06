package com.hackathon.powerguard.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.hackathon.powerguard.data.local.entity.DeviceInsightEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO interface for accessing device insights data
 */
@Dao
interface DeviceInsightDao {

    /**
     * Insert insights into the database
     *
     * @param insights List of insights to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInsights(insights: List<DeviceInsightEntity>)

    /**
     * Get all insights for a specific device
     *
     * @param deviceId Device ID to filter by
     * @return Flow of all insights for the device, ordered by timestamp (newest first)
     */
    @Query("SELECT * FROM device_insights where id = :deviceId ORDER BY timestamp DESC")
    fun getInsightsForDevice(deviceId: String): Flow<List<DeviceInsightEntity>>

    /**
     * Get all insights sorted by timestamp (newest first)
     *
     * @return List of all insights ordered by timestamp descending
     */
    @Query("SELECT * FROM device_insights ORDER BY timestamp DESC")
    suspend fun getAllInsightsSortedByTimestamp(): List<DeviceInsightEntity>
}