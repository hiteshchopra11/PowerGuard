package com.powerguard.llm

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.powerguard.llm.exceptions.NoConnectivityException

/**
 * Engine for performing inference operations with the LLM model.
 * This class handles the actual generation of text responses.
 */
class InferenceEngine(
    private val context: Context,
    private val modelManager: ModelManager,
    private val config: AiConfig
) {
    private val tag = "AiSDK_InferenceEngine"
    
    private fun checkNetworkConnectivity(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null &&
               (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
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
        temperature: Float = config.temperature
    ): String? {
        if (!isOnlineMode()) {
            throw NoConnectivityException("Internet connection required for online API mode")
        }

        if (!checkNetworkConnectivity()) {
            throw NoConnectivityException("No internet connection available")
        }
        
        logDebug("Using Firebase AI Logic")

        return withContext(Dispatchers.Default) {
            logDebug("Generating text for prompt: ${prompt}")
            
            try {
                // Use timeout to prevent long-running inference
                withTimeoutOrNull(config.timeoutMs) {
                    // Create a generation config
                    val generationConfig = generationConfig {
                        maxOutputTokens = 20000
                        this.temperature = temperature
                        topK = config.topK
                        topP = config.topP
                    }
                    
                    // Get the model with this specific generation config
                    val model = modelManager.getModel(generationConfig)
                    val response = model.generateContent(prompt)
                    val result = response.text
                    logDebug("Generated ${result?.length ?: 0} characters of text")
                    result
                }
            } catch (e: Exception) {
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