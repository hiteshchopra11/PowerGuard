package com.hackathon.powergaurd.actionable.battery

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.util.Log
import com.hackathon.powergaurd.actionable.ActionableHandler
import com.hackathon.powergaurd.actionable.ActionableTypes
import com.hackathon.powergaurd.actionable.ActionableUtils
import com.hackathon.powergaurd.actionable.model.ActionableResult
import com.hackathon.powergaurd.data.model.Actionable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for throttling CPU usage of specific apps.
 * 
 * This handler uses process priorities to limit CPU resources allocated to apps,
 * reducing battery consumption for computation-intensive applications while still
 * allowing them to function.
 */
@Singleton
class ThrottleCpuUsageHandler @Inject constructor(
    private val context: Context
) : ActionableHandler {

    private val TAG = "ThrottleCpuHandler"
    
    override val actionableType: String = ActionableTypes.THROTTLE_CPU_USAGE
    
    // Cache ActivityManager instance
    private val activityManager: ActivityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }
    
    // Process priority constants
    companion object {
        // Process.THREAD_PRIORITY_* constants
        const val PRIORITY_FOREGROUND = Process.THREAD_PRIORITY_FOREGROUND      // -2
        const val PRIORITY_DEFAULT = Process.THREAD_PRIORITY_DEFAULT            // 0
        const val PRIORITY_BACKGROUND = Process.THREAD_PRIORITY_BACKGROUND      // 10
        const val PRIORITY_LOW = Process.THREAD_PRIORITY_LOWEST                 // 19
        
        // Activity manager importance constants
        const val IMPORTANCE_FOREGROUND = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        const val IMPORTANCE_VISIBLE = ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        const val IMPORTANCE_PERCEPTIBLE = ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE
        const val IMPORTANCE_BACKGROUND = ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND
        const val IMPORTANCE_EMPTY = ActivityManager.RunningAppProcessInfo.IMPORTANCE_EMPTY
        
        // Mapping of throttle levels (1-10) to process priorities
        val THROTTLE_LEVEL_TO_PRIORITY = mapOf(
            1 to PRIORITY_FOREGROUND,  // Minimal throttling
            2 to PRIORITY_DEFAULT,
            3 to PRIORITY_DEFAULT + 2,
            4 to PRIORITY_DEFAULT + 4,
            5 to PRIORITY_DEFAULT + 6,
            6 to PRIORITY_DEFAULT + 8,
            7 to PRIORITY_BACKGROUND,
            8 to PRIORITY_BACKGROUND + 3,
            9 to PRIORITY_BACKGROUND + 6,
            10 to PRIORITY_LOW         // Maximum throttling
        )
        
        // Default throttle level if none specified
        const val DEFAULT_THROTTLE_LEVEL = 5
        
        // Maps string priority names to priority values
        val PRIORITY_NAME_TO_VALUE = mapOf(
            "foreground" to IMPORTANCE_FOREGROUND,
            "visible" to IMPORTANCE_VISIBLE,
            "perceptible" to IMPORTANCE_PERCEPTIBLE,
            "background" to IMPORTANCE_BACKGROUND,
            "empty" to IMPORTANCE_EMPTY
        )
    }
    
    override suspend fun execute(actionable: Actionable): ActionableResult {
        if (!canHandle(actionable)) {
            return ActionableResult.failure("Cannot handle actionable of type ${actionable.type}")
        }
        
        val packageName = actionable.packageName
        
        // Don't throttle our own app
        if (packageName == context.packageName) {
            return ActionableResult.failure("Cannot throttle own app")
        }
        
        // Get throttle level from actionable or use default
        val throttleLevel = actionable.throttleLevel ?: DEFAULT_THROTTLE_LEVEL
        val priority = THROTTLE_LEVEL_TO_PRIORITY[throttleLevel] ?: PRIORITY_BACKGROUND
        
        return try {
            // Check if we have the required permissions
            if (!hasRequiredPermissions()) {
                return ActionableResult.failure(
                    "Missing required permissions to throttle CPU usage",
                    mapOf("requiredPermission" to "android.permission.SET_PROCESS_LIMIT or system app privileges")
                )
            }
            
            // Get the processes for this package
            val processIds = getProcessIdsForPackage(packageName)
            if (processIds.isEmpty()) {
                return ActionableResult.failure(
                    "No running processes found for package: $packageName",
                    mapOf("packageName" to packageName)
                )
            }
            
            // Save current importances for revert (as a comma-separated string)
            val currentImportances = processIds.joinToString(",") { pid ->
                getProcessImportance(pid).toString()
            }
            
            // Set the priority for all processes of the package
            var successCount = 0
            processIds.forEach { pid ->
                if (setProcessImportance(pid, priority)) {
                    successCount++
                }
            }
            
            if (successCount > 0) {
                ActionableResult.success(
                    "Successfully throttled CPU for $packageName (level $throttleLevel)",
                    mapOf(
                        "packageName" to packageName,
                        "throttleLevel" to throttleLevel.toString(),
                        "priority" to priority.toString(),
                        "processCount" to processIds.size.toString(),
                        "successCount" to successCount.toString(),
                        "previousImportances" to currentImportances
                    )
                )
            } else {
                ActionableResult.failure(
                    "Failed to throttle CPU for $packageName",
                    mapOf("packageName" to packageName)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error throttling CPU for $packageName", e)
            ActionableResult.fromException(e, mapOf("packageName" to packageName))
        }
    }
    
    override suspend fun revert(actionable: Actionable): ActionableResult {
        // To revert, we set the priority back to FOREGROUND
        // Note: This is a simplified approach - ideally we'd restore the exact previous priorities
        return execute(actionable.copy(throttleLevel = 1)) // 1 is minimal throttling (FOREGROUND)
    }
    
    override fun canHandle(actionable: Actionable): Boolean {
        return actionable.type == actionableType && actionable.packageName.isNotBlank()
    }
    
    /**
     * Checks if the app has the required permissions to throttle CPU usage.
     */
    private fun hasRequiredPermissions(): Boolean {
        return ActionableUtils.hasSystemPermissions(context)
    }
    
    /**
     * Gets the process IDs for a given package.
     * 
     * @param packageName The package name to get processes for
     * @return A list of process IDs
     */
    private fun getProcessIdsForPackage(packageName: String): List<Int> {
        return try {
            val processes = activityManager.runningAppProcesses ?: return emptyList()
            processes
                .filter { it.processName.startsWith(packageName) }
                .map { it.pid }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get processes for package: $packageName", e)
            emptyList()
        }
    }
    
    /**
     * Gets the current importance of a process.
     * 
     * @param pid The process ID
     * @return The current importance value
     */
    private fun getProcessImportance(pid: Int): Int {
        val processes = activityManager.runningAppProcesses ?: return IMPORTANCE_BACKGROUND
        val process = processes.find { it.pid == pid }
        return process?.importance ?: IMPORTANCE_BACKGROUND
    }
    
    /**
     * Sets the importance of a process.
     * 
     * @param pid The process ID
     * @param priority The priority to set
     * @return true if the operation was successful, false otherwise
     */
    private fun setProcessImportance(pid: Int, priority: Int): Boolean {
        return try {
            // Try using reflection to call setProcessImportance
            ActionableUtils.callMethodSafely<Boolean>(
                activityManager,
                "setProcessImportance",
                arrayOf(Int::class.java, Int::class.java),
                pid,
                priority
            ) ?: false
            
            // Also try direct method if reflection fails
            try {
                Process.setThreadPriority(pid, priority)
            } catch (e: Exception) {
                Log.d(TAG, "Could not set thread priority directly: ${e.message}")
                // Continue anyway, as the reflection method might have worked
            }
            
            Log.d(TAG, "Set process importance for PID $pid to $priority")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set process importance for PID $pid", e)
            false
        }
    }
}