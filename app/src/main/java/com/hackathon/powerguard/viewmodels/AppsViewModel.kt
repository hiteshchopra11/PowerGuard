package com.hackathon.powerguard.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.powerguard.collector.UsageDataCollector
import com.hackathon.powerguard.models.AppUsageInfo
import com.hackathon.powerguard.models.DeviceData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppsUiState(
    val isLoading: Boolean = true,
    val appUsageList: List<AppUsageInfo> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val usageDataCollector: UsageDataCollector
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = _uiState.asStateFlow()

    init {
        loadAppsData()
    }

    fun loadAppsData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            Log.d("AppsViewModel", "Loading apps usage data...")
            try {
                val allDeviceData: DeviceData = usageDataCollector.collectDeviceData()

                // Extract the needed part from the result
                val appUsage = allDeviceData.appUsage

                Log.d("AppsViewModel", "Loaded ${appUsage.size} apps with usage data.")
                _uiState.update {
                    it.copy(isLoading = false, appUsageList = appUsage, error = null)
                }
            } catch (e: Exception) {
                Log.e("AppsViewModel", "Error loading apps data", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load apps data: ${e.localizedMessage}"
                    )
                }
            }
        }
    }
}