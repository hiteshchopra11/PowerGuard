package com.powerguard.llm

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages the lifecycle of the LLM model, handling initialization, configuration, and cleanup.
 */
class ModelManager(
    private val context: Context,
    private val config: AiConfig
) {
    private val mutex = Mutex()
    private var model: GenerativeModel? = null
    private val tag = "AiSDK_ModelManager"
    
    /**
     * Initializes the LLM model with the provided configuration.
     * This should be called before any inference operations.
     */
    suspend fun initialize() {
        mutex.withLock {
            if (model == null) {
                logDebug("Initializing model: ${config.modelName}")
                try {
                    // Initialize the model using Gemma API
                    model = createGenerativeModel()
                    logDebug("Model initialization successful")
                } catch (e: Exception) {
                    logError("Model initialization failed", e)
                    throw e
                }
            }
        }
    }
    
    /**
     * Retrieves the GenerativeModel instance, initializing it if necessary.
     * 
     * @return The initialized GenerativeModel instance
     * @throws IllegalStateException if model initialization fails
     */
    suspend fun getModel(): GenerativeModel {
        mutex.withLock {
            if (model == null) {
                logDebug("Model not initialized, initializing now")
                initialize()
            }
            return requireNotNull(model) { "Model initialization failed" }
        }
    }
    
    /**
     * Retrieves the GenerativeModel instance with a custom generation config.
     * If the model is already initialized, a new instance is created with the provided config.
     * 
     * @param generationConfig Custom generation configuration to use
     * @return The GenerativeModel instance with the specified configuration
     */
    suspend fun getModel(generationConfig: GenerationConfig): GenerativeModel {
        mutex.withLock {
            // First ensure the standard model is initialized
            if (model == null) {
                logDebug("Model not initialized, initializing now")
                initialize()
            }
            
            // Then create a new model with the custom config
            return createGenerativeModel(generationConfig)
        }
    }
    
    /**
     * Creates a new GenerativeModel instance with the configured settings
     */
    private fun createGenerativeModel(): GenerativeModel {
        return Firebase.ai.generativeModel(config.modelName)
    }
    
    /**
     * Creates a new GenerativeModel instance with custom generation config
     */
    private fun createGenerativeModel(generationConfig: GenerationConfig): GenerativeModel {
        return Firebase.ai.generativeModel(
            config.modelName,
            generationConfig
        )
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
     * Releases all resources associated with the model.
     * This should be called when the model is no longer needed.
     */
    fun release() {
        if (mutex.tryLock()) {
            try {
                logDebug("Releasing model resources")
                model = null
                // Any additional cleanup code here
            } finally {
                mutex.unlock()
            }
        }
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