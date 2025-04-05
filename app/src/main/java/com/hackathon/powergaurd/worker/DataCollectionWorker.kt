package com.hackathon.powergaurd.worker

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hackathon.powergaurd.data.model.*
import com.hackathon.powergaurd.data.repository.ActionHistoryRepository
import com.hackathon.powergaurd.network.ApiService
import com.hackathon.powergaurd.services.ActionableExecutor
import com.hackathon.powergaurd.widget.PowerGuardWidget
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Worker for collecting device usage data and sending it to the backend for analysis. */
@HiltWorker
class DataCollectionWorker
@AssistedInject
constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val apiService: ApiService,
        private val actionableExecutor: ActionableExecutor,
        private val actionHistoryRepository: ActionHistoryRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "DataCollectionWorker"
    }

    /** Performs the background work of collecting and sending data. */
    override suspend fun doWork(): Result =
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "Starting data collection")

                    // Update widget to show loading state
                    PowerGuardWidget.updateAllWidgets(
                            applicationContext,
                            "Collecting device data..."
                    )

                    // Collect device usage data
                    val deviceData = collectDeviceData()

                    // Update widget to show data is being sent
                    PowerGuardWidget.updateAllWidgets(applicationContext, "Analyzing data...")

                    // Send data to backend for analysis
                    val response = apiService.analyzeDeviceData(deviceData)

                    if (response.success && response.actionables.isNotEmpty()) {
                        Log.d(TAG, "Received ${response.actionables.size} actionables")

                        // Update widget to show actions are being applied
                        PowerGuardWidget.updateAllWidgets(
                                applicationContext,
                                "Applying optimizations..."
                        )

                        // Execute actionables
                        val results = actionableExecutor.executeActionables(response.actionables)

                        // Store action history
                        for (result in results) {
                            actionHistoryRepository.addActionHistory(result)
                        }

                        // Show scores in log
                        Log.d(
                                TAG,
                                "Battery score: ${response.batteryScore}, Data score: ${response.dataScore}, Performance score: ${response.performanceScore}"
                        )

                        // Show estimated savings in log
                        response.estimatedSavings?.let {
                            Log.d(
                                    TAG,
                                    "Estimated savings: ${it.batteryMinutes} min battery, ${it.dataMB} MB data, ${it.storageMB} MB storage"
                            )
                        }

                        // Update widget with summary
                        PowerGuardWidget.updateAllWidgets(applicationContext)

                        Result.success()
                    } else {
                        Log.d(TAG, "No actionables received or analysis failed")
                        PowerGuardWidget.updateAllWidgets(
                                applicationContext,
                                if (response.success) "No actions needed" else "Analysis failed"
                        )

                        if (response.success) Result.success() else Result.retry()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during data collection/analysis", e)

                    // Update widget to show error
                    PowerGuardWidget.updateAllWidgets(applicationContext, "Error occurred")

                    Result.failure()
                }
            }

    /**
     * Collects device usage data to send to the backend. In a real implementation, this would
     * gather actual device metrics.
     */
    private suspend fun collectDeviceData(): DeviceData {
        // In a real implementation, we would collect actual device data
        // For now, we'll return mock data that matches our API contract
        return DeviceData(
                deviceId = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                battery =
                        BatteryData(
                                level = 65,
                                temperature = 32.5f,
                                voltage = 3950,
                                isCharging = false,
                                chargingType = "not_charging",
                                health = 2
                        ),
                memory =
                        MemoryData(
                                totalRam = 4 * 1024 * 1024 * 1024L, // 4GB
                                availableRam = (1.5 * 1024 * 1024 * 1024).toLong(), // 1.5GB
                                lowMemory = false,
                                threshold = 100 * 1024 * 1024L // 100MB
                        ),
                cpu =
                        CpuData(
                                usage = 15.0f,
                                temperature = 38.2f,
                                frequencies = listOf(1800000L, 2000000L, 1950000L, 1800000L)
                        ),
                network =
                        NetworkData(
                                type = "WIFI",
                                strength = 3,
                                isRoaming = false,
                                dataUsage =
                                        DataUsage(
                                                foreground = 50 * 1024 * 1024L, // 50MB
                                                background = 15 * 1024 * 1024L // 15MB
                                        )
                        ),
                apps =
                        listOf(
                                AppData(
                                        packageName = "com.example.app1",
                                        processName = "com.example.app1",
                                        appName = "Example App 1",
                                        isSystemApp = false,
                                        lastUsed =
                                                System.currentTimeMillis() -
                                                        1000 * 60 * 10, // 10 minutes ago
                                        foregroundTime = 30 * 60 * 1000L, // 30 minutes
                                        backgroundTime = 60 * 60 * 1000L, // 1 hour
                                        batteryUsage = 5.2f,
                                        dataUsage =
                                                DataUsage(
                                                        foreground = 10 * 1024 * 1024L, // 10MB
                                                        background = 5 * 1024 * 1024L // 5MB
                                                ),
                                        memoryUsage = 200 * 1024 * 1024L, // 200MB
                                        cpuUsage = 4.8f,
                                        notifications = 15,
                                        crashes = 0
                                ),
                                AppData(
                                        packageName = "com.example.app2",
                                        processName = "com.example.app2",
                                        appName = "Example App 2",
                                        isSystemApp = false,
                                        lastUsed =
                                                System.currentTimeMillis() -
                                                        1000 * 60 * 30, // 30 minutes ago
                                        foregroundTime = 15 * 60 * 1000L, // 15 minutes
                                        backgroundTime = 120 * 60 * 1000L, // 2 hours
                                        batteryUsage = 2.1f,
                                        dataUsage =
                                                DataUsage(
                                                        foreground = 5 * 1024 * 1024L, // 5MB
                                                        background = 20 * 1024 * 1024L // 20MB
                                                ),
                                        memoryUsage = 100 * 1024 * 1024L, // 100MB
                                        cpuUsage = 1.2f,
                                        notifications = 8,
                                        crashes = 1
                                )
                        ),
                settings =
                        SettingsData(
                                batteryOptimization = true,
                                dataSaver = false,
                                powerSaveMode = false,
                                adaptiveBattery = true,
                                autoSync = true
                        ),
                deviceInfo =
                        DeviceInfo(
                                manufacturer = Build.MANUFACTURER,
                                model = Build.MODEL,
                                osVersion = "Android " + Build.VERSION.RELEASE,
                                sdkVersion = Build.VERSION.SDK_INT,
                                screenOnTime = 3 * 60 * 60 * 1000L // 3 hours
                        )
        )
    }
}
