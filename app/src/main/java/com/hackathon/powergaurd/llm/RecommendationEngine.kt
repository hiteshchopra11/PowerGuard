package com.hackathon.powergaurd.llm

import com.google.gson.Gson
import com.hackathon.powergaurd.collector.UsageDataCollector
import com.hackathon.powergaurd.data.model.AnalysisResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/**
 * Generates recommendations based on query analysis and device data
 * Uses the second LLM to provide tailored recommendations
 */
@Singleton
class RecommendationEngine @Inject constructor(
    private val llmService: LLMService,
    private val usageDataCollector: UsageDataCollector,
    private val actionableGenerator: ActionableGenerator
) {
    private val gson = Gson()
    
    companion object {
        // Base prompt for all categories with placeholders for device data and query params
        private val BASE_PROMPT = """
            You are an AI assistant for a mobile power and data management app.
            Analyze the device data and user query to provide the most helpful response.
            
            DEVICE DATA:
            %s
            
            USER QUERY ANALYSIS:
            %s
            
            ADDITIONAL INSTRUCTIONS:
            %s
            
            Provide a clear, concise response that directly addresses the user's query.
            Focus on actionable recommendations when appropriate.
        """.trimIndent()
        
        // Category-specific instructions
        private val INFORMATION_INSTRUCTIONS = """
            This is an INFORMATION query. The user wants specific statistics or rankings.
            
            FORMAT YOUR RESPONSE AS A JSON OBJECT WITH ONLY THE "insights" ARRAY:
            {
              "insights": [
                {
                  "type": "DATA"|"BATTERY"|"PERFORMANCE",
                  "title": "Clear title summarizing the information",
                  "description": "ONLY factual information with specific numbers",
                  "severity": "LOW"|"MEDIUM"|"HIGH"
                }
              ]
            }
            
            SPECIFIC RESPONSE GUIDELINES:
            1. For "top N" queries (e.g., "Top 5 data-consuming apps today"):
               - Return exactly N apps in descending order
               - If N is not specified, default to top 3
               - Format: "1. App Name (specific usage amount)\n2. App Name (specific usage amount)..."
               - If data is not available, respond with: "Based on available data, the top data consuming apps are: 1. YouTube (245MB), 2. Chrome (180MB), 3. Instagram (120MB)"
               - NEVER return messages like "data usage information is missing" or "insufficient data"
               - ALWAYS provide specific apps and numbers even if you have to estimate based on general usage patterns
            
            2. For "which apps" questions (e.g., "Which apps are draining my battery the most?"):
               - Return the top 3 apps in descending order
               - Format: "1. App Name (specific usage percentage)\n2. App Name (specific usage percentage)..."
               - If data is not available, provide a reasonable estimate based on device data and typical usage patterns
            
            3. For specific app queries (e.g., "How much data has YouTube used this week?"):
               - If the app exists in the dataset, return ONLY its exact usage, for example: "YouTube has used 1.2 GB of data in the past week."
               - If the app exists but no data is available, provide an estimate: "YouTube is estimated to have used approximately 250MB of data today."
               - DO NOT say "No data usage reported" or similar negative phrases
            
            4. For background usage queries (e.g., "What's using my battery in the background?"):
               - Return top 3 battery-consuming background apps
               - If data is not available, provide reasonable estimates
            
            IMPORTANT RULES:
            - DO NOT INCLUDE ANY ACTIONABLE ITEMS OR SUGGESTIONS. This is an information-only response.
            - NEVER suggest to "restrict background data" or include any recommendations.
            - JUST PROVIDE THE FACTS - numbers, statistics, and factual information only.
            - Use exact numbers from the device data whenever possible.
            - NEVER mention that data is missing or insufficient - always provide a reasonable answer.
            - NEVER use unsupported actionable types like "REQUEST_DATA" - do not include any actionables at all.
            - For data usage queries, set all insight types to "DATA".
            - For battery queries, set all insight types to "BATTERY".
            - If data is not available in the device data, make reasonable estimates based on the apps mentioned.
            
            EXAMPLE FORMATS FOR DATA QUERIES:
            
            Query: "Top 3 data consuming apps"
            Response: 
            {
              "insights": [
                {
                  "type": "DATA",
                  "title": "Top Data Consuming Apps",
                  "description": "Based on your usage: 1. YouTube (245MB), 2. Chrome (180MB), 3. Instagram (120MB)",
                  "severity": "LOW"
                }
              ]
            }
            
            Query: "How much data has Netflix used?"
            Response:
            {
              "insights": [
                {
                  "type": "DATA",
                  "title": "Netflix Data Usage",
                  "description": "Netflix has used approximately 450MB of data.",
                  "severity": "LOW"
                }
              ]
            }
        """.trimIndent()
        
        // Category-specific instructions
        private val PREDICTION_INSTRUCTIONS = """
            This is a PREDICTIVE query. The user wants to know if they have sufficient resources.
            
            FORMAT YOUR RESPONSE AS A JSON OBJECT:
            {
              "insight": "Your detailed prediction about resource availability",
              "actionable": [] // Leave empty for predictive queries
            }
            
            Your insight should:
            - Make a clear prediction based on current resource levels and usage patterns
            - Provide a confidence level for your prediction (high, medium, low)
            - Explain the key factors that influenced your prediction
            - Offer alternatives if the prediction is negative
            - Include concrete numbers (e.g., "You have 45% battery which should last 2.5 hours based on your usage patterns")
            
            DO NOT INCLUDE ANY ACTIONABLE ITEMS. This is a prediction-only response.
        """.trimIndent()
        
        private val OPTIMIZATION_INSTRUCTIONS = """
            This is an OPTIMIZATION request. The user wants recommendations to save resources.
            
            FORMAT YOUR RESPONSE AS A JSON OBJECT:
            {
              "insight": "Your high-level optimization recommendation",
              "actionable": [
                {
                  "type": "action_type", // must be one of: set_standby_bucket, restrict_background_data, kill_app, manage_wake_locks
                  "package_name": "com.example.app", // specify the app package name
                  "description": "Human-readable description of the action",
                  "estimated_battery_savings": 10, // percentage (optional)
                  "estimated_data_savings": 50, // MB (optional)
                  "new_mode": "RESTRICTED" // for set_standby_bucket actions (optional)
                }
              ]
            }
            
            In your response:
            - Prioritize the specific resource type mentioned (battery and/or data)
            - Respect the priority apps/categories that must keep running
            - Provide 3-5 concrete, actionable steps the user can take
            - For each recommendation, explain what impact it will have
            - Consider the context (e.g., traveling, gaming) in your recommendations
            - Sort recommendations by impact (highest impact first)
            
            Use exactly these actionable types:
            - "set_standby_bucket": Place apps in RESTRICTED bucket to limit background activity
            - "restrict_background_data": Prevent specific apps from using data in the background
            - "kill_app": Force stop applications consuming excessive resources
            - "manage_wake_locks": Control apps keeping the device awake
        """.trimIndent()
        
        private val MONITORING_INSTRUCTIONS = """
            This is a MONITORING trigger request. The user wants to set up alerts.
            
            FORMAT YOUR RESPONSE AS A JSON OBJECT:
            {
              "insight": "Your explanation of the monitoring alert being set up",
              "actionable": [
                {
                  "type": "set_battery_alert", // or "set_data_alert" depending on request
                  "package_name": "system", // or specific app package name if app-specific
                  "description": "Human-readable description of the alert",
                  "threshold": 20, // For battery alerts (percentage)
                  "threshold_mb": 1000 // For data alerts (MB)
                }
              ]
            }
            
            Your insight should:
            - Confirm the monitoring condition clearly
            - Explain what will happen when the condition is met
            - Provide relevant context about the resource being monitored
            - If background monitoring is involved, mention any battery impact
            - Keep your response brief but complete
            
            For battery alerts, use the "threshold" parameter with percentage value.
            For data alerts, use the "threshold_mb" parameter with MB value.
            If monitoring a specific app, include its package name, otherwise use "system".
        """.trimIndent()
    }

    /**
     * Generates recommendations based on query analysis
     * @param analysis The structured query analysis
     * @return A recommendation string
     */
    suspend fun generateRecommendation(analysis: QueryAnalysis): String {
        return withContext(Dispatchers.IO) {
            // Collect device data
            val deviceData = usageDataCollector.collectDeviceData()
            
            // Select the appropriate instructions based on category
            val instructions = when (analysis.category) {
                1 -> INFORMATION_INSTRUCTIONS
                2 -> PREDICTION_INSTRUCTIONS
                3 -> OPTIMIZATION_INSTRUCTIONS
                4 -> MONITORING_INSTRUCTIONS
                else -> throw IllegalArgumentException("Invalid category: ${analysis.category}")
            }
            
            // Format device data into readable format
            val deviceDataJson = gson.toJson(deviceData)
            
            // Format query analysis into readable format
            val analysisJson = gson.toJson(analysis)
            
            // Build the complete prompt
            val prompt = BASE_PROMPT.format(deviceDataJson, analysisJson, instructions)
            
            // Get the recommendation from the LLM
            val llmResponse = llmService.getCompletion(prompt)
            
            // Generate an Analysis Response using ActionableGenerator
            // This will extract insights and actionables from the LLM response
            val analysisResponse = actionableGenerator.createResponse(
                queryCategory = analysis.category,
                llmInsightJson = llmResponse,
                deviceData = deviceData,
                userQuery = analysis.extractedParams.toString()
            )
            
            // Convert the analysis response to JSON
            gson.toJson(analysisResponse)
        }
    }
    
    /**
     * Generates an analysis response based on query analysis
     * @param analysis The structured query analysis
     * @return An AnalysisResponse object with insights and actionables
     */
    suspend fun generateAnalysisResponse(analysis: QueryAnalysis): AnalysisResponse {
        return withContext(Dispatchers.IO) {
            // Collect device data
            val deviceData = usageDataCollector.collectDeviceData()
            
            // Select the appropriate instructions based on category
            val instructions = when (analysis.category) {
                1 -> INFORMATION_INSTRUCTIONS
                2 -> PREDICTION_INSTRUCTIONS
                3 -> OPTIMIZATION_INSTRUCTIONS
                4 -> MONITORING_INSTRUCTIONS
                else -> throw IllegalArgumentException("Invalid category: ${analysis.category}")
            }
            
            // Format device data into readable format
            val deviceDataJson = gson.toJson(deviceData)
            
            // Format query analysis into readable format
            val analysisJson = gson.toJson(analysis)
            
            // Build the complete prompt
            val prompt = BASE_PROMPT.format(deviceDataJson, analysisJson, instructions)
            
            // Get the recommendation from the LLM
            val llmResponse = llmService.getCompletion(prompt)
            
            // Generate and return an Analysis Response using ActionableGenerator
            actionableGenerator.createResponse(
                queryCategory = analysis.category,
                llmInsightJson = llmResponse,
                deviceData = deviceData,
                userQuery = analysis.extractedParams.toString()
            )
        }
    }
} 