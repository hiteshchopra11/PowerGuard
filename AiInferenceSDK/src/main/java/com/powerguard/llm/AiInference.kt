package com.powerguard.llm

import android.content.Context
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Simplified AI Inference SDK for Firebase AI Logic.
 * No initialization required - ready to use immediately.
 */
class AiInference(
    private val context: Context,
    private val config: AiConfig = AiConfig.DEFAULT
) {

    private val tag = "AiInference"
    private val modelManager = ModelManager(context, config)
    private val inferenceEngine = InferenceEngine(context, modelManager, config)
    private val responseParser = ResponseParser(config)
    private val promptFormatter = PromptFormatter()

    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        logError("Coroutine exception in AiInference", Exception(exception))
    }
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)


    fun generateResponse(
        prompt: String,
        temperature: Float = config.temperature,
        callback: ((String?, Exception?) -> Unit)
    ) {
        coroutineScope.launch {
            try {
                val result = inferenceEngine.generateText(prompt, temperature)
                withContext(Dispatchers.Main) { callback(result, null) }
            } catch (e: Exception) {
                logError("Failed to generate response", e)
                withContext(Dispatchers.Main) { callback(null, e) }
            }
        }
    }

    suspend fun generateResponseSuspend(
        prompt: String,
        maxTokens: Int = config.maxTokens,
        temperature: Float = config.temperature
    ): String = suspendCoroutine { continuation ->
        generateResponse(prompt, temperature) { result, error ->
            if (error != null) continuation.resumeWith(Result.failure(error))
            else continuation.resume(result ?: "")
        }
    }

    suspend fun generateJsonResponse(
        prompt: String,
        maxTokens: Int = config.maxTokens,
        temperature: Float = config.temperature
    ): JSONObject? {
        val response = generateResponseSuspend(prompt, maxTokens, temperature)
        return responseParser.parseJsonResponse(response)
    }

    suspend fun <T> generateTypedResponse(
        prompt: String,
        responseClass: Class<T>,
        maxTokens: Int = config.maxTokens,
        temperature: Float = config.temperature
    ): T? {
        val response = generateResponseSuspend(prompt, maxTokens, temperature)
        return responseParser.parseTypedResponse(response, responseClass)
    }

    suspend fun generateEfficientResponse(prompt: String): String? {
        return inferenceEngine.generateTextEfficient(prompt)
    }

    fun shutdown() {
        modelManager.release()
        coroutineScope.cancel("SDK shutdown")
    }

    private fun logError(message: String, e: Exception) {
        if (config.enableLogging) {
            android.util.Log.e(tag, message, e)
        }
    }


    companion object { @JvmStatic fun getVersion(): String = "1.0.0" }
}
