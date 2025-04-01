package com.hackathon.powergaurd.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class WakeLockTimeoutWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val packageName = inputData.getString("package_name") ?: return Result.failure()
        return Result.success()
    }
} 