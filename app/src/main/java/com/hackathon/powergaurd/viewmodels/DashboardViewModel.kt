package com.hackathon.powergaurd.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.powergaurd.collector.UsageDataCollector // <-- Import real collector
import com.hackathon.powergaurd.models.AppUsageInfo
import com.hackathon.powergaurd.models.BatteryStats
import com.hackathon.powergaurd.models.DeviceData // Import DeviceData
import com.hackathon.powergaurd.models.NetworkUsage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = true,
    val batteryStats: BatteryStats? = null,
    val topAppUsage: List<AppUsageInfo> = emptyList(), // Example: Show top 3-5 apps
    val networkUsage: NetworkUsage? = null, // Or specific network stats you want to show
    val error: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val usageDataCollector: UsageDataCollector
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            Log.d("DashboardViewModel", "Loading initial dashboard data...")
            try {
                // --- DATA FETCHING CHANGE ---
                // Call the single method from the real collector
                val allDeviceData: DeviceData = usageDataCollector.collectDeviceData()

                // Extract multiple parts needed for the dashboard
                val batteryInfo = allDeviceData.batteryStats
                // Example: Get top 5 apps by combined usage time
                val topApps = allDeviceData.appUsage
                    .sortedByDescending { it.foregroundTimeMs + it.backgroundTimeMs }
                    .take(5)
                val networkInfo = allDeviceData.networkUsage

                Log.d(
                    "DashboardViewModel",
                    "Loaded dashboard data. Battery: ${batteryInfo.level}%, Top App: ${topApps.firstOrNull()?.appName}"
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        batteryStats = batteryInfo,
                        topAppUsage = topApps,
                        networkUsage = networkInfo, // Update based on what Dashboard needs
                        error = null
                    )
                }

            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Error loading dashboard data", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load dashboard data: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    // Add refresh function if needed, which would call loadInitialData again
    fun refreshData() {
        loadInitialData()
    }
}