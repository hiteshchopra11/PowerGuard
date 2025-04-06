package com.hackathon.powerguard.actionable

import com.hackathon.powerguard.data.model.Actionable

/** Interface for handling different types of actionable received from the backend. */
interface ActionableHandler {

    /** The type of actionable this handler can process. */
    val actionableType: String

    /**
     * Processes and executes the actionable.
     *
     * @param actionable The actionable to process
     * @return true if the actionable was successfully executed, false otherwise
     */
    suspend fun handleActionable(actionable: Actionable): Boolean
}
