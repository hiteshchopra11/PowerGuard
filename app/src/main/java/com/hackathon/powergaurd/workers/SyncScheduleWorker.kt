package com.hackathon.powergaurd.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class SyncScheduleWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val accountType = inputData.getString("account_type") ?: return Result.failure()
        // Request sync for the account type
        // This would use ContentResolver.requestSync() in a real implementation
        return Result.success()
    }
} 