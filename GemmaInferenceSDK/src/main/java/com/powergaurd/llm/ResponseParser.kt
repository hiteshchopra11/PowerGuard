package com.powergaurd.llm

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

/**
 * Parser for LLM model responses, handling JSON parsing and error recovery.
 */
class ResponseParser(private val config: GemmaConfig) {
    private val gson = Gson()
    private val tag = "GemmaSDK_ResponseParser"
    
    /**
     * Attempts to parse the LLM response as a JSON object.
     * If parsing fails, attempts to extract and fix the JSON.
     *
     * @param llmResponse The raw response from the LLM
     * @return A parsed JSONObject or null if parsing fails
     */
    fun parseJsonResponse(llmResponse: String?): JSONObject? {
        if (llmResponse.isNullOrBlank()) {
            logError("LLM returned null or empty response")
            return null
        }
        
        try {
            // First, try parsing as-is
            return JSONObject(llmResponse)
        } catch (e: JSONException) {
            logError("Failed to parse response as JSON", e)
            
            // Try to extract JSON from a response that might contain extra text
            return extractJsonFromText(llmResponse)
        }
    }
    
    /**
     * Parses the LLM response into a specific data class.
     *
     * @param llmResponse The raw response from the LLM
     * @param responseClass The class to parse into
     * @return Parsed object or null if parsing fails
     */
    fun <T> parseTypedResponse(llmResponse: String?, responseClass: Class<T>): T? {
        if (llmResponse.isNullOrBlank()) {
            logError("LLM returned null or empty response")
            return null
        }
        
        try {
            // Try direct GSON parsing
            return gson.fromJson(llmResponse, responseClass)
        } catch (e: JsonSyntaxException) {
            logError("Failed to parse response as ${responseClass.simpleName}", e)
            
            // Try to extract and fix JSON before parsing
            val jsonObject = extractJsonFromText(llmResponse)
            if (jsonObject != null) {
                try {
                    return gson.fromJson(jsonObject.toString(), responseClass)
                } catch (e: JsonSyntaxException) {
                    logError("Failed to parse extracted JSON", e)
                }
            }
            return null
        }
    }
    
    /**
     * Attempts to extract a valid JSON object from text that might contain
     * additional content before or after the JSON.
     *
     * @param text The text to extract JSON from
     * @return Extracted JSONObject or null if extraction fails
     */
    private fun extractJsonFromText(text: String): JSONObject? {
        try {
            // Check if the response is wrapped in markdown code blocks
            val markdownPattern = "```(?:json)?([\\s\\S]*?)```"
            val markdownMatcher = Regex(markdownPattern).find(text)

            // If markdown pattern is found, extract the content between code blocks
            val processedText = if (markdownMatcher != null) {
                markdownMatcher.groupValues[1].trim()
            } else {
                text
            }

            // Look for text between { and } (with nested braces)
            val startIndex = processedText.indexOf('{')
            if (startIndex == -1) return null

            var openBraces = 0
            var endIndex = -1

            for (i in startIndex until processedText.length) {
                when (processedText[i]) {
                    '{' -> openBraces++
                    '}' -> {
                        openBraces--
                        if (openBraces == 0) {
                            endIndex = i + 1
                            break
                        }
                    }
                }
            }

            if (endIndex == -1) return null

            val jsonCandidate = processedText.substring(startIndex, endIndex)
            return JSONObject(jsonCandidate)
        } catch (e: Exception) {
            logError("JSON extraction failed", e)
            return null
        }
    }
    
    /**
     * Creates a basic fallback response when parsing fails.
     *
     * @param errorMessage The error message to include
     * @return A simple JSONObject with error details
     */
    fun createFallbackResponse(errorMessage: String): JSONObject {
        val response = JSONObject()
        
        try {
            response.put("id", "error_" + UUID.randomUUID().toString())
            response.put("success", false)
            response.put("timestamp", System.currentTimeMillis())
            response.put("message", "Failed to parse LLM response: $errorMessage")
            
            // Add empty arrays for required fields
            response.put("actionable", arrayOf<Any>())
            response.put("insights", arrayOf<Any>())
            
            // Add default scores
            response.put("batteryScore", 50)
            response.put("dataScore", 50)
            response.put("performanceScore", 50)
            
            // Add estimated savings
            val savings = JSONObject()
            savings.put("batteryMinutes", 0)
            savings.put("dataMB", 0)
            response.put("estimatedSavings", savings)
        } catch (e: JSONException) {
            // This should never happen, but just in case
            logError("Error creating fallback response", e)
        }
        
        return response
    }
    
    private fun logError(message: String, e: Exception? = null) {
        if (config.enableLogging) {
            if (e != null) {
                Log.e(tag, message, e)
            } else {
                Log.e(tag, message)
            }
        }
    }
} 