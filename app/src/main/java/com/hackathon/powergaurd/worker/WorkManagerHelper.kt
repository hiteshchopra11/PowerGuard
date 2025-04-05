package com.hackathon.powergaurd.worker

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Helper class for scheduling workers using WorkManager */
@Singleton
class WorkManagerHelper @Inject constructor(context: Context) {
        private val TAG = "WorkManagerHelper"
        private val workManager = WorkManager.getInstance(context)

        // Constants for work names
        companion object {
                const val PERIODIC_DATA_COLLECTION = "periodic_data_collection"
                const val ONE_TIME_DATA_COLLECTION = "one_time_data_collection"
        }

        /** Schedule periodic data collection */
        fun schedulePeriodicDataCollection() {
                Log.d(TAG, "Scheduling periodic data collection")

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
                                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                                .build()

                workManager.enqueueUniquePeriodicWork(
                        PERIODIC_DATA_COLLECTION,
                        ExistingPeriodicWorkPolicy.UPDATE,
                        dataCollectionRequest
                )
        }

        /** Run a one-time data collection immediately */
        fun runOneTimeDataCollection() {
                Log.d(TAG, "Running one-time data collection")

                val constraints =
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

                val dataCollectionRequest =
                        OneTimeWorkRequestBuilder<DataCollectionWorker>()
                                .setConstraints(constraints)
                                .build()

                workManager.enqueueUniqueWork(
                        ONE_TIME_DATA_COLLECTION,
                        ExistingWorkPolicy.REPLACE,
                        dataCollectionRequest
                )
        }

        /** Cancels all scheduled data collection work. */
        fun cancelAllWork() {
                Log.d(TAG, "Cancelling all scheduled work")
                workManager.cancelAllWork()
        }
}
