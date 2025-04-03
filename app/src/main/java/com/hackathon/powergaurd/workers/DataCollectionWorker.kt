package com.hackathon.powergaurd.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hackathon.powergaurd.data.AppRepository
import com.hackathon.powergaurd.data.BackendService
import com.hackathon.powergaurd.data.DeviceStatsCollector
import com.hackathon.powergaurd.models.ActionResponse
import com.hackathon.powergaurd.models.DeviceData
import com.hackathon.powergaurd.utils.NotificationUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DataCollectionWorker(
    @ApplicationContext private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @Inject
    lateinit var deviceStatsCollector: DeviceStatsCollector

    @Inject
    lateinit var backendService: BackendService

    @Inject
    lateinit var appRepository: AppRepository

    companion object {
        private const val TAG = "DataCollectionWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // 1. Collect device data
            val deviceData = collectDeviceData()
            Log.d(TAG, "Device data collected successfully")

            // 2. Send data to backend and get action response
            val actionResponse = sendDataToBackend(deviceData)
            Log.d(TAG, "Backend response received")

            // 3. Store the response
            storeResponse(actionResponse)

            // 4. Apply actions if auto-optimize is enabled
            if (isAutoOptimizeEnabled()) {
                applyActions(actionResponse)
                showOptimizationNotification(actionResponse)
            }

            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in data collection worker", e)
            return@withContext Result.retry()
        }
    }

    private suspend fun collectDeviceData(): DeviceData {
        return DeviceData(
            appUsage = deviceStatsCollector.collectAppUsage(),
            batteryStats = deviceStatsCollector.collectBatteryStats(),
            networkUsage = deviceStatsCollector.collectNetworkUsage(),
            wakeLocks = deviceStatsCollector.collectWakeLocks(),
            deviceId = deviceStatsCollector.getDeviceId(),
            timestamp = System.currentTimeMillis()
        )
    }

    private suspend fun sendDataToBackend(deviceData: DeviceData): ActionResponse {
        Log.d(TAG, "Sending data to backend")
        return try {
            // This is where we call the new sendDataForAnalysis method
            backendService.sendDataForAnalysis(deviceData)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending data to backend", e)
            // Fallback to simulated response
            backendService.sendDataForAnalysis(deviceData)
        }
    }

    private suspend fun storeResponse(response: ActionResponse) {
        appRepository.saveActionResponse(response)
    }

    private fun isAutoOptimizeEnabled(): Boolean {
        // For now, always return true. In a real app, this would check user preferences
        return true
    }

    private fun applyActions(response: ActionResponse) {
        // TODO: Implement actual action application
        Log.d(TAG, "Applying actions: ${response.actionables}")
    }

    private fun showOptimizationNotification(response: ActionResponse) {
        NotificationUtils.showOptimizationNotification(
            context,
            "PowerGuard Optimization",
            response.summary
        )
    }
}