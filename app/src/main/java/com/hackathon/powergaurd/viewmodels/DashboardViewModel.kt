package com.hackathon.powergaurd.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.powergaurd.data.AppRepository
import com.hackathon.powergaurd.data.DeviceStatsCollector
import com.hackathon.powergaurd.models.ActionResponse
import com.hackathon.powergaurd.models.BatteryStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val deviceStatsCollector: DeviceStatsCollector
) : ViewModel() {

    private val _batteryStats = MutableStateFlow<BatteryStats?>(null)
    val batteryStats: StateFlow<BatteryStats?> = _batteryStats.asStateFlow()

    val latestOptimization: StateFlow<ActionResponse?> = appRepository.latestResponse
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    init {
        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch {
            // Collect battery stats
            val stats = deviceStatsCollector.collectBatteryStats()
            _batteryStats.value = stats
        }
    }
}