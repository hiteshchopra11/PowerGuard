package com.hackathon.powergaurd.llm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Test class to verify the LLM implementation
 * This can be used from any Activity or Fragment to test the LLM pipeline
 */
@HiltViewModel
class LLMTest @Inject constructor(
    private val queryProcessor: QueryProcessor
) : ViewModel() {
    
    private var logListener: ((String) -> Unit)? = null
    
    companion object {
        private const val TAG = "PowerGuard-LLMTest"
        
        // Test queries for each category - expanded to 5 per category
        private val CATEGORY_1_QUERIES = listOf(
            "Top 5 data-consuming apps today",
            "Which apps are draining my battery the most?",
            "What's using my battery in the background?",
            "How much data has YouTube used this week?",
            "How much data has Snapchat used this week?"
        )
        
        private val CATEGORY_2_QUERIES = listOf(
            "Can I watch Netflix for 2 hours with my current battery?",
            "Will I have enough data to use Maps for my 3-hour commute?",
            "Is my battery sufficient to play Call of Duty for 45 minutes?",
            "Do I have enough battery left to stream Spotify for 4 hours?",
            "Will my data plan allow me to watch YouTube videos for 1 hour?"
        )
        
        private val CATEGORY_3_QUERIES = listOf(
            "I'm traveling for 6 hours, optimize battery but keep Maps running",
            "Need to make my data last for 2 more days while keeping messaging apps working",
            "Save battery but keep essential apps running",
            "I want to use Instagram but preserve my battery for the whole day",
            "I'm on a road trip for 8 hours, optimize power usage but ensure navigation works"
        )
        
        private val CATEGORY_4_QUERIES = listOf(
            "Notify me when battery reaches 20% while using Spotify",
            "Alert me if my data usage exceeds 3GB today",
            "Warn me if TikTok uses more than 500MB of data",
            "Let me know when Chrome consumes more than 15% of my battery",
            "Alert me when battery drops below 30% during my video call"
        )
        
        private val ALL_QUERIES = listOf(
            CATEGORY_1_QUERIES, 
            CATEGORY_2_QUERIES,
            CATEGORY_3_QUERIES,
            CATEGORY_4_QUERIES
        ).flatten()
        
        // Information query specific tests with expected response types
        private val INFORMATION_QUERY_TESTS = listOf(
            "Top 5 data-consuming apps today",
            "Top 3 data-consuming apps today",
            "Top 2 data-consuming apps today", 
            "Which apps are draining my battery the most?",
            "What's using my battery in the background?",
            "How much data has YouTube used this week?",
            "How much data has Snapchat used this week?"
        )
    }
    
    /**
     * Set a listener to receive log messages
     */
    fun setLogListener(listener: (String) -> Unit) {
        this.logListener = listener
    }
    
    /**
     * Log a message to both Logcat and the UI
     */
    private fun log(message: String) {
        Log.d(TAG, message)
        logListener?.invoke(message)
    }
    
    /**
     * Run a test query through the LLM pipeline
     */
    fun testQuery(query: String) {
        viewModelScope.launch {
            try {
                log("Testing query: $query")
                val result = queryProcessor.processQuery(query)
                log("Result: $result")
            } catch (e: Exception) {
                val errorMsg = "Error testing query: ${e.message}"
                Log.e(TAG, errorMsg, e)
                logListener?.invoke(errorMsg)
            }
            // Add a delay between tests to avoid overwhelming logs
            delay(1000)
        }
    }
    
    /**
     * Test a specific category of queries
     * @param category 1-4 corresponding to the query categories
     */
    fun testCategory(category: Int) {
        if (category < 1 || category > 4) {
            val errorMsg = "Invalid category: $category"
            Log.e(TAG, errorMsg)
            logListener?.invoke(errorMsg)
            return
        }
        
        val queries = when (category) {
            1 -> CATEGORY_1_QUERIES
            2 -> CATEGORY_2_QUERIES
            3 -> CATEGORY_3_QUERIES
            4 -> CATEGORY_4_QUERIES
            else -> emptyList()
        }
        
        viewModelScope.launch {
            log("Testing category $category")
            queries.forEachIndexed { index, query ->
                log("Category $category - Test ${index + 1}: $query")
                testQuery(query)
                // Add a delay between tests
                delay(2000)
            }
        }
    }
    
    /**
     * Test all categories of queries
     */
    fun testAllCategories() {
        viewModelScope.launch {
            log("Testing all categories")
            for (i in 1..4) {
                log("Starting tests for category $i")
                testCategory(i)
                // Add a delay between categories
                delay(3000)
            }
        }
    }
    
    /**
     * Test specifically information queries to verify they return insights without actionables
     */
    fun testInformationQueries() {
        viewModelScope.launch {
            log("Testing Information Queries")
            log("Expected format: JSON with insights array, no actionables")
            
            INFORMATION_QUERY_TESTS.forEachIndexed { index, query ->
                log("Information Query Test ${index + 1}: $query")
                try {
                    val result = queryProcessor.processQuery(query)
                    
                    // Basic verification that the response looks like valid JSON
                    val hasInsights = result.contains("\"insights\"")
                    val hasNoActionables = !result.contains("\"actionable\"")
                    val isJSON = result.trim().startsWith("{") && result.trim().endsWith("}")
                    
                    if (isJSON && hasInsights && hasNoActionables) {
                        log("✓ PASS: Response is in correct format")
                    } else {
                        log("✗ FAIL: Response format incorrect")
                        if (!isJSON) log("  - Not valid JSON")
                        if (!hasInsights) log("  - Missing insights array")
                        if (!hasNoActionables) log("  - Contains actionable items")
                    }
                    
                    log("Result: $result")
                } catch (e: Exception) {
                    val errorMsg = "Error testing query: ${e.message}"
                    Log.e(TAG, errorMsg, e)
                    logListener?.invoke(errorMsg)
                }
                // Add a delay between tests
                delay(2000)
            }
        }
    }
} 