package com.powergaurd.llm.exceptions

/**
 * Exception thrown when the API key is invalid or missing
 */
class InvalidAPIKeyException(message: String, cause: Throwable? = null) : Exception(message, cause) 