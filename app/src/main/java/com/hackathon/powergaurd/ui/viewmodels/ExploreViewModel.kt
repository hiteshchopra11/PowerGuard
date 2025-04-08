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
    val signalStrength: Int? = 3
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
                    signalStrength = networkInfo.strength
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