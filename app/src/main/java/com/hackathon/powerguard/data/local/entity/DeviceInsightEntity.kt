package com.hackathon.powerguard.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

/**
 * Entity for storing device insights in Room database
 */
@Entity(tableName = "device_insights")
data class DeviceInsightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deviceId: String,
    val insightType: String,
    val insightTitle: String,
    val insightDescription: String,
    val severity: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Returns a formatted date string for display
     */
    fun getFormattedDate(): String {
        val date = Date(timestamp)
        return java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
            .format(date)
    }
}

/**
 * Type converters for Room database
 */
class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return gson.toJson(value ?: emptyList<String>())
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType) ?: emptyList()
    }
}