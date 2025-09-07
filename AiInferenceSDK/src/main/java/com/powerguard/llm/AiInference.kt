package com.powerguard.llm

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Main entry point for the AI Inference SDK (renamed from GemmaInferenceSDK).
 */
class AiInference(
    private val context: Context,
    private val config: AiConfig = AiConfig.DEFAULT
) : DefaultLifecycleObserver {

    private val tag = "AiInference"
    private val modelManager = ModelManager(context, config)
    private val inferenceEngine = InferenceEngine(context, modelManager, config)
    private val responseParser = ResponseParser(config)
    private val promptFormatter = PromptFormatter()

    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        logError("Coroutine exception in AiInference", Exception(exception))
    }
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)
    private val isInitialized = AtomicBoolean(false)
    private val _loadingState = MutableStateFlow(LoadingState.NOT_INITIALIZED)

    val loadingState: StateFlow<LoadingState> = _loadingState

    init {
        if (config.autoInitialize) {
            initializeAsync()
        }

        coroutineScope.launch(Dispatchers.Main) {
            try {
                ProcessLifecycleOwner.get().lifecycle.addObserver(this@AiInference)
            } catch (e: Exception) {
                logError("Failed to add lifecycle observer", e)
            }
        }
    }

    suspend fun initialize(): Boolean {
        if (isInitialized.get()) return true
        return withContext(Dispatchers.IO) {
            try {
                _loadingState.value = LoadingState.INITIALIZING
                modelManager.initialize()
                isInitialized.set(true)
                _loadingState.value = LoadingState.READY
                true
            } catch (e: Exception) {
                logError("SDK initialization failed", e)
                _loadingState.value = LoadingState.ERROR
                false
            }
        }
    }

    fun initializeAsync() {
        coroutineScope.launch { initialize() }
    }

    fun generateResponse(
        prompt: String,
        temperature: Float = config.temperature,
        callback: ((String?, Exception?) -> Unit)
    ) {
        if (!checkInitialized(callback)) return
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

    override fun onStart(owner: LifecycleOwner) {
        if (config.autoInitialize && !isInitialized.get()) {
            initializeAsync()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        if (config.releaseOnBackground && isInitialized.get()) {
            shutdown()
        }
    }

    fun shutdown() {
        if (isInitialized.get()) {
            coroutineScope.launch {
                try {
                    modelManager.release()
                    isInitialized.set(false)
                    _loadingState.value = LoadingState.NOT_INITIALIZED
                } finally {
                    coroutineScope.cancel("SDK shutdown")
                }
            }
        } else {
            coroutineScope.cancel("SDK shutdown - not initialized")
        }
    }

    private fun checkInitialized(callback: (String?, Exception?) -> Unit): Boolean {
        if (!isInitialized.get()) {
            val error = IllegalStateException("SDK not initialized. Call initialize() first.")
            logError("SDK not initialized", error)
            callback(null, error)
            return false
        }
        return true
    }

    private fun logError(message: String, e: Exception) {
        if (config.enableLogging) {
            android.util.Log.e(tag, message, e)
        }
    }

    enum class LoadingState { NOT_INITIALIZED, INITIALIZING, READY, ERROR }

    companion object { @JvmStatic fun getVersion(): String = "1.0.0" }
}
