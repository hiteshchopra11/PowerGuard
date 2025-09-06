package com.hackathon.powerguard.actionable

import android.util.Log
import com.hackathon.powerguard.actionable.battery.KillAppHandler
import com.hackathon.powerguard.actionable.battery.ManageWakeLocksHandler
import com.hackathon.powerguard.actionable.battery.SetStandbyBucketHandler
import com.hackathon.powerguard.actionable.data.RestrictBackgroundDataHandler
import com.hackathon.powerguard.actionable.model.ActionableResult
import com.hackathon.powerguard.actionable.monitoring.BatteryAlertHandler
import com.hackathon.powerguard.actionable.monitoring.DataAlertHandler
import com.hackathon.powerguard.data.model.Actionable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central service for executing actionables.
 *
 * This class coordinates the execution of different types of actionables by routing
 * them to the appropriate handlers based on their type.
 */
@Singleton
class ActionableExecutor @Inject constructor(
    private val killAppHandler: KillAppHandler,
    private val manageWakeLocksHandler: ManageWakeLocksHandler,
    private val restrictBackgroundDataHandler: RestrictBackgroundDataHandler,
    private val setStandbyBucketHandler: SetStandbyBucketHandler,
    private val batteryAlertHandler: BatteryAlertHandler,
    private val dataAlertHandler: DataAlertHandler
) {
    private val TAG = "ActionableExecutor"

    // Register all handlers
    private val handlers = mapOf(
        ActionableTypes.KILL_APP to killAppHandler,
        ActionableTypes.MANAGE_WAKE_LOCKS to manageWakeLocksHandler,
        ActionableTypes.RESTRICT_BACKGROUND_DATA to restrictBackgroundDataHandler,
        ActionableTypes.SET_STANDBY_BUCKET to setStandbyBucketHandler,
        ActionableTypes.SET_BATTERY_ALERT to batteryAlertHandler,
        ActionableTypes.SET_DATA_ALERT to dataAlertHandler
    )

    /**
     * Executes a list of actionables.
     *
     * @param actionables The list of actionables to execute
     * @return A map of actionable IDs to their execution results
     */
    suspend fun executeActionables(actionables: List<Actionable>): Map<String, ActionableResult> {
        Log.d(TAG, "Executing ${actionables.size} actionables")

        val results = mutableMapOf<String, ActionableResult>()

        for (actionable in actionables) {
            val result = executeActionable(actionable)
            results[actionable.id] = result

            // Log the result
            if (result.success) {
                Log.d(TAG, "Successfully executed actionable ${actionable.id} (${actionable.type})")
            } else {
                Log.e(TAG, "Failed to execute actionable ${actionable.id} (${actionable.type}): ${result.message}")
            }
        }

        return results
    }

    /**
     * Executes a single actionable.
     *
     * @param actionable The actionable to execute
     * @return The result of the execution
     */
    suspend fun executeActionable(actionable: Actionable): ActionableResult {
        val handler = handlers[actionable.type]

        return if (handler != null) {
            try {
                if (handler.canHandle(actionable)) {
                    Log.d(TAG, "Executing actionable ${actionable.id} with handler ${handler.javaClass.simpleName}")
                    handler.execute(actionable)
                } else {
                    Log.w(TAG, "Handler ${handler.javaClass.simpleName} cannot handle actionable ${actionable.id}")
                    ActionableResult.failure("Handler cannot handle this actionable")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing actionable ${actionable.id}", e)
                ActionableResult.fromException(e)
            }
        } else {
            Log.e(TAG, "No handler found for actionable type ${actionable.type}")
            ActionableResult.failure("No handler found for actionable type ${actionable.type}")
        }
    }
}