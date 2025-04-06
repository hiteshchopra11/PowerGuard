package com.hackathon.powergaurd.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.hackathon.powergaurd.MainActivity
import com.hackathon.powergaurd.PowerGuardOptimizer
import com.hackathon.powergaurd.R
import com.hackathon.powergaurd.collector.UsageDataCollector
import com.hackathon.powergaurd.domain.usecase.AnalyzeDeviceDataUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Widget provider for PowerGuard home screen widget. */
@AndroidEntryPoint
class PowerGuardWidget : AppWidgetProvider() {

    @Inject
    lateinit var powerGuardOptimizer: PowerGuardOptimizer
    
    @Inject
    lateinit var usageDataCollector: UsageDataCollector
    
    @Inject
    lateinit var analyzeDeviceDataUseCase: AnalyzeDeviceDataUseCase

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    companion object {
        // Action constants for widget button intents
        const val ACTION_SAVE_BATTERY = "com.hackathon.powergaurd.ACTION_SAVE_BATTERY"
        const val ACTION_SAVE_DATA = "com.hackathon.powergaurd.ACTION_SAVE_DATA"
        const val ACTION_OPTIMIZE = "com.hackathon.powergaurd.ACTION_OPTIMIZE"
        
        /** Sends broadcast to update all PowerGuard widgets. */
        fun updateAllWidgets(context: Context, statusText: String? = null) {
            val intent = Intent(context, PowerGuardWidget::class.java)
            intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds =
                appWidgetManager.getAppWidgetIds(
                    android.content.ComponentName(context, PowerGuardWidget::class.java)
                )

            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            context.sendBroadcast(intent)

            // Also update widgets directly
            val widgetProvider = PowerGuardWidget()
            for (appWidgetId in appWidgetIds) {
                widgetProvider.updateWidget(context, appWidgetManager, appWidgetId, statusText)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_SAVE_BATTERY -> {
                coroutineScope.launch {
                    // Show status while operating
                    updateAllWidgets(context, "Saving battery...")
                    
                    // Execute battery saving
                    powerGuardOptimizer.saveBattery()
                    
                    // Update widgets with success status
                    updateAllWidgets(context, "Battery saving applied")
                    
                    // Analyze data in background
                    try {
                        val deviceData = usageDataCollector.collectDeviceData()
                        analyzeDeviceDataUseCase(deviceData)
                    } catch (e: Exception) {
                        // Log error but don't update UI since this is background work
                    }
                }
            }
            ACTION_SAVE_DATA -> {
                coroutineScope.launch {
                    // Show status while operating
                    updateAllWidgets(context, "Saving data...")
                    
                    try {
                        // Get data and find high data usage apps
                        val deviceData = usageDataCollector.collectDeviceData()
                        val highDataApp = deviceData.apps
                            .sortedByDescending { it.dataUsage.background }
                            .firstOrNull()
                        
                        // Execute data saving
                        if (highDataApp != null) {
                            powerGuardOptimizer.saveData(highDataApp.packageName, true)
                        } else {
                            // Apply general data saving if no apps found
                            powerGuardOptimizer.saveData("com.android.settings", true)
                        }
                        
                        // Update widgets with success status
                        updateAllWidgets(context, "Data saving applied")
                        
                        // Analyze data in background
                        analyzeDeviceDataUseCase(deviceData)
                    } catch (e: Exception) {
                        // Update widgets with error status
                        updateAllWidgets(context, "Error: Could not save data")
                    }
                }
            }
            ACTION_OPTIMIZE -> {
                coroutineScope.launch {
                    // Show status while operating
                    updateAllWidgets(context, "Optimizing device...")
                    
                    try {
                        // Collect device data
                        val deviceData = usageDataCollector.collectDeviceData()
                        
                        // Apply battery and data savings
                        powerGuardOptimizer.saveBattery()
                        
                        // Find high data apps
                        val highDataApp = deviceData.apps
                            .sortedByDescending { it.dataUsage.background }
                            .firstOrNull()
                            
                        if (highDataApp != null) {
                            powerGuardOptimizer.saveData(highDataApp.packageName, true)
                        }
                        
                        // Analyze the data
                        analyzeDeviceDataUseCase(deviceData)
                        
                        // Update widgets with success status
                        updateAllWidgets(context, "Device optimized successfully")
                    } catch (e: Exception) {
                        // Update widgets with error status
                        updateAllWidgets(context, "Error during optimization")
                    }
                }
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Update each widget
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId, null)
        }
    }

    /**
     * Updates the widget with the latest status.
     *
     * @param context The context
     * @param appWidgetManager The widget manager
     * @param appWidgetId The widget ID
     * @param statusText Optional status text to display
     */
    fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        statusText: String?
    ) {
        // Create remote views
        val views = RemoteViews(context.packageName, R.layout.widget_power_guard)

        // Set up click intent to open the app
        val mainIntent = Intent(context, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                mainIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        views.setOnClickPendingIntent(R.id.widget_title, pendingIntent)
        
        // Set up Optimize button
        val optimizeIntent = Intent(context, PowerGuardWidget::class.java)
        optimizeIntent.action = ACTION_OPTIMIZE
        val optimizePendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            optimizeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_optimize_button, optimizePendingIntent)
        
        // Set up Save Battery button
        val saveBatteryIntent = Intent(context, PowerGuardWidget::class.java)
        saveBatteryIntent.action = ACTION_SAVE_BATTERY
        val saveBatteryPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            saveBatteryIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_save_battery_button, saveBatteryPendingIntent)
        
        // Set up Save Data button
        val saveDataIntent = Intent(context, PowerGuardWidget::class.java)
        saveDataIntent.action = ACTION_SAVE_DATA
        val saveDataPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            saveDataIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_save_data_button, saveDataPendingIntent)
        
        // Set up the Prompt TextView to open the MainActivity
        val dashboardIntent = Intent(context, MainActivity::class.java)
        // We'll add a flag to indicate the dashboard should be opened
        dashboardIntent.putExtra("OPEN_DASHBOARD", true)
        dashboardIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        
        val dashboardPendingIntent = PendingIntent.getActivity(
            context,
            3, // Different request code to avoid conflicts
            dashboardIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_prompt_text, dashboardPendingIntent)

        // Update status text if provided
        if (statusText != null) {
            views.setTextViewText(R.id.widget_status, statusText)
        } else {
            views.setTextViewText(R.id.widget_status, "Ready")
        }

        // Update widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
