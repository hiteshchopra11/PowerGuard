package com.hackathon.powergaurd.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.powergaurd.data.DeviceStatsCollector
import com.hackathon.powergaurd.models.AppUsageInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val deviceStatsCollector: DeviceStatsCollector
) : ViewModel() {

    private val _appsList = MutableStateFlow<List<AppUsageInfo>>(emptyList())
    val appsList: StateFlow<List<AppUsageInfo>> = _appsList.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filteredApps = MutableStateFlow<List<AppUsageInfo>>(emptyList())
    val filteredApps: StateFlow<List<AppUsageInfo>> = _filteredApps.asStateFlow()

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            val apps = deviceStatsCollector.collectAppUsage()
            _appsList.value = apps
            filterApps()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        filterApps()
    }

    private fun filterApps() {
        val query = _searchQuery.value
        val apps = _appsList.value

        _filteredApps.value = if (query.isEmpty()) {
            apps
        } else {
            apps.filter {
                it.appName.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
            }
        }
    }
}