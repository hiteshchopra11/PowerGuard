package com.hackathon.powerguard.ui.screens.dashboard

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.powerguard.collector.UsageDataCollector
import com.hackathon.powerguard.data.model.Actionable
import com.hackathon.powerguard.data.model.AnalysisResponse
import com.hackathon.powerguard.data.model.DeviceData
import com.hackathon.powerguard.data.model.Insight
import com.hackathon.powerguard.domain.usecase.AnalyzeDeviceDataUseCase
import com.hackathon.powerguard.domain.usecase.GetAllActionableUseCase
import com.hackathon.powerguard.domain.usecase.GetCurrentInsightsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val usageDataCollector: UsageDataCollector,
    private val analyzeDeviceDataUseCase: AnalyzeDeviceDataUseCase,
    private val getAllActionableUseCase: GetAllActionableUseCase,
    private val getCurrentInsightsUseCase: GetCurrentInsightsUseCase
) : ViewModel() {

    // UI state
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // Device data
    private val _deviceData = MutableStateFlow<DeviceData?>(null)
    val deviceData: StateFlow<DeviceData?> = _deviceData.asStateFlow()

    // Analysis response
    private val _analysisResponse = MutableStateFlow<AnalysisResponse?>(null)
    val analysisResponse: StateFlow<AnalysisResponse?> = _analysisResponse.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        refreshData()
    }

    @SuppressLint("NewApi")
    fun refreshData() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // Collect device data
                val data = usageDataCollector.collectDeviceData()
                _deviceData.value = data

                // Update UI state with device data
                updateUiStateFromDeviceData(data)

                // Analyze the data
                analyzeDeviceData(data)
            } catch (e: Exception) {
                _error.value = "Failed to collect device data: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun updateUiStateFromDeviceData(deviceData: DeviceData) {
        _uiState.update { currentState ->
            currentState.copy(
                batteryLevel = deviceData.battery.level,
                isCharging = deviceData.battery.isCharging,
                batteryTemperature = deviceData.battery.temperature,
                chargingType = deviceData.battery.chargingType,
                networkType = deviceData.network.type,
                networkStrength = deviceData.network.strength,
                highUsageApps = deviceData.apps
                    .sortedByDescending { it.dataUsage.rxBytes + it.dataUsage.txBytes }
                    .take(3)
                    .map { "${it.appName} (${formatDataUsage(it.dataUsage.rxBytes + it.dataUsage.txBytes)})" }
            )
        }
    }

    private fun analyzeDeviceData(deviceData: DeviceData) {
        viewModelScope.launch {
            try {
                val result = analyzeDeviceDataUseCase(deviceData)

                if (result.isSuccess) {
                    val response = result.getOrNull()
                    _analysisResponse.value = response

                    response?.let { analysisResponse ->
                        val actionables = getAllActionableUseCase(analysisResponse)
                        val insights = getCurrentInsightsUseCase(analysisResponse)

                        updateUiStateWithAnalysis(analysisResponse, actionables, insights)
                    }
                } else {
                    _error.value = "Analysis failed: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _error.value = "Analysis error: ${e.message}"
            }
        }
    }

    private fun updateUiStateWithAnalysis(
        analysisResponse: AnalysisResponse,
        actionables: List<Actionable>,
        insights: List<Insight>
    ) {
        _uiState.update { currentState ->
            currentState.copy(
                batteryScore = analysisResponse.batteryScore,
                dataScore = analysisResponse.dataScore,
                performanceScore = analysisResponse.performanceScore,
                estimatedBatterySavings = analysisResponse.estimatedSavings.batteryMinutes,
                estimatedDataSavings = analysisResponse.estimatedSavings.dataMB,
                actionables = actionables,
                insights = insights,
                aiSummary = generateAiSummary(insights, actionables, analysisResponse)
            )
        }
    }

    private fun generateAiSummary(
        insights: List<Insight>,
        actionables: List<Actionable>,
        analysisResponse: AnalysisResponse
    ): String {
        val highSeverityInsights = insights.filter { it.severity == "HIGH" }
        val mediumSeverityInsights = insights.filter { it.severity == "MEDIUM" }

        val appNames = actionables.map { it.packageName }.distinct().take(2)
            .mapNotNull { packageName ->
                _deviceData.value?.apps?.find { it.packageName == packageName }?.appName
            }

        return if (insights.isNotEmpty() && actionables.isNotEmpty()) {
            val insightSummary = if (highSeverityInsights.isNotEmpty()) {
                highSeverityInsights.first().description
            } else if (mediumSeverityInsights.isNotEmpty()) {
                mediumSeverityInsights.first().description
            } else {
                insights.first().description
            }

            val appMention = if (appNames.isNotEmpty()) {
                "We've detected issues with ${appNames.joinToString(" and ")}. "
            } else {
                ""
            }

            val savingEstimate = if (analysisResponse.estimatedSavings.batteryMinutes > 0) {
                "Applying our recommendations could improve battery life by approximately " +
                        "${analysisResponse.estimatedSavings.batteryMinutes / 60} hours and " +
                        "${analysisResponse.estimatedSavings.batteryMinutes % 60} minutes."
            } else {
                "Applying our recommendations could optimize your device performance."
            }

            "$appMention$insightSummary $savingEstimate"
        } else {
            "Based on our analysis, your device is operating efficiently. We'll continue monitoring for optimization opportunities."
        }
    }

    private fun formatDataUsage(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}

data class DashboardUiState(
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false,
    val batteryTemperature: Float = 0f,
    val chargingType: String = "unknown",
    val networkType: String = "unknown",
    val networkStrength: Int = 0,
    val highUsageApps: List<String> = emptyList(),
    val batteryScore: Int = 0,
    val dataScore: Int = 0,
    val performanceScore: Int = 0,
    val estimatedBatterySavings: Int = 0,
    val estimatedDataSavings: Int = 0,
    val actionables: List<Actionable> = emptyList(),
    val insights: List<Insight> = emptyList(),
    val aiSummary: String = ""
)