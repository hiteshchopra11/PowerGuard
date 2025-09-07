package com.powerguard.llm

/**
 * Configuration class for the AI Inference SDK.
 * Contains all the settings needed for Firebase AI Logic model configuration and inference parameters.
 */
data class AiConfig(
    val modelName: String = "gemini-2.0-flash-exp",
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val timeoutMs: Long = 30000L,
    val enableLogging: Boolean = true,
    val offlineOnly: Boolean = false
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
            timeoutMs = 15000L
        )

        /**
         * Configuration for high-quality responses
         */
        val HIGH_QUALITY = AiConfig(
            temperature = 0.9f,
            topK = 50,
            topP = 0.98f,
            timeoutMs = 60000L
        )
    }
}