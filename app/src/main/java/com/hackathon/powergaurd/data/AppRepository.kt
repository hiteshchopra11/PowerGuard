package com.hackathon.powergaurd.data

import com.hackathon.powergaurd.models.ActionResponse
import com.hackathon.powergaurd.models.AppUsageData
import com.hackathon.powergaurd.models.BatteryOptimizationData
import com.hackathon.powergaurd.models.NetworkUsageData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepository @Inject constructor() {

    private val _latestResponse = MutableStateFlow<ActionResponse?>(null)
    val latestResponse: StateFlow<ActionResponse?> = _latestResponse.asStateFlow()

    private val _optimizationHistory = MutableStateFlow<List<ActionResponse>>(emptyList())
    val optimizationHistory: StateFlow<List<ActionResponse>> = _optimizationHistory.asStateFlow()

    private val _appUsageData = MutableStateFlow<List<AppUsageData>>(emptyList())
    val appUsageData: StateFlow<List<AppUsageData>> = _appUsageData.asStateFlow()

    private val _batteryData = MutableStateFlow<List<BatteryOptimizationData>>(emptyList())
    val batteryData: StateFlow<List<BatteryOptimizationData>> = _batteryData.asStateFlow()

    private val _networkData = MutableStateFlow<List<NetworkUsageData>>(emptyList())
    val networkData: StateFlow<List<NetworkUsageData>> = _networkData.asStateFlow()

    suspend fun saveActionResponse(response: ActionResponse) {
        _latestResponse.value = response

        // Add to history
        val currentHistory = _optimizationHistory.value.toMutableList()
        currentHistory.add(0, response) // Add at the beginning (newest first)

        // Limit history size
        if (currentHistory.size > MAX_HISTORY_SIZE) {
            _optimizationHistory.value = currentHistory.take(MAX_HISTORY_SIZE)
        } else {
            _optimizationHistory.value = currentHistory
        }
    }

    suspend fun saveUsageData(
        appUsage: List<AppUsageData>,
        batteryInfo: BatteryOptimizationData,
        networkUsage: List<NetworkUsageData>
    ) {
        // Update app usage data
        val currentAppUsage = _appUsageData.value.toMutableList()
        currentAppUsage.addAll(appUsage)
        _appUsageData.value = currentAppUsage.takeLast(MAX_APP_DATA_SIZE)

        // Update battery data
        val currentBatteryData = _batteryData.value.toMutableList()
        currentBatteryData.add(batteryInfo)
        _batteryData.value = currentBatteryData.takeLast(MAX_BATTERY_DATA_SIZE)

        // Update network data
        val currentNetworkData = _networkData.value.toMutableList()
        currentNetworkData.addAll(networkUsage)
        _networkData.value = currentNetworkData.takeLast(MAX_NETWORK_DATA_SIZE)
    }

    suspend fun getTopBatteryConsumers(count: Int = 5): List<AppUsageData> {
        return _appUsageData
            .value
            .sortedByDescending { it.batteryUsagePercent }
            .distinctBy { it.packageName }
            .take(count)
    }

    suspend fun getTopNetworkUsers(count: Int = 5): List<NetworkUsageData> {
        return _networkData
            .value
            .sortedByDescending { it.mobileDataUsageBytes + it.wifiDataUsageBytes }
            .distinctBy { it.packageName }
            .take(count)
    }

    suspend fun getAppUsagePattern(packageName: String): List<AppUsageData> {
        return _appUsageData.value.filter { it.packageName == packageName }.sortedBy {
            it.timestamp
        }
    }

    companion object {
        private const val MAX_HISTORY_SIZE = 50
        private const val MAX_APP_DATA_SIZE = 1000
        private const val MAX_BATTERY_DATA_SIZE = 100
        private const val MAX_NETWORK_DATA_SIZE = 1000
    }
}
