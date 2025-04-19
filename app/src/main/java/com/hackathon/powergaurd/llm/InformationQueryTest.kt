package com.hackathon.powergaurd.llm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Test class specifically for verifying Information Query responses with real LLM
 */
@HiltViewModel
class InformationQueryTest @Inject constructor(
    private val queryProcessor: QueryProcessor
) : ViewModel() {
    
    private var logListener: ((String) -> Unit)? = null
    
    companion object {
        private const val TAG = "PowerGuard-InfoQueryTest"
        
        // Information Query Test Cases - Simplified for token efficiency
        private val TEST_CASES = listOf(
            // Using shorter queries to reduce token usage
            "Top 3 data apps today", 
            "Battery draining apps?",
            "Apps using background battery?",
            "YouTube data usage?",
            "Instagram data usage?"
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
     * Run all information query tests with the real LLM
     */
    fun runAllTests() {
        viewModelScope.launch {
            log("------- INFORMATION QUERY TEST WITH REAL LLM -------")
            log("Running ${TEST_CASES.size} test cases...")
            
            TEST_CASES.forEachIndexed { index, query ->
                runTest(index + 1, query)
                delay(2000) // Delay between tests
            }
            
            log("------- INFORMATION QUERY TEST COMPLETED -------")
        }
    }
    
    /**
     * Run a single test case
     */
    private suspend fun runTest(testNumber: Int, query: String) {
        log("\nTEST #$testNumber: \"$query\"")
        log("Processing...")
        
        try {
            val startTime = System.currentTimeMillis()
            val result = queryProcessor.processQuery(query)
            val duration = System.currentTimeMillis() - startTime
            
            // Validate response format
            val hasInsights = result.contains("\"insights\"")
            val hasNoActionables = !result.contains("\"actionable\"")
            val isJSON = result.trim().startsWith("{") && result.trim().endsWith("}")
            
            // Log validation results
            if (isJSON && hasInsights && hasNoActionables) {
                log("✓ PASS: Response is in correct format (${duration}ms)")
            } else {
                log("✗ FAIL: Response format incorrect (${duration}ms)")
                if (!isJSON) log("  - Not valid JSON")
                if (!hasInsights) log("  - Missing insights array")
                if (!hasNoActionables) log("  - Contains actionable items")
            }
            
            // Log the result
            log("Result: $result")
        } catch (e: Exception) {
            // Check for MAX_TOKENS error
            if (e.toString().contains("MAX_TOKENS") || e.message?.contains("MAX_TOKENS") == true) {
                log("✗ MAX_TOKENS ERROR: Query too complex, try a shorter query")
                log("  Message: ${e.message}")
            } else {
                log("✗ ERROR: ${e.message}")
            }
            Log.e(TAG, "Test failed", e)
        }
    }
    
    /**
     * Run a single test case
     */
    fun runSingleTest(query: String) {
        viewModelScope.launch {
            log("------- RUNNING SINGLE INFORMATION QUERY TEST -------")
            runTest(1, query)
            log("------- TEST COMPLETED -------")
        }
    }
} 