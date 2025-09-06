package com.hackathon.powerguard.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hackathon.powerguard.R
import com.hackathon.powerguard.utils.NotificationUtils

/**
 * BroadcastReceiver that checks if the data usage has reached a specified threshold
 * and displays a notification if it has.
 */
class DataAlertReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "DataAlertReceiver"
        private const val NOTIFICATION_ID = 3000
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Extract threshold from intent
        val thresholdMB = intent.getIntExtra("threshold_mb", 1000)
        val description = intent.getStringExtra("description") ?: "Data usage alert"
        val actionableId = intent.getStringExtra("actionable_id") ?: ""
        val packageName = intent.getStringExtra("package_name")
        
        Log.d(TAG, "Checking data usage for alert, threshold: $thresholdMB MB")
        
        // Get current data usage
        if (packageName != null) {
            // App-specific tracking using TrafficStats
            try {
                val uid = context.packageManager.getApplicationInfo(packageName, 0).uid
                val rxBytes = TrafficStats.getUidRxBytes(uid)
                val txBytes = TrafficStats.getUidTxBytes(uid)
                val totalBytes = rxBytes + txBytes
                val totalMB = totalBytes / (1024 * 1024)
                
                val appName = context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(packageName, 0)
                ).toString()
                
                Log.d(TAG, "App $packageName ($appName) data usage: $totalMB MB, threshold: $thresholdMB MB")
                
                if (totalMB >= thresholdMB) {
                    showNotification(
                        context, 
                        thresholdMB, 
                        totalMB.toInt(),
                        appName,
                        description,
                        actionableId
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get data usage for app $packageName: ${e.message}")
            }
        } else {
            // System-wide data usage
            val mobileTotalRx = TrafficStats.getMobileRxBytes()
            val mobileTotalTx = TrafficStats.getMobileTxBytes()
            val totalMobile = (mobileTotalRx + mobileTotalTx) / (1024 * 1024)
            
            Log.d(TAG, "Total mobile data usage: $totalMobile MB, threshold: $thresholdMB MB")
            
            if (totalMobile >= thresholdMB) {
                showNotification(
                    context, 
                    thresholdMB, 
                    totalMobile.toInt(), 
                    "Mobile data",
                    description,
                    actionableId
                )
            }
        }
    }
    
    /**
     * Shows a notification about the data usage alert
     */
    private fun showNotification(
        context: Context, 
        threshold: Int,
        currentUsage: Int,
        source: String,
        description: String,
        actionableId: String
    ) {
        // Ensure notification channel exists
        NotificationUtils.createNotificationChannels(context)
        
        // Build notification
        val notification = NotificationCompat.Builder(context, NotificationUtils.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$source Data Alert: $currentUsage MB")
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        
        // Show notification
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID + threshold, notification)
            Log.d(TAG, "Data alert notification shown for $actionableId")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to show data alert notification: ${e.message}")
        }
    }
} 