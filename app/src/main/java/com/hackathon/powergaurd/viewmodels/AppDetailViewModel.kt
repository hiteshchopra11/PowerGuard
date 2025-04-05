package com.hackathon.powergaurd.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.powergaurd.PowerGuardOptimizer
import com.hackathon.powergaurd.data.AppRepository
import com.hackathon.powergaurd.data.DeviceStatsCollector
import com.hackathon.powergaurd.models.AppDetailState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    private val deviceStatsCollector: DeviceStatsCollector,
    private val appRepository: AppRepository,
    private val optimizer: PowerGuardOptimizer,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val packageName: String = checkNotNull(savedStateHandle["packageName"])

    private val _appState = MutableStateFlow(AppDetailState())
    val appState: StateFlow<AppDetailState> = _appState.asStateFlow()

    init {
        loadAppDetails()
    }

    private fun loadAppDetails() {
        viewModelScope.launch {
            // Get app usage information
            val appUsage = deviceStatsCollector.collectAppUsage()
                .firstOrNull { it.packageName == packageName }

            // Get app network usage
            val networkUsage = deviceStatsCollector.collectNetworkUsage()
                .appNetworkUsage.firstOrNull { it.packageName == packageName }

            // Get app wake locks
            val wakeLock = deviceStatsCollector.collectWakeLocks()
                .firstOrNull { it.packageName == packageName }

            // Get app optimization patterns (if any)
            val patterns = appRepository.latestResponse.value
                ?.usagePatterns?.get(packageName)

            _appState.value = AppDetailState(
                packageName = packageName,
                appName = appUsage?.appName ?: packageName.substringAfterLast('.'),
                usageInfo = appUsage,
                networkInfo = networkUsage,
                wakeLockInfo = wakeLock,
                usagePattern = patterns
            )
        }
    }

    fun setBackgroundRestriction(restrictionLevel: String) {
        viewModelScope.launch {
            optimizer.setAppBackgroundRestriction(packageName, restrictionLevel)
            // In a real app, we would update the UI state or show a confirmation
        }
    }

    fun manageWakeLock(action: String, timeoutMs: Long = 0) {
        viewModelScope.launch {
            optimizer.manageWakeLock(packageName, action, timeoutMs)
            // In a real app, we would update the UI state or show a confirmation
        }
    }

    fun restrictBackgroundData(
        enabled: Boolean,
        scheduleTimeRanges: List<PowerGuardOptimizer.TimeRange>? = null
    ) {
        viewModelScope.launch {
            optimizer.restrictBackgroundData(packageName, enabled, scheduleTimeRanges)
            // In a real app, we would update the UI state or show a confirmation
        }
    }
}