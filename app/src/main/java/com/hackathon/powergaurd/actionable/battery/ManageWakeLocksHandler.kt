package com.hackathon.powergaurd.actionable.battery

import android.app.AppOpsManager
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
 * Handler for controlling apps' ability to use wake locks.
 *
 * This handler uses the AppOpsManager to control whether apps can acquire
 * wake locks, which can significantly improve battery life by preventing
 * apps from keeping the device awake unnecessarily.
 */
@Singleton
class ManageWakeLocksHandler @Inject constructor(
    private val context: Context
) : ActionableHandler {

    private val TAG = "ManageWakeLocksHandler"

    override val actionableType: String = ActionableTypes.MANAGE_WAKE_LOCKS

    // AppOpsManager operation codes
    private val OP_WAKE_LOCK = 40 // AppOpsManager.OP_WAKE_LOCK

    // String constants for operation names
    private val OPSTR_WAKE_LOCK = "WAKE_LOCK" // AppOpsManager.OPSTR_WAKE_LOCK

    // AppOpsManager.MODE constants
    private val MODE_ALLOWED = 0  // AppOpsManager.MODE_ALLOWED
    private val MODE_IGNORED = 1  // AppOpsManager.MODE_IGNORED

    // Cache AppOpsManager instance
    private val appOpsManager: AppOpsManager by lazy {
        context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    }

    override suspend fun execute(actionable: Actionable): ActionableResult {
        if (!canHandle(actionable)) {
            return ActionableResult.failure("Cannot handle actionable of type ${actionable.type}")
        }

        val packageName = actionable.packageName

        // Don't restrict our own app
        if (packageName == context.packageName) {
            return ActionableResult.failure("Cannot restrict own app")
        }

        return try {
            // Check if we have the required permissions
            if (!hasRequiredPermissions()) {
                return ActionableResult.failure(
                    "Missing required permissions to manage wake locks",
                    mapOf("requiredPermission" to "android.permission.UPDATE_APP_OPS_STATS")
                )
            }

            val uid = ActionableUtils.getPackageUid(context, packageName)
            if (uid < 0) {
                return ActionableResult.failure(
                    "Could not find UID for package: $packageName",
                    mapOf("packageName" to packageName)
                )
            }

            // Check the current mode
            val currentMode = getWakeLockMode(uid)
            val newMode = if (actionable.enabled == false) MODE_ALLOWED else MODE_IGNORED

            // Set the new mode
            val success = setWakeLockMode(uid, newMode)

            if (success) {
                val modeString = if (newMode == MODE_IGNORED) "restricted" else "allowed"
                ActionableResult.success(
                    "Successfully ${modeString} wake locks for $packageName",
                    mapOf(
                        "packageName" to packageName,
                        "previousMode" to currentMode.toString(),
                        "newMode" to newMode.toString()
                    )
                )
            } else {
                ActionableResult.failure(
                    "Failed to set wake lock mode for $packageName",
                    mapOf("packageName" to packageName)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error managing wake locks for $packageName", e)
            ActionableResult.fromException(e, mapOf("packageName" to packageName))
        }
    }

    override suspend fun revert(actionable: Actionable): ActionableResult {
        // To revert, we just need to enable wake locks again if they were disabled
        if (actionable.enabled == false) {
            // Original action disabled wake locks, so we enable them
            return execute(actionable.copy(enabled = true))
        } else {
            // Original action enabled wake locks, so we disable them
            return execute(actionable.copy(enabled = false))
        }
    }

    override fun canHandle(actionable: Actionable): Boolean {
        return actionable.type == actionableType && actionable.packageName.isNotBlank()
    }

    /**
     * Checks if the app has the required permissions to manage wake locks.
     */
    private fun hasRequiredPermissions(): Boolean {
        // Basic permissions check
        val appOpsPermission = ActionableUtils.hasPermission(
            context,
            "android.permission.UPDATE_APP_OPS_STATS"
        )

        // Fall back to checking system app status
        return appOpsPermission || ActionableUtils.hasSystemPermissions(context)
    }

    /**
     * Gets the current wake lock mode for a UID.
     *
     * @param uid The UID to check
     * @return The current mode (MODE_ALLOWED or MODE_IGNORED)
     */
    private fun getWakeLockMode(uid: Int): Int {
        return try {
            ActionableUtils.callMethodSafely<Int>(
                appOpsManager,
                "unsafeCheckOpNoThrow",
                arrayOf(String::class.java, Int::class.java, String::class.java),
                OPSTR_WAKE_LOCK,
                uid,
                null
            ) ?: MODE_ALLOWED
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get wake lock mode for UID $uid", e)
            MODE_ALLOWED
        }
    }

    /**
     * Sets the wake lock mode for a UID.
     *
     * @param uid The UID to set the mode for
     * @param mode The mode to set (MODE_ALLOWED or MODE_IGNORED)
     * @return true if the operation was successful, false otherwise
     */
    private fun setWakeLockMode(uid: Int, mode: Int): Boolean {
        return try {
            ActionableUtils.callMethodSafely<Void>(
                appOpsManager,
                "setUidMode",
                arrayOf(String::class.java, Int::class.java, Int::class.java),
                OPSTR_WAKE_LOCK,
                uid,
                mode
            )

            Log.d(TAG, "Set wake lock mode for UID $uid to $mode")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set wake lock mode for UID $uid", e)
            false
        }
    }
}