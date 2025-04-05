package com.hackathon.powergaurd.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.powergaurd.data.repository.ActionHistoryItem
import com.hackathon.powergaurd.data.repository.ActionHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Data class representing the state of the history screen. */
data class HistoryState(
    val items: List<ActionHistoryItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

/** ViewModel for the history screen. */
@HiltViewModel
class HistoryViewModel
@Inject
constructor(private val actionHistoryRepository: ActionHistoryRepository) : ViewModel() {

    private val _historyState = MutableStateFlow(HistoryState())
    val historyState: StateFlow<HistoryState> = _historyState.asStateFlow()

    init {
        viewModelScope.launch {
            // Start observing the history items
            actionHistoryRepository.historyItems.collect { items ->
                _historyState.update { state -> state.copy(items = items, isLoading = false) }
            }
        }
    }

    /** Clears all action history. */
    fun clearHistory() {
        viewModelScope.launch {
            try {
                actionHistoryRepository.clearHistory()
            } catch (e: Exception) {
                _historyState.update { it.copy(error = e.message) }
            }
        }
    }
}
