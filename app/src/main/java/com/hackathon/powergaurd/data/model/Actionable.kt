package com.hackathon.powergaurd.data.model

/** Data class representing an actionable recommendation from the backend. */
data class Actionable(
        val id: String,
        val type: String,
        val packageName: String? = null,
        val priority: Int = 0,
        val description: String? = null,
        val reason: String? = null,
        val parameters: Map<String, Any> = emptyMap()
)

/** An insight or analysis from the backend. */
data class Insight(
        val type: String,
        val title: String,
        val description: String,
        val severity: String
)

/** Estimated savings if the recommended actions are taken. */
data class SavingsEstimate(val batteryMinutes: Int, val dataMB: Int, val storageMB: Int)
