package com.powerguard.llm

import android.content.Context
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    context: Context,
    private val config: AiConfig = AiConfig.DEFAULT
) {

    private val tag = "AiInference"
    private val modelManager = ModelManager(config)
    private val inferenceEngine = InferenceEngine(context, modelManager, config)
    private val responseParser = ResponseParser(config)

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
        temperature: Float = config.temperature
    ): String = suspendCoroutine { continuation ->
        generateResponse(prompt, temperature) { result, error ->
            if (error != null) continuation.resumeWith(Result.failure(error))
            else continuation.resume(result ?: "")
        }
    }

    suspend fun generateJsonResponse(
        prompt: String,
        temperature: Float = config.temperature
    ): JSONObject? {
        val response = generateResponseSuspend(prompt, temperature)
        return responseParser.parseJsonResponse(response)
    }

    private fun logError(message: String, e: Exception) {
        if (config.enableLogging) {
            android.util.Log.e(tag, message, e)
        }
    }


    companion object { @JvmStatic fun getVersion(): String = "1.0.0" }
}
