package com.hackathon.powergaurd.models

data class ActionResponse(
    val actionables: List<Actionable>,
    val summary: String,
    val usagePatterns: Map<String, String>,
    val timestamp: Long
) {
    data class Actionable(
        val type: String,
        val app: String? = null,
        val newMode: String? = null,
        val reason: String? = null,
        val enabled: Boolean? = null
    )
}