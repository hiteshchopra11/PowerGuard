package com.hackathon.powergaurd.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Processes user queries through the two-stage LLM pipeline
 */
@Singleton
class QueryProcessor @Inject constructor(
    private val queryAnalyzer: QueryAnalyzer,
    private val recommendationEngine: RecommendationEngine
) {
    
    companion object {
        private const val TAG = "PowerGuard-QueryProcessor"
    }
    
    /**
     * Process a user query through the complete pipeline:
     * 1. Analyze query to categorize and extract parameters
     * 2. Generate recommendations based on the analysis
     * 
     * @param userQuery The raw user query
     * @return The LLM's recommendation response
     */
    suspend fun processQuery(userQuery: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // Step 1: Analyze the query
                Log.d(TAG, "Analyzing query: $userQuery")
                val analysis = queryAnalyzer.analyzeQuery(userQuery)
                
                // Log the analysis results
                Log.d(TAG, "Query category: ${analysis.category}")
                analysis.extractedParams.resourceType?.let { 
                    Log.d(TAG, "Resource types: ${it.joinToString()}") 
                }
                
                // Step 2: Generate recommendation based on analysis
                Log.d(TAG, "Generating recommendation")
                val recommendation = recommendationEngine.generateRecommendation(analysis)
                
                Log.d(TAG, "Recommendation generated")
                recommendation
            } catch (e: Exception) {
                Log.e(TAG, "Error processing query", e)
                "Sorry, I couldn't process your query. ${e.message}"
            }
        }
    }
} 