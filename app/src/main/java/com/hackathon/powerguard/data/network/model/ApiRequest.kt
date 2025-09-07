package com.hackathon.powerguard.data.network.model

import com.google.gson.annotations.SerializedName

/**
 * Network request model for PowerGuard backend API
 * Matches the backend API contract exactly
 */
data class ApiRequest(
    @SerializedName("deviceId")
    val deviceId: String,
    
    @SerializedName("timestamp")
    val timestamp: Long,
    
    @SerializedName("battery")
    val battery: ApiBatteryInfo,
    
    @SerializedName("memory")
    val memory: ApiMemoryInfo,
    
    @SerializedName("cpu")
    val cpu: ApiCpuInfo,
    
    @SerializedName("network")
    val network: ApiNetworkInfo,
    
    @SerializedName("apps")
    val apps: List<ApiAppInfo>,
    
    @SerializedName("prompt")
    val prompt: String? = null
)

data class ApiBatteryInfo(
    @SerializedName("level")
    val level: Double,
    
    @SerializedName("temperature")
    val temperature: Double,
    
    @SerializedName("voltage")
    val voltage: Double,
    
    @SerializedName("isCharging")
    val isCharging: Boolean,
    
    @SerializedName("chargingType")
    val chargingType: String,
    
    @SerializedName("health")
    val health: Int,
    
    @SerializedName("capacity")
    val capacity: Double,
    
    @SerializedName("currentNow")
    val currentNow: Double
)

data class ApiMemoryInfo(
    @SerializedName("totalRam")
    val totalRam: Long,
    
    @SerializedName("availableRam")
    val availableRam: Long,
    
    @SerializedName("lowMemory")
    val lowMemory: Boolean,
    
    @SerializedName("threshold")
    val threshold: Long
)

data class ApiCpuInfo(
    @SerializedName("usage")
    val usage: Double,
    
    @SerializedName("temperature")
    val temperature: Double,
    
    @SerializedName("frequencies")
    val frequencies: List<Int>
)

data class ApiNetworkInfo(
    @SerializedName("type")
    val type: String,
    
    @SerializedName("strength")
    val strength: Double,
    
    @SerializedName("isRoaming")
    val isRoaming: Boolean,
    
    @SerializedName("dataUsage")
    val dataUsage: ApiDataUsage,
    
    @SerializedName("activeConnectionInfo")
    val activeConnectionInfo: String,
    
    @SerializedName("linkSpeed")
    val linkSpeed: Double,
    
    @SerializedName("cellularGeneration")
    val cellularGeneration: String
)

data class ApiDataUsage(
    @SerializedName("foreground")
    val foreground: Double,
    
    @SerializedName("background")
    val background: Double,
    
    @SerializedName("rxBytes")
    val rxBytes: Long,
    
    @SerializedName("txBytes")
    val txBytes: Long
)

data class ApiAppInfo(
    @SerializedName("packageName")
    val packageName: String,
    
    @SerializedName("processName")
    val processName: String,
    
    @SerializedName("appName")
    val appName: String,
    
    @SerializedName("isSystemApp")
    val isSystemApp: Boolean,
    
    @SerializedName("lastUsed")
    val lastUsed: Long,
    
    @SerializedName("foregroundTime")
    val foregroundTime: Long,
    
    @SerializedName("backgroundTime")
    val backgroundTime: Long,
    
    @SerializedName("batteryUsage")
    val batteryUsage: Double,
    
    @SerializedName("dataUsage")
    val dataUsage: ApiDataUsage,
    
    @SerializedName("memoryUsage")
    val memoryUsage: Double,
    
    @SerializedName("cpuUsage")
    val cpuUsage: Double,
    
    @SerializedName("notifications")
    val notifications: Int,
    
    @SerializedName("crashes")
    val crashes: Int,
    
    @SerializedName("versionName")
    val versionName: String,
    
    @SerializedName("versionCode")
    val versionCode: Long,
    
    @SerializedName("targetSdkVersion")
    val targetSdkVersion: Int,
    
    @SerializedName("installTime")
    val installTime: Long,
    
    @SerializedName("updatedTime")
    val updatedTime: Long,
    
    @SerializedName("alarmWakeups")
    val alarmWakeups: Int,
    
    @SerializedName("currentPriority")
    val currentPriority: String,
    
    @SerializedName("bucket")
    val bucket: String
)