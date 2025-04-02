package com.hackathon.powergaurd.models

data class DeviceData(
    val appUsage: List<AppUsageInfo>,
    val batteryStats: BatteryStats,
    val networkUsage: NetworkUsage,
    val wakeLocks: List<WakeLockInfo>,
    val deviceId: String,
    val timestamp: Long
)

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val foregroundTimeMs: Long,
    val backgroundTimeMs: Long,
    val lastUsed: Long,
    val launchCount: Int
)

data class BatteryStats(
    val level: Int,
    val temperature: Float,
    val isCharging: Boolean,
    val chargingType: String,
    val voltage: Int,
    val health: String,
    val estimatedRemainingTime: Long?
)

data class NetworkUsage(
    val appNetworkUsage: List<AppNetworkInfo>,
    val wifiConnected: Boolean,
    val mobileDataConnected: Boolean,
    val networkType: String
)

data class AppNetworkInfo(
    val packageName: String,
    val dataUsageBytes: Long,
    val wifiUsageBytes: Long
)

data class WakeLockInfo(
    val packageName: String,
    val wakeLockName: String,
    val timeHeldMs: Long
)