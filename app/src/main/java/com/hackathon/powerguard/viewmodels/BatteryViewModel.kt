package com.hackathon.powerguard.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.powerguard.collector.UsageDataCollector // <-- Import real collector
import com.hackathon.powerguard.models.BatteryStats // Keep if needed for state
import com.hackathon.powerguard.models.DeviceData // Import DeviceData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BatteryUiState(
    val isLoading: Boolean = true,
    val batteryDetails: BatteryStats? = null,
    val error: String? = null
)

@HiltViewModel
class BatteryViewModel @Inject constructor(
    private val usageDataCollector: UsageDataCollector
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatteryUiState())
    val uiState: StateFlow<BatteryUiState> = _uiState.asStateFlow()

    init {
        loadBatteryData()
    }

    fun loadBatteryData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            Log.d("BatteryViewModel", "Loading battery data...")
            try {
                // Call the single method from the real collector
                val allDeviceData: DeviceData = usageDataCollector.collectDeviceData()

                // Extract the needed part from the result
                val batteryInfo = allDeviceData.batteryStats

                Log.d("BatteryViewModel", "Loaded battery data: Level ${batteryInfo.level}%")
                _uiState.update {
                    it.copy(isLoading = false, batteryDetails = batteryInfo, error = null)
                }
            } catch (e: Exception) {
                Log.e("BatteryViewModel", "Error loading battery data", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load battery data: ${e.localizedMessage}"
                    )
                }
            }
        }
    }
}