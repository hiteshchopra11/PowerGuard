package com.hackathon.powergaurd.ui.viewmodels

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.powergaurd.actionable.ActionableExecutor
import com.hackathon.powergaurd.actionable.ActionableTypes
import com.hackathon.powergaurd.collector.UsageDataCollector
import com.hackathon.powergaurd.data.local.entity.DeviceInsightEntity
import com.hackathon.powergaurd.data.model.Actionable
import com.hackathon.powergaurd.data.model.AnalysisResponse
import com.hackathon.powergaurd.data.model.DeviceData
import com.hackathon.powergaurd.domain.usecase.AnalyzeDeviceDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log
import kotlinx.coroutines.delay
import java.util.UUID

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val usageDataCollector: UsageDataCollector,
    private val analyzeDeviceDataUseCase: AnalyzeDeviceDataUseCase,
    private val actionableExecutor: ActionableExecutor
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

    /**
     * Kills a specific app to immediately reduce resource usage
     */
    fun killApp(packageName: String, appName: String) {
        viewModelScope.launch {
            try {
                Log.d("DashboardViewModel", "Attempting to kill app: $packageName")
                
                val actionable = Actionable(
                    id = UUID.randomUUID().toString(),
                    type = ActionableTypes.KILL_APP,
                    description = "Force stop $appName to save resources",
                    packageName = packageName,
                    estimatedBatterySavings = 8.0f,
                    estimatedDataSavings = 10.0f,
                    severity = 5,
                    newMode = null,
                    enabled = true,
                    throttleLevel = null,
                    reason = "Immediate resource optimization"
                )
                
                val result = actionableExecutor.executeActionable(listOf(actionable))
                
                if (result.values.first()) {
                    Log.d("DashboardViewModel", "Successfully killed app: $packageName")
                } else {
                    Log.e("DashboardViewModel", "Failed to kill app: $packageName")
                    _error.value = "Failed to force stop $appName"
                }
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Error killing app", e)
                _error.value = "Error stopping $appName: ${e.message}"
            }
        }
    }

    /**
     * Submits the user's prompt to the API for analysis
     */
    @RequiresApi(Build.VERSION_CODES.P)
    fun submitPrompt(prompt: String) {
        viewModelScope.launch {
            _isLoading.value = true
            Log.d("DashboardViewModel", "Submitting prompt: $prompt")

            try {
                // Get latest device data
                val deviceData = usageDataCollector.collectDeviceData().copy(prompt = prompt)
                _deviceData.value = deviceData
                Log.d("DashboardViewModel", "Collected device data for analysis")

                // Analyze the data with the prompt
                analyzeDeviceData(deviceData)
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Failed to submit prompt: ${e.message}", e)
                _error.value = "Failed to submit prompt: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    private fun analyzeDeviceData(deviceData: DeviceData) {
        viewModelScope.launch {
            _isLoading.value = true
            Log.d("DashboardViewModel", "Analyzing device data")

            try {
                val response = analyzeDeviceDataUseCase(deviceData).getOrNull()
                _analysisResponse.value = response
                
                Log.d("DashboardViewModel", "Analysis complete: response=${response != null}")
                if (response != null) {
                    Log.d("DashboardViewModel", "Analysis returned ${response.insights.size} insights and ${response.actionable.size} actionables")
                    updateUiStateFromDeviceData(deviceData)
                    
                    // Save the response to the history database
                    saveResponseToHistory(response)
                    
                    // Execute actionables from the response
                    if (response.actionable.isNotEmpty()) {
                        Log.d("DashboardViewModel", "Executing ${response.actionable.size} actionables from analysis")
                        actionableExecutor.executeActionable(response.actionable)
                    }
                } else {
                    Log.w("DashboardViewModel", "Analysis returned null response")
                }
                
                _isLoading.value = false
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Failed to analyze data: ${e.message}", e)
                _error.value = "Failed to analyze data: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    private fun saveResponseToHistory(response: AnalysisResponse) {
        viewModelScope.launch {
            try {
                // Get the HistoryViewModel to save the response
                val historyViewModel = HistoryViewModel.getInstance()
                historyViewModel.storeAnalysisResponse(response)
                
                // Force refresh history data after saving
                delay(500) // Small delay to ensure DB transaction completes
                historyViewModel.refreshInsights()
                
                // Log successful save
                Log.d("DashboardViewModel", "Saved analysis response with ${response.insights.size} insights and ${response.actionable.size} actionables")
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Failed to save analysis to history: ${e.message}", e)
                // Don't show error to user since this is a background operation
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
                "${insight.insightTitle}: ${insight.insightDescription}. Tap 'Optimize Battery' or 'Optimize Data' to improve your device."
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