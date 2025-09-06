package com.hackathon.powerguard.models

/**
 * Data class to hold battery optimization data
 */
data class BatteryOptimizationData(
    val batteryLevel: Int,
    val isCharging: Boolean,
    val temperature: Double,
    val voltage: Int,
    val currentAverage: Long,
    val timestamp: Long
) 