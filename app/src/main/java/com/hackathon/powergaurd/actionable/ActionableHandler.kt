package com.hackathon.powergaurd.actionable

import com.hackathon.powergaurd.data.model.Actionable
import com.hackathon.powergaurd.actionable.model.ActionableResult

/**
 * Interface for all actionable handlers that implement specific optimization strategies.
 * Each handler is responsible for executing a specific type of actionable.
 */
interface ActionableHandler {
    /**
     * The type of actionable this handler supports.
     * Should match one of the constants in [ActionableTypes].
     */
    val actionableType: String

    /**
     * Executes the specified actionable.
     *
     * @param actionable The actionable to execute
     * @return An [ActionableResult] indicating success or failure and any additional information
     */
    suspend fun execute(actionable: Actionable): ActionableResult

    /**
     * Reverts the effect of a previously executed actionable.
     * Not all actionables support reversal.
     *
     * @param actionable The actionable to revert
     * @return An [ActionableResult] indicating success or failure of the revert operation
     */
    suspend fun revert(actionable: Actionable): ActionableResult

    /**
     * Checks if this handler can execute the specified actionable.
     * Handlers should verify permissions and device compatibility.
     *
     * @param actionable The actionable to check
     * @return true if this handler can execute the actionable, false otherwise
     */
    fun canHandle(actionable: Actionable): Boolean =
        actionable.type == actionableType
}