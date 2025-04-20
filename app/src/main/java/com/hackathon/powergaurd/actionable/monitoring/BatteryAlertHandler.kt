package com.hackathon.powergaurd.actionable.monitoring

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log
import com.hackathon.powergaurd.actionable.ActionableHandler
import com.hackathon.powergaurd.actionable.ActionableTypes
import com.hackathon.powergaurd.actionable.model.ActionableResult
import com.hackathon.powergaurd.data.model.Actionable
import com.hackathon.powergaurd.receivers.BatteryAlertReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for battery level alerts.
 * 
 * This handler sets up alarms that will trigger when the battery level reaches
 * a specified threshold.
 */
@Singleton
class BatteryAlertHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager
) : ActionableHandler {
    
    companion object {
        private const val TAG = "BatteryAlertHandler"
        private const val DEFAULT_THRESHOLD = 20 // 20% battery level
        private const val BATTERY_ALERT_REQUEST_CODE = 1000
    }
    
    override val actionableType: String = ActionableTypes.SET_BATTERY_ALERT
    
    override suspend fun execute(actionable: Actionable): ActionableResult {
        try {
            // Extract threshold from actionable parameters
            val threshold = actionable.parameters["threshold"]?.toString()?.toIntOrNull() 
                ?: DEFAULT_THRESHOLD
            
            val appPackageName = actionable.packageName.takeIf { it != "system" }
            
            Log.d(TAG, "Setting up battery alert for threshold: $threshold%, app: $appPackageName")
            
            // Create intent for BatteryAlertReceiver
            val intent = Intent(context, BatteryAlertReceiver::class.java).apply {
                putExtra("threshold", threshold)
                putExtra("description", actionable.description)
                putExtra("actionable_id", actionable.id)
                if (appPackageName != null) {
                    putExtra("package_name", appPackageName)
                }
            }
            
            // Create pending intent
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                BATTERY_ALERT_REQUEST_CODE + threshold,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Set up the alarm to check battery level regularly
            // We'll use a repeating alarm that checks every 15 minutes
            val intervalMillis = 15 * 60 * 1000L // 15 minutes
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 10000, // Start after 10 seconds
                intervalMillis,
                pendingIntent
            )
            
            // Get current battery level for the response
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val currentLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            
            return ActionableResult.success(
                "Battery alert set for $threshold% threshold. Current level: $currentLevel%",
                mapOf(
                    "threshold" to threshold.toString(),
                    "current_level" to currentLevel.toString(),
                    "check_interval" to "15 minutes"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set battery alert", e)
            return ActionableResult.failure(
                "Failed to set battery alert: ${e.message}",
                mapOf("error" to (e.message ?: "Unknown error"))
            )
        }
    }
    
    override suspend fun revert(actionable: Actionable): ActionableResult {
        try {
            // Extract threshold from actionable parameters
            val threshold = actionable.parameters["threshold"]?.toString()?.toIntOrNull() 
                ?: DEFAULT_THRESHOLD
            
            Log.d(TAG, "Cancelling battery alert for threshold: $threshold%")
            
            // Create the same intent as when we set up the alarm
            val intent = Intent(context, BatteryAlertReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                BATTERY_ALERT_REQUEST_CODE + threshold,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Cancel the alarm
            alarmManager.cancel(pendingIntent)
            
            return ActionableResult.success("Battery alert for $threshold% threshold cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel battery alert", e)
            return ActionableResult.failure(
                "Failed to cancel battery alert: ${e.message}",
                mapOf("error" to (e.message ?: "Unknown error"))
            )
        }
    }
} 