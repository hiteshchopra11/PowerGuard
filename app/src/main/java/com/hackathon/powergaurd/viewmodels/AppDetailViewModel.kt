package com.hackathon.powergaurd.viewmodels

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.powergaurd.collector.UsageDataCollector
import com.hackathon.powergaurd.models.AppUsageInfo
import com.hackathon.powergaurd.models.DeviceData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppDetailUiState(
    val isLoading: Boolean = true,
    val appDetails: AppUsageInfo? = null,
    val error: String? = null,
    val packageName: String = ""
)

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    private val usageDataCollector: UsageDataCollector,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppDetailUiState())
    val uiState: StateFlow<AppDetailUiState> = _uiState.asStateFlow()

    private val packageName: String = savedStateHandle.get<String>("packageName") ?: ""

    init {
        if (packageName.isNotEmpty()) {
            _uiState.update { it.copy(packageName = packageName) }
            loadAppDetails(packageName)
        } else {
            _uiState.update { it.copy(isLoading = false, error = "Package name not provided") }
        }
    }

    fun loadAppDetails(pkgName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            Log.d("AppDetailViewModel", "Loading details for package: $pkgName")
            try {
                // Call the single method from the real collector
                val allDeviceData: DeviceData = usageDataCollector.collectDeviceData()

                // Find the specific app's details within the collected data
                val appDetail = allDeviceData.appUsage.find { it.packageName == pkgName }

                if (appDetail != null) {
                    Log.d("AppDetailViewModel", "Found details: ${appDetail.appName}")
                    _uiState.update {
                        it.copy(isLoading = false, appDetails = appDetail, error = null)
                    }
                } else {
                    Log.w("AppDetailViewModel", "App details not found for package: $pkgName")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            appDetails = null,
                            error = "App usage data not found for this package."
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("AppDetailViewModel", "Error loading app details", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load app details: ${e.localizedMessage}"
                    )
                }
            }
        }
    }
}