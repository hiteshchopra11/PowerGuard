package com.hackathon.powergaurd.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hackathon.powergaurd.workers.DataCollectionWorker
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Schedule the data collection work after device boot
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val dataCollectionRequest = PeriodicWorkRequestBuilder<DataCollectionWorker>(
                30, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES // Flex period for battery optimization
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "data_collection_work",
                    ExistingPeriodicWorkPolicy.REPLACE,
                    dataCollectionRequest
                )
        }
    }
}