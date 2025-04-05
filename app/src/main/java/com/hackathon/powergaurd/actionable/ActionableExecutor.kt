package com.hackathon.powergaurd.actionable

import android.util.Log
import com.hackathon.powergaurd.models.ActionResponse
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Service for processing and executing actionables received from the backend. */
@Singleton
class ActionableExecutor
@Inject
constructor(
        // Inject all handlers here
        private val killAppHandler: KillAppHandler,
        private val enableBatterySaverHandler: EnableBatterySaverHandler,
        private val enableDataSaverHandler: EnableDataSaverHandler
// TODO: Add other handlers as they are implemented
) {
    private val TAG = "ActionableExecutor"

    // Map of actionable types to their handlers
    private val handlers: Map<String, ActionableHandler> by lazy {
        mapOf(
                killAppHandler.actionableType to killAppHandler,
                enableBatterySaverHandler.actionableType to enableBatterySaverHandler,
                enableDataSaverHandler.actionableType to enableDataSaverHandler
                // TODO: Add other handlers as they are implemented
                )
    }

    /**
     * Executes a list of actionables received from the backend.
     *
     * @param actionables List of actionables to execute
     * @return Map of actionable to execution result (true if successful, false otherwise)
     */
    suspend fun executeActionables(
            actionables: List<ActionResponse.Actionable>
    ): Map<ActionResponse.Actionable, Boolean> =
            withContext(Dispatchers.IO) {
                val results = mutableMapOf<ActionResponse.Actionable, Boolean>()

                if (actionables.isEmpty()) {
                    Log.d(TAG, "No actionables to execute")
                    return@withContext results
                }

                Log.d(TAG, "Executing ${actionables.size} actionables")

                for (actionable in actionables) {
                    val result =
                            try {
                                val handler = handlers[actionable.type]

                                if (handler == null) {
                                    Log.w(
                                            TAG,
                                            "No handler found for actionable type: ${actionable.type}"
                                    )
                                    false
                                } else {
                                    Log.d(
                                            TAG,
                                            "Executing actionable: ${actionable.type} for app: ${actionable.app}"
                                    )
                                    handler.handleActionable(actionable)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error executing actionable: ${actionable.type}", e)
                                false
                            }

                    results[actionable] = result
                }

                val successCount = results.values.count { it }
                Log.d(TAG, "Executed ${results.size} actionables, $successCount successful")

                results
            }

    /**
     * Verifies if an actionable type is supported by the system.
     *
     * @param actionableType The type to check
     * @return true if the actionable type is supported, false otherwise
     */
    fun isActionableTypeSupported(actionableType: String): Boolean {
        return handlers.containsKey(actionableType)
    }

    /**
     * Gets a list of all supported actionable types.
     *
     * @return List of supported actionable types
     */
    fun getSupportedActionableTypes(): List<String> {
        return handlers.keys.toList()
    }
}
