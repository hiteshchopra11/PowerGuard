package com.hackathon.powergaurd.actionable.battery

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.hackathon.powergaurd.actionable.ActionableHandler
import com.hackathon.powergaurd.actionable.ActionableTypes
import com.hackathon.powergaurd.actionable.ActionableUtils
import com.hackathon.powergaurd.actionable.model.ActionableResult
import com.hackathon.powergaurd.data.model.Actionable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for force stopping applications.
 *
 * This handler uses the ActivityManager.forceStopPackage API to immediately
 * terminate all processes and services of a given app, providing immediate
 * battery and resource savings.
 */
@Singleton
class KillAppHandler @Inject constructor(
    private val context: Context
) : ActionableHandler {

    private val TAG = "KillAppHandler"

    override val actionableType: String = ActionableTypes.KILL_APP

    // Cache ActivityManager instance
    private val activityManager: ActivityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    override suspend fun execute(actionable: Actionable): ActionableResult {
        if (!canHandle(actionable)) {
            return ActionableResult.failure("Cannot handle actionable of type ${actionable.type}")
        }

        val packageName = actionable.packageName

        // Don't allow killing our own app
        if (packageName == context.packageName) {
            return ActionableResult.failure("Cannot kill own app")
        }

        return try {
            // Check if we have the required permissions
            if (!hasRequiredPermissions()) {
                return ActionableResult.failure(
                    "Missing required permissions to force stop apps",
                    mapOf("requiredPermission" to "android.permission.FORCE_STOP_PACKAGES")
                )
            }

            // Try to force stop the app using reflection
            val result = forceStopPackage(packageName)

            if (result) {
                ActionableResult.success(
                    "Successfully force stopped app: $packageName",
                    mapOf("packageName" to packageName)
                )
            } else {
                ActionableResult.failure(
                    "Failed to force stop app: $packageName",
                    mapOf("packageName" to packageName)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error killing app: $packageName", e)
            ActionableResult.fromException(e, mapOf("packageName" to packageName))
        }
    }

    override suspend fun revert(actionable: Actionable): ActionableResult {
        // We can't undo a force stop, so just return a message
        return ActionableResult.success(
            "Cannot undo force stop, app will restart when launched",
            mapOf("packageName" to actionable.packageName)
        )
    }

    override fun canHandle(actionable: Actionable): Boolean {
        return actionable.type == actionableType && actionable.packageName.isNotBlank()
    }

    /**
     * Checks if the app has the required permissions to force stop other apps.
     */
    private fun hasRequiredPermissions(): Boolean {
        return ActionableUtils.hasSystemPermissions(context)
    }

    /**
     * Force stops an app using the ActivityManager.forceStopPackage API.
     *
     * @param packageName The package name of the app to force stop
     * @return true if the operation was successful, false otherwise
     */
    private fun forceStopPackage(packageName: String): Boolean {
        return try {
            // Try using reflection to call the hidden forceStopPackage method
            ActionableUtils.callMethodSafely<Void>(
                activityManager,
                "forceStopPackage",
                arrayOf(String::class.java),
                packageName
            )

            // If we get here without an exception, assume it worked
            Log.d(TAG, "Force stopped package: $packageName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to force stop package: $packageName", e)
            false
        }
    }
}