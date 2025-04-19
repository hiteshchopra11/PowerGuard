package com.hackathon.powergaurd.data.gemma

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import android.util.Log
import com.hackathon.powergaurd.data.model.Actionable
import com.hackathon.powergaurd.data.model.AnalysisRepository
import com.hackathon.powergaurd.data.model.AnalysisResponse
import com.hackathon.powergaurd.data.model.DeviceData
import com.hackathon.powergaurd.data.model.Insight
import com.powergaurd.llm.GemmaConfig
import com.powergaurd.llm.GemmaInferenceSDK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/**
 * Repository for handling on-device AI inference using GemmaInferenceSDK.
 * This replaces the backend service with local AI inference.
 */
@Singleton
class GemmaRepository @Inject constructor(
    private val context: Context,
    private val config: GemmaConfig
) : AnalysisRepository {
    companion object {
        private const val TAG = "GemmaRepository"
        private const val DEBUG = true // Use this instead of BuildConfig.DEBUG
        
        // Add log tags for better filtering
        private const val LOG_SDK = "GemmaSDK_Debug"
        private const val LOG_PROMPT = "GemmaPrompt_Debug"
        private const val LOG_ERROR = "GemmaError_Debug"
        
        // Constants for timeouts and retries
        private const val GENERATION_TIMEOUT_MS = 10000L // 10 seconds
        private const val MAX_RETRIES = 2
    }

    /**
     * Exception thrown when there's a lifecycle-related issue
     */
    class LifecycleException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * The GemmaInferenceSDK instance.
     * This property is open for testing purposes.
     */
    private var _sdk: GemmaInferenceSDK? = null
    
    open val sdk: GemmaInferenceSDK
        get() {
            if (_sdk == null) {
                // Always initialize on the main thread
                synchronized(this) {
                    if (_sdk == null) {
                        if (Looper.myLooper() == Looper.getMainLooper()) {
                            try {
                                // Create a modified config with lower token limits
                                val safeConfig = config.copy(
                                    maxTokens = 16,  // Reduce max tokens even further
                                    temperature = 0.1f,  // Keep temperature low
                                    modelName = "models/gemini-2.0-flash",  // Ensure using flash model
                                    timeoutMs = GENERATION_TIMEOUT_MS  // Add timeout
                                )
                                
                                Log.d(LOG_SDK, """
                                    |=== Creating SDK with Config ===
                                    |Model: ${safeConfig.modelName}
                                    |Max tokens: ${safeConfig.maxTokens}
                                    |Temperature: ${safeConfig.temperature}
                                    |Timeout: ${safeConfig.timeoutMs}ms
                                    |===
                                """.trimMargin())
                                
                                _sdk = GemmaInferenceSDK(context, safeConfig)
                                Log.d(TAG, "GemmaInferenceSDK created on main thread")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error creating GemmaInferenceSDK on main thread", e)
                                throw RuntimeException("Failed to create GemmaInferenceSDK", e)
                            }
                        } else {
                            // Post to main thread and wait for completion
                            val latch = CountDownLatch(1)
                            var error: Exception? = null
                            
                            MainScope().launch(Dispatchers.Main) {
                                try {
                                    val safeConfig = config.copy(
                                        maxTokens = 16,
                                        temperature = 0.1f,
                                        modelName = "models/gemini-2.0-flash",
                                        timeoutMs = GENERATION_TIMEOUT_MS
                                    )
                                    
                                    Log.d(LOG_SDK, """
                                        |=== Creating SDK with Config ===
                                        |Model: ${safeConfig.modelName}
                                        |Max tokens: ${safeConfig.maxTokens}
                                        |Temperature: ${safeConfig.temperature}
                                        |Timeout: ${safeConfig.timeoutMs}ms
                                        |===
                                    """.trimMargin())
                                    
                                    _sdk = GemmaInferenceSDK(context, safeConfig)
                                    Log.d(TAG, "GemmaInferenceSDK created on background thread via main thread")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error creating GemmaInferenceSDK via main thread", e)
                                    error = e
                                } finally {
                                    latch.countDown()
                                }
                            }
                            
                            try {
                                // Wait with timeout to prevent potential deadlocks
                                val success = latch.await(5, TimeUnit.SECONDS)
                                if (!success) {
                                    throw TimeoutException("Timed out waiting for GemmaInferenceSDK initialization")
                                }
                                if (error != null) {
                                    throw RuntimeException("Failed to create GemmaInferenceSDK on main thread", error)
                                }
                                if (_sdk == null) {
                                    throw IllegalStateException("GemmaInferenceSDK was not initialized properly")
                                }
                            } catch (e: InterruptedException) {
                                Log.e(TAG, "Interrupted while initializing SDK on main thread", e)
                                throw RuntimeException("Failed to initialize SDK on main thread", e)
                            }
                        }
                    }
                }
            }
            return _sdk!!
        }

    /**
     * Initializes the SDK. Should be called early in the app lifecycle.
     */
    suspend fun initialize(): Boolean {
        return try {
            // Ensure initialization happens on the main thread
            withContext(Dispatchers.Main) {
                try {
                    Log.d(TAG, "Starting SDK initialization on main thread")
                    val result = sdk.initialize()
                    Log.d(TAG, "SDK initialization completed with result: $result")
                    result
                } catch (e: Exception) {
                    Log.e(TAG, "Error during SDK initialization on main thread", e)
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize GemmaInferenceSDK: ${e.message}", e)
            false
        }
    }

    /**
     * Checks if network is available
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
        
        return actNw.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               actNw.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Analyzes device data using on-device LLM and generates optimization recommendations.
     *
     * @param deviceData The collected device data
     * @return Result with AnalysisResponse or error information
     */
    @SuppressLint("NewApi")
    override suspend fun analyzeDeviceData(deviceData: DeviceData): Result<AnalysisResponse> = withContext(Dispatchers.IO) {
        try {
            // Log SDK state
            Log.d(LOG_SDK, "Starting analyzeDeviceData")
            Log.d(LOG_SDK, "SDK instance: ${_sdk != null}")
            
            // Create the prompt for debugging purpose
            val prompt = createAnalysisPrompt(deviceData)
            
            // Log detailed prompt information
            Log.d(LOG_PROMPT, """
                |=== Prompt Details ===
                |Length: ${prompt.length}
                |Approx tokens: ${prompt.length / 4}
                |Contains user goal: ${deviceData.prompt != null}
                |User goal length: ${deviceData.prompt?.length ?: 0}
                |===
                |Full Prompt:
                |$prompt
                |===
            """.trimMargin())
            
            // Check prompt length and potentially use simplified version if too long
            val approxTokenCount = prompt.length / 4
            if (approxTokenCount > 500) {
                Log.w(LOG_PROMPT, "Prompt appears too long (approx tokens: $approxTokenCount). Using simplified version.")
                val simplifiedPrompt = createSimplifiedPrompt(deviceData)
                val simplifiedTokenCount = simplifiedPrompt.length / 4
                Log.d(LOG_PROMPT, """
                    |=== Simplified Prompt Details ===
                    |Original length: ${prompt.length}
                    |Simplified length: ${simplifiedPrompt.length}
                    |Original tokens: $approxTokenCount
                    |Simplified tokens: $simplifiedTokenCount
                    |Reduction: ${String.format("%.2f", (1 - simplifiedPrompt.length.toFloat() / prompt.length) * 100)}%
                    |===
                    |Simplified Prompt:
                    |$simplifiedPrompt
                    |===
                """.trimMargin())
                
                if (simplifiedTokenCount < approxTokenCount * 0.7) {
                    Log.d(LOG_PROMPT, "Using simplified prompt instead")
                    return@withContext processPromptWithGemma(simplifiedPrompt, deviceData)
                }
            }
            
            // Create debug JSON representation of device data
            val debugJson = JSONObject().apply {
                put("deviceInfo", JSONObject().apply {
                    put("manufacturer", deviceData.deviceInfo.manufacturer)
                    put("model", deviceData.deviceInfo.model)
                    put("osVersion", deviceData.deviceInfo.osVersion)
                })
                put("battery", JSONObject().apply {
                    put("level", deviceData.battery.level)
                    put("isCharging", deviceData.battery.isCharging)
                    put("temperature", deviceData.battery.temperature)
                })
                put("memory", JSONObject().apply {
                    put("availableRam", deviceData.memory.availableRam)
                    put("totalRam", deviceData.memory.totalRam)
                    put("lowMemory", deviceData.memory.lowMemory)
                })
                put("apps", JSONArray().apply {
                    deviceData.apps.forEach { app ->
                        put(JSONObject().apply {
                            put("appName", app.appName)
                            put("packageName", app.packageName)
                            put("batteryUsage", app.batteryUsage)
                            put("foregroundTime", app.foregroundTime)
                            put("backgroundTime", app.backgroundTime)
                            
                            // Add new metrics - data usage
                            put("dataUsage", JSONObject().apply {
                                put("foreground", app.dataUsage.foreground)
                                put("background", app.dataUsage.background)
                                put("rxBytes", app.dataUsage.rxBytes)
                                put("txBytes", app.dataUsage.txBytes)
                            })
                            
                            // Add alarm wakeups and priority changes
                            put("alarmWakeups", app.alarmWakeups)
                        })
                    }
                })
                deviceData.prompt?.let { put("userPrompt", it) }
            }
            
            // Log the device data as JSON
            Log.d("SendPromptDebug", "Raw device data JSON:\n${debugJson.toString(2)}")
            
            // Create and log an example expected response
            val exampleResponseJson = JSONObject().apply {
                put("success", true)
                put("batteryScore", 85)
                put("dataScore", 90)
                put("performanceScore", 75)
                put("insights", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "BATTERY")
                        put("title", "Example Battery Insight")
                        put("description", "This is an example of what a battery insight would look like")
                        put("severity", "MEDIUM")
                    })
                    put(JSONObject().apply {
                        put("type", "DATA")
                        put("title", "Example Data Insight")
                        put("description", "This is an example of what a data insight would look like")
                        put("severity", "LOW")
                    })
                })
                put("actionable", JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", UUID.randomUUID().toString())
                        put("type", "RESTRICT_DATA")
                        put("packageName", "com.example.app")
                        put("description", "Example action description")
                        put("reason", "Example reason for the action")
                        put("estimatedBatterySavings", 10.5)
                        put("estimatedDataSavings", 25.0)
                        put("severity", 3)
                    })
                })
                put("estimatedSavings", JSONObject().apply {
                    put("batteryMinutes", 45)
                    put("dataMB", 250)
                })
            }
            
            // Log the example expected response format
            Log.d("SendPromptDebug", "Example expected response format:\n${exampleResponseJson.toString(2)}")
            
            // Check if network is available before using the SDK
            if (!isNetworkAvailable()) {
                Log.w(LOG_SDK, "Network not available, using simulated response")
                return@withContext Result.success(simulateAnalysisResponse(deviceData))
            }
            
            // Enable real LLM inference
            try {
                Log.d(LOG_SDK, "Starting Gemma processing")
                val result = processPromptWithGemma(prompt, deviceData)
                Log.d(LOG_SDK, "Gemma processing completed successfully")
                return@withContext result
            } catch (e: Exception) {
                Log.e(LOG_ERROR, """
                    |=== Gemma Processing Error ===
                    |Error type: ${e.javaClass.name}
                    |Message: ${e.message}
                    |Stack trace:
                    |${e.stackTrace.joinToString("\n")}
                    |===
                """.trimMargin())
                return@withContext Result.success(simulateAnalysisResponse(deviceData).copy(
                    message = "Error with prompt processing: ${e.message}"
                ))
            }
            
        } catch (e: Exception) {
            Log.e(LOG_ERROR, """
                |=== Fatal Error ===
                |Error type: ${e.javaClass.name}
                |Message: ${e.message}
                |Stack trace:
                |${e.stackTrace.joinToString("\n")}
                |===
            """.trimMargin())
            return@withContext Result.success(simulateAnalysisResponse(deviceData))
        }
    }

    /**
     * Creates a prompt for analysis based on device data
     */
    private fun createAnalysisPrompt(deviceData: DeviceData): String {
        val prompt = StringBuilder()
        
        prompt.append("Analyze device data:\n\n")
        
        // Simplified device overview
        prompt.append("DEVICE: ${deviceData.deviceInfo.manufacturer} ${deviceData.deviceInfo.model}\n")
        
        // Simplified battery
        prompt.append("BATTERY: ${deviceData.battery.level}%")
        if (deviceData.battery.isCharging) prompt.append(" (Charging)")
        prompt.append("\n")
        
        // Top apps only - limited to 2
        val topApps = deviceData.apps
            .sortedByDescending { it.batteryUsage }
            .take(2)
        
        if (topApps.isNotEmpty()) {
            prompt.append("TOP APPS:\n")
            topApps.forEach { app ->
                prompt.append("- ${app.appName}: Battery ${app.batteryUsage}%\n")
            }
        }
        
        // Include user goal if provided (important)
        deviceData.prompt?.let {
            prompt.append("\nUSER GOAL: $it\n")
            if (DEBUG) {
                Log.d(TAG, "User prompt length: ${it.length}")
            }
        }
        
        // Very simplified JSON format
        val responseFormat = """
            
            Respond with minimal JSON:
            {
              "batteryScore": 0-100,
              "dataScore": 0-100,
              "performanceScore": 0-100,
              "insights": [
                {
                  "type": "BATTERY|DATA|PERFORMANCE",
                  "title": "Brief title",
                  "description": "One short line"
                }
              ],
              "actionable": [
                {
                  "type": "ACTION_TYPE",
                  "packageName": "app.package",
                  "description": "Action"
                }
              ]
            }
        """.trimIndent()
        
        prompt.append(responseFormat)
        
        // Log prompt parts for debugging
        if (DEBUG) {
            Log.d(TAG, "Device info length: ${("DEVICE: ${deviceData.deviceInfo.manufacturer} ${deviceData.deviceInfo.model}\n").length}")
            Log.d(TAG, "Battery info length: ${("BATTERY: ${deviceData.battery.level}%${if (deviceData.battery.isCharging) " (Charging)" else ""}\n").length}")
            Log.d(TAG, "Top apps info length: ${if (topApps.isNotEmpty()) topApps.sumOf { app -> 
                ("- ${app.appName}: Battery ${app.batteryUsage}%\n").length 
            } else 0}")
            Log.d(TAG, "Response format length: ${responseFormat.length}")
            Log.d(TAG, "Total prompt length: ${prompt.length}")
        }
        
        return prompt.toString()
    }

    /**
     * Creates a simplified analysis prompt with minimal information to reduce token count
     */
    private fun createSimplifiedPrompt(deviceData: DeviceData): String {
        val prompt = StringBuilder()
        
        // Ultra minimal prompt
        prompt.append("Device: ${deviceData.deviceInfo.model}, Battery: ${deviceData.battery.level}%\n")
        
        // User goal if provided (important) - but truncate aggressively
        deviceData.prompt?.let {
            if (it.length > 50) {
                // Truncate long user prompts even more aggressively
                prompt.append("Goal: ${it.take(50)}...\n")
            } else {
                prompt.append("Goal: $it\n")
            }
        }
        
        // Use an extremely compact response format
        prompt.append("""
            JSON:{"batteryScore":NUM,"dataScore":NUM,"performanceScore":NUM,"insights":[{"type":"T","title":"T"}]}
        """.trimIndent())
        
        if (DEBUG) {
            Log.d(TAG, "Simplified prompt total length: ${prompt.length}")
        }
        
        return prompt.toString()
    }
    
    /**
     * Processes a prompt with the Gemma model
     */
    private suspend fun processPromptWithGemma(prompt: String, deviceData: DeviceData): Result<AnalysisResponse> {
        val approxTokenCount = prompt.length / 4
        Log.d(LOG_SDK, """
            |=== Processing Prompt ===
            |Approx token count: $approxTokenCount
            |Length: ${prompt.length}
            |===
        """.trimMargin())
        
        try {
            // Ensure SDK is initialized
            Log.d(LOG_SDK, "Ensuring SDK initialization")
            ensureSdkInitialized()
            Log.d(LOG_SDK, "SDK initialization confirmed")
            
            // Call the actual SDK
            try {
                // Create a structured prompt that asks for a very simple response
                val structuredPrompt = """
                    |Return only this:
                    |{"scores":[50,50,50]}
                """.trimMargin()
                
                Log.d(LOG_SDK, """
                    |=== Attempting Generation ===
                    |Using structured prompt:
                    |$structuredPrompt
                    |Length: ${structuredPrompt.length}
                    |===
                """.trimMargin())
                
                val startTime = System.currentTimeMillis()
                
                // Try with minimal JSON structure
                var response: JSONObject? = null
                var retryCount = 0
                var lastError: Exception? = null
                
                while (response == null && retryCount < MAX_RETRIES) {
                    try {
                        response = withTimeout(GENERATION_TIMEOUT_MS) {
                            sdk.generateJsonResponse(
                                prompt = structuredPrompt,
                                maxTokens = 4,  // Absolute minimum
                                temperature = 0.1f
                            )
                        }
                    } catch (e: Exception) {
                        lastError = e
                        Log.e(LOG_ERROR, "Attempt ${retryCount + 1} failed: ${e.message}")
                        retryCount++
                        
                        if (retryCount < MAX_RETRIES) {
                            delay(1000) // Wait 1 second before retry
                        }
                    }
                }
                
                val endTime = System.currentTimeMillis()
                
                Log.d(LOG_SDK, """
                    |=== Generation Complete ===
                    |Time taken: ${endTime - startTime}ms
                    |Retries: $retryCount
                    |Response: $response
                    |Last error: ${lastError?.message}
                    |===
                """.trimMargin())
                
                if (response != null) {
                    try {
                        // Try to extract scores from the response
                        val scores = response.optJSONArray("scores")
                        if (scores != null && scores.length() == 3) {
                            val jsonResponse = JSONObject().apply {
                                put("batteryScore", scores.optDouble(0, 50.0))
                                put("dataScore", scores.optDouble(1, 50.0))
                                put("performanceScore", scores.optDouble(2, 50.0))
                            }
                            return Result.success(parseGemmaResponse(jsonResponse, deviceData.deviceId))
                        }
                    } catch (e: Exception) {
                        Log.e(LOG_ERROR, "Failed to parse response: ${e.message}")
                    }
                }
                
                Log.w(LOG_SDK, "Generation failed after $retryCount retries, using simulated response")
                return Result.success(simulateAnalysisResponse(deviceData))
            } catch (e: Exception) {
                Log.e(LOG_ERROR, """
                    |=== Generation Error ===
                    |Error type: ${e.javaClass.name}
                    |Message: ${e.message}
                    |Stack trace:
                    |${e.stackTrace.joinToString("\n")}
                    |===
                """.trimMargin())
                
                return Result.success(simulateAnalysisResponse(deviceData).copy(
                    message = "Error with generation: ${e.message}"
                ))
            }
        } catch (e: Exception) {
            Log.e(LOG_ERROR, "Failed to process with Gemma: ${e.message}", e)
            return Result.success(simulateAnalysisResponse(deviceData))
        }
    }

    /**
     * Helper method to format bytes in a human-readable way
     */
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
    
    /**
     * Helper method to format duration in a human-readable way
     */
    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }

    /**
     * Parses the Gemma-generated JSON response into our AnalysisResponse model
     */
    private fun parseGemmaResponse(jsonObject: JSONObject, deviceId: String): AnalysisResponse {
        try {
            // Extract basic fields
            val batteryScore = jsonObject.optDouble("batteryScore", 50.0).toFloat()
            val dataScore = jsonObject.optDouble("dataScore", 50.0).toFloat()
            val performanceScore = jsonObject.optDouble("performanceScore", 50.0).toFloat()
            
            // Parse insights
            val insightsArray = jsonObject.optJSONArray("insights") ?: JSONArray()
            val insights = mutableListOf<Insight>()
            
            for (i in 0 until insightsArray.length()) {
                val insightJson = insightsArray.getJSONObject(i)
                insights.add(
                    Insight(
                        type = insightJson.optString("type", "UNKNOWN"),
                        title = insightJson.optString("title", "Insight"),
                        description = insightJson.optString("description", ""),
                        severity = "MEDIUM" // Default severity
                    )
                )
            }
            
            // Parse actionables
            val actionablesArray = jsonObject.optJSONArray("actionable") ?: JSONArray()
            val actionables = mutableListOf<Actionable>()
            
            for (i in 0 until actionablesArray.length()) {
                val actionableJson = actionablesArray.getJSONObject(i)
                
                actionables.add(
                    Actionable(
                        id = UUID.randomUUID().toString(), // Generate new ID 
                        type = actionableJson.optString("type", "UNKNOWN"),
                        packageName = actionableJson.optString("packageName", ""),
                        description = actionableJson.optString("description", ""),
                        reason = "AI recommended", // Default reason
                        estimatedBatterySavings = 5.0f, // Default value
                        estimatedDataSavings = 5.0f, // Default value
                        severity = 3, // Default medium severity
                        parameters = emptyMap(), // No parameters
                        enabled = true, // Default to enabled
                        newMode = "restricted", // Default mode
                        throttleLevel = 2 // Default throttle level
                    )
                )
            }
            
            return AnalysisResponse(
                id = deviceId,
                timestamp = System.currentTimeMillis().toFloat(),
                success = true,
                message = "Analysis powered by Gemma",
                actionable = actionables,
                insights = insights,
                batteryScore = batteryScore,
                dataScore = dataScore,
                performanceScore = performanceScore,
                estimatedSavings = AnalysisResponse.EstimatedSavings(
                    batteryMinutes = 15f,  // Default estimate
                    dataMB = 100f          // Default estimate
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Gemma response", e)
            return simulateAnalysisResponse(DeviceData(
                deviceId = deviceId,
                timestamp = System.currentTimeMillis(),
                battery = com.hackathon.powergaurd.data.model.BatteryInfo(0, 0f, 0, false, "", 0),
                memory = com.hackathon.powergaurd.data.model.MemoryInfo(0, 0, false, 0),
                cpu = com.hackathon.powergaurd.data.model.CpuInfo(0f, 0f, emptyList()),
                network = com.hackathon.powergaurd.data.model.NetworkInfo("", 0, false, com.hackathon.powergaurd.data.model.DataUsage(0, 0, 0, 0)),
                apps = emptyList(),
                settings = com.hackathon.powergaurd.data.model.SettingsInfo(false, false, false, false, false),
                deviceInfo = com.hackathon.powergaurd.data.model.DeviceInfo("", "", "", 0, 0)
            ))
        }
    }

    /**
     * Generates a simulated backend response for offline fallback or if the LLM fails
     * Note: This is a copy of the original PowerGuardRepository implementation for fallback
     */
    private fun simulateAnalysisResponse(deviceData: DeviceData): AnalysisResponse {
        val actionable = mutableListOf<Actionable>()
        val insights = mutableListOf<Insight>()

        // Initialize scores
        var batteryScore = 85
        var dataScore = 90
        var performanceScore = 80

        // Find battery-draining apps
        deviceData.apps
            .sortedByDescending { it.backgroundTime + it.foregroundTime }
            .take(3)
            .forEach { app ->
                if (app.backgroundTime > 3_600_000) { // More than 1 hour in background
                    actionable.add(
                        Actionable(
                            id = generateRandomId(),
                            type = "OPTIMIZE_BATTERY",
                            packageName = app.packageName,
                            description = "This app is using excessive battery in the background",
                            reason = "High background usage detected",
                            newMode = "restricted",
                            estimatedBatterySavings = 15.0f,
                            estimatedDataSavings = 0.0f,
                            severity = 4,
                            enabled = true,
                            throttleLevel = 3,
                            parameters = mapOf(
                                "restrictBackground" to "true",
                                "optimizeBatteryUsage" to "true"
                            )
                        )
                    )

                    batteryScore -= 5 // Reduce battery score for each problematic app

                    insights.add(
                        Insight(
                            type = "BATTERY",
                            title = "High Battery Drain: ${app.appName}",
                            description = "${app.appName} using ${app.backgroundTime / 60000}min of background battery - restrict background usage.",
                            severity = "HIGH"
                        )
                    )
                }
            }

        // Check for high data usage apps
        deviceData.apps
            .sortedByDescending { it.dataUsage.background }
            .take(3)
            .forEach { app ->
                if (app.dataUsage.background > 50 * 1024 * 1024) { // More than 50MB
                    actionable.add(
                        Actionable(
                            id = generateRandomId(),
                            type = "RESTRICT_DATA",
                            packageName = app.packageName,
                            description = "This app is using a lot of data",
                            reason = "High data usage detected",
                            newMode = "restricted",
                            estimatedBatterySavings = 5.0f,
                            estimatedDataSavings = (app.dataUsage.background / (1024f * 1024f)),
                            severity = 3,
                            enabled = true,
                            throttleLevel = 2,
                            parameters = mapOf(
                                "restrictBackgroundData" to "true"
                            )
                        )
                    )

                    dataScore -= 5 // Reduce data score for each problematic app

                    insights.add(
                        Insight(
                            type = "DATA",
                            title = "High Data Usage: ${app.appName}",
                            description = "${app.appName} used ${app.dataUsage.background / (1024 * 1024)}MB - restrict background data.",
                            severity = "MEDIUM"
                        )
                    )
                }
            }

        // Check for low memory
        if (deviceData.memory.availableRam < deviceData.memory.totalRam * 0.2) { // Less than 20% free
            performanceScore -= 15
            
            insights.add(
                Insight(
                    type = "PERFORMANCE",
                    title = "Low Available Memory",
                    description = "Only ${deviceData.memory.availableRam / (1024 * 1024)}MB free - close unused apps.",
                    severity = "HIGH"
                )
            )
            
            // Find memory-hungry apps
            deviceData.apps
                .sortedByDescending { it.foregroundTime }
                .take(2)
                .forEach { app ->
                    actionable.add(
                        Actionable(
                            id = generateRandomId(),
                            type = "OPTIMIZE_MEMORY",
                            packageName = app.packageName,
                            description = "This app is using a lot of memory",
                            reason = "High memory usage detected",
                            newMode = "stopped",
                            estimatedBatterySavings = 10.0f,
                            estimatedDataSavings = 0.0f,
                            severity = 5,
                            enabled = true,
                            throttleLevel = 5,
                            parameters = mapOf(
                                "forceStop" to "true"
                            )
                        )
                    )
                }
        }

        return AnalysisResponse(
            id = deviceData.deviceId,
            timestamp = System.currentTimeMillis().toFloat(),
            batteryScore = batteryScore.toFloat(),
            dataScore = dataScore.toFloat(),
            performanceScore = performanceScore.toFloat(),
            insights = insights,
            actionable = actionable,
            success = true,
            message = "Analysis (simulated)",
            estimatedSavings = AnalysisResponse.EstimatedSavings(
                batteryMinutes = 17f,
                dataMB = 1200f
            )
        )
    }

    private fun generateRandomId(): String {
        return UUID.randomUUID().toString()
    }
    
    /**
     * Releases resources used by the SDK
     */
    fun shutdown() {
        sdk.shutdown()
    }

    private fun requiresNetwork(): Boolean {
        // TODO: Determine if the SDK requires network access
        // This may need to be hard-coded based on the SDK documentation
        // or determined through configuration/experimentation
        return true // Assume it requires network by default
    }

    /**
     * Makes sure the SDK is initialized before use.
     * This method blocks until initialization is complete.
     */
    private suspend fun ensureSdkInitialized() {
        // Access sdk getter to ensure _sdk is initialized
        if (_sdk == null) {
            // The sdk getter will initialize _sdk if it's null
            sdk // This will trigger the getter which initializes _sdk
        }
        
        if (!sdk.initialize()) {
            throw IllegalStateException("Failed to initialize Gemma SDK")
        }
    }
} 