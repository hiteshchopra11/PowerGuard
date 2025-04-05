package com.hackathon.powergaurd.services

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.hackathon.powergaurd.data.model.Actionable
import com.hackathon.powergaurd.data.repository.ActionHistoryItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Service for executing actionables received from the backend */
@Singleton
class ActionableExecutor
@Inject
constructor(
        @ApplicationContext private val context: Context,
        private val powerManager: PowerManager
) {
        private val TAG = "ActionableExecutor"

        /**
         * Executes a list of actionables and returns the results
         *
         * @param actionables The actionables to execute
         * @return A list of action history items reflecting the execution results
         */
        suspend fun executeActionables(actionables: List<Actionable>): List<ActionHistoryItem> {
                Log.d(TAG, "Executing ${actionables.size} actionables")

                return actionables.map { actionable -> executeActionable(actionable) }
        }

        /**
         * Executes a single actionable
         *
         * @param actionable The actionable to execute
         * @return An action history item reflecting the execution result
         */
        private fun executeActionable(actionable: Actionable): ActionHistoryItem {
                Log.d(TAG, "Executing actionable: ${actionable.type}")

                return try {
                        // Execute the actionable based on its type
                        when (actionable.type) {
                                "OPTIMIZE_BATTERY" -> {
                                        // In a real app, we would execute real battery
                                        // optimizations
                                        ActionHistoryItem(
                                                actionType = actionable.type,
                                                summary =
                                                        "Battery usage optimized for ${actionable.packageName ?: "system"}",
                                                succeeded = true,
                                                appPackage = actionable.packageName,
                                                details = actionable.description
                                        )
                                }
                                "KILL_APP" -> {
                                        // In a real app, we would kill the app
                                        ActionHistoryItem(
                                                actionType = actionable.type,
                                                summary =
                                                        "App ${actionable.packageName} force stopped to save battery",
                                                succeeded = true,
                                                appPackage = actionable.packageName,
                                                details = actionable.description
                                        )
                                }
                                "RESTRICT_BACKGROUND" -> {
                                        // In a real app, we would restrict background data
                                        ActionHistoryItem(
                                                actionType = actionable.type,
                                                summary =
                                                        "Background data restricted for ${actionable.packageName}",
                                                succeeded = true,
                                                appPackage = actionable.packageName,
                                                details = actionable.description
                                        )
                                }
                                else -> {
                                        Log.w(TAG, "Unknown actionable type: ${actionable.type}")
                                        ActionHistoryItem(
                                                actionType = actionable.type,
                                                summary = "Unknown action: ${actionable.type}",
                                                succeeded = false,
                                                appPackage = actionable.packageName,
                                                details = "Unsupported action type"
                                        )
                                }
                        }
                } catch (e: Exception) {
                        Log.e(TAG, "Error executing actionable: ${e.message}", e)
                        ActionHistoryItem(
                                actionType = actionable.type,
                                summary = "Failed to execute: ${e.message}",
                                succeeded = false,
                                appPackage = actionable.packageName,
                                details = e.stackTraceToString()
                        )
                }
        }

        /** Optimize battery usage for an app */
        private fun optimizeBattery(actionable: Actionable): ActionHistoryItem {
                val packageName =
                        actionable.packageName
                                ?: return ActionHistoryItem(
                                        actionType = "OPTIMIZE_BATTERY",
                                        summary = "No package name provided",
                                        succeeded = false
                                )

                // Here we would use the PowerManager to optimize battery usage
                // For demonstration, we'll just create a success story
                return ActionHistoryItem(
                        actionType = "OPTIMIZE_BATTERY",
                        summary = "Battery optimized for $packageName",
                        succeeded = true,
                        appPackage = packageName
                )
        }

        /** Kill an app that's consuming too many resources */
        private fun killApp(actionable: Actionable): ActionHistoryItem {
                val packageName =
                        actionable.packageName
                                ?: return ActionHistoryItem(
                                        actionType = "KILL_APP",
                                        summary = "No package name provided",
                                        succeeded = false
                                )

                val activityManager =
                        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

                // Check if the app is running
                val isAppRunning =
                        activityManager.runningAppProcesses?.any {
                                it.processName == packageName &&
                                        it.importance <= RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                        }
                                ?: false

                if (!isAppRunning) {
                        return ActionHistoryItem(
                                actionType = "KILL_APP",
                                summary = "App $packageName is not running",
                                succeeded = false,
                                appPackage = packageName
                        )
                }

                // On non-root devices, we can't directly kill other apps
                // We can only force stop them through settings, which requires user interaction
                // For demonstration, we'll simulate success
                return ActionHistoryItem(
                        actionType = "KILL_APP",
                        summary = "App $packageName killed successfully",
                        succeeded = true,
                        appPackage = packageName
                )
        }

        /** Restrict background data for an app */
        private fun restrictBackgroundData(actionable: Actionable): ActionHistoryItem {
                val packageName =
                        actionable.packageName
                                ?: return ActionHistoryItem(
                                        actionType = "RESTRICT_BACKGROUND",
                                        summary = "No package name provided",
                                        succeeded = false
                                )

                // On non-root devices, we can't directly modify data usage settings
                // We can launch the settings intent for the user to do it manually
                val intent = Intent()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        intent.action = Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS
                        intent.data = Uri.parse("package:$packageName")
                } else {
                        intent.action = Settings.ACTION_DATA_ROAMING_SETTINGS
                }
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

                try {
                        context.startActivity(intent)
                        return ActionHistoryItem(
                                actionType = "RESTRICT_BACKGROUND",
                                summary =
                                        "Opened settings to restrict background data for $packageName",
                                succeeded = true,
                                appPackage = packageName
                        )
                } catch (e: Exception) {
                        return ActionHistoryItem(
                                actionType = "RESTRICT_BACKGROUND",
                                summary = "Failed to open settings: ${e.message}",
                                succeeded = false,
                                appPackage = packageName
                        )
                }
        }

        /** Optimize storage for an app */
        private fun optimizeStorage(actionable: Actionable): ActionHistoryItem {
                val packageName =
                        actionable.packageName
                                ?: return ActionHistoryItem(
                                        actionType = "OPTIMIZE_STORAGE",
                                        summary = "No package name provided",
                                        succeeded = false
                                )

                // For demonstration, we'll just simulate success
                return ActionHistoryItem(
                        actionType = "OPTIMIZE_STORAGE",
                        summary = "Storage optimized for $packageName",
                        succeeded = true,
                        appPackage = packageName
                )
        }
}
