package com.hackathon.powergaurd.llm

import com.google.gson.annotations.SerializedName

/**
 * Data structures for the LLM query analysis system
 */
data class Duration(
    val value: Int,
    val unit: String // "minutes", "hours", "days"
)

data class TimePeriod(
    val value: Int,
    val unit: String // "hour", "day", "week", "month"
)

data class Thresholds(
    val battery: Int? = null, // percentage
    val data: Int? = null // MB
)

data class ExtractedParameters(
    val apps: List<String>? = null,
    
    @SerializedName("app_categories")
    val appCategories: List<String>? = null,
    
    val duration: Duration? = null,
    
    @SerializedName("time_period")
    val timePeriod: TimePeriod? = null,
    
    @SerializedName("resource_type")
    val resourceType: List<String>? = null,
    
    val thresholds: Thresholds? = null,
    
    val limit: Int? = null,
    
    val context: String? = null,
    
    @SerializedName("priority_apps")
    val priorityApps: List<String>? = null,
    
    @SerializedName("priority_apps_categories")
    val priorityAppCategories: List<String>? = null,
    
    @SerializedName("condition_type")
    val conditionType: String? = null // "while_using", "exceeds_usage", "reaches_threshold"
)

data class QueryAnalysis(
    val category: Int, // 1-4
    
    @SerializedName("extracted_params")
    val extractedParams: ExtractedParameters
) 