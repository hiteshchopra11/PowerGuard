package com.hackathon.powergaurd.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.hackathon.powergaurd.MainActivity
import com.hackathon.powergaurd.R
import com.hackathon.powergaurd.data.repository.ActionHistoryItem
import com.hackathon.powergaurd.data.repository.ActionHistoryRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Widget provider for PowerGuard home screen widget. */
@AndroidEntryPoint
class PowerGuardWidget : AppWidgetProvider() {

    @Inject
    lateinit var actionHistoryRepository: ActionHistoryRepository

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

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
     * Updates the widget with the latest action history.
     *
     * @param context The context
     * @param appWidgetManager The widget manager
     * @param appWidgetId The widget ID
     * @param loadingText Optional loading text to display
     */
    fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        loadingText: String?
    ) {
        // Create remote views
        val views = RemoteViews(context.packageName, R.layout.widget_power_guard)

        // Set up click intent to open the app
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        views.setOnClickPendingIntent(R.id.widget_title, pendingIntent)

        // Show loading state if requested
        if (loadingText != null) {
            views.setViewVisibility(R.id.widget_loading_text, View.VISIBLE)
            views.setViewVisibility(R.id.widget_summary_text, View.GONE)
            views.setTextViewText(R.id.widget_loading_text, loadingText)
            views.setTextViewText(R.id.widget_status, "Working...")

            // Update widget immediately with loading state
            appWidgetManager.updateAppWidget(appWidgetId, views)

            // Fetch data in background
            coroutineScope.launch {
                val history = getRecentActionHistory()
                val summaryText = formatActionHistorySummary(history)

                // Update with data on main thread
                views.setViewVisibility(R.id.widget_loading_text, View.GONE)
                views.setViewVisibility(R.id.widget_summary_text, View.VISIBLE)
                views.setTextViewText(R.id.widget_summary_text, summaryText)
                views.setTextViewText(R.id.widget_status, "Active")

                // Update widget again with data
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        } else {
            // Fetch data in background
            coroutineScope.launch {
                val history = getRecentActionHistory()
                val summaryText = formatActionHistorySummary(history)

                // Update with data
                views.setViewVisibility(R.id.widget_loading_text, View.GONE)
                views.setViewVisibility(R.id.widget_summary_text, View.VISIBLE)
                views.setTextViewText(R.id.widget_summary_text, summaryText)
                views.setTextViewText(R.id.widget_status, "Active")

                // Update widget
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    /** Gets the most recent action history entries. */
    private suspend fun getRecentActionHistory(): List<ActionHistoryItem> {
        return try {
            actionHistoryRepository.historyItems.firstOrNull() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Formats a list of action history entries into a human-readable summary. */
    private fun formatActionHistorySummary(history: List<ActionHistoryItem>): String {
        if (history.isEmpty()) {
            return "No recent optimizations"
        }

        return history.joinToString(separator = "\n\n") { item -> item.summary }
    }

    companion object {
        /** Sends broadcast to update all PowerGuard widgets. */
        fun updateAllWidgets(context: Context, loadingText: String? = null) {
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
                widgetProvider.updateWidget(context, appWidgetManager, appWidgetId, loadingText)
            }
        }
    }
}
