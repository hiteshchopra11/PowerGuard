package com.hackathon.powergaurd.llm

import com.google.gson.Gson
import com.hackathon.powergaurd.collector.UsageDataCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

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
            - Provide clear, factual information based on the device data
            - Include exact numbers and percentages when available
            - For top-N queries, show exactly N results in ranked order
            - If time period is specified, limit your analysis to that period
            - Keep your response concise and focused on the statistics requested
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
            llmService.getCompletion(prompt)
        }
    }
} 