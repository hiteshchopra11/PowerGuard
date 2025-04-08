package com.hackathon.powergaurd.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hackathon.powergaurd.data.local.entity.DeviceActionableEntity

/**
 * Data access object for device actionable entities
 */
@Dao
interface DeviceActionableDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActionable(actionable: DeviceActionableEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActionables(actionables: List<DeviceActionableEntity>)
    
    @Delete
    suspend fun deleteActionable(actionable: DeviceActionableEntity)
    
    @Query("SELECT * FROM device_actionables ORDER BY timestamp DESC")
    suspend fun getAllActionablesSortedByTimestamp(): List<DeviceActionableEntity>
    
    @Query("SELECT * FROM device_actionables WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getActionablesInTimeRange(startTime: Long, endTime: Long): List<DeviceActionableEntity>
    
    @Query("DELETE FROM device_actionables WHERE timestamp < :olderThan")
    suspend fun deleteOlderActionables(olderThan: Long)
    
    @Query("DELETE FROM device_actionables")
    suspend fun deleteAllActionables()
} 