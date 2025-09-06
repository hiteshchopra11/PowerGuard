package com.powerguard.llm

import android.content.Context
import android.os.BatteryManager
import kotlinx.coroutines.CoroutineExceptionHandler
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
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
 * Main entry point for the Gemma Inference SDK.
 * This class provides methods for on-device LLM inference using Google's Gemma models.
 */
class GemmaInferenceSDK(
    private val context: Context,
    private val config: GemmaConfig = GemmaConfig.DEFAULT
) : DefaultLifecycleObserver {
    
    private val tag = "GemmaInferenceSDK"
    private val modelManager = ModelManager(context, config)
    private val inferenceEngine = InferenceEngine(context, modelManager, config)
    private val responseParser = ResponseParser(config)
    private val promptFormatter = PromptFormatter()
    
    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        logError("Coroutine exception in GemmaInferenceSDK", Exception(exception))
    }
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)
    private val isInitialized = AtomicBoolean(false)
    private val _loadingState = MutableStateFlow(LoadingState.NOT_INITIALIZED)
    
    /**
     * Current loading state of the SDK
     */
    val loadingState: StateFlow<LoadingState> = _loadingState
    
    init {
        if (config.autoInitialize) {
            initializeAsync()
        }

        // Add lifecycle observer on main thread using coroutines
        coroutineScope.launch(Dispatchers.Main) {
            try {
                ProcessLifecycleOwner.get().lifecycle.addObserver(this@GemmaInferenceSDK)
                logDebug("Added lifecycle observer using coroutines")
            } catch (e: Exception) {
                logError("Failed to add lifecycle observer", e)
            }
        }
    }
    
    /**
     * Initializes the SDK and loads the model.
     * This should be called before making any inference requests.
     * If autoInitialize is true in the config, this is called automatically.
     *
     * @return true if initialization was successful, false otherwise
     */
    suspend fun initialize(): Boolean {
        if (isInitialized.get()) {
            logDebug("SDK already initialized")
            return true
        }
        
        return withContext(Dispatchers.IO) {
            try {
                _loadingState.value = LoadingState.INITIALIZING
                modelManager.initialize()
                isInitialized.set(true)
                _loadingState.value = LoadingState.READY
                logDebug("SDK initialized successfully")
                true
            } catch (e: Exception) {
                logError("SDK initialization failed", e)
                _loadingState.value = LoadingState.ERROR
                false
            }
        }
    }
    
    /**
     * Asynchronously initializes the SDK in a coroutine.
     */
    fun initializeAsync() {
        coroutineScope.launch {
            initialize()
        }
    }
    
    /**
     * Generates a response for the provided prompt using the Gemma model.
     *
     * @param prompt The input prompt for the model
     * @param maxTokens Maximum number of tokens to generate
     * @param temperature Temperature parameter for controlling randomness
     * @param callback Callback to receive the result
     */
    fun generateResponse(
        prompt: String,
        maxTokens: Int = config.maxTokens,
        temperature: Float = config.temperature,
        callback: ((String?, Exception?) -> Unit)
    ) {
        if (!checkInitialized(callback)) {
            return
        }
        
        coroutineScope.launch {
            try {
                val result = inferenceEngine.generateText(
                    prompt = prompt,
                    temperature = temperature
                )
                
                withContext(Dispatchers.Main) {
                    callback(result, null)
                }
            } catch (e: Exception) {
                logError("Failed to generate response", e)
                withContext(Dispatchers.Main) {
                    callback(null, e)
                }
            }
        }
    }
    
    /**
     * Suspending version of generateResponse that returns the result directly
     * 
     * @param prompt The input prompt
     * @param maxTokens Optional max tokens to generate
     * @param temperature Optional temperature parameter
     * @return The generated response or throws an exception
     */
    suspend fun generateResponseSuspend(
        prompt: String,
        maxTokens: Int = config.maxTokens,
        temperature: Float = config.temperature
    ): String = suspendCoroutine { continuation ->
        generateResponse(prompt, maxTokens, temperature) { result, error ->
            if (error != null) {
                continuation.resumeWith(Result.failure(error))
            } else {
                continuation.resume(result ?: "")
            }
        }
    }
    
    /**
     * Checks if the SDK is initialized and calls the callback with an error if not.
     * 
     * @param callback The callback to call with an error if the SDK is not initialized
     * @return true if the SDK is initialized, false otherwise
     */
    private fun checkInitialized(callback: (String?, Exception?) -> Unit): Boolean {
        if (!isInitialized.get()) {
            val error = IllegalStateException("SDK not initialized. Call initialize() first.")
            logError("SDK not initialized", error)
            callback(null, error)
            return false
        }
        return true
    }
    
    /**
     * Generates a response and attempts to parse it as a JSON object.
     *
     * @param prompt The input prompt for the model
     * @param maxTokens Maximum number of tokens to generate
     * @param temperature Temperature parameter for controlling randomness
     * @return The parsed JSONObject or null if generation/parsing fails
     */
    suspend fun generateJsonResponse(
        prompt: String,
        maxTokens: Int = config.maxTokens,
        temperature: Float = config.temperature
    ): JSONObject? {
        val response = generateResponseSuspend(prompt, maxTokens, temperature)
        return responseParser.parseJsonResponse(response)
    }
    
    /**
     * Generates a response and parses it into the specified data class.
     *
     * @param prompt The input prompt for the model
     * @param responseClass The class to parse the response into
     * @param maxTokens Maximum number of tokens to generate
     * @param temperature Temperature parameter for controlling randomness
     * @return The parsed object or null if generation/parsing fails
     */
    suspend fun <T> generateTypedResponse(
        prompt: String,
        responseClass: Class<T>,
        maxTokens: Int = config.maxTokens,
        temperature: Float = config.temperature
    ): T? {
        val response = generateResponseSuspend(prompt, maxTokens, temperature)
        return responseParser.parseTypedResponse(response, responseClass)
    }
    
    /**
     * Generates a response from a map of data, formatted using the prompt formatter.
     *
     * @param data Key-value pairs to include in the prompt
     * @param userGoal Optional user goal to include in the prompt
     * @param maxTokens Maximum number of tokens to generate
     * @param temperature Temperature parameter for controlling randomness
     * @return The parsed JSONObject or null if generation/parsing fails
     */
    suspend fun generateFromData(
        data: Map<String, Any>,
        userGoal: String? = null,
        maxTokens: Int = config.maxTokens,
        temperature: Float = config.temperature
    ): JSONObject? {
        val formattedPrompt = promptFormatter.formatMapData(data, userGoal)
        return generateJsonResponse(formattedPrompt, maxTokens, temperature)
    }
    
    /**
     * Generates a response optimized for battery efficiency.
     * Uses lower max tokens and temperature for faster inference.
     *
     * @param prompt The input prompt for the model
     * @return The raw text response, or null if generation fails
     */
    suspend fun generateEfficientResponse(prompt: String): String? {
        // Use battery-efficient settings
        return inferenceEngine.generateTextEfficient(prompt)
    }
    
    /**
     * Generates a response using content format for the provided prompt using the Gemma model.
     *
     * @param prompt The input prompt for the model
     * @param maxTokens Maximum number of tokens to generate
     * @param temperature Temperature parameter for controlling randomness
     * @param callback Callback to receive the result
     */
    fun generateContent(
        prompt: String,
        maxTokens: Int = config.maxTokens,
        temperature: Float = config.temperature,
        callback: ((String?, Exception?) -> Unit)
    ) {
        if (!checkInitialized(callback)) {
            return
        }
        
        coroutineScope.launch {
            try {
                // Just pass the prompt directly to the inference engine
                val result = inferenceEngine.generateText(
                    prompt = prompt,
                    temperature = temperature
                )
                
                withContext(Dispatchers.Main) {
                    callback(result, null)
                }
            } catch (e: Exception) {
                logError("Failed to generate content", e)
                withContext(Dispatchers.Main) {
                    callback(null, e)
                }
            }
        }
    }
    
    /**
     * Suspending version of generateContent that returns the result directly
     * 
     * @param prompt The input prompt
     * @param maxTokens Optional max tokens to generate
     * @param temperature Optional temperature parameter
     * @return The generated response or throws an exception
     */
    suspend fun generateContentSuspend(
        prompt: String,
        maxTokens: Int = config.maxTokens,
        temperature: Float = config.temperature
    ): String = suspendCoroutine { continuation ->
        generateContent(prompt, maxTokens, temperature) { result, error ->
            if (error != null) {
                continuation.resumeWith(Result.failure(error))
            } else {
                continuation.resume(result ?: "")
            }
        }
    }
    
    /**
     * Returns whether the device is in a low battery state.
     * Useful for deciding when to use more efficient inference options.
     */
    fun isLowBattery(): Boolean {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return batteryManager?.let {
            val batteryLevel = it.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            batteryLevel <= LOW_BATTERY_THRESHOLD
        } ?: false
    }
    
    /**
     * Releases all resources held by the SDK.
     * Call this when the SDK is no longer needed.
     */
    fun shutdown() {
        if (isInitialized.get()) {
            logDebug("Shutting down SDK")
            coroutineScope.launch {
                try {
                    modelManager.release()
                    isInitialized.set(false)
                    _loadingState.value = LoadingState.NOT_INITIALIZED
                } finally {
                    // Cancel the coroutine scope to prevent memory leaks
                    coroutineScope.cancel("SDK shutdown")
                }
            }
        } else {
            // Cancel scope even if not initialized to prevent memory leaks
            coroutineScope.cancel("SDK shutdown - not initialized")
        }
    }
    
    /**
     * Called when the app enters the foreground.
     * Initializes the model if not already initialized.
     */
    override fun onStart(owner: LifecycleOwner) {
        if (config.autoInitialize && !isInitialized.get()) {
            initializeAsync()
        }
    }
    
    /**
     * Called when the app enters the background.
     * Optionally releases resources based on configuration.
     */
    override fun onStop(owner: LifecycleOwner) {
        if (config.releaseOnBackground && isInitialized.get()) {
            shutdown()
        }
    }
    
    private suspend fun ensureInitialized(): Boolean {
        if (!isInitialized.get()) {
            logDebug("SDK not initialized, initializing now")
            return initialize()
        }
        return true
    }
    
    private fun logDebug(message: String) {
        if (config.enableLogging) {
            Log.d(tag, message)
        }
    }
    
    private fun logError(message: String, e: Exception) {
        Log.e(tag, message, e)
    }
    
    /**
     * Possible loading states for the SDK
     */
    enum class LoadingState {
        /** SDK is not initialized */
        NOT_INITIALIZED,
        
        /** SDK is currently initializing */
        INITIALIZING,
        
        /** SDK is ready for inference */
        READY,
        
        /** SDK initialization failed */
        ERROR
    }

    
    companion object {
        private const val LOW_BATTERY_THRESHOLD = 15
        
        /**
         * Returns the SDK version
         */
        @JvmStatic
        fun getVersion(): String = "1.0.0"
    }
} 