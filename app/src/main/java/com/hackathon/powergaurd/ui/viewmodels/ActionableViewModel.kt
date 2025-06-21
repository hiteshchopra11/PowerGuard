package com.hackathon.powergaurd.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.powergaurd.actionable.ActionableExecutor
import com.hackathon.powergaurd.data.model.Actionable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel responsible for executing actionables
 */
@HiltViewModel
class ActionableViewModel @Inject constructor(
    private val actionableExecutor: ActionableExecutor
) : ViewModel() {

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _executionResults = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val executionResults: StateFlow<Map<String, Boolean>> = _executionResults.asStateFlow()

    /**
     * Calculates the success rate of executed actionables as a percentage
     *
     * @return Percentage of successfully executed actionables (0-100)
     */
    fun getSuccessRate(): Int {
        val results = _executionResults.value
        if (results.isEmpty()) return 0

        val successCount = results.values.count { it }
        return ((successCount.toFloat() / results.size) * 100).toInt()
    }

    /**
     * Executes a single actionable
     *
     * @param actionable The actionable to execute
     * @return True if execution was successful, false otherwise
     */
    suspend fun executeActionable(actionable: Actionable): Boolean {
        _isExecuting.value = true

        try {
            Log.d(TAG, "Executing actionable: ${actionable.type} for ${actionable.packageName}")
            val result = actionableExecutor.executeActionable(actionable)

            val success = result.success

            // Update execution results
            val currentResults = _executionResults.value.toMutableMap()
            currentResults[actionable.id] = success
            _executionResults.value = currentResults

            return success
        } catch (e: Exception) {
            Log.e(TAG, "Error executing actionable: ${e.message}", e)

            // Update execution results with failure
            val currentResults = _executionResults.value.toMutableMap()
            currentResults[actionable.id] = false
            _executionResults.value = currentResults

            return false
        } finally {
            _isExecuting.value = false
        }
    }

    /**
     * Executes multiple actionables
     *
     * @param actionables The list of actionables to execute
     * @return Map of actionable IDs to execution results (true if successful, false otherwise)
     */
    suspend fun executeAllActionables(actionables: List<Actionable>): Map<String, Boolean> {
        if (actionables.isEmpty()) {
            return emptyMap()
        }

        _isExecuting.value = true

        try {
            Log.d(TAG, "Executing ${actionables.size} actionables")
            val results = actionableExecutor.executeActionables(actionables)

            // Convert ActionableResult to boolean success values
            val idResults = results.mapValues { (_, result) -> result.success }

            // Update execution results
            _executionResults.value = idResults

            return idResults
        } catch (e: Exception) {
            Log.e(TAG, "Error executing actionables: ${e.message}", e)
            return actionables.associate { it.id to false }
        } finally {
            _isExecuting.value = false
        }
    }

    /**
     * Execute an actionable and handle the result in the ViewModel scope
     */
    fun executeActionableAsync(actionable: Actionable, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = executeActionable(actionable)
            onComplete(success)
        }
    }

    /**
     * Execute all actionables and handle the results in the ViewModel scope
     */
    fun executeAllActionablesAsync(
        actionables: List<Actionable>,
        onComplete: (Map<String, Boolean>) -> Unit
    ) {
        viewModelScope.launch {
            val results = executeAllActionables(actionables)
            onComplete(results)
        }
    }

    /**
     * Clear execution results
     */
    fun clearExecutionResults() {
        _executionResults.value = emptyMap()
    }

    companion object {
        private const val TAG = "ActionableViewModel"
    }
}