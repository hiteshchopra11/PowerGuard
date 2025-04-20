package com.hackathon.powergaurd.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hackathon.powergaurd.R
import com.hackathon.powergaurd.utils.NotificationUtils

/**
 * BroadcastReceiver that checks if the battery level has reached a specified threshold
 * and displays a notification if it has.
 */
class BatteryAlertReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BatteryAlertReceiver"
        private const val NOTIFICATION_ID = 2000
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Extract threshold from intent
        val threshold = intent.getIntExtra("threshold", 20)
        val description = intent.getStringExtra("description") ?: "Battery alert"
        val actionableId = intent.getStringExtra("actionable_id") ?: ""
        val packageName = intent.getStringExtra("package_name")
        
        Log.d(TAG, "Checking battery level for alert, threshold: $threshold%")
        
        // Get current battery level
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        
        if (batteryStatus != null) {
            val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            
            // Calculate battery percentage
            val batteryPct = level * 100 / scale
            
            Log.d(TAG, "Current battery level: $batteryPct%, threshold: $threshold%")
            
            // Check if we should show notification
            if (batteryPct <= threshold) {
                // Check if app is in foreground if packageName is provided
                var shouldNotify = true
                if (packageName != null) {
                    // This is a package-specific alert, only notify if the app is in use
                    // This simplified implementation just notifies regardless
                    // A real implementation would check if the app is in foreground
                    Log.d(TAG, "App-specific alert for $packageName, showing notification")
                }
                
                if (shouldNotify) {
                    showNotification(context, threshold, batteryPct, description, actionableId)
                }
            }
        }
    }
    
    /**
     * Shows a notification about the battery level alert
     */
    private fun showNotification(
        context: Context, 
        threshold: Int, 
        currentLevel: Int, 
        description: String,
        actionableId: String
    ) {
        // Ensure notification channel exists
        NotificationUtils.createNotificationChannels(context)
        
        // Build notification
        val notification = NotificationCompat.Builder(context, NotificationUtils.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Battery Alert: $currentLevel%")
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        
        // Show notification
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID + threshold, notification)
            Log.d(TAG, "Battery alert notification shown for $actionableId")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to show battery alert notification: ${e.message}")
        }
    }
} 