package com.hackathon.powergaurd.models

data class AppDetailState(
    val packageName: String = "",
    val appName: String = "",
    val usageInfo: AppUsageInfo? = null,
    val networkInfo: AppNetworkInfo? = null,
    val wakeLockInfo: WakeLockInfo? = null,
    val usagePattern: String? = null,
    val backgroundRestrictionLevel: String = "none",
    val wakeLockManagement: String = "enable",
    val backgroundDataRestricted: Boolean = false
)