package com.hackathon.powergaurd.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hackathon.powergaurd.services.PowerGuardService
import com.hackathon.powergaurd.workers.DataCollectionWorker
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device boot completed, starting PowerGuard services")

            // 1. Schedule the data collection work after device boot
            scheduleDataCollection(context)

            // 2. Start the PowerGuardService
            startPowerGuardService(context)
        }
    }

    private fun scheduleDataCollection(context: Context) {
        val constraints =
                Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()

        val dataCollectionRequest =
                PeriodicWorkRequestBuilder<DataCollectionWorker>(
                                30,
                                TimeUnit.MINUTES,
                                5,
                                TimeUnit.MINUTES // Flex period for battery optimization
                        )
                        .setConstraints(constraints)
                        .build()

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                        "data_collection_work",
                        ExistingPeriodicWorkPolicy.UPDATE,
                        dataCollectionRequest
                )
    }

    private fun startPowerGuardService(context: Context) {
        val serviceIntent = Intent(context, PowerGuardService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
