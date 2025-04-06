package com.hackathon.powergaurd.workers // Ensure this matches your actual package structure

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hackathon.powergaurd.collector.UsageDataCollector
import com.hackathon.powergaurd.data.BackendService
import com.hackathon.powergaurd.models.DeviceData
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class DataCollectionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val usageDataCollector: UsageDataCollector,
    // --- Keep other injections ---
    private val backendService: BackendService
    // Add other dependencies if needed (e.g., repository)
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        // Consider using a unique name if you also have a worker in com.hackathon.powergaurd.worker
        const val WORK_NAME = "com.hackathon.powergaurd.workers.DataCollectionWorker"
        private const val TAG = "DataCollectionWorker" // Keep or adjust TAG
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting data collection work in package 'workers'...") // Added log clarity

        try {
            // --- CALL THE REAL COLLECTOR'S UNIFIED METHOD ---
            Log.d(TAG, "Calling usageDataCollector.collectDeviceData()")
            val deviceData: DeviceData = usageDataCollector.collectDeviceData()

            // Log some collected data for verification
            Log.d(TAG, "Device data collected:")
            Log.d(TAG, "  Device ID: ${deviceData.deviceId}")
            Log.d(TAG, "  Timestamp: ${deviceData.timestamp}")
            Log.d(TAG, "  App Usage Count: ${deviceData.appUsage.size}")
            Log.d(TAG, "  Battery Level: ${deviceData.batteryStats.level}%")
            Log.d(TAG, "  Network Usage Apps: ${deviceData.networkUsage.appNetworkUsage.size}")
            Log.d(
                TAG,
                "  Wake Locks Count: ${deviceData.wakeLocks.size} (Note: Data likely limited)"
            ) // Remind about wake lock limitation

            // Example: Send the collected data to the backend
            Log.d(TAG, "Sending collected data to backend service...")
            val actionResponse = backendService.sendDataForAnalysis(deviceData)
            Log.i(TAG, "Data sent to backend. Response Summary: ${actionResponse.summary}")
            Log.d(TAG, "Received ${actionResponse.actionables.size} actionables.")

            // TODO: Implement logic to handle the actionResponse if necessary
            // For example, queueing actions based on actionResponse.actionables

            Log.i(TAG, "Data collection work finished successfully.")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Error during data collection work", e)
            // Consider retrying for transient network errors etc.
            Result.failure()
        }
    }
}