package com.hackathon.powergaurd.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Database entity for storing device insights.
 */
@Entity(tableName = "device_insights")
data class DeviceInsightEntity(
    @PrimaryKey val id: Long = 0,
    val insightType: String, // e.g., "BATTERY", "NETWORK", "CPU"
    val insightTitle: String,
    val insightDescription: String,
    val severity: String, // "LOW", "MEDIUM", "HIGH"
    val timestamp: Long
) {
    /**
     * Returns a formatted date string from the timestamp.
     */
    fun getFormattedDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
}