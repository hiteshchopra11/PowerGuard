package com.hackathon.powerguard.data.network.model

import com.google.gson.annotations.SerializedName

/**
 * Network response model for PowerGuard backend API
 * Matches the backend API contract exactly
 */
data class ApiResponse(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("timestamp")
    val timestamp: Long,
    
    @SerializedName("message")
    val message: String,
    
    @SerializedName("responseType")
    val responseType: String,
    
    @SerializedName("actionable")
    val actionable: List<ApiActionable>,
    
    @SerializedName("insights")
    val insights: List<ApiInsight>,
    
    @SerializedName("batteryScore")
    val batteryScore: Double,
    
    @SerializedName("dataScore")
    val dataScore: Double,
    
    @SerializedName("performanceScore")
    val performanceScore: Double,
    
    @SerializedName("estimatedSavings")
    val estimatedSavings: ApiEstimatedSavings
)

data class ApiActionable(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("type")
    val type: String,
    
    @SerializedName("description")
    val description: String,
    
    @SerializedName("package_name")
    val packageName: String,
    
    @SerializedName("estimated_battery_savings")
    val estimatedBatterySavings: Double,
    
    @SerializedName("estimated_data_savings")
    val estimatedDataSavings: Double,
    
    @SerializedName("severity")
    val severity: Int,
    
    @SerializedName("new_mode")
    val newMode: String?,
    
    @SerializedName("enabled")
    val enabled: Boolean,
    
    @SerializedName("reason")
    val reason: String,
    
    @SerializedName("parameters")
    val parameters: Map<String, Any>
)

data class ApiInsight(
    @SerializedName("type")
    val type: String,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("description")
    val description: String,
    
    @SerializedName("severity")
    val severity: String
)

data class ApiEstimatedSavings(
    @SerializedName("batteryMinutes")
    val batteryMinutes: Double,
    
    @SerializedName("dataMB")
    val dataMB: Double
)