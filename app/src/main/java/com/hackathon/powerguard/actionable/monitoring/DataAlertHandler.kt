package com.hackathon.powerguard.actionable.monitoring

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.util.Log
import com.hackathon.powerguard.actionable.ActionableHandler
import com.hackathon.powerguard.actionable.ActionableTypes
import com.hackathon.powerguard.actionable.model.ActionableResult
import com.hackathon.powerguard.data.model.Actionable
import com.hackathon.powerguard.receivers.DataAlertReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for data usage alerts.
 * 
 * This handler sets up alarms that will trigger when the data usage reaches
 * a specified threshold.
 */
@Singleton
class DataAlertHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager
) : ActionableHandler {
    
    companion object {
        private const val TAG = "DataAlertHandler"
        private const val DEFAULT_THRESHOLD_MB = 1000 // 1GB data usage
        private const val DATA_ALERT_REQUEST_CODE = 2000
    }
    
    override val actionableType: String = ActionableTypes.SET_DATA_ALERT
    
    override suspend fun execute(actionable: Actionable): ActionableResult {
        try {
            // Extract threshold from actionable parameters (in MB)
            val thresholdMB = actionable.parameters["threshold_mb"]?.toString()?.toIntOrNull() 
                ?: DEFAULT_THRESHOLD_MB
            
            val appPackageName = actionable.packageName.takeIf { it != "system" }
            
            Log.d(TAG, "Setting up data alert for threshold: $thresholdMB MB, app: $appPackageName")
            
            // Create intent for DataAlertReceiver
            val intent = Intent(context, DataAlertReceiver::class.java).apply {
                putExtra("threshold_mb", thresholdMB)
                putExtra("description", actionable.description)
                putExtra("actionable_id", actionable.id)
                if (appPackageName != null) {
                    putExtra("package_name", appPackageName)
                }
            }
            
            // Create pending intent
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                DATA_ALERT_REQUEST_CODE + thresholdMB,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Set up the alarm to check data usage regularly
            // We'll use a repeating alarm that checks every 30 minutes
            val intervalMillis = 30 * 60 * 1000L // 30 minutes
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 10000, // Start after 10 seconds
                intervalMillis,
                pendingIntent
            )
            
            // Get current data usage for the response
            val currentDataUsage = getCurrentDataUsage(appPackageName)
            
            return ActionableResult.success(
                "Data alert set for $thresholdMB MB threshold. Current usage: $currentDataUsage MB",
                mapOf(
                    "threshold_mb" to thresholdMB.toString(),
                    "current_usage_mb" to currentDataUsage.toString(),
                    "check_interval" to "30 minutes"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set data alert", e)
            return ActionableResult.failure(
                "Failed to set data alert: ${e.message}",
                mapOf("error" to (e.message ?: "Unknown error"))
            )
        }
    }
    
    override suspend fun revert(actionable: Actionable): ActionableResult {
        try {
            // Extract threshold from actionable parameters
            val thresholdMB = actionable.parameters["threshold_mb"]?.toString()?.toIntOrNull() 
                ?: DEFAULT_THRESHOLD_MB
            
            Log.d(TAG, "Cancelling data alert for threshold: $thresholdMB MB")
            
            // Create the same intent as when we set up the alarm
            val intent = Intent(context, DataAlertReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                DATA_ALERT_REQUEST_CODE + thresholdMB,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Cancel the alarm
            alarmManager.cancel(pendingIntent)
            
            return ActionableResult.success("Data alert for $thresholdMB MB threshold cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel data alert", e)
            return ActionableResult.failure(
                "Failed to cancel data alert: ${e.message}",
                mapOf("error" to (e.message ?: "Unknown error"))
            )
        }
    }
    
    /**
     * Gets the current data usage in MB for the specified package or the whole system
     */
    private fun getCurrentDataUsage(packageName: String?): Int {
        return try {
            if (packageName != null) {
                val uid = context.packageManager.getApplicationInfo(packageName, 0).uid
                val rxBytes = TrafficStats.getUidRxBytes(uid)
                val txBytes = TrafficStats.getUidTxBytes(uid)
                val totalBytes = rxBytes + txBytes
                (totalBytes / (1024 * 1024)).toInt()
            } else {
                val mobileTotalRx = TrafficStats.getMobileRxBytes()
                val mobileTotalTx = TrafficStats.getMobileTxBytes()
                ((mobileTotalRx + mobileTotalTx) / (1024 * 1024)).toInt()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get data usage: ${e.message}")
            0
        }
    }
} 