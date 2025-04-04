package com.hackathon.powergaurd.models

/**
 * Data class to represent battery information. This includes current battery status, level,
 * temperature, and other metrics.
 */
data class BatteryOptimizationData(
        val batteryLevel: Int,
        val isCharging: Boolean,
        val temperature: Double,
        val voltage: Int,
        val currentAverage: Long,
        val timestamp: Long
)
