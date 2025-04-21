package com.hackathon.powergaurd.ui.viewmodels

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.powergaurd.collector.UsageDataCollector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppUsageInfo(
    val appName: String,
    val batteryPercentage: Float
)

data class AppDataUsage(
    val appName: String,
    val dataUsed: String
)

data class ExploreUiState(
    val batteryLevel: Int = 0,
    val temperature: Float = 0f,
    val voltage: Int = 0,
    val isCharging: Boolean = false,
    val chargingType: String = "",
    val health: Int = 0,
    val capacity: Long = 0,
    val currentNow: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    val remainingBatteryTime: Int = 0,
    val topBatteryApps: List<AppUsageInfo> = emptyList(),
    val networkType: String? = "WiFi",
    val signalStrength: Int? = 3,
    val totalDataPlanMb: Int = 10000,  // Total data plan in MB
    val usedDataMb: Int = 2600,      // Used data in MB
    val remainingDays: Int = 12,     // Days remaining in billing cycle
    val topDataApps: List<AppDataUsage> = emptyList()  // Top data-consuming apps
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val usageDataCollector: UsageDataCollector
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    init {
        refreshDeviceData()
    }

    @SuppressLint("NewApi")
    fun refreshDeviceData() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                val deviceData = usageDataCollector.collectDeviceData()
                val batteryInfo = deviceData.battery
                val networkInfo = deviceData.network
                
                // Generate estimated battery time (for demo)
                val remainingTime = estimateRemainingBatteryTime(batteryInfo.level, batteryInfo.isCharging)
                
                // Generate top battery usage apps (for demo)
                val topBatteryApps = deviceData.apps
                    .sortedByDescending { it.batteryUsage }
                    .take(5)
                    .map { app -> 
                        AppUsageInfo(
                            appName = app.appName,
                            batteryPercentage = if (app.batteryUsage < 0) {
                                // Generate random percentage between 1-20% if data not available
                                (1..20).random().toFloat()
                            } else {
                                app.batteryUsage
                            }
                        )
                    }
                
                // Calculate data usage based on network stats
                val totalDataPlanMb = 4000 // Default 4GB plan in MB
                
                // Calculate used data based on network stats if available
                val usedDataMb = if (networkInfo.dataUsage.rxBytes > 0 || networkInfo.dataUsage.txBytes > 0) {
                    // Convert bytes to MB (1 MB = 1024 * 1024 bytes)
                    ((networkInfo.dataUsage.rxBytes + networkInfo.dataUsage.txBytes) / (1024 * 1024)).toInt()
                } else {
                    2600 // Default fallback value
                }
                
                // Create list of top data usage apps
                val topDataApps = deviceData.apps
                    .sortedByDescending { it.dataUsage.rxBytes + it.dataUsage.txBytes }
                    .take(5)
                    .map { app ->
                        val totalBytes = app.dataUsage.rxBytes + app.dataUsage.txBytes
                        val dataUsedMb = totalBytes / (1024 * 1024)
                        AppDataUsage(
                            appName = app.appName,
                            dataUsed = "${dataUsedMb} MB"
                        )
                    }
                
                _uiState.value = ExploreUiState(
                    batteryLevel = batteryInfo.level,
                    temperature = batteryInfo.temperature,
                    voltage = batteryInfo.voltage,
                    isCharging = batteryInfo.isCharging,
                    chargingType = batteryInfo.chargingType,
                    health = batteryInfo.health,
                    capacity = batteryInfo.capacity,
                    currentNow = batteryInfo.currentNow,
                    isLoading = false,
                    remainingBatteryTime = remainingTime,
                    topBatteryApps = topBatteryApps,
                    networkType = networkInfo.type,
                    signalStrength = networkInfo.strength,
                    totalDataPlanMb = totalDataPlanMb,
                    usedDataMb = usedDataMb,
                    remainingDays = 12, // Default value for remaining days
                    topDataApps = topDataApps
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load device data: ${e.message}"
                )
            }
        }
    }
    
    // Estimates remaining battery time in minutes based on battery level
    private fun estimateRemainingBatteryTime(batteryLevel: Int, isCharging: Boolean): Int {
        return if (isCharging) {
            // Estimate time to full charge
            (100 - batteryLevel) * 2 // Very rough estimate: 2 minutes per percent
        } else {
            // Estimate time until empty
            batteryLevel * 5 // Very rough estimate: 5 minutes per percent
        }
    }
} 