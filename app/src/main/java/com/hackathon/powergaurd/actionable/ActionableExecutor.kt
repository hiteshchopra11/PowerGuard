package com.hackathon.powergaurd.actionable

import android.util.Log
import com.hackathon.powergaurd.data.model.Actionable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Service for processing and executing actionable received from the backend. */
@Singleton
class ActionableExecutor
@Inject
constructor(
    // Inject all handlers here
    private val killAppHandler: KillAppHandler,
    private val enableBatterySaverHandler: EnableBatterySaverHandler,
    private val enableDataSaverHandler: EnableDataSaverHandler,
    private val standbyBucketHandler: StandbyBucketHandler,
    private val appInactiveHandler: AppInactiveHandler
) {
    private val TAG = "ActionableExecutor"

    // Map of actionable types to their handlers
    private val handlers: Map<String, ActionableHandler> by lazy {
        mapOf(
            killAppHandler.actionableType to killAppHandler,
            enableBatterySaverHandler.actionableType to enableBatterySaverHandler,
            enableDataSaverHandler.actionableType to enableDataSaverHandler,
            standbyBucketHandler.actionableType to standbyBucketHandler,
            appInactiveHandler.actionableType to appInactiveHandler
        )
    }

    /**
     * Executes a list of actionable received from the backend.
     *
     * @param actionable List of actionable to execute
     * @return Map of actionable to execution result (true if successful, false otherwise)
     */
    suspend fun executeActionable(
        actionable: List<Actionable>
    ): Map<Actionable, Boolean> =
        withContext(Dispatchers.IO) {
            val results = mutableMapOf<Actionable, Boolean>()

            if (actionable.isEmpty()) {
                Log.d(TAG, "No actionable to execute")
                return@withContext results
            }

            Log.d(TAG, "Executing ${actionable.size} actionable")

            for (action in actionable) {
                val result =
                    try {
                        val handler = handlers[action.type]

                        if (handler == null) {
                            Log.w(
                                TAG,
                                "No handler found for actionable type: ${action.type}"
                            )
                            false
                        } else {
                            Log.d(
                                TAG,
                                "Executing actionable: ${action.type} for app: ${action.packageName}"
                            )
                            handler.handleActionable(action)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error executing actionable: ${action.type}", e)
                        false
                    }

                results[action] = result
            }

            val successCount = results.values.count { it }
            Log.d(TAG, "Executed ${results.size} actionable, $successCount successful")

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
