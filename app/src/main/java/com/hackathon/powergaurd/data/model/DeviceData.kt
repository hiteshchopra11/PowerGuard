package com.hackathon.powergaurd.data.model

/**
 * Data class representing collected device data for analysis
 */
data class DeviceData(
    val deviceId: String,
    val timestamp: Long,
    val battery: BatteryInfo,
    val memory: MemoryInfo,
    val cpu: CpuInfo,
    val network: NetworkInfo,
    val apps: List<AppInfo>,
    val settings: SettingsInfo,
    val deviceInfo: DeviceInfo,
    val prompt: String? = null
)

/**
 * Enhanced battery information model
 */
data class BatteryInfo(
    val level: Int,
    val temperature: Float,
    val voltage: Int,
    val isCharging: Boolean,
    val chargingType: String,
    val health: Int,
    val capacity: Long = -1L, // Battery capacity in mAh if available
    val currentNow: Int = 0   // Current draw in mA if available
)

/**
 * Memory information model (unchanged)
 */
data class MemoryInfo(
    val totalRam: Long,
    val availableRam: Long,
    val lowMemory: Boolean,
    val threshold: Long
)

/**
 * CPU information model (unchanged)
 */
data class CpuInfo(
    val usage: Float,
    val temperature: Float,
    val frequencies: List<Long>
)

/**
 * Enhanced network information model
 */
data class NetworkInfo(
    val type: String,
    val strength: Int,
    val isRoaming: Boolean,
    val dataUsage: DataUsage,
    val activeConnectionInfo: String = "", // Additional connection details
    val linkSpeed: Int = -1,               // Link speed in Mbps (for WiFi)
    val cellularGeneration: String = ""    // 2G/3G/4G/5G
)

/**
 * Enhanced data usage model
 */
data class DataUsage(
    val foreground: Long,    // Combined foreground usage
    val background: Long,    // Combined background usage
    val rxBytes: Long,       // Total received bytes
    val txBytes: Long        // Total transmitted bytes
)

/**
 * Enhanced app information model
 */
data class AppInfo(
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
    val crashes: Int,
    val versionName: String = "",      // App version name
    val versionCode: Long = 0L,        // App version code
    val targetSdkVersion: Int = 0,     // Target SDK version
    val installTime: Long = 0L,        // When app was installed
    val updatedTime: Long = 0L         // When app was last updated
)

/**
 * Settings information model (unchanged)
 */
data class SettingsInfo(
    val batteryOptimization: Boolean,
    val dataSaver: Boolean,
    val powerSaveMode: Boolean,
    val adaptiveBattery: Boolean,
    val autoSync: Boolean
)

/**
 * Device information model (unchanged)
 */
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val osVersion: String,
    val sdkVersion: Int,
    val screenOnTime: Long
)

// Keep the response models as they were
/**
 * Analysis response model from backend
 */
data class AnalysisResponse(
    val id: String = "-1",
    val success: Boolean,
    val timestamp: Long,
    val message: String,
    val actionable: List<Actionable>,
    val insights: List<Insight>,
    val batteryScore: Int,
    val dataScore: Int,
    val performanceScore: Int,
    val estimatedSavings: EstimatedSavings
) {
    /**
     * Estimated savings model
     */
    data class EstimatedSavings(
        val batteryMinutes: Int,
        val dataMB: Int
    )
}

/**
 * Actionable model for recommended actions
 */
data class Actionable(
    val id: String,
    val type: String,
    val packageName: String,
    val description: String,
    val reason: String,
    val newMode: String,
    val parameters: Map<String, String>
)

/**
 * Insight model for providing analysis insights
 */
data class Insight(
    val type: String,
    val title: String,
    val description: String,
    val severity: String
)