package com.hackathon.powergaurd.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hackathon.powergaurd.data.model.Actionable

/**
 * Room entity representing an actionable item suggested to the user
 */
@Entity(tableName = "device_actionables")
data class DeviceActionableEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val actionableId: String,
    val actionableType: String,
    val packageName: String,
    val description: String,
    val reason: String,
    val newMode: String,
    val timestamp: Long // Timestamp when the actionable was received/saved
) {
    /**
     * Convert entity to domain model
     */
    fun toActionable(): Actionable {
        return Actionable(
            id = actionableId,
            type = actionableType,
            packageName = packageName,
            description = description,
            reason = reason,
            newMode = newMode,
            parameters = mapOf()
        )
    }
} 