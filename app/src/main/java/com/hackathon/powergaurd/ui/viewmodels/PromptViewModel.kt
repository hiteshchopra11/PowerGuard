package com.hackathon.powergaurd.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.powergaurd.actionable.ActionableService
import com.hackathon.powergaurd.data.model.AnalysisResponse
import com.hackathon.powergaurd.data.model.Insight
import com.hackathon.powergaurd.domain.usecase.GetAllActionableUseCase
import com.hackathon.powergaurd.domain.usecase.toEntity
import com.hackathon.powergaurd.llm.QueryProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for handling user prompts and executing LLM queries
 */
@HiltViewModel
class PromptViewModel @Inject constructor(
    private val queryProcessor: QueryProcessor,
    private val actionableService: ActionableService
) : ViewModel() {

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _lastResponse = MutableStateFlow<String?>(null)
    val lastResponse: StateFlow<String?> = _lastResponse.asStateFlow()

    private val _analysisResponse = MutableStateFlow<AnalysisResponse?>(null)
    val analysisResponse: StateFlow<AnalysisResponse?> = _analysisResponse.asStateFlow()

    /**
     * Process a user prompt and update the last response
     */
    suspend fun processUserPrompt(prompt: String) {
        _isProcessing.value = true
        try {
            // Get the response from the LLM
            Log.d(TAG, "Processing user prompt: $prompt")

            // Process the query to get actionables and insights
            val response = queryProcessor.processQueryForAnalysisResponse(prompt)
            
            // Save the analysis response
            _analysisResponse.value = response
            
            // Generate a readable response for the UI
            val readableResponse = generateReadableResponse(response)
            _lastResponse.value = readableResponse
            
            // Execute actionables if available
            if (response.actionable.isNotEmpty()) {
                Log.d(TAG, "Executing ${response.actionable.size} actionables")
                viewModelScope.launch {
                    val results = actionableService.executeAllActionables(response.actionable)
                    Log.d(TAG, "Actionable execution results: $results")
                }
            }
            
            Log.d(TAG, "Prompt processing complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing prompt", e)
            _lastResponse.value = "Error: ${e.message}"
        } finally {
            _isProcessing.value = false
        }
    }

    /**
     * Generate a human-readable response from the analysis response
     */
    private fun generateReadableResponse(response: AnalysisResponse): String {
        // For information queries, just return the insights
        if (response.insights.isNotEmpty()) {
            return response.insights.joinToString("\n\n") { it.description }
        }

        // For actionable responses, explain what's being done
        val actionableCount = response.actionable.size
        val insightText = if (response.insights.isNotEmpty()) response.insights.first().description else ""
        
        return when {
            actionableCount > 0 -> {
                val actionableText = response.actionable.joinToString("\n") { "• ${it.description}" }
                "$insightText\n\nActions being taken:\n$actionableText"
            }
            else -> {
                insightText.ifEmpty { "I analyzed your request but couldn't find any relevant information or actions to take." }
            }
        }
    }

    companion object {
        private const val TAG = "PromptViewModel"
    }
} 