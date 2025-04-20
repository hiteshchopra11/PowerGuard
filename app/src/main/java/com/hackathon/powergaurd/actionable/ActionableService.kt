package com.hackathon.powergaurd.actionable

import android.util.Log
import com.hackathon.powergaurd.data.model.Actionable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for executing actionable items
 * 
 * This service is used as a non-ViewModel alternative to ActionableViewModel
 * for components that should not directly inject ViewModels
 */
@Singleton
class ActionableService @Inject constructor(
    private val actionableExecutor: ActionableExecutor
) {
    private val TAG = "ActionableService"
    
    /**
     * Executes a single actionable
     * 
     * @param actionable The actionable to execute
     * @return True if execution was successful, false otherwise
     */
    suspend fun executeActionable(actionable: Actionable): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Executing actionable: ${actionable.type} for ${actionable.packageName}")
                val result = actionableExecutor.executeActionable(actionable)
                result.success
            } catch (e: Exception) {
                Log.e(TAG, "Error executing actionable: ${e.message}", e)
                false
            }
        }
    }
    
    /**
     * Executes multiple actionables
     * 
     * @param actionables The list of actionables to execute
     * @return Map of actionable IDs to execution results (true if successful, false otherwise)
     */
    suspend fun executeAllActionables(actionables: List<Actionable>): Map<String, Boolean> {
        return withContext(Dispatchers.IO) {
            if (actionables.isEmpty()) {
                return@withContext emptyMap()
            }
            
            try {
                Log.d(TAG, "Executing ${actionables.size} actionables")
                val results = actionableExecutor.executeActionables(actionables)
                
                // Convert ActionableResult to boolean success values
                results.mapValues { (_, result) -> result.success }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing actionables: ${e.message}", e)
                actionables.associate { it.id to false }
            }
        }
    }
} 