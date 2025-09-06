package com.hackathon.powerguard.models

/**
 * Data class to hold app usage statistics
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