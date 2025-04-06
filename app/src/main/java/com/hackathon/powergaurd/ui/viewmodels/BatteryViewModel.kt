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

data class BatteryUiState(
    val batteryLevel: Int = 0,
    val temperature: Float = 0f,
    val voltage: Int = 0,
    val isCharging: Boolean = false,
    val chargingType: String = "",
    val health: Int = 0,
    val capacity: Long = 0,
    val currentNow: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class BatteryViewModel @Inject constructor(
    private val usageDataCollector: UsageDataCollector
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatteryUiState())
    val uiState: StateFlow<BatteryUiState> = _uiState.asStateFlow()

    init {
        refreshBatteryData()
    }

    @SuppressLint("NewApi")
    fun refreshBatteryData() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                val deviceData = usageDataCollector.collectDeviceData()
                val batteryInfo = deviceData.battery
                
                _uiState.value = BatteryUiState(
                    batteryLevel = batteryInfo.level,
                    temperature = batteryInfo.temperature,
                    voltage = batteryInfo.voltage,
                    isCharging = batteryInfo.isCharging,
                    chargingType = batteryInfo.chargingType,
                    health = batteryInfo.health,
                    capacity = batteryInfo.capacity,
                    currentNow = batteryInfo.currentNow,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load battery data: ${e.message}"
                )
            }
        }
    }
} 