package com.hackathon.powerguard.data.gemma

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import android.util.Log
import com.hackathon.powerguard.data.model.Actionable
import com.hackathon.powerguard.data.model.AnalysisRepository
import com.hackathon.powerguard.data.model.AnalysisResponse
import com.hackathon.powerguard.data.model.DeviceData
import com.hackathon.powerguard.data.model.Insight
import com.hackathon.powerguard.utils.PackageNameResolver
import com.powerguard.llm.GemmaConfig
import com.powerguard.llm.GemmaInferenceSDK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for handling on-device AI inference using GemmaInferenceSDK.
 */
@Singleton
class GemmaRepository @Inject constructor(
    private val context: Context,
    private val config: GemmaConfig,
    private val packageNameResolver: PackageNameResolver
) : AnalysisRepository {
    companion object {
        private const val TAG = "GemmaRepository"

        // Log tags
        private const val LOG_SDK = "GemmaSDK_Debug"
        private const val LOG_PROMPT = "GemmaPrompt_Debug"
        private const val LOG_QUERY = "GemmaQuery_Debug"
        private const val LOG_ERROR = "GemmaError_Debug"

        // Constants
        private const val GENERATION_TIMEOUT_MS = 10000L

        // Query categories
        private const val CATEGORY_INFORMATION = 1
        private const val CATEGORY_PREDICTIVE = 2
        private const val CATEGORY_OPTIMIZATION = 3
        private const val CATEGORY_MONITORING = 4
        private const val CATEGORY_PAST_USAGE = 5
        private const val CATEGORY_INVALID = 0

        // Resource types
        private const val RESOURCE_BATTERY = "BATTERY"
        private const val RESOURCE_DATA = "DATA"
        private const val RESOURCE_OTHER = "OTHER"

        // Actionable types
        const val SET_STANDBY_BUCKET = "set_standby_bucket"
        const val RESTRICT_BACKGROUND_DATA = "restrict_background_data"
        const val KILL_APP = "kill_app"
        const val MANAGE_WAKE_LOCKS = "manage_wake_locks"
        const val SET_NOTIFICATION = "set_notification"
        const val SET_ALARM = "set_alarm"

        private val ALLOWED_ACTIONABLE_TYPES = listOf(
            SET_STANDBY_BUCKET,
            RESTRICT_BACKGROUND_DATA,
            KILL_APP,
            MANAGE_WAKE_LOCKS,
            SET_NOTIFICATION,
            SET_ALARM
        )
    }

    private var _sdk: GemmaInferenceSDK? = null

    private val sdk: GemmaInferenceSDK
        get() {
            if (_sdk == null) {
                synchronized(this) {
                    if (_sdk == null) {
                        if (Looper.myLooper() == Looper.getMainLooper()) {
                            _sdk = GemmaInferenceSDK(context, config)
                            Log.d(TAG, "GemmaInferenceSDK created on main thread")
                        } else {
                            val latch = CountDownLatch(1)
                            var error: Exception? = null
                            MainScope().launch(Dispatchers.Main) {
                                try {
                                    _sdk = GemmaInferenceSDK(context, config)
                                } catch (e: Exception) {
                                    error = e
                                } finally {
                                    latch.countDown()
                                }
                            }
                            latch.await(5, TimeUnit.SECONDS)
                            if (error != null) throw error!!
                            if (_sdk == null) throw IllegalStateException("SDK initialization failed")
                        }
                    }
                }
            }
            return _sdk!!
        }

    suspend fun initialize(): Boolean = withContext(Dispatchers.Main) {
        try {
            sdk.initialize()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SDK: ${e.message}", e)
            false
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nc = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        return nc?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun getCurrentTimeString(): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Calendar.getInstance().time)
    }

    private fun getCurrentDayOfWeek(): String {
        return SimpleDateFormat("EEEE", Locale.getDefault()).format(Calendar.getInstance().time)
    }

    /**
     * Analyzes device data following the specified flow:
     * 1. Determine resource type (battery, data, or other).
     * 2. Categorize the query.
     * 3. Create and send a modified prompt to LLM.
     * 4. Return JSON response with insights and actionables.
     */
    @SuppressLint("NewApi")
    override suspend fun analyzeDeviceData(deviceData: DeviceData): Result<AnalysisResponse> =
        withContext(Dispatchers.IO) {
            try {
                val userQuery = deviceData.prompt
                var resourceType = RESOURCE_OTHER
                var queryCategory = CATEGORY_INVALID
                var dataToProcess = deviceData

                if (!userQuery.isNullOrBlank()) {
                    resourceType = determineResourceType(userQuery)
                    Log.d(LOG_QUERY, "Resource type: $resourceType")
                    queryCategory = categorizeQuery(userQuery, resourceType)
                    Log.d(LOG_QUERY, "User query: $userQuery, Categorized as: $queryCategory")
                    dataToProcess = deviceData.copy(
                        prompt = "RESOURCE:$resourceType QUERY_CATEGORY:$queryCategory - $userQuery"
                    )
                }

                val prompt = createAnalysisPrompt(dataToProcess, resourceType, queryCategory)
                Log.d(LOG_PROMPT, "Full Prompt:\n$prompt")

                if (!isNetworkAvailable()) {
                    Log.w(LOG_SDK, "Network unavailable, using simulated response")
                    return@withContext Result.success(
                        simulateAnalysisResponse(
                            dataToProcess,
                            resourceType,
                            queryCategory
                        )
                    )
                }

                try {
                    val result = processPromptWithGemma(prompt, dataToProcess, resourceType)
                    Log.d(LOG_SDK, "LLM processing completed")
                    return@withContext result
                } catch (e: Exception) {
                    Log.e(LOG_ERROR, "LLM error: ${e.message}", e)
                    return@withContext Result.success(
                        simulateAnalysisResponse(dataToProcess, resourceType, queryCategory).copy(
                            message = "LLM error: ${e.message}"
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(LOG_ERROR, "Fatal error: ${e.message}", e)
                return@withContext Result.success(
                    simulateAnalysisResponse(
                        deviceData,
                        RESOURCE_OTHER,
                        CATEGORY_INVALID
                    )
                )
            }
        }

    private suspend fun determineResourceType(query: String): String {
        try {
            val prompt = """
                Determine if the following user query is primarily about BATTERY, DATA, or OTHER.
                Respond with ONLY the resource type: BATTERY, DATA, or OTHER.
                Query: "$query"
            """.trimIndent()

            val response = withTimeout(15000) {
                sdk.generateResponseSuspend(prompt, maxTokens = 10, temperature = 0.1f)
            }.trim().uppercase()

            return when (response) {
                RESOURCE_BATTERY, RESOURCE_DATA -> response
                else -> RESOURCE_OTHER
            }
        } catch (e: Exception) {
            Log.e(LOG_QUERY, "Error determining resource type: ${e.message}", e)
            return RESOURCE_OTHER
        }
    }

    private suspend fun categorizeQuery(query: String, resourceType: String): Int {
        try {
            val categorizePrompt = """
                Categorize this user query into one of these categories based on its intent:
                1. Information Queries (e.g., "Which apps use the most $resourceType?")
                2. Predictive Queries (e.g., "Can I use an app with current $resourceType?")
                3. Optimization Requests (e.g., "Optimize $resourceType for an activity")
                4. Monitoring Triggers (e.g., "Notify me when $resourceType reaches a threshold")
                5. Past Usage Pattern Analysis (e.g., "Optimize $resourceType based on past usage patterns")
                0. Invalid Query (unrelated to $resourceType or unclear)
                Respond with ONLY the category number (0-5).
                Query: "$query"
            """.trimIndent()

            val response = withTimeout(15000) {
                sdk.generateResponseSuspend(categorizePrompt, maxTokens = 5, temperature = 0.1f)
            }.trim().toIntOrNull()

            return response?.coerceIn(0, 5) ?: CATEGORY_INVALID
        } catch (e: Exception) {
            Log.e(LOG_QUERY, "Error categorizing query: ${e.message}", e)
            return CATEGORY_INVALID
        }
    }

    private fun createAnalysisPrompt(
        deviceData: DeviceData,
        resourceType: String,
        queryCategory: Int
    ): String {
        val prompt = StringBuilder()
        val userQuery = deviceData.prompt?.substringAfter("- ")?.trim() ?: ""

        prompt.append(
            """
            You are a battery and data analysis/saver system. Analyze the device data and user query below.
            Respond with ONLY a valid JSON object matching this structure:
            {
                "batteryScore": number between 0-100,
                "dataScore": number between 0-100,
                "performanceScore": number between 0-100,
                "insights": [{"type": "BATTERY|DATA", "title": "string", "description": "string", "severity": "LOW|MEDIUM|HIGH"}],
                "actionable": [{"type": "string", "description": "string", "parameters": {}}]
            }

            IMPORTANT: The "actionable" array must only contain objects with "type" being one of the following, with their purposes:
    - $SET_STANDBY_BUCKET: Limits an app's background activity by placing it in a standby bucket (e.g., restricted), saving battery and data.
    - $RESTRICT_BACKGROUND_DATA: Prevents an app from using data in the background, reducing data consumption.
    - $KILL_APP: Force stops an app to immediately reduce resource usage, though it may restart later.
    - $MANAGE_WAKE_LOCKS: Restricts an app from keeping the device awake, reducing battery drain.
    - $SET_NOTIFICATION: Sets a notification to alert the user when a condition is met (e.g., low battery).
    - $SET_ALARM: Configures an alarm to trigger when a condition is met (e.g., data threshold reached).
    Do not create or use any other actionable types.

    When including actionables, ensure to provide the necessary parameters:
    - For $SET_STANDBY_BUCKET: "packageName" and "newMode", newMode should be one of the "active", "working_set" or "frequent" where frequent is the most aggressive mode
    - For $RESTRICT_BACKGROUND_DATA: "packageName"
    - For $KILL_APP: "packageName"
    - For $MANAGE_WAKE_LOCKS: "packageName"
    - For $SET_NOTIFICATION and $SET_ALARM: "condition" and "message"

            Device Data (last 24 hours):
            DEVICE: ${deviceData.deviceInfo.manufacturer} ${deviceData.deviceInfo.model}
            BATTERY: ${deviceData.battery.level}%${if (deviceData.battery.isCharging) " (Charging)" else ""}
            DATA: Current ${deviceData.currentDataMb}MB, Total ${deviceData.totalDataMb}MB
        """.trimIndent()
        )

        val topBatteryApps = deviceData.apps.sortedByDescending { it.batteryUsage }.take(10)
        val topDataApps =
            deviceData.apps.sortedByDescending { it.dataUsage.rxBytes + it.dataUsage.txBytes }
                .take(10)

        if (topBatteryApps.isNotEmpty()) {
            prompt.append("\nTOP BATTERY APPS:\n")
            topBatteryApps.forEach { prompt.append("- ${it.appName}: ${it.batteryUsage}%\n") }
        }

        if (topDataApps.isNotEmpty()) {
            prompt.append("\nTOP DATA APPS:\n")
            topDataApps.forEach {
                val mb = (it.dataUsage.rxBytes + it.dataUsage.txBytes) / (1024 * 1024)
                prompt.append("- ${it.appName}: ${mb}MB\n")
            }
        }

        if (userQuery.isNotEmpty()) {
            prompt.append("\nUSER QUERY: $userQuery\n")
        }

        when (queryCategory) {
            CATEGORY_INFORMATION -> {
                val numMatch = "\\b(\\d+)\\b".toRegex().find(userQuery)
                val numRequested = numMatch?.groupValues?.get(1)?.toIntOrNull() ?: 5
                prompt.append(
                    """
                    INSTRUCTIONS FOR INFORMATION QUERY:
                    - Provide exactly $numRequested items if specified, else 5.
                    - Use data from Device Data for ${resourceType.lowercase()}.
                    - Return insights only, do not include any actionables.
                    - Type: "$resourceType".
                """.trimIndent()
                )
            }

            CATEGORY_PREDICTIVE -> {
                prompt.append(
                    """
                    INSTRUCTIONS FOR PREDICTIVE QUERY:
                    - Analyze feasibility based on current ${resourceType.lowercase()} and typical app usage.
                    - Return yes/no with a brief explanation in insights.
                    - Return insights only, do not include any actionables.
                    - Type: "$resourceType".
                """.trimIndent()
                )
            }

            CATEGORY_OPTIMIZATION -> {
                prompt.append(
                    """
                    INSTRUCTIONS FOR OPTIMIZATION REQUEST:
                    - Provide recommendations to optimize $resourceType.
                    - Include actionables only from: $SET_STANDBY_BUCKET, $RESTRICT_BACKGROUND_DATA, $KILL_APP, $MANAGE_WAKE_LOCKS. choose from description whatever is appropriate
                    - For each actionable, include the required parameters as specified above.
                    - Type: "$resourceType".
                    
                    IMPORTANT EXCLUSION HANDLING:
                    1. First, identify any apps that should be kept running based on the user query.
                       For example, in "Save battery but keep WhatsApp running", "WhatsApp" should be excluded from restrictions.
                    2. For each excluded app:
                       - Include a "$SET_STANDBY_BUCKET" actionable with "newMode" set to "active"
                       - Set "packageName" to the appropriate package name for that app
                       - Include a description like "Keep [AppName] active as requested"
                    3. Do NOT apply any restrictive actions to excluded apps.
                    4. For all other apps, apply appropriate restrictions to optimize $resourceType.
                    5. Include one insight stating something like limiting functionality for other apps while keeping WhatsApp running for "Save battery but keep WhatsApp running". Just an example, you choose appropriate insight
                """.trimIndent()
                )
            }

            CATEGORY_MONITORING -> {
                prompt.append(
                    """
                    INSTRUCTIONS FOR MONITORING TRIGGER:
                    - Set up a trigger using $SET_NOTIFICATION if the query mentions "notify", otherwise use $SET_ALARM.
                    - Do not return any other actionable other than $SET_NOTIFICATION or $SET_ALARM
                    - Include "condition" and "message" in parameters.
                    - Type: "$resourceType".
                """.trimIndent()
                )
            }

            CATEGORY_PAST_USAGE -> {
                prompt.append(
                    """
                    INSTRUCTIONS FOR PAST USAGE PATTERN OPTIMIZATION:
                    - CURRENT TIME: ${getCurrentTimeString()}
                    - CURRENT DAY: ${getCurrentDayOfWeek()}
                    - BATTERY LEVEL: ${deviceData.battery.level}%${if (deviceData.battery.isCharging) " (Charging)" else ""}
                    - DATA USAGE: Current ${deviceData.currentDataMb}MB, Total ${deviceData.totalDataMb}MB
                    
                    1. Analyze the past usage patterns below
                    2. Identify patterns relevant to the current time, day, and resource level ($resourceType)
                    3. For BATTERY patterns:
                       - Only suggest actions if current battery is below 40% AND not charging AND a high-usage period is approaching (within 1-2 hours)
                       - Set severity based on battery level: LOW (>70%), MEDIUM (30-70%), HIGH (<30%)
                       - Use $SET_STANDBY_BUCKET, $KILL_APP, or $MANAGE_WAKE_LOCKS for critical periods
                    
                    4. For DATA patterns:
                       - Only suggest actions if remaining data is below 25% AND a high-usage period is approaching
                       - Set severity based on remaining data: LOW (>50%), MEDIUM (25-50%), HIGH (<25%)
                       - Use $RESTRICT_BACKGROUND_DATA for critical periods
                    
                    5. Include insights about identified patterns even if no action is needed
                    6. If no relevant patterns are found for today/current time, don't suggest actions and return an insight indicating no patterns were found
                    7. If a pattern is found but the resource level is adequate, include an insight but no actionables
                    8. If no past usage patterns are provided, return an insight requesting more usage data
                    
                    PAST USAGE PATTERNS:
                    ${deviceData.pastUsagePatterns ?: "No past usage patterns available"}
                """.trimIndent()
                )
            }

            else -> {
                prompt.append("INSTRUCTIONS: General analysis, minimal response. Do not include actionables.")
            }
        }

        return prompt.toString()
    }

    private suspend fun processPromptWithGemma(
        prompt: String,
        deviceData: DeviceData,
        resourceType: String
    ): Result<AnalysisResponse> {
        try {
            val jsonResponse = withTimeout(GENERATION_TIMEOUT_MS) {
                sdk.generateJsonResponse(prompt)
            }
            return Result.success(jsonResponse?.let { parseGemmaResponse(it, resourceType) }
                ?: simulateAnalysisResponse(deviceData, resourceType, CATEGORY_OPTIMIZATION))
        } catch (e: Exception) {
            Log.w(LOG_ERROR, "LLM processing failed, using offline analysis: ${e.message}")
            return Result.success(
                simulateAnalysisResponse(
                    deviceData,
                    resourceType,
                    CATEGORY_OPTIMIZATION
                )
            )
        }
    }

    private fun parseGemmaResponse(json: JSONObject, resourceType: String = RESOURCE_OTHER): AnalysisResponse {
        val insights = mutableListOf<Insight>()
        val actionables = mutableListOf<Actionable>()
        val insightsArray = json.optJSONArray("insights") ?: JSONArray()
        val actionablesArray = json.optJSONArray("actionable") ?: JSONArray()

        for (i in 0 until insightsArray.length()) {
            val insight = insightsArray.getJSONObject(i)
            val insightType = insight.optString("type")

            // Only include insights matching the query's resource type
            if (resourceType == RESOURCE_OTHER || insightType == resourceType) {
                insights.add(
                    Insight(
                        type = insightType,
                        title = insight.optString("title"),
                        description = insight.optString("description"),
                        severity = insight.optString("severity")
                    )
                )
            }
        }

        for (i in 0 until actionablesArray.length()) {
            val actionable = actionablesArray.getJSONObject(i)
            val type = actionable.optString("type")
            if (type !in ALLOWED_ACTIONABLE_TYPES) {
                Log.w(LOG_ERROR, "Invalid actionable type: $type, discarding")
                continue
            }
            val description = actionable.optString("description")
            val params = actionable.optJSONObject("parameters")?.let {
                mutableMapOf<String, String>().apply {
                    it.keys().forEach { key -> put(key, it.getString(key)) }
                }
            } ?: emptyMap()

            val packageName =
                params["packageName"] ?: runBlocking { 
                    packageNameResolver.extractPackageNameFromDescription(description) 
                } ?: "com.android.settings"

            // Create the description with app name instead of "data-intensive apps" for set_standby_bucket
            val formattedDescription = if (type == SET_STANDBY_BUCKET &&
                description.contains("data-intensive apps", ignoreCase = true)) {
                // Get app name if possible
                val appName = try {
                    val packageManager = context.packageManager
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    // If can't get app name, use package name
                    packageName
                }

                // Replace "data-intensive apps" with app name
                description.replace("data-intensive apps", appName, ignoreCase = true)
            } else {
                description
            }

            actionables.add(
                Actionable(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    packageName = packageName,
                    description = formattedDescription,
                    reason = "AI recommended",
                    estimatedBatterySavings = if (type in listOf(
                            SET_STANDBY_BUCKET,
                            KILL_APP,
                            MANAGE_WAKE_LOCKS
                        )
                    ) 10.0f else 0.0f,
                    estimatedDataSavings = if (type == RESTRICT_BACKGROUND_DATA) 50.0f else 0.0f,
                    severity = 3,
                    parameters = params,
                    enabled = true,
                    newMode = params["newMode"]
                        ?: if (type == SET_STANDBY_BUCKET) "working_set" else "",
                    throttleLevel = 0
                )
            )
        }

        return AnalysisResponse(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis().toFloat(),
            success = true,
            message = "Gemma response parsed successfully",
            responseType = "analysis",
            actionable = actionables,
            insights = insights,
            batteryScore = 75f,
            dataScore = 80f,
            performanceScore = 70f,
            estimatedSavings = AnalysisResponse.EstimatedSavings(
                batteryMinutes = 15f,
                dataMB = 200f
            )
        )
    }



    private fun simulateAnalysisResponse(
        deviceData: DeviceData,
        resourceType: String,
        queryCategory: Int
    ): AnalysisResponse {
        val insights = mutableListOf<Insight>()
        val actionables = mutableListOf<Actionable>()
        val userQuery = deviceData.prompt?.substringAfter("- ")?.trim() ?: ""

        when (queryCategory) {
            CATEGORY_INFORMATION -> {
                val numRequested =
                    "\\b(\\d+)\\b".toRegex().find(userQuery)?.groupValues?.get(1)?.toIntOrNull()
                        ?: 5
                val apps = if (resourceType == RESOURCE_BATTERY) {
                    deviceData.apps.sortedByDescending { it.batteryUsage }.take(numRequested)
                } else {
                    deviceData.apps.sortedByDescending { it.dataUsage.rxBytes + it.dataUsage.txBytes }
                        .take(numRequested)
                }
                val desc = if (apps.isNotEmpty()) {
                    apps.joinToString("\n") {
                        if (resourceType == RESOURCE_BATTERY) "• ${it.appName}: ${it.batteryUsage}%"
                        else "• ${it.appName}: ${(it.dataUsage.rxBytes + it.dataUsage.txBytes) / (1024 * 1024)}MB"
                    }
                } else "Sorry, no ${resourceType.lowercase()} data available."
                insights.add(
                    Insight(
                        resourceType,
                        "Top $numRequested ${resourceType.lowercase()} usage",
                        desc,
                        "LOW"
                    )
                )
            }

            CATEGORY_PREDICTIVE -> {
                val remaining =
                    if (resourceType == RESOURCE_BATTERY) deviceData.battery.level else (deviceData.totalDataMb - deviceData.currentDataMb)
                insights.add(
                    Insight(
                        resourceType,
                        "Prediction",
                        "Assuming typical usage, $remaining ${if (resourceType == RESOURCE_BATTERY) "%" else "MB"} may suffice.",
                        "MEDIUM"
                    )
                )
            }

            CATEGORY_OPTIMIZATION -> {
                insights.add(
                    Insight(
                        resourceType,
                        "Optimization Applied",
                        "Restricted high-usage apps.",
                        "MEDIUM"
                    )
                )
                actionables.add(
                    Actionable(
                        id = UUID.randomUUID().toString(),
                        type = if (resourceType == RESOURCE_BATTERY) KILL_APP else RESTRICT_BACKGROUND_DATA,
                        packageName = "com.android.settings",
                        description = "Restrict high ${resourceType.lowercase()} usage apps",
                        reason = "Optimization",
                        estimatedBatterySavings = if (resourceType == RESOURCE_BATTERY) 10.0f else 0.0f,
                        estimatedDataSavings = if (resourceType == RESOURCE_DATA) 50.0f else 0.0f,
                        severity = 3,
                        parameters = emptyMap(),
                        enabled = true,
                        newMode = "",
                        throttleLevel = 0
                    )
                )
            }

            CATEGORY_MONITORING -> {
                insights.add(Insight(resourceType, "Monitoring Set", "Trigger configured.", "LOW"))
                actionables.add(
                    Actionable(
                        id = UUID.randomUUID().toString(),
                        type = if ("notify" in userQuery.lowercase()) SET_NOTIFICATION else SET_ALARM,
                        packageName = "com.android.settings",
                        description = "Monitor $resourceType",
                        reason = "User request",
                        estimatedBatterySavings = 0.0f,
                        estimatedDataSavings = 0.0f,
                        severity = 1,
                        parameters = mapOf(
                            "condition" to userQuery,
                            "message" to "Threshold reached"
                        ),
                        enabled = true,
                        newMode = "",
                        throttleLevel = 0
                    )
                )
            }

            CATEGORY_PAST_USAGE -> {
                val currentDay = getCurrentDayOfWeek()
                val currentTime = getCurrentTimeString()
                val isLowBattery = deviceData.battery.level < 40 && !deviceData.battery.isCharging
                val isLowData = (deviceData.totalDataMb - deviceData.currentDataMb) < (deviceData.totalDataMb * 0.25)

                val severity = when {
                    resourceType == RESOURCE_BATTERY && deviceData.battery.level < 30 -> "HIGH"
                    resourceType == RESOURCE_BATTERY && deviceData.battery.level < 70 -> "MEDIUM"
                    resourceType == RESOURCE_DATA &&
                            (deviceData.totalDataMb - deviceData.currentDataMb) < (deviceData.totalDataMb * 0.25) -> "HIGH"
                    resourceType == RESOURCE_DATA &&
                            (deviceData.totalDataMb - deviceData.currentDataMb) < (deviceData.totalDataMb * 0.5) -> "MEDIUM"
                    else -> "LOW"
                }

                insights.add(
                    Insight(
                        resourceType,
                        "Past Usage Pattern Analysis",
                        "Based on your usage patterns, ${if (resourceType == RESOURCE_BATTERY) "battery" else "data"} " +
                                "consumption ${if (severity == "HIGH") "may be critical" else "is being monitored"} " +
                                "for $currentDay at $currentTime.",
                        severity
                    )
                )

                if ((resourceType == RESOURCE_BATTERY && isLowBattery) ||
                    (resourceType == RESOURCE_DATA && isLowData)) {

                    actionables.add(
                        Actionable(
                            id = UUID.randomUUID().toString(),
                            type = if (resourceType == RESOURCE_BATTERY) SET_STANDBY_BUCKET else RESTRICT_BACKGROUND_DATA,
                            packageName = "com.android.chrome",  // Example app, would be determined by LLM in real case
                            description = "Optimize ${if (resourceType == RESOURCE_BATTERY) "battery" else "data"} " +
                                    "usage based on past patterns for $currentDay",
                            reason = "Past usage pattern optimization",
                            estimatedBatterySavings = if (resourceType == RESOURCE_BATTERY) 15.0f else 0.0f,
                            estimatedDataSavings = if (resourceType == RESOURCE_DATA) 100.0f else 0.0f,
                            severity = if (severity == "HIGH") 4 else if (severity == "MEDIUM") 3 else 2,
                            parameters = if (resourceType == RESOURCE_BATTERY)
                                mapOf("newMode" to "working_set")
                            else emptyMap(),
                            enabled = true,
                            newMode = if (resourceType == RESOURCE_BATTERY) "working_set" else "",
                            throttleLevel = 0
                        )
                    )
                }
            }
        }

        return AnalysisResponse(
            id = deviceData.deviceId,
            timestamp = System.currentTimeMillis().toFloat(),
            success = true,
            message = "Simulated response",
            actionable = actionables,
            insights = insights,
            batteryScore = 50f,
            dataScore = 50f,
            performanceScore = 50f,
            estimatedSavings = AnalysisResponse.EstimatedSavings(15f, 100f)
        )
    }

    private suspend fun ensureSdkInitialized() {
        if (_sdk == null) sdk
        if (!sdk.initialize()) throw IllegalStateException("SDK initialization failed")
    }
}