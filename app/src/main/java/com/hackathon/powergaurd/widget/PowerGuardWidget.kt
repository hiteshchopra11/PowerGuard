package com.hackathon.powergaurd.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.hackathon.powergaurd.MainActivity
import com.hackathon.powergaurd.PowerGuardOptimizer
import com.hackathon.powergaurd.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask

class PowerGuardWidget : AppWidgetProvider() {

    companion object {
        private val mainScope = MainScope()
        private var currentPromptIndex = 0
        
        // Create a list of rotating placeholder texts for widget
        private val placeholders = listOf(
            "Which app is draining my battery?",
            "Save battery but keep WhatsApp running",
            "What's using my data?",
            "Going on a trip with 10% battery, need help"
        )
        
        // Timer for rotating placeholders in widget
        private val timer = Timer()
        private var timerTask: TimerTask? = null
        
        // Action constants for widget buttons
        const val ACTION_SAVE_BATTERY = "com.hackathon.powergaurd.ACTION_SAVE_BATTERY"
        const val ACTION_SAVE_DATA = "com.hackathon.powergaurd.ACTION_SAVE_DATA"
        
        fun startRotatingPlaceholders(context: Context) {
            if (timerTask != null) {
                timerTask?.cancel()
            }
            
            timerTask = object : TimerTask() {
                override fun run() {
                    currentPromptIndex = (currentPromptIndex + 1) % placeholders.size
                    // Update widget on UI thread
                    Handler(Looper.getMainLooper()).post {
                        mainScope.launch {
                            updatePromptText(context)
                        }
                    }
                }
            }
            
            timer.schedule(timerTask, 3000, 3000) // Initial delay 3s, repeat every 3s
        }
        
        private fun updatePromptText(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, PowerGuardWidget::class.java)
            )
            
            // Update all widgets
            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_power_guard)
                views.setTextViewText(R.id.widget_prompt_text, "Ask: ${placeholders[currentPromptIndex]}")
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Start rotating placeholders
        startRotatingPlaceholders(context)
        
        // Update each widget
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_SAVE_BATTERY -> {
                // Apply battery optimization
                val optimizer = PowerGuardOptimizer(context)
                optimizer.optimizeBatteryCharging()
                
                // Open the app with the specific prompt
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("PROMPT", "Optimize battery")
                }
                context.startActivity(openIntent)
                
                Log.d("PowerGuardWidget", "Battery optimization applied from widget")
            }
            ACTION_SAVE_DATA -> {
                // Apply data optimization
                val optimizer = PowerGuardOptimizer(context)
                optimizer.restrictBackgroundData("com.android.settings", true)
                
                // Open the app with the specific prompt
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("PROMPT", "Optimize data")
                }
                context.startActivity(openIntent)
                
                Log.d("PowerGuardWidget", "Data optimization applied from widget")
            }
        }
    }
    
    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        // Get the layout for the widget
        val views = RemoteViews(context.packageName, R.layout.widget_power_guard)
        
        // Set up click intent for the prompt text and title
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context, 0, mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_prompt_text, mainPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_title, mainPendingIntent)
        
        // Set up Save Battery button
        val batteryIntent = Intent(context, PowerGuardWidget::class.java).apply {
            action = ACTION_SAVE_BATTERY
        }
        val batteryPendingIntent = PendingIntent.getBroadcast(
            context, 1, batteryIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_save_battery_button, batteryPendingIntent)
        
        // Set up Save Data button
        val dataIntent = Intent(context, PowerGuardWidget::class.java).apply {
            action = ACTION_SAVE_DATA
        }
        val dataPendingIntent = PendingIntent.getBroadcast(
            context, 2, dataIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_save_data_button, dataPendingIntent)
        
        // Set the initial prompt text
        views.setTextViewText(R.id.widget_prompt_text, "Ask: ${placeholders[currentPromptIndex]}")
        
        // Update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
