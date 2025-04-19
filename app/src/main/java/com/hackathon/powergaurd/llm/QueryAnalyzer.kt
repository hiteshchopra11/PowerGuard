package com.hackathon.powergaurd.llm

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Analyzes user queries about battery and data usage
 * Uses the first LLM to categorize and extract parameters
 */
@Singleton
class QueryAnalyzer @Inject constructor(private val llmService: LLMService) {
    
    private val gson = Gson()
    
    companion object {
        private val PROMPT_TEMPLATE = """
            You are an AI that analyzes user queries about battery and data usage on mobile devices. Your task is to:
            1. Categorize the query into one of four types
            2. Extract all relevant parameters
            3. Return a structured JSON response
            
            Categories:
            1. Information Queries (usage statistics, rankings, historical data)
            2. Predictive Queries (resource availability estimates)
            3. Optimization Requests (resource management recommendations)
            4. Monitoring Triggers (threshold-based alerts)
            
            Return a JSON object with:
            - category: number 1-4
            - extracted_params: {
                // Apps and Categories
                apps: array of specific apps mentioned
                app_categories: array of app types (e.g., "streaming", "messaging", "games", "navigation")
                
                // Time-related parameters
                duration: {
                    value: number
                    unit: "minutes"|"hours"|"days"
                }
                time_period: {
                    value: number
                    unit: "hour"|"day"|"week"|"month"
                }
                
                // Resource parameters
                resource_type: array of ["battery"|"data"]
                thresholds: {
                    battery: percentage value (0-100)
                    data: MB value
                }
                
                // Query-specific parameters
                limit: number (for top-N queries)
                context: string (e.g., "traveling", "commute", "gaming")
                
                // Priority/Optimization parameters
                priority_apps: array of apps that must keep running
                priority_apps_categories: array of app categories that must keep running
                
                // Monitoring specifics
                condition_type: "while_using"|"exceeds_usage"|"reaches_threshold"
            }
            
            Only include parameters that are explicitly mentioned or clearly implied in the query.
            Omit any fields that are not relevant to the query.
            
            Example queries and responses:
            
            1. Information Query Examples:
            Query: "Show me top 5 battery-draining apps from last week"
            {
                "category": 1,
                "extracted_params": {
                    "resource_type": ["battery"],
                    "limit": 5,
                    "time_period": {
                        "value": 1,
                        "unit": "week"
                    }
                }
            }
            
            Query: "Which apps are draining my battery the most?"
            {
                "category": 1,
                "extracted_params": {
                    "resource_type": ["battery"],
                    "limit": 3
                }
            }
            
            Query: "Top 5 data-consuming apps today"
            {
                "category": 1,
                "extracted_params": {
                    "resource_type": ["data"],
                    "limit": 5,
                    "time_period": {
                        "value": 1,
                        "unit": "day"
                    }
                }
            }
            
            Query: "What's using my battery in the background?"
            {
                "category": 1,
                "extracted_params": {
                    "resource_type": ["battery"],
                    "limit": 3,
                    "context": "background"
                }
            }
            
            Query: "How much data has YouTube used this week?"
            {
                "category": 1,
                "extracted_params": {
                    "apps": ["YouTube"],
                    "resource_type": ["data"],
                    "time_period": {
                        "value": 1,
                        "unit": "week"
                    }
                }
            }
            
            2. Predictive Query Examples:
            Query: "Can I watch Netflix and use WhatsApp for next 3 hours with current battery?"
            {
                "category": 2,
                "extracted_params": {
                    "apps": ["Netflix", "WhatsApp"],
                    "app_categories": ["streaming", "messaging"],
                    "duration": {
                        "value": 3,
                        "unit": "hours"
                    },
                    "resource_type": ["battery"]
                }
            }
            
            3. Optimization Request Examples:
            Query: "I'm traveling for 8 hours, save battery but keep Maps and Gmail running"
            {
                "category": 3,
                "extracted_params": {
                    "duration": {
                        "value": 8,
                        "unit": "hours"
                    },
                    "resource_type": ["battery"],
                    "context": "traveling",
                    "priority_apps": ["Maps", "Gmail"],
                    "app_categories": ["navigation", "email"]
                }
            }
            
            4. Monitoring Trigger Examples:
            Query: "Notify me when TikTok uses more than 2GB of data while using"
            {
                "category": 4,
                "extracted_params": {
                    "apps": ["TikTok"],
                    "app_categories": ["social"],
                    "resource_type": ["data"],
                    "thresholds": {
                        "data": 2000
                    },
                    "condition_type": "exceeds_usage"
                }
            }
            
            Query: "Alert me if battery drops to 15%% while gaming"
            {
                "category": 4,
                "extracted_params": {
                    "app_categories": ["games"],
                    "resource_type": ["battery"],
                    "thresholds": {
                        "battery": 15
                    },
                    "condition_type": "while_using"
                }
            }
            
            Important Notes:
            1. Convert all data values to MB (e.g., 2GB = 2000MB)
            2. Normalize time units when possible (prefer hours over minutes unless precision needed)
            3. Identify app categories even when specific apps are mentioned
            4. Include context when it affects the recommendation
            5. For monitoring, always specify condition_type
            
            User query: "%s"
        """.trimIndent()
    }

    /**
     * Analyzes a user query and categorizes it
     * @param userQuery The raw user query
     * @return A structured analysis of the query
     */
    suspend fun analyzeQuery(userQuery: String): QueryAnalysis {
        return withContext(Dispatchers.IO) {
            val prompt = PROMPT_TEMPLATE.replace("%s", userQuery)
            
            try {
                val llmResponse = llmService.getCompletion(prompt)
                
                try {
                    // Try to parse the JSON response
                    gson.fromJson(llmResponse, QueryAnalysis::class.java)
                } catch (e: Exception) {
                    // If parsing fails, log the error and create a fallback response
                    val errorMsg = "Failed to parse LLM response: ${e.message}"
                    val fallbackResponse = createFallbackQueryAnalysis(userQuery, errorMsg)
                    
                    throw QueryAnalysisException("Failed to analyze query: ${e.message}", e)
                }
            } catch (e: Exception) {
                if (e is QueryAnalysisException) {
                    throw e
                } else {
                    throw QueryAnalysisException("Failed to get LLM response: ${e.message}", e)
                }
            }
        }
    }
    
    /**
     * Creates a fallback QueryAnalysis when parsing fails
     */
    private fun createFallbackQueryAnalysis(userQuery: String, errorReason: String): QueryAnalysis {
        // Attempt basic categorization based on keywords
        val lowercaseQuery = userQuery.lowercase()
        
        val category = when {
            lowercaseQuery.contains("show") || lowercaseQuery.contains("list") || 
            lowercaseQuery.contains("which") || lowercaseQuery.contains("what") -> 1
                
            lowercaseQuery.contains("can i") || lowercaseQuery.contains("will") || 
            lowercaseQuery.contains("enough") -> 2
                
            lowercaseQuery.contains("optimize") || lowercaseQuery.contains("save") || 
            lowercaseQuery.contains("preserve") -> 3
                
            lowercaseQuery.contains("notify") || lowercaseQuery.contains("alert") || 
            lowercaseQuery.contains("warn") -> 4
                
            else -> 1 // Default to information query
        }
        
        val resourceType = when {
            lowercaseQuery.contains("battery") -> listOf("battery")
            lowercaseQuery.contains("data") -> listOf("data")
            else -> listOf("battery") // Default to battery
        }
        
        return QueryAnalysis(
            category = category,
            extractedParams = ExtractedParameters(resourceType = resourceType)
        )
    }
}

/**
 * Exception thrown when query analysis fails
 */
class QueryAnalysisException(message: String, cause: Throwable? = null) : Exception(message, cause) 