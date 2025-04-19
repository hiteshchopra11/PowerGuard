package com.hackathon.powergaurd.ui.viewmodels

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.powergaurd.actionable.ActionableExecutor
import com.hackathon.powergaurd.actionable.ActionableTypes
import com.hackathon.powergaurd.collector.UsageDataCollector
import com.hackathon.powergaurd.data.PowerGuardAnalysisRepository
import com.hackathon.powergaurd.data.local.entity.DeviceInsightEntity
import com.hackathon.powergaurd.data.model.Actionable
import com.hackathon.powergaurd.data.model.AnalysisResponse
import com.hackathon.powergaurd.data.model.DeviceData
import com.hackathon.powergaurd.domain.usecase.AnalyzeDeviceDataUseCase
import com.hackathon.powergaurd.domain.usecase.GetAllActionableUseCase
import com.hackathon.powergaurd.domain.usecase.GetCurrentInsightsUseCase
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
    private val getAllActionableUseCase: GetAllActionableUseCase,
    private val getCurrentInsightsUseCase: GetCurrentInsightsUseCase,
    private val actionableExecutor: ActionableExecutor,
    private val analysisRepository: PowerGuardAnalysisRepository
) : ViewModel() {

    companion object {
        private const val TAG = "DashboardViewModel"
    }

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

    // Execution state tracking
    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()
    
    private val _executionResults = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val executionResults: StateFlow<Map<String, Boolean>> = _executionResults.asStateFlow()
    
    // Track which implementation is currently active
    private val _isUsingGemma = MutableStateFlow(true)
    val isUsingGemma: StateFlow<Boolean> = _isUsingGemma.asStateFlow()

    init {
        _isUsingGemma.value = analysisRepository.isUsingGemma()
        // Don't automatically call refreshData() on initialization
        // This prevents automatic LLM API calls on app startup
    }

    /**
     * Toggles between GemmaInferenceSDK and backend API
     */
    fun toggleInferenceMode(useGemma: Boolean) {
        analysisRepository.setUseGemma(useGemma)
        _isUsingGemma.value = useGemma
        Log.d("DashboardViewModel", "Switched to ${if (useGemma) "Gemma SDK" else "backend API"} mode")
    }

    @SuppressLint("NewApi")
    fun refreshData() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                Log.d(TAG, "Starting to refresh data")
                
                // Get device data
                val deviceData = usageDataCollector.collectDeviceData()
                _deviceData.value = deviceData
                Log.d(TAG, "Device data collected successfully")
                
                // Analyze it
                Log.d(TAG, "Starting analysis with ${if (analysisRepository.isUsingGemma()) "Gemma" else "remote API"}")
                val result = analyzeDeviceDataUseCase(deviceData)
                
                if (result.isSuccess) {
                    val response = result.getOrNull()
                    if (response != null) {
                        Log.d(TAG, "Analysis successful: battery=${response.batteryScore}, data=${response.dataScore}, " +
                                "performance=${response.performanceScore}, actionable items=${response.actionable.size}")
                        _analysisResponse.value = response
                        
                        // Update UI state
                        _uiState.value = DashboardUiState(
                            batteryLevel = deviceData.battery.level,
                            isCharging = deviceData.battery.isCharging,
                            networkType = deviceData.network.type,
                            networkStrength = deviceData.network.strength,
                            batteryScore = response.batteryScore.toInt(),
                            dataScore = response.dataScore.toInt(),
                            performanceScore = response.performanceScore.toInt(),
                            estimatedBatterySavings = response.estimatedSavings.batteryMinutes,
                            estimatedDataSavings = response.estimatedSavings.dataMB
                        )
                    } else {
                        Log.e(TAG, "Analysis result was success but response is null")
                        _error.value = "Analysis failed: Empty response"
                    }
                } else {
                    val error = result.exceptionOrNull()
                    Log.e(TAG, "Analysis failed: ${error?.message}", error)
                    
                    // Handle network-related errors more gracefully
                    val errorMessage = when (error) {
                        is java.net.UnknownHostException -> "Network connection issue: Unable to connect to server"
                        is java.net.ConnectException -> "Network connection issue: Server is unreachable"
                        is java.net.SocketTimeoutException -> "Network connection issue: Connection timed out"
                        is javax.net.ssl.SSLException -> "Network security issue: Could not establish secure connection"
                        else -> "Analysis failed: ${error?.message ?: "Unknown error"}"
                    }
                    
                    _error.value = errorMessage
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing data: ${e.message}", e)
                
                // Handle network-related errors more gracefully
                val errorMessage = when (e) {
                    is java.net.UnknownHostException -> "Network connection issue: Unable to connect to server"
                    is java.net.ConnectException -> "Network connection issue: Server is unreachable"
                    is java.net.SocketTimeoutException -> "Network connection issue: Connection timed out"
                    is javax.net.ssl.SSLException -> "Network security issue: Could not establish secure connection"
                    else -> "Error: ${e.message ?: "Unknown error"}"
                }
                
                _error.value = errorMessage
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
     * Fetches only device data without running LLM analysis
     * This separates data collection from LLM inference
     */
    @SuppressLint("NewApi")
    fun fetchDeviceDataOnly() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                Log.d(TAG, "Starting to fetch device data only")
                
                // Get device data
                val deviceData = usageDataCollector.collectDeviceData()
                _deviceData.value = deviceData
                Log.d(TAG, "Device data collected successfully")
                
                // Update UI state with device data only
                updateUiStateFromDeviceData(deviceData)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching device data: ${e.message}", e)
                
                // Handle network-related errors more gracefully
                val errorMessage = when (e) {
                    is java.net.UnknownHostException -> "Network connection issue: Unable to connect to server"
                    is java.net.ConnectException -> "Network connection issue: Server is unreachable"
                    is java.net.SocketTimeoutException -> "Network connection issue: Connection timed out"
                    is javax.net.ssl.SSLException -> "Network security issue: Could not establish secure connection"
                    else -> "Error: ${e.message ?: "Unknown error"}"
                }
                
                _error.value = errorMessage
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Submits the user's prompt to the API for analysis
     */
    fun submitPrompt(prompt: String) {
        viewModelScope.launch {
            _isLoading.value = true
            Log.d("DashboardViewModel", "Submitting prompt: $prompt")

            try {
                // Get latest device data
                Log.d("DashboardViewModel", "Starting device data collection for prompt")
                val deviceData = usageDataCollector.collectDeviceData()
                
                // Log the quality of collected device data
                Log.d("DashboardViewModel", """
                    Device Data Quality Check:
                    - Battery Level: ${deviceData.battery.level}%
                    - Battery Temp: ${deviceData.battery.temperature}°C
                    - Network Type: ${deviceData.network.type}
                    - Network Strength: ${deviceData.network.strength}
                    - Memory: ${deviceData.memory.availableRam}/${deviceData.memory.totalRam} bytes
                    - Installed Apps: ${deviceData.apps.size}
                """.trimIndent())

                // Give more time for data collection to complete
                delay(800)
                
                val deviceDataWithPrompt = deviceData.copy(prompt = prompt)
                _deviceData.value = deviceDataWithPrompt
                Log.d("DashboardViewModel", "Collected device data for analysis")

                // Analyze the data with the prompt
                analyzeDeviceData(deviceDataWithPrompt)
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Failed to submit prompt: ${e.message}", e)
                _error.value = "Failed to submit prompt: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    private fun analyzeDeviceData(deviceData: DeviceData) {
        viewModelScope.launch {
            try {
                Log.d("DashboardViewModel", "Analyzing device data")
                val result = analyzeDeviceDataUseCase(deviceData)
                
                when {
                    result.isSuccess -> {
                        val response = result.getOrNull()
                        Log.d("DashboardViewModel", "Analysis result success, response: ${response?.insights?.size ?: 0} insights")
                        _analysisResponse.value = response
                        
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
                            Log.w("DashboardViewModel", "Analysis returned success but null response")
                            _error.value = "Failed to process analysis response"
                        }
                    }
                    result.isFailure -> {
                        val exception = result.exceptionOrNull()
                        Log.e("DashboardViewModel", "Analysis failed with error: ${exception?.message}", exception)
                        _error.value = "Analysis failed: ${exception?.message ?: "Unknown error"}"
                    }
                }
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Failed to analyze data: ${e.message}", e)
                _error.value = "Failed to analyze data: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    private fun saveResponseToHistory(response: AnalysisResponse) {
        viewModelScope.launch {
            try {
                // Get the HistoryViewModel to save the response
                val historyViewModel = HistoryViewModel.getInstance()
                if (historyViewModel == null) {
                    Log.w("DashboardViewModel", "HistoryViewModel not initialized yet, can't save to history")
                    return@launch
                }
                
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
            return "Your device is operating efficiently. We'll continue monitoring for optimization opportunities."
        }

        val highSeverityInsights = insights.filter { it.severity == "HIGH" }
        val mediumSeverityInsights = insights.filter { it.severity == "MEDIUM" }

        return when {
            highSeverityInsights.isNotEmpty() -> {
                val insight = highSeverityInsights.first()
                insight.insightDescription
            }
            mediumSeverityInsights.isNotEmpty() -> {
                val insight = mediumSeverityInsights.first()
                insight.insightDescription
            }
            else -> {
                val insight = insights.first()
                insight.insightDescription
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

    /**
     * Clears the current analysis response and resets related states
     */
    fun clearAnalysisResponse() {
        _analysisResponse.value = null
        _error.value = null
        _isLoading.value = false
    }

    /**
     * Clears the execution results
     */
    fun clearExecutionResults() {
        _executionResults.value = emptyMap()
    }

    /**
     * Execute an actionable asynchronously and handle the result
     */
    fun executeActionableAsync(actionable: Actionable, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isExecuting.value = true
            try {
                val result = actionableExecutor.executeActionable(listOf(actionable))
                val success = result.values.firstOrNull() ?: false
                
                // Update execution results
                _executionResults.value = mapOf(actionable.id to success)
                
                onComplete(success)
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Error executing actionable: ${e.message}", e)
                onComplete(false)
            } finally {
                _isExecuting.value = false
            }
        }
    }

    /**
     * Execute all actionables asynchronously and handle the results
     */
    fun executeAllActionablesAsync(actionables: List<Actionable>, onComplete: (Map<String, Boolean>) -> Unit) {
        viewModelScope.launch {
            _isExecuting.value = true
            try {
                val results = actionableExecutor.executeActionable(actionables)
                
                // Convert to ID-based map
                val idResults = results.entries.associate { (actionable, success) ->
                    actionable.id to success
                }
                
                // Update execution results
                _executionResults.value = idResults
                
                onComplete(idResults)
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Error executing actionables: ${e.message}", e)
                val failureResults = actionables.associate { it.id to false }
                onComplete(failureResults)
            } finally {
                _isExecuting.value = false
            }
        }
    }

    fun setUseGemma(useGemma: Boolean) {
        if (useGemma != _isUsingGemma.value) {
            analysisRepository.setUseGemma(useGemma)
            _isUsingGemma.value = useGemma
            // Refresh with new inference mode
            refreshData()
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
    val aiSummary: String = "Analyzing your device usage patterns...",
    val inferenceMode: String = "Gemma SDK",
    val estimatedBatterySavings: Float = 0f,
    val estimatedDataSavings: Float = 0f
)