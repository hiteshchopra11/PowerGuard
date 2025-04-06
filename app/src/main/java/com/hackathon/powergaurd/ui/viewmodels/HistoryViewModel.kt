package com.hackathon.powergaurd.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.powergaurd.data.local.entity.DeviceInsightEntity
import com.hackathon.powergaurd.domain.usecase.GetPastInsightsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Data class representing the state of the history screen. */
data class HistoryState(
    val insights: List<DeviceInsightEntity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

/** ViewModel for the history screen showing device insights. */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getPastInsightsUseCase: GetPastInsightsUseCase
) : ViewModel() {

    private val _historyState = MutableStateFlow(HistoryState())
    val historyState: StateFlow<HistoryState> = _historyState.asStateFlow()

    init {
        loadInsights()
    }

    fun refreshInsights() {
        _historyState.value = _historyState.value.copy(isLoading = true, error = null)
        loadInsights()
    }

    private fun loadInsights() {
        viewModelScope.launch {
            try {
                // Use the device ID from shared preferences or generate one
                val deviceId = getDeviceId()
                
                getPastInsightsUseCase(deviceId)
                    .stateIn(
                        scope = viewModelScope,
                        started = SharingStarted.WhileSubscribed(5000),
                        initialValue = emptyList()
                    )
                    .collect { insights ->
                        _historyState.value = HistoryState(
                            insights = insights,
                            isLoading = false
                        )
                    }
            } catch (e: Exception) {
                _historyState.value = HistoryState(
                    isLoading = false,
                    error = "Failed to load insights: ${e.message}"
                )
            }
        }
    }
    
    // Helper function to get device ID - in a real app, this would come from shared preferences
    private fun getDeviceId(): String {
        return "current_device" // Simplified for this example
    }
}
