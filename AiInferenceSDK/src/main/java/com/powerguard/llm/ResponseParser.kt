package com.powerguard.llm

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

/**
 * Parser for LLM model responses, handling JSON parsing and error recovery.
 */
class ResponseParser(private val config: AiConfig) {
    private val gson = Gson()
    private val tag = "AiSDK_ResponseParser"
    
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
        
        logDebug("Raw LLM response: $llmResponse")
        
        try {
            // First, try parsing as-is
            return JSONObject(llmResponse)
        } catch (e: JSONException) {
            logError("Failed to parse response as JSON (direct): ${e.message}")
            
            // Try to extract JSON from a response that might contain extra text
            val extracted = extractJsonFromText(llmResponse)
            if (extracted != null) {
                logDebug("Successfully extracted JSON from text")
                return extracted
            } else {
                logError("Failed to extract JSON from text")
                logError("Original response content: $llmResponse")
            }
            return null
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
            logDebug("Attempting to extract JSON from text of length: ${text.length}")
            
            // Clean up common response artifacts
            var cleanedText = text.trim()
            
            // Remove various markdown code block patterns
            val patterns = listOf(
                "```json\\s*([\\s\\S]*?)\\s*```",  // ```json ... ```
                "```([\\s\\S]*?)```",             // ``` ... ```
                "`([\\s\\S]*?)`"                  // ` ... `
            )
            
            for (pattern in patterns) {
                val matcher = Regex(pattern, RegexOption.DOT_MATCHES_ALL).find(cleanedText)
                if (matcher != null) {
                    cleanedText = matcher.groupValues[1].trim()
                    logDebug("Extracted from markdown pattern: $pattern")
                    break
                }
            }
            
            // Try parsing the cleaned text first
            try {
                val testJson = JSONObject(cleanedText)
                logDebug("Successfully parsed cleaned text as JSON")
                return testJson
            } catch (e: JSONException) {
                logDebug("Cleaned text is not valid JSON, extracting braces content")
            }

            // Look for text between { and } (with nested braces)
            val startIndex = cleanedText.indexOf('{')
            if (startIndex == -1) {
                logDebug("No opening brace found in text")
                return null
            }

            var openBraces = 0
            var endIndex = -1

            for (i in startIndex until cleanedText.length) {
                when (cleanedText[i]) {
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

            if (endIndex == -1) {
                logDebug("No matching closing brace found")
                return null
            }

            val jsonCandidate = cleanedText.substring(startIndex, endIndex)
            logDebug("Extracted JSON candidate of length: ${jsonCandidate.length}")
            
            val result = JSONObject(jsonCandidate)
            logDebug("Successfully created JSONObject from extracted text")
            return result
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
    
    private fun logDebug(message: String) {
        if (config.enableLogging) {
            Log.d(tag, message)
        }
    }
} 