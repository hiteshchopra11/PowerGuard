package com.hackathon.powergaurd.actionable

import com.hackathon.powergaurd.data.model.Actionable

/**
 * Interface for handling different types of actionables received from the backend or user requests.
 * 
 * The ActionableHandler is part of a strategy pattern implementation that allows PowerGuard
 * to perform various optimization actions in a consistent manner. Each concrete handler 
 * implements this interface to provide specialized behavior for a specific actionable type.
 * 
 * Handlers are registered with the ActionableExecutor, which routes actionable requests
 * to the appropriate handler based on the actionable's type.
 * 
 * Each handler should:
 * 1. Provide a unique actionable type identifier
 * 2. Implement error handling and fallback mechanisms
 * 3. Follow a consistent logging pattern
 * 4. Use ActionableUtils for common operations
 */
interface ActionableHandler {

    /** 
     * The unique type identifier for actionables this handler can process.
     * This must match one of the constants defined in ActionableTypes.
     */
    val actionableType: String

    /**
     * Processes and executes the actionable.
     *
     * This method should:
     * - Validate the incoming actionable
     * - Apply the necessary system changes
     * - Handle errors gracefully
     * - Provide appropriate fallback mechanisms
     * - Log the operation result
     *
     * @param actionable The actionable to process
     * @return true if the actionable was successfully executed, false otherwise
     */
    suspend fun handleActionable(actionable: Actionable): Boolean
}
