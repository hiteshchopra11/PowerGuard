package com.hackathon.powergaurd.workers

import android.content.Context
import android.os.BatteryManager
import androidx.work.Worker
import androidx.work.WorkerParameters

class BatteryMonitorWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val maxChargeLevel = inputData.getInt("max_charge_level", 80)

        val batteryManager = applicationContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        if (batteryLevel >= maxChargeLevel) {
            // Create a notification to alert user to unplug
            // Implementation of notification creation omitted for brevity
        }

        return Result.success()
    }
} 