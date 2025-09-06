package com.hackathon.powerguard.models

/**
 * Data class to hold network usage data for an app
 */
data class NetworkUsageData(
    val packageName: String,
    val appName: String,
    val mobileDataUsageBytes: Long,
    val wifiDataUsageBytes: Long,
    val backgroundDataUsageBytes: Long,
    val timestamp: Long
) 