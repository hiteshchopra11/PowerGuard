package com.powergaurd.llm

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerationConfig
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.powergaurd.llm.exceptions.NoConnectivityException
import com.powergaurd.llm.exceptions.InvalidAPIKeyException

/**
 * Engine for performing inference operations with the LLM model.
 * This class handles the actual generation of text responses.
 */
class InferenceEngine(
    private val context: Context,
    private val modelManager: ModelManager,
    private val config: GemmaConfig
) {
    private val tag = "GemmaSDK_InferenceEngine"
    
    private suspend fun checkNetworkConnectivity(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            return capabilities != null && 
                   (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || 
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            return networkInfo != null && networkInfo.isConnected
        }
    }

    /**
     * Generates text based on the provided prompt.
     *
     * @param prompt The input prompt for generation
     * @param maxTokens Override the default max tokens setting (optional)
     * @param temperature Override the default temperature setting (optional)
     * @return The generated text response or null if generation fails/times out
     */
    suspend fun generateText(
        prompt: String,
        maxTokens: Int = config.maxTokens,
        temperature: Float = config.temperature
    ): String? {
        if (!isOnlineMode()) {
            throw NoConnectivityException("Internet connection required for Gemma API")
        }

        if (!checkNetworkConnectivity()) {
            throw NoConnectivityException("No internet connection available")
        }
        
        // Check for empty or placeholder API keys
        if (config.apiKey.isBlank()) {
            throw InvalidAPIKeyException("API key is not set or is using the placeholder value. Please obtain a valid API key from https://aistudio.google.com/app/apikey")
        }

        return withContext(Dispatchers.Default) {
            logDebug("Generating text for prompt: ${prompt.take(50)}...")
            
            try {
                // Use timeout to prevent long-running inference
                withTimeoutOrNull(config.timeoutMs) {
                    // Create a generation config
                    val generationConfig = GenerationConfig.builder().apply {
                        maxOutputTokens = maxTokens
                        this.temperature = temperature
                        topK = config.topK
                        topP = config.topP
                    }.build()
                    
                    // Get the model with this specific generation config
                    val model = modelManager.getModel(generationConfig)
                    
                    // Create content from prompt
                    val promptContent = content(role = "user") { text(prompt) }
                    
                    // Generate content - no need to pass generationConfig here as it's already
                    // part of the model configuration
                    val response = model.generateContent(promptContent)
                    
                    // Extract and return the text response
                    val result = response.text
                    logDebug("Generated ${result?.length ?: 0} characters of text")
                    result
                }
            } catch (e: Exception) {
                if (e.message?.contains("API key not valid") == true || 
                    e.toString().contains("InvalidAPIKeyException")) {
                    throw InvalidAPIKeyException("The API key is invalid or has expired. Please obtain a new key from https://aistudio.google.com/app/apikey", e)
                }
                logError("Text generation failed", e)
                null
            }
        }
    }
    
    /**
     * Performs battery-efficient inference by reducing model parameters.
     * Useful for background operations or low-battery situations.
     *
     * @param prompt The input prompt for generation
     * @return The generated text response or null if generation fails/times out
     */
    suspend fun generateTextEfficient(prompt: String): String? {
        return generateText(
            prompt = prompt,
            maxTokens = config.maxTokens / 2,
            temperature = 0.1f
        )
    }
    
    private fun logDebug(message: String) {
        if (config.enableLogging) {
            Log.d(tag, message)
        }
    }
    
    private fun logError(message: String, e: Exception) {
        Log.e(tag, "$message: ${e.message}", e)
    }

    /**
     * Checks if we're in online mode (vs. offline/local-only mode)
     */
    private fun isOnlineMode(): Boolean {
        return !config.offlineOnly
    }
} 