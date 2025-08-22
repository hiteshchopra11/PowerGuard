package com.hackathon.powergaurd.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.hackathon.powergaurd.MainActivity
import com.hackathon.powergaurd.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

class PowerGuardWidget : AppWidgetProvider() {

    companion object {
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var currentPromptIndex = 0
        private var rotationJob: Job? = null

        // Create a list of rotating placeholder texts for widget
        private val placeholders = listOf(
            "Which app is draining my battery?",
            "Save battery but keep WhatsApp running",
            "What's using my data?",
            "Going on a trip with 10% battery, need help"
        )

        // Action constants for widget buttons
        const val ACTION_SAVE_BATTERY = "com.hackathon.powergaurd.ACTION_SAVE_BATTERY"
        const val ACTION_SAVE_DATA = "com.hackathon.powergaurd.ACTION_SAVE_DATA"

        fun startRotatingPlaceholders(context: Context) {
            // Cancel any existing rotation job
            rotationJob?.cancel()

            // Create a flow that emits every 3 seconds
            rotationJob = flow {
                while (true) {
                    delay(3000) // 3 second delay
                    currentPromptIndex = (currentPromptIndex + 1) % placeholders.size
                    emit(currentPromptIndex)
                }
            }.onEach {
                // Update widget on main thread
                withContext(Dispatchers.Main) {
                    updatePromptText(context)
                }
            }.launchIn(widgetScope)
        }

        private fun updatePromptText(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, PowerGuardWidget::class.java)
            )

            // Update all widgets
            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_power_guard)
                views.setTextViewText(
                    R.id.widget_prompt_text,
                    "Ask: ${placeholders[currentPromptIndex]}"
                )
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        fun stopRotatingPlaceholders() {
            rotationJob?.cancel()
            rotationJob = null
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Start rotating placeholders
        startRotatingPlaceholders(context)

        // Update each widget
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Clean up when the last widget instance is removed
        stopRotatingPlaceholders()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_SAVE_BATTERY -> {
                // Open the app with the specific prompt
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("PROMPT", "Optimize battery")
                }
                context.startActivity(openIntent)

                Log.d("PowerGuardWidget", "Battery optimization applied from widget")
            }

            ACTION_SAVE_DATA -> {
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

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
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
