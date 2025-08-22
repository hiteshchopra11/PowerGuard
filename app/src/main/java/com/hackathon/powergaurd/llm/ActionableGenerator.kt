package com.hackathon.powergaurd.llm

import android.util.Log
import com.google.gson.JsonObject
import com.hackathon.powergaurd.actionable.ActionableTypes
import com.hackathon.powergaurd.data.model.Actionable
import com.hackathon.powergaurd.data.model.AnalysisResponse
import com.hackathon.powergaurd.data.model.DeviceData
import com.hackathon.powergaurd.data.model.Insight
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class that generates actionables and insights based on query category
 */
@Singleton
class ActionableGenerator @Inject constructor() {

    companion object {
        private const val TAG = "ActionableGenerator"
        
        // Query categories
        const val CATEGORY_INFORMATION = 1
        const val CATEGORY_PREDICTIVE = 2
        const val CATEGORY_OPTIMIZATION = 3
        const val CATEGORY_MONITORING = 4
        const val CATEGORY_INVALID = 0
    }
    
    /**
     * Create a response for the given query result, device data, and user prompt
     */
    fun createResponse(
        queryCategory: Int,
        llmInsightJson: String,
        deviceData: DeviceData,
        userQuery: String
    ): AnalysisResponse {
        val insights = mutableListOf<Insight>()
        val actionables = mutableListOf<Actionable>()
        
        // Parse the LLM response
        var insightText = llmInsightJson
        
        // If the response is a JSON object, extract the insight field
        if (llmInsightJson.trim().startsWith("{")) {
            try {
                val jsonObject = com.google.gson.JsonParser.parseString(llmInsightJson).asJsonObject
                
                // Handle insights based on query category
                when (queryCategory) {
                    CATEGORY_INFORMATION -> {
                        // For information queries, check if there's an "insights" array
                        if (jsonObject.has("insights") && !jsonObject.get("insights").isJsonNull) {
                            val insightsArray = jsonObject.getAsJsonArray("insights")
                            for (i in 0 until insightsArray.size()) {
                                val insightObj = insightsArray[i].asJsonObject
                                
                                val type = if (insightObj.has("type")) insightObj.get("type").asString else "INFORMATION"
                                val title = if (insightObj.has("title")) insightObj.get("title").asString else "Data Usage Information"
                                val description = insightObj.get("description").asString
                                val severity = if (insightObj.has("severity")) insightObj.get("severity").asString else "MEDIUM"
                                
                                insights.add(
                                    Insight(
                                        type = type,
                                        title = title,
                                        description = description,
                                        severity = severity
                                    )
                                )
                            }
                        } else {
                            // If no insights array found, use the raw response as a single insight
                            insightText = "No specific data usage information available. Please check your device settings for accurate data usage statistics."
                            insights.add(
                                Insight(
                                    type = "DATA",
                                    title = "Data Usage Information",
                                    description = insightText,
                                    severity = "MEDIUM"
                                )
                            )
                        }
                        
                        // For information queries, we explicitly ignore any actionables, even if present in the response
                        Log.d(TAG, "Information query - ignoring any actionables from LLM response")
                    }
                    
                    else -> {
                        // For non-information queries, handle insight and actionables normally
                        if (jsonObject.has("insight") && !jsonObject.get("insight").isJsonNull) {
                            insightText = jsonObject.get("insight").asString
                        }
                        
                        // Process actionables if present
                        if (jsonObject.has("actionable") && !jsonObject.get("actionable").isJsonNull) {
                            val actionableArray = jsonObject.getAsJsonArray("actionable")
                            
                            // Process each actionable
                            for (i in 0 until actionableArray.size()) {
                                val actionableObj = actionableArray[i].asJsonObject
                                
                                val type = actionableObj.get("type").asString
                                
                                // Skip unsupported actionable types
                                if (!isValidActionableType(type)) {
                                    Log.w(TAG, "Skipping unsupported actionable type: $type")
                                    continue
                                }
                                
                                val packageName = actionableObj.get("package_name").asString
                                val description = actionableObj.get("description").asString
                                
                                // Extract optional fields
                                val params = mutableMapOf<String, Any>()
                                
                                if (actionableObj.has("threshold")) {
                                    params["threshold"] = actionableObj.get("threshold").asInt
                                } else if (actionableObj.has("threshold_mb")) {
                                    params["threshold_mb"] = actionableObj.get("threshold_mb").asInt
                                }
                                
                                if (actionableObj.has("new_mode")) {
                                    val newMode = actionableObj.get("new_mode").asString
                                    params["new_mode"] = newMode
                                }
                                
                                actionables.add(
                                    Actionable(
                                        id = UUID.randomUUID().toString(),
                                        type = type,
                                        packageName = packageName,
                                        description = description,
                                        reason = if (actionableObj.has("reason")) actionableObj.get("reason").asString else description,
                                        newMode = actionableObj.get("new_mode")?.asString,
                                        estimatedBatterySavings = 
                                            if (actionableObj.has("estimated_battery_savings")) 
                                                actionableObj.get("estimated_battery_savings").asFloat else null,
                                        estimatedDataSavings = 
                                            if (actionableObj.has("estimated_data_savings")) 
                                                actionableObj.get("estimated_data_savings").asFloat else null,
                                        severity = 
                                            if (actionableObj.has("severity")) 
                                                actionableObj.get("severity").asInt else 3,
                                        enabled = true,
                                        throttleLevel = 
                                            if (actionableObj.has("throttle_level")) 
                                                actionableObj.get("throttle_level").asInt else null,
                                        parameters = params
                                    )
                                )
                            }
                        }
                        
                        // Add the insight if none were added yet
                        if (insights.isEmpty()) {
                            insights.add(
                                Insight(
                                    type = getInsightTypeForCategory(queryCategory),
                                    title = getInsightTitleForCategory(queryCategory),
                                    description = insightText,
                                    severity = "MEDIUM"
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing LLM response as JSON", e)
                // Use the raw text as insight
                insights.add(
                    Insight(
                        type = getInsightTypeForCategory(queryCategory),
                        title = getInsightTitleForCategory(queryCategory),
                        description = insightText,
                        severity = "MEDIUM"
                    )
                )
            }
        } else {
            // If not JSON, use the raw text as insight
            insights.add(
                Insight(
                    type = getInsightTypeForCategory(queryCategory),
                    title = getInsightTitleForCategory(queryCategory),
                    description = insightText,
                    severity = "MEDIUM"
                )
            )
        }
        
        // Do not generate actionables for information queries
        if (queryCategory != CATEGORY_INFORMATION) {
            // Generate actionables based on category if none were provided in the JSON
            if (actionables.isEmpty() && queryCategory == CATEGORY_MONITORING) {
                actionables.addAll(generateMonitoringActionables(userQuery, deviceData))
            } else if (actionables.isEmpty() && queryCategory == CATEGORY_OPTIMIZATION) {
                actionables.addAll(generateOptimizationActionables(userQuery, deviceData))
            }
        }
        
        // For information queries, ensure insights match the query resource type
        if (queryCategory == CATEGORY_INFORMATION) {
            // Filter insights based on query content
            if (userQuery.contains("battery", ignoreCase = true)) {
                // Keep only battery insights for battery queries
                insights.removeAll { it.type != "BATTERY" }
                // If no battery insights, add a default one
                if (insights.isEmpty()) {
                    insights.add(Insight("BATTERY", "Battery Usage", "No significant battery usage detected.", "LOW"))
                }
            } else if (userQuery.contains("data", ignoreCase = true)) {
                // Keep only data insights for data queries
                insights.removeAll { it.type != "DATA" }
                // If no data insights, add a default one
                if (insights.isEmpty()) {
                    insights.add(Insight("DATA", "Data Usage", "No significant data usage detected.", "LOW"))
                }
            }
        }
        
        return AnalysisResponse(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis().toFloat(),
            success = true,
            message = "Analysis of ${getCategoryName(queryCategory)} query",
            responseType = getCategoryName(queryCategory),
            actionable = actionables,
            insights = insights,
            batteryScore = 75f,  // Placeholder values
            dataScore = 80f,
            performanceScore = 70f,
            estimatedSavings = AnalysisResponse.EstimatedSavings(
                batteryMinutes = 15f,
                dataMB = 200f
            )
        )
    }
    
    /**
     * Check if the actionable type is valid and supported
     */
    private fun isValidActionableType(type: String): Boolean {
        return when (type) {
            ActionableTypes.KILL_APP,
            ActionableTypes.MANAGE_WAKE_LOCKS,
            ActionableTypes.RESTRICT_BACKGROUND_DATA,
            ActionableTypes.SET_STANDBY_BUCKET,
            ActionableTypes.SET_BATTERY_ALERT,
            ActionableTypes.SET_DATA_ALERT -> true
            else -> false
        }
    }
    
    /**
     * Generate monitoring actionables based on the user query
     */
    private fun generateMonitoringActionables(userQuery: String, deviceData: DeviceData): List<Actionable> {
        val actionables = mutableListOf<Actionable>()
        val lowerQuery = userQuery.lowercase()
        
        // Determine if this is a battery or data alert
        val isBatteryQuery = lowerQuery.contains("battery") || 
                            lowerQuery.contains("charge") || 
                            lowerQuery.contains("power")
                            
        val isDataQuery = lowerQuery.contains("data") || 
                         lowerQuery.contains("mb") || 
                         lowerQuery.contains("gb")
        
        // Extract threshold values
        val batteryPattern = "(\\d+)%".toRegex()
        val gbPattern = "(\\d+)\\s*gb".toRegex()
        val mbPattern = "(\\d+)\\s*mb".toRegex()
        
        val batteryMatch = batteryPattern.find(lowerQuery)
        val gbMatch = gbPattern.find(lowerQuery)
        val mbMatch = mbPattern.find(lowerQuery)
        
        // Extract app name if present
        val appPackage = findMentionedApp(lowerQuery, deviceData)
        
        if (isBatteryQuery) {
            val threshold = batteryMatch?.groupValues?.get(1)?.toIntOrNull() ?: 20
            
            actionables.add(
                Actionable(
                    id = UUID.randomUUID().toString(),
                    type = ActionableTypes.SET_BATTERY_ALERT,
                    packageName = appPackage ?: "system",
                    description = "Alert when battery level reaches $threshold%",
                    reason = "User requested battery monitoring",
                    newMode = null,
                    estimatedBatterySavings = null,
                    estimatedDataSavings = null,
                    severity = 2,
                    enabled = true,
                    throttleLevel = null,
                    parameters = mapOf("threshold" to threshold)
                )
            )
        }
        
        if (isDataQuery) {
            val thresholdMB = when {
                gbMatch != null -> gbMatch.groupValues[1].toInt() * 1000
                mbMatch != null -> mbMatch.groupValues[1].toInt()
                else -> 1000 // Default 1GB
            }
            
            actionables.add(
                Actionable(
                    id = UUID.randomUUID().toString(),
                    type = ActionableTypes.SET_DATA_ALERT,
                    packageName = appPackage ?: "system",
                    description = "Alert when data usage reaches $thresholdMB MB",
                    reason = "User requested data usage monitoring",
                    newMode = null,
                    estimatedBatterySavings = null,
                    estimatedDataSavings = null,
                    severity = 2,
                    enabled = true,
                    throttleLevel = null,
                    parameters = mapOf("threshold_mb" to thresholdMB)
                )
            )
        }
        
        return actionables
    }
    
    /**
     * Generate optimization actionables based on the user query
     */
    private fun generateOptimizationActionables(userQuery: String, deviceData: DeviceData): List<Actionable> {
        val actionables = mutableListOf<Actionable>()
        val lowerQuery = userQuery.lowercase()
        
        // Determine optimization type
        val isBatteryOptimization = lowerQuery.contains("battery") || 
                                    lowerQuery.contains("power") ||
                                    lowerQuery.contains("charge")
                                    
        val isDataOptimization = lowerQuery.contains("data") || 
                                 lowerQuery.contains("network") ||
                                 lowerQuery.contains("wifi") ||
                                 lowerQuery.contains("mobile")
        
        // Find apps to optimize
        val appsToKeep = extractAppsToKeep(lowerQuery, deviceData)
        val appsToOptimize = deviceData.apps
            .filter { app -> !appsToKeep.contains(app.packageName) }
            .sortedByDescending { 
                if (isBatteryOptimization) it.batteryUsage else it.dataUsage.background.toFloat()
            }
            .take(3)
            .map { it.packageName }
        
        // Create actionables for each app
        appsToOptimize.forEach { packageName ->
            if (isBatteryOptimization) {
                actionables.add(
                    Actionable(
                        id = UUID.randomUUID().toString(),
                        type = ActionableTypes.SET_STANDBY_BUCKET,
                        packageName = packageName,
                        description = "Restrict background activity for $packageName",
                        reason = "High battery usage in the background",
                        newMode = "RESTRICTED",
                        estimatedBatterySavings = 10f,
                        estimatedDataSavings = null,
                        severity = 3,
                        enabled = true,
                        throttleLevel = null,
                        parameters = mapOf("new_bucket" to "RESTRICTED")
                    )
                )
            }
            
            if (isDataOptimization) {
                actionables.add(
                    Actionable(
                        id = UUID.randomUUID().toString(),
                        type = ActionableTypes.RESTRICT_BACKGROUND_DATA,
                        packageName = packageName,
                        description = "Restrict background data for $packageName",
                        reason = "High data usage in the background",
                        newMode = null,
                        estimatedBatterySavings = null,
                        estimatedDataSavings = 20f,
                        severity = 3,
                        enabled = true,
                        throttleLevel = null,
                        parameters = emptyMap()
                    )
                )
            }
        }
        
        return actionables
    }
    
    /**
     * Extract apps mentioned in the query that should not be optimized
     */
    private fun extractAppsToKeep(query: String, deviceData: DeviceData): List<String> {
        val keepApps = mutableListOf<String>()
        val lowerQuery = query.lowercase()
        
        // Use real installed apps from device data
        deviceData.apps.forEach { appInfo ->
            val appName = appInfo.appName.lowercase()
            val packageName = appInfo.packageName
            
            // Check if this app is mentioned in the query
            if (lowerQuery.contains(appName) || 
                lowerQuery.contains(packageName) ||
                appName.split(" ").any { word -> lowerQuery.contains(word) && word.length > 3 }) {
                keepApps.add(packageName)
                Log.d(TAG, "User mentioned app to keep: ${appInfo.appName} ($packageName)")
            }
        }
        
        // Also consider apps that are in the device data
        deviceData.apps.forEach { app ->
            val appNameLower = app.appName.lowercase()
            if (lowerQuery.contains(appNameLower)) {
                keepApps.add(app.packageName)
            }
        }
        
        return keepApps.distinct()
    }
    
    /**
     * Find app package based on app name mentioned in query
     */
    private fun findMentionedApp(query: String, deviceData: DeviceData): String? {
        val lowerQuery = query.lowercase()
        
        // Use real installed apps from device data instead of hardcoded fake package names
        deviceData.apps.forEach { appInfo ->
            val appName = appInfo.appName.lowercase()
            val packageName = appInfo.packageName
            
            // Check if this app is mentioned in the query
            if (lowerQuery.contains(appName) || 
                lowerQuery.contains(packageName) ||
                appName.split(" ").any { word -> lowerQuery.contains(word) && word.length > 3 }) {
                Log.d(TAG, "Found mentioned app: ${appInfo.appName} ($packageName)")
                return packageName
            }
        }
        
        return null
    }
    
    /**
     * Get the insight type based on query category
     */
    private fun getInsightTypeForCategory(category: Int): String {
        return when (category) {
            CATEGORY_INFORMATION -> "INFORMATION"
            CATEGORY_PREDICTIVE -> "PREDICTION"
            CATEGORY_OPTIMIZATION -> "OPTIMIZATION"
            CATEGORY_MONITORING -> "MONITORING"
            else -> "UNKNOWN"
        }
    }
    
    /**
     * Get a human-readable insight title based on query category
     */
    private fun getInsightTitleForCategory(category: Int): String {
        return when (category) {
            CATEGORY_INFORMATION -> "Usage Information"
            CATEGORY_PREDICTIVE -> "Resource Prediction"
            CATEGORY_OPTIMIZATION -> "Optimization Recommendations"
            CATEGORY_MONITORING -> "Monitoring Setup"
            else -> "Analysis Result"
        }
    }
    
    /**
     * Get the name of the category
     */
    private fun getCategoryName(category: Int): String {
        return when (category) {
            CATEGORY_INFORMATION -> "Information"
            CATEGORY_PREDICTIVE -> "Predictive"
            CATEGORY_OPTIMIZATION -> "Optimization"
            CATEGORY_MONITORING -> "Monitoring"
            else -> "Unknown"
        }
    }
} 