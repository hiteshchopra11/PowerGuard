package com.hackathon.powerguard.data.ai

import org.json.JSONException
import org.json.JSONObject
import android.util.Log
import javax.inject.Inject

/**
 * Utility class for parsing JSON responses
 */
class JsonParser @Inject constructor() {
    
    companion object {
        private const val TAG = "JsonParser"
    }
    
    /**
     * Parses a string as JSON
     * 
     * @param jsonString The JSON string to parse
     * @return A JSONObject or null if parsing fails
     */
    fun parse(jsonString: String?): JSONObject? {
        if (jsonString.isNullOrBlank()) {
            Log.e(TAG, "Cannot parse null or empty JSON string")
            return null
        }
        
        return try {
            JSONObject(jsonString)
        } catch (e: JSONException) {
            Log.e(TAG, "Failed to parse JSON: ${e.message}", e)
            
            // Try to fix common JSON formatting issues
            tryFixAndParse(jsonString)
        }
    }
    
    /**
     * Attempts to extract valid JSON from text that might contain extra content
     */
    private fun tryFixAndParse(text: String): JSONObject? {
        // Try to find JSON-like content within curly braces
        val startIndex = text.indexOf("{")
        val endIndex = text.lastIndexOf("}")
        
        if (startIndex >= 0 && endIndex > startIndex) {
            val jsonCandidate = text.substring(startIndex, endIndex + 1)
            
            return try {
                JSONObject(jsonCandidate)
            } catch (e: JSONException) {
                Log.e(TAG, "Failed to parse extracted JSON: ${e.message}", e)
                null
            }
        }
        
        return null
    }
} 