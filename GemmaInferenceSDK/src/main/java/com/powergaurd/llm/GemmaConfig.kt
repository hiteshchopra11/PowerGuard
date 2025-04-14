package com.powergaurd.llm

/**
 * Configuration class for the Gemma LLM Inference SDK.
 * 
 * @property modelName The name of the Gemma model to use
 * @property apiKey API key for accessing the Gemma models
 * @property maxCacheSize Maximum size of the model cache in bytes
 * @property enableLogging Whether to enable detailed logging
 * @property timeoutMs Timeout for inference operations in milliseconds
 * @property maxTokens Maximum number of tokens to generate per inference
 * @property temperature Temperature parameter for controlling randomness (0.0-1.0)
 * @property topK Parameter that controls diversity via nucleus sampling
 * @property topP Parameter that controls diversity via nucleus sampling
 * @property autoInitialize Whether to initialize the model automatically when the app is in foreground
 * @property releaseOnBackground Whether to release model resources when the app goes to background
 * @property offlineOnly Whether to operate in offline-only mode (no API calls)
 */
data class GemmaConfig(
    val modelName: String = "gemma-3-1b",
    val apiKey: String = "",
    val maxCacheSize: Long = 100 * 1024 * 1024, // 100 MB
    val enableLogging: Boolean = false,
    val timeoutMs: Long = 30000, // 30 seconds
    val maxTokens: Int = 128,
    val temperature: Float = 0.2f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val autoInitialize: Boolean = true,
    val releaseOnBackground: Boolean = false,
    val offlineOnly: Boolean = false
) {
    companion object {
        /**
         * Default configuration for general use
         */
        val DEFAULT = GemmaConfig()
        
        /**
         * Configuration optimized for battery efficiency
         */
        val BATTERY_EFFICIENT = GemmaConfig(
            maxCacheSize = 50 * 1024 * 1024,
            timeoutMs = 15000,
            maxTokens = 64,
            releaseOnBackground = true
        )
        
        /**
         * Configuration optimized for performance
         */
        val PERFORMANCE = GemmaConfig(
            maxCacheSize = 200 * 1024 * 1024,
            timeoutMs = 60000,
            maxTokens = 256,
            temperature = 0.7f,
            autoInitialize = true,
            releaseOnBackground = false
        )
        
        /**
         * Configuration for offline-only mode (no API calls)
         */
        val OFFLINE = GemmaConfig(
            offlineOnly = true,
            maxCacheSize = 50 * 1024 * 1024,
            timeoutMs = 10000,
            maxTokens = 64,
            temperature = 0.2f
        )
    }
} 