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
import kotlinx.coroutines.withTimeout
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
        private const val LOG_QUERY = "GemmaQuery_Debug"
        private const val LOG_ERROR = "GemmaError_Debug"

        // Constants for timeouts and retries
        private const val GENERATION_TIMEOUT_MS = 10000L // 10 seconds
        private const val MAX_RETRIES = 2

        // Query categories
        private const val CATEGORY_INFORMATION = 1  // Information queries
        private const val CATEGORY_PREDICTIVE = 2   // Predictive queries
        private const val CATEGORY_OPTIMIZATION = 3 // Optimization requests
        private const val CATEGORY_MONITORING = 4   // Monitoring triggers
        private const val CATEGORY_INVALID = 0      // Invalid or uncategorizable query
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
                                _sdk = GemmaInferenceSDK(context, config)
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
                                    _sdk = GemmaInferenceSDK(context, config)
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

            // Check if there's a user query to categorize
            val userQuery = deviceData.prompt
            var queryCategory = CATEGORY_INVALID
            var dataToProcess = deviceData

            if (!userQuery.isNullOrBlank()) {
                // Categorize the query using LLM
                queryCategory = categorizeQuery(userQuery)
                Log.d(LOG_QUERY, "User query: $userQuery")
                Log.d(LOG_QUERY, "Categorized as: $queryCategory")

                // Create a device data copy with the categorized query information
                dataToProcess = deviceData.copy(
                    prompt = "QUERY_CATEGORY:$queryCategory - $userQuery"
                )
            }

            // Create the prompt for debugging purpose
            val prompt = createAnalysisPrompt(dataToProcess)

            // Log detailed prompt information
            Log.d(LOG_PROMPT, """
                |=== Prompt Details ===
                |Length: ${prompt.length}
                |Approx tokens: ${prompt.length / 4}
                |Contains user goal: ${deviceData.prompt != null}
                |User goal length: ${deviceData.prompt?.length ?: 0}
                |Query category: $queryCategory
                |===
                |Full Prompt:
                |$prompt
                |===
            """.trimMargin())

            // Check prompt length and potentially use simplified version if too long
            val approxTokenCount = prompt.length / 4
            if (approxTokenCount > 500) {
                Log.w(LOG_PROMPT, "Prompt appears too long (approx tokens: $approxTokenCount). Using simplified version.")
                val simplifiedPrompt = createSimplifiedPrompt(dataToProcess)
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
                    return@withContext processPromptWithGemma(simplifiedPrompt, dataToProcess)
                }
            }

            // Check if network is available before using the SDK
            if (!isNetworkAvailable()) {
                Log.w(LOG_SDK, "Network not available, using simulated response")
                return@withContext Result.success(simulateAnalysisResponse(dataToProcess))
            }

            // Enable real LLM inference
            try {
                Log.d(LOG_SDK, "Starting Gemma processing")
                val result = processPromptWithGemma(prompt, dataToProcess)
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
                return@withContext Result.success(simulateAnalysisResponse(dataToProcess).copy(
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
     * Categorizes a user query using LLM into one of four categories:
     * 1. Information Queries
     * 2. Predictive Queries
     * 3. Optimization Requests
     * 4. Monitoring Triggers
     * 0. Invalid Query (if it doesn't fit into any category)
     *
     * @param query The user query to categorize
     * @return The category number (1-4, or 0 for invalid)
     */
    private suspend fun categorizeQuery(query: String): Int {
        try {
            Log.d(LOG_QUERY, "Categorizing query: $query")

            val categorizePrompt = """
                You are a query categorization system that helps users identify the type of battery and data usage queries.
                I'll give you a user query, and you need to categorize it into one of these categories:
                
                1. Information Queries
                   - Direct questions about usage statistics
                   - Format: "Show me [metric] for [optional: time period]"
                   - Examples:
                     - "Which apps are draining my battery the most?"
                     - "Top 5 data-consuming apps today"
                     - "What's using my battery in the background?"
                     - "How much data has YouTube used this week?"
                
                2. Predictive Queries
                   - Estimate resource availability for specific scenarios
                   - Format: "Can I use [app] for [duration] with current [resource]?"
                   - Examples:
                     - "Can I watch Netflix for 3 hours with my current battery?"
                     - "Will I have enough data to use Maps for my 2-hour commute?"
                     - "Is my battery sufficient to play Call of Duty for 45 minutes?"
                     - "Can I stream Spotify for 5 hours without depleting my data plan?"
                
                3. Optimization Requests
                   - Provide actionable recommendations for specific scenarios
                   - Format: "I want to [activity] for [duration], optimize [resource]"
                   - Examples:
                     - "I'm traveling for 6 hours and need Maps and WhatsApp, optimize battery"
                     - "Need to make my data last for 3 more days while keeping messaging apps working"
                     - "I want to use Instagram but save battery"
                     - "Optimize my phone for a 12-hour flight with occasional gaming"
                
                4. Monitoring Triggers
                   - Set conditional alerts based on resource thresholds
                   - Format: "Notify me when [condition] while [optional: context]"
                   - Examples:
                     - "Notify me when battery reaches 20% while using Spotify"
                     - "Alert me if my data usage exceeds 3GB today"
                     - "Warn me if any app is using excessive battery in the background"
                     - "Notify me when TikTok has used more than 500MB of data"
                
                0. Invalid Query
                   - Use this if the query doesn't fit into any of the above categories
                   - Examples:
                     - "What's the weather like today?"
                     - "Tell me a joke"
                     - Other queries not related to battery/data usage or device optimization
                
                VERY IMPORTANT: Your response must be ONLY the category number (0, 1, 2, 3, or 4). No other text or explanation.
                
                User query: "$query"
                Category number (0-4):
            """.trimIndent()

            // Ensure SDK is initialized
            ensureSdkInitialized()

            // Generate the response with a short timeout
            val response = withTimeout(5000) {
                sdk.generateResponseSuspend(
                    prompt = categorizePrompt,
                    maxTokens = 5, // Very short response needed
                    temperature = 0.1f // Low temperature for consistent results
                )
            }

            // Parse the response to extract the category number
            val categoryNumber = response.trim().toIntOrNull()

            return categoryNumber?.let {
                if (it in 0..4) {
                    Log.d(LOG_QUERY, "Successfully categorized query as: $it")
                    it
                } else {
                    // Number outside valid range
                    Log.w(LOG_QUERY, "Invalid category number from response: '$it', returning INVALID")
                    CATEGORY_INVALID
                }
            } ?: run {
                // Could not parse a valid number
                Log.w(LOG_QUERY, "Failed to parse category number from response: '$response', returning INVALID")
                CATEGORY_INVALID
            }
        } catch (e: Exception) {
            Log.e(LOG_QUERY, "Error categorizing query: ${e.message}", e)
            // Return invalid category in case of error
            return CATEGORY_INVALID
        }
    }

    /**
     * Creates a prompt for analysis based on device data
     */
    private fun createAnalysisPrompt(deviceData: DeviceData): String {
        val prompt = StringBuilder()

        prompt.append("""
            You are a battery and performance analysis system. Analyze the following device data and provide insights.
            
            IMPORTANT: You must respond with ONLY a valid JSON object. No other text, no markdown, no explanations.
            The response must match this exact structure:
            {
                "batteryScore": number between 0-100,
                "dataScore": number between 0-100,
                "performanceScore": number between 0-100,
                "insights": [
                    {
                        "type": "BATTERY|DATA|PERFORMANCE",
                        "title": "Brief title",
                        "description": "One line description",
                        "severity": "LOW|MEDIUM|HIGH"
                    }
                ],
                "actionable": [
                    {
                        "type": "ACTION_TYPE",
                        "packageName": "app.package.name",
                        "description": "Action description",
                        "reason": "Reason for action"
                    }
                ]
            }
            
            Device Data to Analyze:
        """.trimIndent())

        prompt.append("\n\nDEVICE: ${deviceData.deviceInfo.manufacturer} ${deviceData.deviceInfo.model}\n")

        prompt.append("BATTERY: ${deviceData.battery.level}%")
        if (deviceData.battery.isCharging) prompt.append(" (Charging)")
        prompt.append("\n")

        val topApps = deviceData.apps
            .sortedByDescending { it.batteryUsage }
            .take(2)

        if (topApps.isNotEmpty()) {
            prompt.append("TOP APPS:\n")
            topApps.forEach { app ->
                prompt.append("- ${app.appName}: Battery ${app.batteryUsage}%\n")
            }
        }

        deviceData.prompt?.let {
            prompt.append("\nUSER GOAL: $it\n")
        }

        prompt.append("\nRemember: Respond with ONLY the JSON object. No other text.")

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

        var retryCount = 0
        val maxRetries = MAX_RETRIES

        try {
            Log.d(LOG_SDK, "Ensuring SDK initialization")
            ensureSdkInitialized()
            Log.d(LOG_SDK, "SDK initialization confirmed")

            try {
                val startTime = System.currentTimeMillis()

                val jsonResponse = withTimeout(GENERATION_TIMEOUT_MS) {
                    var response = sdk.generateResponseSuspend(
                        prompt = prompt,
                        maxTokens = config.maxTokens,
                        temperature = 0.1f
                    )

                    // Log the raw response for debugging
                    Log.d(LOG_SDK, "Raw response from Gemma: $response")

                    // Try to extract JSON from the response
                    val jsonStart = response.indexOf("{")
                    val jsonEnd = response.lastIndexOf("}")

                    if (jsonStart >= 0 && jsonEnd > jsonStart) {
                        response = response.substring(jsonStart, jsonEnd + 1)
                        Log.d(LOG_SDK, "Extracted JSON: $response")
                    } else {
                        Log.e(LOG_SDK, "No JSON found in response")
                        return@withTimeout null
                    }

                    try {
                        JSONObject(response)
                    } catch (e: Exception) {
                        Log.e(LOG_SDK, "Failed to parse response as JSON", e)
                        null
                    }
                }

                val endTime = System.currentTimeMillis()

                Log.d(LOG_SDK, """
                    |=== Generation Complete ===
                    |Time taken: ${endTime - startTime}ms
                    |Response: $jsonResponse
                    |===
                """.trimMargin())

                return Result.success(
                    if (jsonResponse != null) {
                        parseGemmaResponse(jsonResponse, deviceData.deviceId)
                    } else {
                        Log.w(LOG_SDK, "Null response from SDK, using simulated response")
                        simulateAnalysisResponse(deviceData)
                    }
                )
            } catch (e: TimeoutException) {
                Log.e(LOG_ERROR, "Generation timed out after ${GENERATION_TIMEOUT_MS}ms", e)
                return Result.success(simulateAnalysisResponse(deviceData).copy(
                    message = "Generation timed out, using fallback response"
                ))
            } catch (e: Exception) {
                Log.e(LOG_ERROR, """
                    |=== Generation Error ===
                    |Error type: ${e.javaClass.name}
                    |Message: ${e.message}
                    |Stack trace:
                    |${e.stackTrace.joinToString("\n")}
                    |===
                """.trimMargin())

                if (shouldRetry(e)) {
                    retryCount++
                    Log.w(LOG_ERROR, "Retrying generation (attempt $retryCount of $maxRetries)")
                    delay(1000L * retryCount) // Exponential backoff
                }

                return Result.success(simulateAnalysisResponse(deviceData).copy(
                    message = "Error with generation: ${e.message}"
                ))
            }
        } catch (e: Exception) {
            Log.e(LOG_ERROR, "Failed to process with Gemma: ${e.message}", e)
            return Result.success(simulateAnalysisResponse(deviceData))
        }
    }

    // Add helper function to determine if we should retry
    private fun shouldRetry(e: Exception): Boolean {
        return when (e) {
            is IOException,
            is UnknownHostException,
            is TimeoutException -> true
            else -> false
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
                        severity = insightJson.optString("severity", "MEDIUM")
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
                        reason = actionableJson.optString("reason", "AI recommended"),
                        estimatedBatterySavings = actionableJson.optDouble("estimatedBatterySavings", 5.0).toFloat(),
                        estimatedDataSavings = actionableJson.optDouble("estimatedDataSavings", 5.0).toFloat(),
                        severity = actionableJson.optInt("severity", 3),
                        parameters = emptyMap(), // No parameters
                        enabled = actionableJson.optBoolean("enabled", true),
                        newMode = actionableJson.optString("newMode", "restricted"),
                        throttleLevel = actionableJson.optInt("throttleLevel", 2)
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
                    batteryMinutes = jsonObject.optJSONObject("estimatedSavings")?.optInt("batteryMinutes", 15)?.toFloat() ?: 15f,
                    dataMB = jsonObject.optJSONObject("estimatedSavings")?.optInt("dataMB", 100)?.toFloat() ?: 100f
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

        // Extract query category if available
        var queryCategory = CATEGORY_INVALID
        deviceData.prompt?.let { prompt ->
            if (prompt.startsWith("QUERY_CATEGORY:")) {
                val categoryStr = prompt.substringAfter("QUERY_CATEGORY:").substringBefore(" ")
                queryCategory = categoryStr.toIntOrNull() ?: CATEGORY_INVALID
            }
        }

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

        // Add response message based on query category if available
        val categoryMessage = when (queryCategory) {
            CATEGORY_INFORMATION -> "Information analysis"
            CATEGORY_PREDICTIVE -> "Predictive analysis"
            CATEGORY_OPTIMIZATION -> "Optimization recommendations"
            CATEGORY_MONITORING -> "Monitoring configuration"
            CATEGORY_INVALID -> "Invalid query format"
            else -> "Analysis (simulated)"
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
            message = categoryMessage,
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
        _sdk?.shutdown()
        _sdk = null
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