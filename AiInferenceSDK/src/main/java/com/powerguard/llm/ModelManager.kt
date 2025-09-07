package com.powerguard.llm

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.GenerativeBackend

/**
 * Simplified model manager for Firebase AI Logic.
 * No complex initialization needed - models are created on demand.
 */
class ModelManager(
    private val context: Context,
    private val config: AiConfig
) {
    private val tag = "AiSDK_ModelManager"
    
    /**
     * Creates a GenerativeModel instance on demand - no initialization needed.
     * 
     * @return A new GenerativeModel instance
     */
    fun getModel(): GenerativeModel {
        logDebug("Creating model: ${config.modelName}")
        return createGenerativeModel()
    }
    
    /**
     * Creates a GenerativeModel instance with custom generation config.
     * 
     * @param generationConfig Custom generation configuration to use
     * @return The GenerativeModel instance with the specified configuration
     */
    fun getModel(generationConfig: GenerationConfig): GenerativeModel {
        logDebug("Creating model with custom config: ${config.modelName}")
        return createGenerativeModel(generationConfig)
    }
    
    /**
     * Creates a new GenerativeModel instance using Firebase AI with Google AI backend
     */
    private fun createGenerativeModel(): GenerativeModel {
        return Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(config.modelName)
    }
    
    /**
     * Creates a new GenerativeModel instance with custom generation config
     */
    private fun createGenerativeModel(generationConfig: GenerationConfig): GenerativeModel {
        return Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(config.modelName, generationConfig)
    }
    
    /**
     * Builds a GenerationConfig object based on the SDK configuration.
     * 
     * @return Configuration for text generation
     */
    fun buildGenerationConfig(): GenerationConfig {
        return generationConfig {
            maxOutputTokens = config.maxTokens
            temperature = config.temperature
            topK = config.topK
            topP = config.topP
            candidateCount = 1
            stopSequences = null
        }
    }
    
    /**
     * No cleanup needed for Firebase AI Logic models.
     */
    fun release() {
        logDebug("Release called - no cleanup needed for Firebase AI Logic")
    }
    
    private fun logDebug(message: String) {
        if (config.enableLogging) {
            Log.d(tag, message)
        }
    }
    
    private fun logError(message: String, e: Exception) {
        Log.e(tag, "$message: ${e.message}", e)
    }
} 