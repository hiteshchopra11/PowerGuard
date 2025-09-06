package com.hackathon.powerguard.actionable.model

/**
 * Represents the result of executing an actionable.
 *
 * @property success Whether the actionable execution was successful
 * @property message A message describing the result, particularly useful for errors
 * @property details Additional details about the execution, if any
 */
data class ActionableResult(
    val success: Boolean,
    val message: String,
    val details: Map<String, String> = emptyMap()
) {
    companion object {
        /**
         * Creates a successful result.
         *
         * @param message A message describing the successful execution
         * @param details Additional details about the execution, if any
         * @return An [ActionableResult] indicating success
         */
        fun success(message: String, details: Map<String, String> = emptyMap()): ActionableResult =
            ActionableResult(true, message, details)
        
        /**
         * Creates a failed result.
         *
         * @param message A message describing the failure
         * @param details Additional details about the failure, if any
         * @return An [ActionableResult] indicating failure
         */
        fun failure(message: String, details: Map<String, String> = emptyMap()): ActionableResult =
            ActionableResult(false, message, details)
        
        /**
         * Creates a result from an exception.
         *
         * @param exception The exception that caused the failure
         * @param details Additional details about the failure, if any
         * @return An [ActionableResult] indicating failure
         */
        fun fromException(exception: Exception, details: Map<String, String> = emptyMap()): ActionableResult =
            ActionableResult(
                success = false,
                message = "Error: ${exception.message ?: exception.javaClass.simpleName}",
                details = details + mapOf("exceptionType" to exception.javaClass.name)
            )
    }
}