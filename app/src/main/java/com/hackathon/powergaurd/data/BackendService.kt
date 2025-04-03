package com.hackathon.powergaurd.data

import android.util.Log
import com.hackathon.powergaurd.models.ActionResponse
import com.hackathon.powergaurd.models.DeviceData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackendService @Inject constructor() {

    companion object {
        // Replace with your actual backend URL
        private const val BACKEND_URL = "https://powerguard-ai-backend.example.com/analyze"
        private const val TAG = "BackendService"
    }

    suspend fun sendDataForAnalysis(deviceData: DeviceData): ActionResponse {
        return try {
            withContext(Dispatchers.IO) {
                val jsonPayload = convertDeviceDataToJson(deviceData)
                val responseJson = makeHttpRequest(jsonPayload)
                parseBackendResponse(responseJson)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending data to backend", e)
            // Fallback to simulated response if network call fails
            simulateBackendResponse(deviceData)
        }
    }

    private fun convertDeviceDataToJson(deviceData: DeviceData): String {
        // Detailed logging of device data before JSON conversion
        Log.d(TAG, "Converting DeviceData to JSON")
        Log.d(TAG, "Device ID: ${deviceData.deviceId}")
        Log.d(TAG, "Timestamp: ${deviceData.timestamp}")
        Log.d(TAG, "App Usage Count: ${deviceData.appUsage.size}")
        Log.d(TAG, "Battery Level: ${deviceData.batteryStats.level}")
        Log.d(TAG, "Network Usage Apps: ${deviceData.networkUsage.appNetworkUsage.size}")
        Log.d(TAG, "Wake Locks Count: ${deviceData.wakeLocks.size}")

        val jsonObject = JSONObject().apply {
            put("deviceId", deviceData.deviceId)
            put("timestamp", deviceData.timestamp)

            // App Usage Data
            val appUsageArray = JSONArray()
            deviceData.appUsage.forEach { app ->
                appUsageArray.put(JSONObject().apply {
                    put("packageName", app.packageName)
                    put("appName", app.appName)
                    put("foregroundTimeMs", app.foregroundTimeMs)
                    put("backgroundTimeMs", app.backgroundTimeMs)
                    put("lastUsed", app.lastUsed)
                    put("launchCount", app.launchCount)
                })
            }
            put("appUsage", appUsageArray)

            // Battery Stats
            put("batteryStats", JSONObject().apply {
                put("level", deviceData.batteryStats.level)
                put("temperature", deviceData.batteryStats.temperature)
                put("isCharging", deviceData.batteryStats.isCharging)
                put("chargingType", deviceData.batteryStats.chargingType)
                put("voltage", deviceData.batteryStats.voltage)
                put("health", deviceData.batteryStats.health)
                put("estimatedRemainingTime", deviceData.batteryStats.estimatedRemainingTime)
            })

            // Network Usage
            val networkUsageArray = JSONArray()
            deviceData.networkUsage.appNetworkUsage.forEach { networkApp ->
                networkUsageArray.put(JSONObject().apply {
                    put("packageName", networkApp.packageName)
                    put("dataUsageBytes", networkApp.dataUsageBytes)
                    put("wifiUsageBytes", networkApp.wifiUsageBytes)
                })
            }
            put("networkUsage", networkUsageArray)
            put("wifiConnected", deviceData.networkUsage.wifiConnected)
            put("mobileDataConnected", deviceData.networkUsage.mobileDataConnected)
            put("networkType", deviceData.networkUsage.networkType)

            // Wake Locks
            val wakeLockArray = JSONArray()
            deviceData.wakeLocks.forEach { wakeLock ->
                wakeLockArray.put(JSONObject().apply {
                    put("packageName", wakeLock.packageName)
                    put("wakeLockName", wakeLock.wakeLockName)
                    put("timeHeldMs", wakeLock.timeHeldMs)
                })
            }
            put("wakeLocks", wakeLockArray)
        }

        return jsonObject.toString()
    }

    private fun makeHttpRequest(payload: String): String {
        // Log the full JSON payload before sending
        Log.d(TAG, "Full JSON Payload:")
        Log.d(TAG, payload)

        val url = URL(BACKEND_URL)
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true
            connection.doInput = true

            // Write payload
            connection.outputStream.use { os ->
                val input = payload.toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            // Log additional network details
            Log.d(TAG, "Request URL: $BACKEND_URL")
            Log.d(TAG, "Request Method: ${connection.requestMethod}")
            Log.d(TAG, "Content Type: ${connection.getRequestProperty("Content-Type")}")

            // Read response
            val responseCode = connection.responseCode
            Log.d(TAG, "Response Code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }

                    // Log full response
                    Log.d(TAG, "Full Response:")
                    Log.d(TAG, response.toString())

                    return response.toString()
                }
            } else {
                // Log error response
                BufferedReader(InputStreamReader(connection.errorStream)).use { reader ->
                    val errorResponse = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        errorResponse.append(line)
                    }
                    Log.e(TAG, "Error Response: $errorResponse")
                }
                throw Exception("HTTP error code: $responseCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network request error", e)
            throw e
        } finally {
            connection.disconnect()
        }
    }

    private fun parseBackendResponse(responseJson: String): ActionResponse {
        val jsonObject = JSONObject(responseJson)

        // Parse actionables
        val actionablesArray = jsonObject.getJSONArray("actionables")
        val actionables = (0 until actionablesArray.length()).map { index ->
            val actionableObj = actionablesArray.getJSONObject(index)
            ActionResponse.Actionable(
                type = actionableObj.getString("type"),
                app = actionableObj.optString("app", null),
                newMode = actionableObj.optString("newMode", null),
                reason = actionableObj.optString("reason", null),
                enabled = actionableObj.optBoolean("enabled", false)
            )
        }

        // Parse usage patterns
        val usagePatternsObj = jsonObject.getJSONObject("usagePatterns")
        val usagePatterns = mutableMapOf<String, String>()
        usagePatternsObj.keys().forEach { key ->
            usagePatterns[key] = usagePatternsObj.getString(key)
        }

        return ActionResponse(
            actionables = actionables,
            summary = jsonObject.getString("summary"),
            usagePatterns = usagePatterns,
            timestamp = System.currentTimeMillis()
        )
    }

    // Fallback method to simulate backend response if network call fails
    private fun simulateBackendResponse(deviceData: DeviceData): ActionResponse {
        // Similar to the previous simulated response logic
        val actionables = mutableListOf<ActionResponse.Actionable>()
        val usagePatterns = mutableMapOf<String, String>()

        // Find battery-draining apps
        deviceData.appUsage
            .sortedByDescending { it.foregroundTimeMs + it.backgroundTimeMs }
            .take(3)
            .forEach { appUsage ->
                if (appUsage.backgroundTimeMs > 3_600_000) { // More than 1 hour in background
                    actionables.add(
                        ActionResponse.Actionable(
                            type = "app_mode_change",
                            app = appUsage.packageName,
                            newMode = "strict"
                        )
                    )
                    usagePatterns[appUsage.packageName] = "Uses significant background resources"
                }
            }

        // Check network usage
        deviceData.networkUsage.appNetworkUsage
            .filter { it.dataUsageBytes > 50_000_000 } // 50MB
            .forEach { networkUsage ->
                actionables.add(
                    ActionResponse.Actionable(
                        type = "restrict_background_data",
                        app = networkUsage.packageName,
                        enabled = true
                    )
                )
                usagePatterns[networkUsage.packageName] =
                    "Uses significant network data in background"
            }

        // Check battery temperature
        if (deviceData.batteryStats.temperature > 40) {
            actionables.add(
                ActionResponse.Actionable(
                    type = "cut_charging",
                    reason = "Battery overheating"
                )
            )
        }

        return ActionResponse(
            actionables = actionables,
            summary = "Simulated offline optimization recommendations based on device data.",
            usagePatterns = usagePatterns,
            timestamp = System.currentTimeMillis()
        )
    }
}