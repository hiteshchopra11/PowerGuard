package com.hackathon.powergaurd.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.powergaurd.data.local.ActionablesDBRepository
import com.hackathon.powergaurd.data.local.InsightsDBRepository
import com.hackathon.powergaurd.data.local.entity.DeviceActionableEntity
import com.hackathon.powergaurd.data.local.entity.DeviceInsightEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class HistoryUiState(
    val insights: List<DeviceInsightEntity> = emptyList(),
    val actionables: List<DeviceActionableEntity> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val insightsRepository: InsightsDBRepository,
    private val actionablesRepository: ActionablesDBRepository
) : ViewModel() {

    private val _historyState = MutableStateFlow(HistoryUiState(isLoading = true))
    val historyState: StateFlow<HistoryUiState> = _historyState.asStateFlow()

    init {
        // Register as the singleton instance
        INSTANCE = this
        
        // Try loading history on initialization
        try {
            loadHistory()
            Log.d("HistoryViewModel", "Initialized and started loading history")
        } catch (e: Exception) {
            Log.e("HistoryViewModel", "Error during initialization: ${e.message}", e)
            // Set up empty UI state but don't show loading
            _historyState.value = HistoryUiState(isLoading = false)
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            _historyState.update { it.copy(isLoading = true) }
            
            try {
                // Load insights from repository
                val insights = insightsRepository.getAllInsightsSortedByTimestamp()
                Log.d("HistoryViewModel", "Loaded ${insights.size} insights from database")
                if (insights.isNotEmpty()) {
                    Log.d("HistoryViewModel", "First insight - Type: ${insights[0].insightType}, Title: ${insights[0].insightTitle}")
                }
                
                // Load actionables from repository
                val actionables = actionablesRepository.getAllActionablesSortedByTimestamp()
                Log.d("HistoryViewModel", "Loaded ${actionables.size} actionables from database")
                if (actionables.isNotEmpty()) {
                    Log.d("HistoryViewModel", "First actionable - Type: ${actionables[0].actionableType}, Description: ${actionables[0].description}")
                }
                
                // We don't need to verify for overlapping IDs since insights use auto-generated primary keys
                // and actionables have their own actionableId field which is different
                // The previous check was incorrectly comparing auto-generated room IDs
                
                _historyState.update { 
                    it.copy(
                        insights = insights,
                        actionables = actionables,
                        isLoading = false
                    )
                }
                
                Log.d("HistoryViewModel", "Updated UI state with ${insights.size} insights and ${actionables.size} actionables")
            } catch (e: Exception) {
                Log.e("HistoryViewModel", "Error loading history: ${e.message}", e)
                _historyState.update { 
                    it.copy(
                        isLoading = false
                    )
                }
            }
        }
    }

    fun refreshInsights() {
        loadHistory()
    }
    
    // Store insights and actionables when received from API
    fun storeAnalysisResponse(analysisResponse: com.hackathon.powergaurd.data.model.AnalysisResponse) {
        viewModelScope.launch {
            try {
                val timestamp = System.currentTimeMillis()
                
                // Log the raw API response
                Log.d("HistoryViewModel", "Raw API response - ${analysisResponse.insights.size} insights, ${analysisResponse.actionable.size} actionables")
                if (analysisResponse.insights.isNotEmpty()) {
                    Log.d("HistoryViewModel", "First API insight - Type: ${analysisResponse.insights[0].type}, Title: ${analysisResponse.insights[0].title}")
                }
                if (analysisResponse.actionable.isNotEmpty()) {
                    Log.d("HistoryViewModel", "First API actionable - Type: ${analysisResponse.actionable[0].type}, Description: ${analysisResponse.actionable[0].description}")
                }
                
                // Save insights
                if (analysisResponse.insights.isNotEmpty()) {
                    val insights = analysisResponse.insights.map { insight ->
                        DeviceInsightEntity(
                            // Let Room auto-generate the ID
                            insightType = insight.type,
                            insightTitle = insight.title,
                            insightDescription = insight.description,
                            severity = insight.severity,
                            timestamp = timestamp
                        )
                    }
                    
                    Log.d("HistoryViewModel", "Mapped ${insights.size} insights to DB entities")
                    insightsRepository.saveInsights(insights)
                    Log.d("HistoryViewModel", "Saved ${insights.size} insights to database")
                } else {
                    Log.d("HistoryViewModel", "No insights to save")
                }
                
                // Save actionables
                if (analysisResponse.actionable.isNotEmpty()) {
                    val actionables = analysisResponse.actionable.map { actionable ->
                        DeviceActionableEntity(
                            // Let Room auto-generate the ID
                            actionableId = actionable.id,  // This comes from the API and is already a unique string
                            actionableType = actionable.type,
                            packageName = actionable.packageName ?: "",
                            description = actionable.description,
                            reason = actionable.reason ?: "",
                            newMode = actionable.newMode ?: "",
                            timestamp = timestamp,
                            estimatedBatterySavings = actionable.estimatedBatterySavings,
                            estimatedDataSavings = actionable.estimatedDataSavings,
                            severity = actionable.severity,
                            enabled = actionable.enabled,
                            throttleLevel = actionable.throttleLevel
                        )
                    }
                    
                    Log.d("HistoryViewModel", "Mapped ${actionables.size} actionables to DB entities")
                    actionablesRepository.saveActionables(actionables)
                    Log.d("HistoryViewModel", "Saved ${actionables.size} actionables to database")
                } else {
                    Log.d("HistoryViewModel", "No actionables to save")
                }
                
                // Force refresh history data after saving
                kotlinx.coroutines.delay(100)
                loadHistory()
                
                // Add additional diagnostic logging about the saved data
                viewModelScope.launch {
                    val savedInsights = insightsRepository.getAllInsightsSortedByTimestamp()
                    val savedActionables = actionablesRepository.getAllActionablesSortedByTimestamp()
                    
                    Log.d("HistoryViewModel", "After saving: ${savedInsights.size} insights in DB, " +
                            "${savedActionables.size} actionables in DB")
                    
                    // Log a few examples with their IDs for debugging
                    if (savedInsights.isNotEmpty()) {
                        val sample = savedInsights.take(3)
                        sample.forEach { insight ->
                            Log.d("HistoryViewModel", "Insight ID: ${insight.id}, Type: ${insight.insightType}, " +
                                    "Title: ${insight.insightTitle}")
                        }
                    }
                    
                    if (savedActionables.isNotEmpty()) {
                        val sample = savedActionables.take(3)
                        sample.forEach { actionable ->
                            Log.d("HistoryViewModel", "Actionable ID: ${actionable.id}, ActionableID: ${actionable.actionableId}, " +
                                    "Type: ${actionable.actionableType}")
                        }
                    }
                }
                
                Log.d("HistoryViewModel", "Refreshed history after saving response")
            } catch (e: Exception) {
                Log.e("HistoryViewModel", "Error storing analysis response: ${e.message}", e)
            }
        }
    }
    
    companion object {
        @Volatile
        private var INSTANCE: HistoryViewModel? = null
        
        fun getInstance(): HistoryViewModel? {
            return INSTANCE
        }
    }
}

// Extension function to display the timestamp in a readable format
fun DeviceInsightEntity.getFormattedDate(): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}

// Extension function to display the timestamp in a readable format
fun DeviceActionableEntity.getFormattedDate(): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}
