package com.hackathon.powergaurd.llm

import com.google.gson.Gson
import com.hackathon.powergaurd.collector.UsageDataCollector
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
    private val usageDataCollector: UsageDataCollector
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
                  "type": "Information",
                  "title": "Clear title summarizing the information",
                  "description": "ONLY factual information with specific numbers",
                  "severity": "info"
                }
              ]
            }
            
            SPECIFIC RESPONSE GUIDELINES:
            1. For "top N" queries (e.g., "Top 5 data-consuming apps today"):
               - Return exactly N apps in descending order
               - If N is not specified, default to top 3
               - Format: "1. App Name (specific usage amount)\n2. App Name (specific usage amount)..."
            
            2. For "which apps" questions (e.g., "Which apps are draining my battery the most?"):
               - Return the top 3 apps in descending order
               - Format: "1. App Name (specific usage percentage)\n2. App Name (specific usage percentage)..."
            
            3. For specific app queries (e.g., "How much data has YouTube used this week?"):
               - If the app exists in the dataset, return ONLY its exact usage, for example: "YouTube has used 1.2 GB of data in the past week."
               - If not, return "No data usage reported by [app name]"
               - Do NOT include any recommendations or suggestions
            
            4. For background usage queries (e.g., "What's using my battery in the background?"):
               - Return top 3 battery-consuming background apps
            
            DO NOT INCLUDE ANY ACTIONABLE ITEMS OR SUGGESTIONS. This is an information-only response.
            NEVER suggest to "restrict background data" or include any recommendations.
            JUST PROVIDE THE FACTS - numbers, statistics, and factual information only.
            Use exact numbers from the device data whenever possible.
        """.trimIndent()
        
        private val PREDICTION_INSTRUCTIONS = """
            This is a PREDICTIVE query. The user wants to know if they have sufficient resources.
            - Make a clear prediction based on current resource levels and usage patterns
            - Provide a confidence level for your prediction (high, medium, low)
            - Explain the key factors that influenced your prediction
            - Offer alternatives if the prediction is negative
            - Include concrete numbers (e.g., "You have 45% battery which should last 2.5 hours based on your usage patterns")
        """.trimIndent()
        
        private val OPTIMIZATION_INSTRUCTIONS = """
            This is an OPTIMIZATION request. The user wants recommendations to save resources.
            - Prioritize the specific resource type mentioned (battery and/or data)
            - Respect the priority apps/categories that must keep running
            - Provide 3-5 concrete, actionable steps the user can take
            - For each recommendation, explain what impact it will have
            - Consider the context (e.g., traveling, gaming) in your recommendations
            - Sort recommendations by impact (highest impact first)
        """.trimIndent()
        
        private val MONITORING_INSTRUCTIONS = """
            This is a MONITORING trigger request. The user wants to set up alerts.
            - Confirm the monitoring condition clearly
            - Explain what will happen when the condition is met
            - Provide any relevant additional context about the resource being monitored
            - If background monitoring is involved, mention any battery impact
            - Keep your response brief but complete
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
            
            // For information queries (category 1), ensure we have the correct response format
            if (analysis.category == 1) {
                try {
                    // Try to parse the response as JSON
                    val responseJson = try {
                        // If it's already JSON, keep it as is
                        if (llmResponse.trim().startsWith("{") && llmResponse.trim().endsWith("}")) {
                            // Parse the JSON and ensure it only contains insights, not actionables
                            try {
                                val jsonObject = JSONObject(llmResponse)
                                // If it contains actionables, remove them
                                if (jsonObject.has("actionable")) {
                                    jsonObject.remove("actionable")
                                    jsonObject.toString()
                                } else {
                                    llmResponse
                                }
                            } catch (e: Exception) {
                                llmResponse
                            }
                        } else {
                            // If not JSON, wrap it in a simple JSON structure
                            """
                            {
                              "insights": [
                                {
                                  "type": "Information",
                                  "title": "Usage Information",
                                  "description": "${llmResponse.replace("\"", "\\\"").replace("\n", "\\n")}",
                                  "severity": "info"
                                }
                              ]
                            }
                            """.trimIndent()
                        }
                    } catch (e: Exception) {
                        // If there's any issue, ensure we have a valid response
                        """
                        {
                          "insights": [
                            {
                              "type": "Information",
                              "title": "Usage Information",
                              "description": "Could not retrieve the requested information.",
                              "severity": "info"
                            }
                          ]
                        }
                        """.trimIndent()
                    }
                    
                    return@withContext responseJson
                } catch (e: Exception) {
                    return@withContext llmResponse
                }
            }
            
            llmResponse
        }
    }
} 