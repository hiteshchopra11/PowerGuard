package com.powerguard.llm

/**
 * Configuration class for the AI Inference SDK.
 * Contains all the settings needed for AI model configuration and inference parameters.
 */
data class AiConfig(
    val apiKey: String = "",
    val modelName: String = "models/gemini-2.0-flash",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val timeoutMs: Long = 30000L,
    val enableLogging: Boolean = true,
    val autoInitialize: Boolean = true,
    val offlineOnly: Boolean = false,
    val releaseOnBackground: Boolean = false
) {
    companion object {
        /**
         * Default configuration for the AI Inference SDK
         */
        val DEFAULT = AiConfig()
        
        /**
         * Configuration optimized for battery efficiency
         */
        val LOW_POWER = AiConfig(
            temperature = 0.1f,
            maxTokens = 2048,
            timeoutMs = 15000L,
            autoInitialize = false
        )
        
        /**
         * Configuration for high-quality responses
         */
        val HIGH_QUALITY = AiConfig(
            temperature = 0.9f,
            maxTokens = 8192,
            topK = 50,
            topP = 0.98f,
            timeoutMs = 60000L
        )
    }
}

@Deprecated("Use AiConfig instead", ReplaceWith("AiConfig"))
typealias GemmaConfig = AiConfig