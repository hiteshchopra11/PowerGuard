package com.hackathon.powerguard.llm

/**
 * Interface for LLM service
 */
interface LLMService {
    /**
     * Gets completion from the LLM
     * @param prompt The prompt to send to the LLM
     * @return The LLM's response as a string
     */
    suspend fun getCompletion(prompt: String): String
} 