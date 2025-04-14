package com.hackathon.powergaurd.data.gemma

import android.content.Context
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.annotation.SuppressLint
import android.os.Build
import java.io.IOException
import java.net.UnknownHostException
import android.os.Handler
import com.powergaurd.llm.exceptions.NoConnectivityException
import com.powergaurd.llm.exceptions.InvalidAPIKeyException

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
            // Create the prompt for debugging purpose
            val prompt = createAnalysisPrompt(deviceData)
            
            // Log the prompt in human-readable format
            Log.d("SendPromptDebug", "Human-readable prompt:\n$prompt")
            
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
                                put("dozeBytes", app.dataUsage.dozeBytes)
                            })
                            
                            // Add wakelock information
                            put("wakelockInfo", JSONObject().apply {
                                put("acquireCount", app.wakelockInfo.acquireCount)
                                put("totalDurationMs", app.wakelockInfo.totalDurationMs)
                                put("wakelockTypes", JSONObject().apply {
                                    app.wakelockInfo.wakelockTypes.forEach { (name, count) ->
                                        put(name, count)
                                    }
                                })
                            })
                            
                            // Add socket connection information
                            put("socketConnections", JSONObject().apply {
                                put("totalConnections", app.socketConnections.totalConnections)
                                put("activeConnections", app.socketConnections.activeConnections)
                                put("totalDurationMs", app.socketConnections.totalDurationMs)
                                put("tcpConnections", app.socketConnections.tcpConnections)
                                put("udpConnections", app.socketConnections.udpConnections)
                            })
                            
                            // Add alarm wakeups and priority changes
                            put("alarmWakeups", app.alarmWakeups)
                            put("priorityChanges", app.priorityChanges)
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
            
            // TEMPORARILY BYPASSING SDK CALL
            Log.d("SendPromptDebug", "Temporarily bypassing SDK call and using simulated response")
            
            // Check if network is available before using the SDK
            if (!isNetworkAvailable()) {
                Log.w(TAG, "Network not available, using simulated response")
                return@withContext Result.success(simulateAnalysisResponse(deviceData))
            }
            
            // TODO : Hitesh
            // COMMENTED OUT SDK INITIALIZATION AND CALL
            // ensureSdkInitialized()
            // 
            // try {
            //     val jsonResponse = sdk.generateJsonResponse(prompt, maxTokens = 512, temperature = 0.5f)
            //     
            //     if (DEBUG) {
            //         Log.d(TAG, "Gemma response: $jsonResponse")
            //     }
            //     
            //     val parsedResponse = if (jsonResponse != null) {
            //         parseGemmaResponse(jsonResponse, deviceData.deviceId)
            //     } else {
            //         simulateAnalysisResponse(deviceData)
            //     }
            //     
            //     return@withContext Result.success(parsedResponse)
            // } catch (e: Exception) {
            //     // SDK error handling code...
            // }
            
            // Use simulated response instead
            return@withContext Result.success(simulateAnalysisResponse(deviceData).copy(
                message = "DEBUG MODE: Using simulated response (SDK call bypassed)"
            ))
            
        } catch (e: LifecycleException) {
            Log.e(TAG, "Lifecycle exception while analyzing device data: ${e.message}", e)
            return@withContext Result.success(simulateAnalysisResponse(deviceData))
        } catch (e: IOException) {
            Log.e(TAG, "IO exception while analyzing device data: ${e.message}", e)
            return@withContext Result.success(simulateAnalysisResponse(deviceData))
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Network error (unknown host) while analyzing device data: ${e.message}", e)
            return@withContext Result.success(simulateAnalysisResponse(deviceData))
        } catch (e: Exception) {
            Log.e(TAG, "Exception while analyzing device data with Gemma (${e.javaClass.simpleName}): ${e.message}", e)
            // Fall back to simulated response
            return@withContext Result.success(simulateAnalysisResponse(deviceData))
        }
    }

    /**
     * Creates a prompt for analysis based on device data
     */
    private fun createAnalysisPrompt(deviceData: DeviceData): String {
        val prompt = StringBuilder()
        
        prompt.append("As PowerGuard AI, analyze this device data and provide optimizations:\n\n")
        
        // Add device details - more compact format
        prompt.append("Device: ${deviceData.deviceInfo.manufacturer} ${deviceData.deviceInfo.model}, ")
        prompt.append("Android ${deviceData.deviceInfo.osVersion}, ")
        prompt.append("Battery: ${deviceData.battery.level}%, ")
        prompt.append("Memory: ${deviceData.memory.availableRam / (1024 * 1024)}/${deviceData.memory.totalRam / (1024 * 1024)} MB\n")
        
        // Log device details separately for debugging
        Log.d("SendPromptDebug", "Device Details: " +
                "${deviceData.deviceInfo.manufacturer} ${deviceData.deviceInfo.model}, " +
                "Android ${deviceData.deviceInfo.osVersion}, " +
                "Battery: ${deviceData.battery.level}%, " +
                "Memory: ${deviceData.memory.availableRam / (1024 * 1024)}/${deviceData.memory.totalRam / (1024 * 1024)} MB")
        
        // Top battery using apps - limit to 3 instead of 5
        prompt.append("Top Battery Apps: ")
        val topBatteryApps = deviceData.apps
            .sortedByDescending { it.batteryUsage }
            .take(3)
            
        topBatteryApps.forEach { app ->
            prompt.append("${app.appName} (${app.batteryUsage}%), ")
        }
        prompt.append("\n")
        
        // Log battery apps for debugging with additional metrics
        Log.d("SendPromptDebug", "Top Battery Apps: " + 
            topBatteryApps.joinToString("\n") { app -> 
                "${app.appName} (${app.batteryUsage}%), " +
                "Wakelocks: ${app.wakelockInfo.acquireCount} (${formatDuration(app.wakelockInfo.totalDurationMs)}), " +
                "Alarm wakeups: ${app.alarmWakeups}"
            })
        
        // Top data using apps - limit to 3 instead of 5
        prompt.append("Top Data Apps: ")
        val topDataApps = deviceData.apps
            .sortedByDescending { it.dataUsage.background + it.dataUsage.foreground }
            .take(3)
            
        topDataApps.forEach { app ->
            val dataMB = (app.dataUsage.background + app.dataUsage.foreground) / (1024 * 1024)
            prompt.append("${app.appName} (${dataMB} MB), ")
        }
        prompt.append("\n")
        
        // Log data apps for debugging with additional metrics
        Log.d("SendPromptDebug", "Top Data Apps: " + 
            topDataApps.joinToString("\n") { app -> 
                val dataMB = (app.dataUsage.background + app.dataUsage.foreground) / (1024 * 1024)
                val dozeMB = app.dataUsage.dozeBytes / (1024 * 1024)
                "${app.appName} (${dataMB} MB), " +
                "Foreground: ${formatBytes(app.dataUsage.foreground)}, " +
                "Background: ${formatBytes(app.dataUsage.background)}, " +
                "Doze mode: ${formatBytes(app.dataUsage.dozeBytes)}, " +
                "Socket connections: ${app.socketConnections.totalConnections}"
            })
        
        // Add top wakelocks info
        prompt.append("Top Wakelock Apps: ")
        val topWakelockApps = deviceData.apps
            .sortedByDescending { it.wakelockInfo.acquireCount }
            .filter { it.wakelockInfo.acquireCount > 0 }
            .take(3)
        
        topWakelockApps.forEach { app ->
            prompt.append("${app.appName} (${app.wakelockInfo.acquireCount} wakelocks), ")
        }
        prompt.append("\n")
        
        // Log wakelock apps for debugging
        Log.d("SendPromptDebug", "Top Wakelock Apps: " + 
            topWakelockApps.joinToString("\n") { app -> 
                "${app.appName}: ${app.wakelockInfo.acquireCount} wakelocks, " +
                "duration: ${formatDuration(app.wakelockInfo.totalDurationMs)}, " +
                "types: ${app.wakelockInfo.wakelockTypes.entries.joinToString(", ") { entry -> "${entry.key}=${entry.value}" }}"
            })
        
        // Add doze mode data usage
        prompt.append("Apps Using Data in Doze Mode: ")
        val dozeApps = deviceData.apps
            .filter { it.dataUsage.dozeBytes > 0 }
            .sortedByDescending { it.dataUsage.dozeBytes }
            .take(3)
        
        dozeApps.forEach { app ->
            val dozeMB = app.dataUsage.dozeBytes / (1024 * 1024)
            prompt.append("${app.appName} (${dozeMB} MB), ")
        }
        prompt.append("\n")
        
        // Log doze mode apps for debugging
        Log.d("SendPromptDebug", "Apps Using Data in Doze Mode: " + 
            dozeApps.joinToString("\n") { app -> 
                "${app.appName}: ${formatBytes(app.dataUsage.dozeBytes)}, " +
                "Socket connections: ${app.socketConnections.totalConnections}, " +
                "Priority changes: ${app.priorityChanges}"
            })
        
        // Include user goal if provided
        deviceData.prompt?.let {
            prompt.append("User goal: $it\n")
            Log.d("SendPromptDebug", "User goal: $it")
        }
        
        // Instructions for JSON format - simplified
        prompt.append("\nRespond with JSON:\n")
        prompt.append("""
            {
              "success": true,
              "batteryScore": 85,
              "dataScore": 90,
              "performanceScore": 75,
              "insights": [
                {"type": "BATTERY", "title": "Title", "description": "Brief insight", "severity": "LOW"}
              ],
              "actionable": [
                {"id": "uuid", "type": "RESTRICT_DATA", "packageName": "com.example", "description": "Description", 
                 "reason": "Reason", "estimatedBatterySavings": 10.5, "estimatedDataSavings": 25.0, "severity": 3}
              ],
              "estimatedSavings": {"batteryMinutes": 45, "dataMB": 250}
            }
        """.trimIndent())
        
        return prompt.toString()
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
            val success = jsonObject.optBoolean("success", true)
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
                
                // Parse parameters map
                val parametersJson = actionableJson.optJSONObject("parameters")
                val parameters = mutableMapOf<String, Any>()
                
                if (parametersJson != null) {
                    parametersJson.keys().forEach { key ->
                        parameters[key] = parametersJson.opt(key)
                    }
                }
                
                actionables.add(
                    Actionable(
                        id = actionableJson.optString("id", UUID.randomUUID().toString()),
                        type = actionableJson.optString("type", "UNKNOWN"),
                        packageName = actionableJson.optString("packageName", ""),
                        description = actionableJson.optString("description", ""),
                        reason = actionableJson.optString("reason", ""),
                        estimatedBatterySavings = actionableJson.optDouble("estimatedBatterySavings", 0.0).toFloat(),
                        estimatedDataSavings = actionableJson.optDouble("estimatedDataSavings", 0.0).toFloat(),
                        severity = actionableJson.optInt("severity", 1),
                        newMode = actionableJson.optString("newMode", null),
                        enabled = actionableJson.optBoolean("enabled", true),
                        throttleLevel = actionableJson.optInt("throttleLevel", 1),
                        parameters = parameters
                    )
                )
            }
            
            // Parse estimated savings
            val savingsJson = jsonObject.optJSONObject("estimatedSavings") ?: JSONObject()
            val estimatedSavings = AnalysisResponse.EstimatedSavings(
                batteryMinutes = savingsJson.optDouble("batteryMinutes", 0.0).toFloat(),
                dataMB = savingsJson.optDouble("dataMB", 0.0).toFloat()
            )
            
            return AnalysisResponse(
                id = deviceId,
                timestamp = System.currentTimeMillis().toFloat(),
                success = success,
                message = "Analysis powered by Gemma",
                actionable = actionables,
                insights = insights,
                batteryScore = batteryScore,
                dataScore = dataScore,
                performanceScore = performanceScore,
                estimatedSavings = estimatedSavings
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