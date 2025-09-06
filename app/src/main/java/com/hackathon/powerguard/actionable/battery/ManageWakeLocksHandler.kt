package com.hackathon.powerguard.actionable.battery

import android.app.AppOpsManager
import android.content.Context
import android.util.Log
import com.hackathon.powerguard.actionable.ActionableHandler
import com.hackathon.powerguard.actionable.ActionableTypes
import com.hackathon.powerguard.actionable.ActionableUtils
import com.hackathon.powerguard.actionable.model.ActionableResult
import com.hackathon.powerguard.data.model.Actionable
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

    override val actionableType: String = ActionableTypes.MANAGE_WAKE_LOCKS

    // Cache AppOpsManager instance
    private val appOpsManager: AppOpsManager by lazy {
        context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    }

    companion object {
        private const val TAG = "ManageWakeLocksHandler"

        // AppOpsManager operation codes
        private const val OP_WAKE_LOCK = 40 // AppOpsManager.OP_WAKE_LOCK

        // AppOpsManager.MODE constants
        private const val MODE_ALLOWED = 0  // AppOpsManager.MODE_ALLOWED
        private const val MODE_IGNORED = 1  // AppOpsManager.MODE_IGNORED
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
            val currentMode = getWakeLockMode(uid, packageName)
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
     * @param packageName The package name (needed for some API levels)
     * @return The current mode (MODE_ALLOWED or MODE_IGNORED)
     */
    private fun getWakeLockMode(uid: Int, packageName: String): Int {
        return try {
            // Try with numeric code first - we know this works from logs
            try {
                val result = ActionableUtils.callMethodSafely<Int>(
                    appOpsManager,
                    "checkOpNoThrow",
                    arrayOf(Int::class.java, Int::class.java, String::class.java),
                    OP_WAKE_LOCK,
                    uid,
                    packageName
                )

                if (result != null) {
                    Log.d(TAG, "Got wake lock mode using int op code")
                    return result
                }
            } catch (e: Exception) {
                Log.d(TAG, "checkOpNoThrow with int op failed: ${e.message}")
            }

            // Try the noteOp method as fallback
            try {
                val method = appOpsManager.javaClass.getDeclaredMethod(
                    "noteOp",
                    Int::class.java,
                    Int::class.java,
                    String::class.java
                )
                method.isAccessible = true
                val result = method.invoke(appOpsManager, OP_WAKE_LOCK, uid, packageName)

                if (result is Int) {
                    Log.d(TAG, "Got wake lock mode using noteOp through reflection")
                    return result
                }
            } catch (e: Exception) {
                Log.d(TAG, "noteOp failed: ${e.message}")
            }

            Log.d(TAG, "All wake lock mode check methods failed, defaulting to MODE_ALLOWED")
            MODE_ALLOWED

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get wake lock mode for UID $uid: ${e.message}", e)
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
        // Get the package name from the UID for logging
        val packageName = try {
            context.packageManager.getNameForUid(uid) ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }

        return try {
            // First, check if we have root access
            val hasRoot = checkRootAccess()

            if (hasRoot) {
                // Use shell command to set the app ops mode
                val modeStr = if (mode == MODE_ALLOWED) "allow" else "ignore"
                val command = "appops set --uid $uid WAKE_LOCK $modeStr"

                Log.d(TAG, "Executing root command: $command")
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                val exitCode = process.waitFor()

                if (exitCode == 0) {
                    Log.d(TAG, "Successfully set wake lock mode for $packageName (UID: $uid) to $mode using root")
                    return true
                } else {
                    Log.e(TAG, "Root command failed with exit code $exitCode")
                }
            } else {
                Log.d(TAG, "No root access available, trying alternative approaches")

                // Try using adb shell command if available
                try {
                    val command = "appops set --uid $uid WAKE_LOCK ${if (mode == MODE_ALLOWED) "allow" else "ignore"}"
                    val process = Runtime.getRuntime().exec(command)
                    val exitCode = process.waitFor()

                    if (exitCode == 0) {
                        Log.d(TAG, "Successfully set wake lock mode for $packageName to $mode using adb shell")
                        return true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Shell command failed: ${e.message}")
                }

                // If we reached here, neither root nor adb worked
                Log.e(TAG, "Failed to set wake lock mode for $packageName: No permission")
                return false
            }

            false
        } catch (e: Exception) {
            Log.e(TAG, "Error setting wake lock mode for $packageName: ${e.message}", e)
            false
        }
    }

    /**
     * Checks if the application has root access.
     */
    private fun checkRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val exitValue = process.waitFor()
            exitValue == 0
        } catch (e: Exception) {
            Log.d(TAG, "Root check failed: ${e.message}")
            false
        }
    }
}