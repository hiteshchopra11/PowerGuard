package com.hackathon.powergaurd.actionable

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.hackathon.powergaurd.data.model.Actionable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for the throttle_cpu_usage actionable type.
 * 
 * This handler limits CPU resources for specific apps to reduce power consumption
 * while still allowing them to run. Unlike killing apps, throttling strikes a balance
 * between battery savings and functionality.
 * 
 * Implementation details:
 * - Using Android process priority groups to control resource allocation
 * - Leveraging cgroups via root access as a fallback strategy
 * - Different throttling levels (1-10) map to different process priorities
 * 
 * Requires system privileges to use the setProcessGroup API or root access to
 * directly manipulate cgroups.
 */
@Singleton
class ThrottleCpuUsageHandler @Inject constructor(@ApplicationContext private val context: Context) :
    ActionableHandler {

    private val TAG = "ThrottleCpuHandler"

    override val actionableType: String = ActionableTypes.THROTTLE_CPU_USAGE

    override suspend fun handleActionable(actionable: Actionable): Boolean {
        val packageName = actionable.packageName

        if (packageName.isBlank()) {
            Log.e(TAG, "Cannot throttle CPU: package name is blank")
            return false
        }

        // Get throttle level, default to medium throttling (level 5)
        val throttleLevel = actionable.throttleLevel ?: 5
        
        try {
            Log.d(TAG, "Attempting to throttle CPU for app: $packageName at level $throttleLevel")

            // Get the UIDs for the package
            val uid = ActionableUtils.getUidForPackage(context, packageName)
            if (uid == -1) {
                Log.e(TAG, "Could not find UID for package $packageName")
                return false
            }
            
            // First approach: Try to use setProcessGroup to set process priority
            val pids = ActionableUtils.getPidsForUid(uid)
            if (pids.isNotEmpty()) {
                var success = false
                
                for (pid in pids) {
                    // Map throttleLevel (1-10) to Android process priorities
                    // ActivityManager.PROCESS_STATE_TOP to ActivityManager.PROCESS_STATE_CACHED_EMPTY
                    val priority = when {
                        throttleLevel >= 8 -> 20 // PROCESS_STATE_CACHED_EMPTY (most restricted)
                        throttleLevel >= 6 -> 19 // PROCESS_STATE_CACHED_ACTIVITY
                        throttleLevel >= 4 -> 11 // PROCESS_STATE_SERVICE
                        throttleLevel >= 2 -> 6  // PROCESS_STATE_IMPORTANT_FOREGROUND
                        else -> 2                // PROCESS_STATE_TOP (least restricted)
                    }
                    
                    // Try to set the process group using the utility method
                    if (ActionableUtils.setProcessGroup(context, pid, priority)) {
                        Log.d(TAG, "Successfully throttled CPU for process $pid of app $packageName")
                        success = true
                    }
                }
                
                if (success) {
                    return true
                }
            } else {
                Log.d(TAG, "No running processes found for app $packageName")
            }
            
            // Second approach: Try to use cgroups directly if we have root
            val cpusetPath = "/dev/cpuset/background/tasks"
            val pidsToWrite = ActionableUtils.getPidsForUid(uid) // Check again as app might have started
            
            if (pidsToWrite.isNotEmpty()) {
                // Build command to move processes to background cgroup
                val command = pidsToWrite.joinToString(" && ") { 
                    "echo $it > $cpusetPath"
                }
                
                if (ActionableUtils.executeRootCommand(command)) {
                    Log.d(TAG, "Successfully throttled CPU for $packageName using cgroups")
                    return true
                }
            }
            
            Log.i(TAG, "ACTION NEEDED: Unable to throttle CPU for app $packageName directly.")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to throttle CPU: ${e.message}", e)
            return false
        }
    }
} 