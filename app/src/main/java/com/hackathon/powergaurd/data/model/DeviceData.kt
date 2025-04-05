package com.hackathon.powergaurd.data.model

/** Data class representing device data sent to the server for analysis. */
data class DeviceData(
        val deviceId: String,
        val timestamp: Long = System.currentTimeMillis(),
        val battery: BatteryData,
        val memory: MemoryData,
        val cpu: CpuData,
        val network: NetworkData,
        val apps: List<AppData>,
        val settings: SettingsData,
        val deviceInfo: DeviceInfo
)

/** Battery information. */
data class BatteryData(
        val level: Int,
        val temperature: Float,
        val voltage: Int,
        val isCharging: Boolean,
        val chargingType: String,
        val health: Int
)

/** Memory information. */
data class MemoryData(
        val totalRam: Long,
        val availableRam: Long,
        val lowMemory: Boolean,
        val threshold: Long
)

/** CPU information. */
data class CpuData(val usage: Float, val temperature: Float, val frequencies: List<Long>)

/** Network information. */
data class NetworkData(
        val type: String,
        val strength: Int,
        val isRoaming: Boolean,
        val dataUsage: DataUsage
)

/** Data usage information. */
data class DataUsage(val foreground: Long, val background: Long)

/** Application data information. */
data class AppData(
        val packageName: String,
        val processName: String,
        val appName: String,
        val isSystemApp: Boolean,
        val lastUsed: Long,
        val foregroundTime: Long,
        val backgroundTime: Long,
        val batteryUsage: Float,
        val dataUsage: DataUsage,
        val memoryUsage: Long,
        val cpuUsage: Float,
        val notifications: Int,
        val crashes: Int
)

/** Device settings information. */
data class SettingsData(
        val batteryOptimization: Boolean,
        val dataSaver: Boolean,
        val powerSaveMode: Boolean,
        val adaptiveBattery: Boolean,
        val autoSync: Boolean
)

/** Device information. */
data class DeviceInfo(
        val manufacturer: String,
        val model: String,
        val osVersion: String,
        val sdkVersion: Int,
        val screenOnTime: Long
)
