package com.hackathon.powerguard

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.hackathon.powerguard.worker.WorkManagerHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PowerGuardApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var workManagerHelper: WorkManagerHelper

    override fun onCreate() {
        super.onCreate()

        // Initialize WorkManager
        Log.d(TAG, "Initializing PowerGuard App")

        // Schedule periodic data collection
        workManagerHelper.schedulePeriodicDataCollection()

        // Run a one-time data collection for immediate results
        workManagerHelper.runOneTimeDataCollection()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    companion object {
        private const val TAG = "PowerGuardApp"
    }
}
