package com.hackathon.powergaurd.ui.viewmodels

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.powergaurd.PowerGuardOptimizer
import com.hackathon.powergaurd.collector.UsageDataCollector
import com.hackathon.powergaurd.data.local.entity.DeviceInsightEntity
import com.hackathon.powergaurd.data.model.Actionable
import com.hackathon.powergaurd.data.model.AnalysisResponse
import com.hackathon.powergaurd.data.model.DeviceData
import com.hackathon.powergaurd.data.model.Insight
import com.hackathon.powergaurd.domain.usecase.AnalyzeDeviceDataUseCase
import com.hackathon.powergaurd.domain.usecase.GetAllActionableUseCase
import com.hackathon.powergaurd.domain.usecase.GetCurrentInsightsUseCase
import com.hackathon.powergaurd.domain.usecase.toEntity
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
    private val getCurrentInsightsUseCase: GetCurrentInsightsUseCase,
    private val powerGuardOptimizer: PowerGuardOptimizer
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

    // Analysis insights
    private val _insights = MutableStateFlow<List<DeviceInsightEntity>>(emptyList())
    val insights: StateFlow<List<DeviceInsightEntity>> = _insights.asStateFlow()

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
                    .sortedByDescending { it.dataUsage.background }
                    .take(3)
                    .map { "${it.appName} (${formatDataUsage(it.dataUsage.background)})" }
            )
        }
    }

    fun saveBattery() {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                // Using PowerGuardOptimizer to save battery
                powerGuardOptimizer.saveBattery()

                // Then trigger analysis to update recommendations
                _deviceData.value?.let { analyzeDeviceData(it) }
            } catch (e: Exception) {
                _error.value = "Failed to save battery: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveData() {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                // Get the most data-consuming app
                val highDataApp = _deviceData.value?.apps?.maxByOrNull { it.dataUsage.background }

                if (highDataApp != null) {
                    // Save data for the high usage app
                    powerGuardOptimizer.saveData(highDataApp.packageName, true)
                } else {
                    // If no app found, apply general data saving
                    powerGuardOptimizer.saveData("com.android.settings", true)
                }

                // Then trigger analysis to update recommendations
                _deviceData.value?.let { analyzeDeviceData(it) }
            } catch (e: Exception) {
                _error.value = "Failed to save data: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun submitPrompt(prompt: String) {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                // Create a new DeviceData object with the prompt added
                val currentData = _deviceData.value
                if (currentData != null) {
                    val dataWithPrompt = currentData.copy(prompt = prompt)

                    // Analyze with the prompt
                    analyzeDeviceData(dataWithPrompt)
                } else {
                    // Collect new data with prompt if we don't have current data
                    val newData = usageDataCollector.collectDeviceData()
                    val dataWithPrompt = newData.copy(prompt = prompt)
                    _deviceData.value = dataWithPrompt

                    // Update UI and analyze
                    updateUiStateFromDeviceData(dataWithPrompt)
                    analyzeDeviceData(dataWithPrompt)
                }
            } catch (e: Exception) {
                _error.value = "Failed to analyze with prompt: ${e.message}"
            } finally {
                _isLoading.value = false
            }
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
                        val insightEntities = getCurrentInsightsUseCase(analysisResponse).map {
                            it.toEntity()
                        }

                        if (insightEntities.isNotEmpty()) {
                            _insights.value = insightEntities

                            // Update UI state based on insights
                            updateUiStateWithInsights(insightEntities)
                        }
                    }
                } else {
                    _error.value = "Analysis failed: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _error.value = "Analysis error: ${e.message}"
            }
        }
    }

    private fun updateUiStateWithInsights(insightEntities: List<DeviceInsightEntity>) {
        // Extract basic statistics and scores from insights
        val batteryInsights = insightEntities.filter { it.insightType == "BATTERY" }
        val dataInsights = insightEntities.filter { it.insightType == "DATA" }
        val performanceInsights = insightEntities.filter { it.insightType == "PERFORMANCE" }

        // Calculate simple scores based on number and severity of insights
        val batteryScore = calculateScore(batteryInsights)
        val dataScore = calculateScore(dataInsights)
        val performanceScore = calculateScore(performanceInsights)

        _uiState.update { currentState ->
            currentState.copy(
                batteryScore = batteryScore,
                dataScore = dataScore,
                performanceScore = performanceScore,
                insights = insightEntities,
                aiSummary = generateAiSummary(insightEntities)
            )
        }
    }

    private fun calculateScore(insights: List<DeviceInsightEntity>): Int {
        // Start with a base score
        var score = 90

        // Adjust based on number and severity of insights
        insights.forEach { insight ->
            score -= when(insight.severity) {
                "HIGH" -> 15
                "MEDIUM" -> 10
                else -> 5
            }
        }

        // Ensure score is between 0 and 100
        return score.coerceIn(0, 100)
    }

    private fun generateAiSummary(insights: List<DeviceInsightEntity>): String {
        if (insights.isEmpty()) {
            return "Based on our analysis, your device is operating efficiently. We'll continue monitoring for optimization opportunities."
        }

        val highSeverityInsights = insights.filter { it.severity == "HIGH" }
        val mediumSeverityInsights = insights.filter { it.severity == "MEDIUM" }

        return when {
            highSeverityInsights.isNotEmpty() -> {
                val insight = highSeverityInsights.first()
                "${insight.insightTitle}: ${insight.insightDescription}. Tap 'Save Battery' or 'Save Data' to optimize your device."
            }
            mediumSeverityInsights.isNotEmpty() -> {
                val insight = mediumSeverityInsights.first()
                "${insight.insightTitle}: ${insight.insightDescription}. Consider optimizing your device settings."
            }
            else -> {
                val insight = insights.first()
                "${insight.insightTitle}: ${insight.insightDescription}."
            }
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
    val batteryScore: Int = 90,
    val dataScore: Int = 90,
    val performanceScore: Int = 90,
    val insights: List<DeviceInsightEntity> = emptyList(),
    val aiSummary: String = "Analyzing your device usage patterns..."
)