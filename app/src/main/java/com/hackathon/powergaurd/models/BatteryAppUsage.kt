package com.hackathon.powergaurd.models

data class BatteryAppUsage(
    val packageName: String,
    val appName: String,
    val percentUsage: Float
)