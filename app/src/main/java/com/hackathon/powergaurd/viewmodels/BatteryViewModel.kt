package com.hackathon.powergaurd.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.powergaurd.data.AppRepository
import com.hackathon.powergaurd.data.DeviceStatsCollector
import com.hackathon.powergaurd.models.BatteryAppUsage
import com.hackathon.powergaurd.models.BatteryStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BatteryViewModel @Inject constructor(
    private val deviceStatsCollector: DeviceStatsCollector,
    private val appRepository: AppRepository
) : ViewModel() {

    private val _batteryStats = MutableStateFlow<BatteryStats?>(null)
    val batteryStats: StateFlow<BatteryStats?> = _batteryStats.asStateFlow()

    private val _appUsage = MutableStateFlow<List<BatteryAppUsage>>(emptyList())
    val appUsage: StateFlow<List<BatteryAppUsage>> = _appUsage.asStateFlow()

    private val _maxChargeLevel = MutableStateFlow(80f)
    val maxChargeLevel: StateFlow<Float> = _maxChargeLevel.asStateFlow()

    init {
        loadBatteryStats()
        loadAppUsage()
    }

    fun loadBatteryStats() {
        viewModelScope.launch {
            val stats = deviceStatsCollector.collectBatteryStats()
            _batteryStats.value = stats
        }
    }

    private fun loadAppUsage() {
        viewModelScope.launch {
            val appUsage = deviceStatsCollector.collectAppUsage()

            // Calculate battery usage percentage (simulated)
            val totalUsage = appUsage.sumOf { it.foregroundTimeMs + it.backgroundTimeMs }.toFloat()

            val batteryAppUsage = appUsage.map { app ->
                val usagePercentage = (app.foregroundTimeMs + app.backgroundTimeMs) / totalUsage * 100
                BatteryAppUsage(
                    packageName = app.packageName,
                    appName = app.appName,
                    percentUsage = usagePercentage
                )
            }.sortedByDescending { it.percentUsage }

            _appUsage.value = batteryAppUsage
        }
    }

    fun setMaxChargeLevel(level: Float) {
        _maxChargeLevel.value = level
    }
}