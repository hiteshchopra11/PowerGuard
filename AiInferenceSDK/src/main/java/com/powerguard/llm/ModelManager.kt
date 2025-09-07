package com.powerguard.llm

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
    private val config: AiConfig
) {
    private val tag = "AiSDK_ModelManager"
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
     * Creates a new GenerativeModel instance with custom generation config
     */
    private fun createGenerativeModel(generationConfig: GenerationConfig): GenerativeModel {
        return Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(config.modelName, generationConfig)
    }
    
    private fun logDebug(message: String) {
        if (config.enableLogging) {
            Log.d(tag, message)
        }
    }
} 