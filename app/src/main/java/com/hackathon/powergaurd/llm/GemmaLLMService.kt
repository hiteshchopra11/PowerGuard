package com.hackathon.powergaurd.llm

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.powergaurd.llm.GemmaConfig
import com.powergaurd.llm.GemmaInferenceSDK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real LLM service implementation using GemmaInferenceSDK
 */
@Singleton
class GemmaLLMService @Inject constructor(
    private val context: Context,
    private val config: GemmaConfig
) : LLMService {
    
    companion object {
        private const val TAG = "PowerGuard-GemmaLLM"
    }
    
    private var sdk: GemmaInferenceSDK? = null
    private val gson = Gson()
    
    /**
     * Returns the SDK instance, initializing it if necessary
     */
    private suspend fun getSDK(): GemmaInferenceSDK {
        if (sdk == null) {
            sdk = GemmaInferenceSDK(context, config)
            val initialized = sdk?.initialize() ?: false
            
            if (!initialized) {
                throw IllegalStateException("Failed to initialize Gemma SDK")
            }
        }
        
        return sdk ?: throw IllegalStateException("SDK is null after initialization")
    }
    
    /**
     * Gets completion from the Gemma LLM
     * @param prompt The prompt to send to the LLM
     * @return The LLM's response as a string
     */
    override suspend fun getCompletion(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Sending prompt to Gemma LLM with JSON instruction")
            val sdk = getSDK()
            
            // Enhance the prompt to specifically request JSON
            val enhancedPrompt = if (prompt.contains("INFORMATION query")) {
                """
                $prompt
                
                IMPORTANT: Response must be JSON with only "insights" array. No "actionable" items.
                
                FORMAT:
                {
                  "insights": [
                    {
                      "type": "Information",
                      "title": "Brief title",
                      "description": "Factual data only",
                      "severity": "info"
                    }
                  ]
                }
                
                RULES:
                - Facts only, no suggestions
                - No "restrict background data" phrases
                - For app queries: just state usage (e.g., "YouTube: 1.2 GB")
                - For top N queries: exactly N items in descending order
                
                JSON only, no markdown or text.
                """
            } else {
                """
                $prompt
                
                IMPORTANT: Response must be valid JSON. No additional text or markdown.
                """
            }.trimIndent()
            
            // Send the prompt to the Gemma SDK and get the response
            var response = sdk.generateResponseSuspend(
                prompt = enhancedPrompt,
                maxTokens = 256,  // Reduced from 512 to prevent MAX_TOKENS errors
                temperature = 0.1f // Lower temperature for more deterministic and concise responses
            )
            
            Log.d(TAG, "Raw response from Gemma LLM: $response")
            
            // Process the response to extract a valid JSON object
            response = extractValidJson(response)
            Log.d(TAG, "Processed JSON response: $response")
            
            return@withContext response
        } catch (e: Exception) {
            Log.e(TAG, "Error getting completion from Gemma LLM", e)
            // Create a fallback JSON response for error cases
            val fallbackResponse = createFallbackResponse(e.message ?: "Unknown error")
            return@withContext fallbackResponse
        }
    }
    
    /**
     * Extracts valid JSON from the raw model response
     */
    private fun extractValidJson(text: String): String {
        // Try to find JSON content in the response
        val startBrace = text.indexOf('{')
        val endBrace = text.lastIndexOf('}')
        
        if (startBrace != -1 && endBrace != -1 && endBrace > startBrace) {
            val jsonContent = text.substring(startBrace, endBrace + 1)
            try {
                // Validate that it's parseable
                val jsonObject = JSONObject(jsonContent)
                return jsonContent
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse extracted JSON", e)
            }
        }
        
        // If we couldn't extract JSON or it was invalid, create a structured response
        return createFallbackResponse("Could not extract valid JSON from model response")
    }
    
    /**
     * Creates a fallback JSON response when the model fails to return valid JSON
     */
    private fun createFallbackResponse(errorMessage: String): String {
        val fallbackJson = JsonObject().apply {
            addProperty("category", 1) // Default to category 1
            add("extractedParams", JsonObject().apply {
                add("resource_type", gson.toJsonTree(arrayOf("battery")))
            })
            addProperty("error", errorMessage)
        }
        
        return fallbackJson.toString()
    }
} 