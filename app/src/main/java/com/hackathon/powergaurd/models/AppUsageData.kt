package com.hackathon.powergaurd.models

/**
 * Data class to represent app usage statistics. This includes both foreground and background usage
 * time, as well as wakelock time and battery consumption.
 */
data class AppUsageData(
    val packageName: String,
    val appName: String,
    val foregroundTimeMinutes: Long,
    val backgroundTimeMinutes: Long,
    val wakelockTimeMinutes: Long,
    val batteryUsagePercent: Double,
    val timestamp: Long
)
