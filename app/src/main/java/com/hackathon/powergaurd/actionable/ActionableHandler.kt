package com.hackathon.powergaurd.actionable

import com.hackathon.powergaurd.models.ActionResponse

/** Interface for handling different types of actionables received from the backend. */
interface ActionableHandler {

    /** The type of actionable this handler can process. */
    val actionableType: String

    /**
     * Processes and executes the actionable.
     *
     * @param actionable The actionable to process
     * @return true if the actionable was successfully executed, false otherwise
     */
    suspend fun handleActionable(actionable: ActionResponse.Actionable): Boolean
}
