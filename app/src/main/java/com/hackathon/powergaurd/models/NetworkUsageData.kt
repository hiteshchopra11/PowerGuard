package com.hackathon.powergaurd.models

/**
 * Data class to represent network usage statistics. This includes mobile data and WiFi usage, as
 * well as background data consumption.
 */
data class NetworkUsageData(
    val packageName: String,
    val appName: String,
    val mobileDataUsageBytes: Long,
    val wifiDataUsageBytes: Long,
    val backgroundDataUsageBytes: Long,
    val timestamp: Long
)
