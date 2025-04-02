package com.hackathon.powergaurd.data

import com.hackathon.powergaurd.models.ActionResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepository @Inject constructor() {

    private val _latestResponse = MutableStateFlow<ActionResponse?>(null)
    val latestResponse: StateFlow<ActionResponse?> = _latestResponse.asStateFlow()

    private val _optimizationHistory = MutableStateFlow<List<ActionResponse>>(emptyList())
    val optimizationHistory: StateFlow<List<ActionResponse>> = _optimizationHistory.asStateFlow()

    suspend fun saveActionResponse(response: ActionResponse) {
        _latestResponse.value = response

        // Add to history
        val currentHistory = _optimizationHistory.value.toMutableList()
        currentHistory.add(0, response) // Add at the beginning (newest first)

        // Limit history size
        if (currentHistory.size > MAX_HISTORY_SIZE) {
            _optimizationHistory.value = currentHistory.take(MAX_HISTORY_SIZE)
        } else {
            _optimizationHistory.value = currentHistory
        }
    }

    companion object {
        private const val MAX_HISTORY_SIZE = 50
    }
}