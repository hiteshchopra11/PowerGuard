package com.hackathon.powergaurd.data.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Data class representing an action history item */
data class ActionHistoryItem(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String,
    val summary: String,
    val succeeded: Boolean,
    val appPackage: String? = null,
    val details: String? = null
)

/** Repository for managing action history */
@Singleton
class ActionHistoryRepository @Inject constructor() {
    private val TAG = "ActionHistoryRepository"

    private val _historyItems = MutableStateFlow<List<ActionHistoryItem>>(emptyList())
    val historyItems: Flow<List<ActionHistoryItem>> = _historyItems.asStateFlow()

    /** Add a new action to the history */
    suspend fun addActionHistory(item: ActionHistoryItem) {
        Log.d(TAG, "Adding action history: $item")
        val currentList = _historyItems.value.toMutableList()
        currentList.add(0, item) // Add to the beginning to keep newest items first
        _historyItems.value = currentList

        // In a real app, we would save this to persistent storage
    }

    /** Clear all action history */
    suspend fun clearHistory() {
        Log.d(TAG, "Clearing all action history")
        _historyItems.value = emptyList()

        // In a real app, we would clear the persistent storage
    }

    /** Get the number of history items */
    fun getHistoryCount(): Int = _historyItems.value.size
}
