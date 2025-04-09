package com.hackathon.powergaurd.actionable

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.hackathon.powergaurd.data.model.Actionable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for the manage_wake_locks actionable type.
 * 
 * This handler prevents excessive wake locks by restricting wake lock operations
 * for specific apps. Wake locks allow apps to keep the device awake to perform
 * background work, but when abused can lead to significant battery drain.
 * 
 * Implementation details:
 * - Uses AppOpsManager to deny WAKE_LOCK operations on a per-app basis
 * - Requires system privileges to function effectively
 * - Works on Android M (API 23) and above, where the AppOps API is available
 * 
 * Benefits:
 * - Targets one of the most common sources of battery drain
 * - More granular than app killing since core functionality can remain intact
 * - Preserves user experience while optimizing power consumption
 */
@Singleton
class ManageWakeLocksHandler @Inject constructor(@ApplicationContext private val context: Context) :
    ActionableHandler {

    private val TAG = "WakeLocksHandler"

    override val actionableType: String = ActionableTypes.MANAGE_WAKE_LOCKS

    override suspend fun handleActionable(actionable: Actionable): Boolean {
        val packageName = actionable.packageName

        if (packageName.isBlank()) {
            Log.e(TAG, "Cannot manage wake locks: package name is blank")
            return false
        }

        try {
            Log.d(TAG, "Attempting to restrict wake locks for app: $packageName")

            // Check if we're on a supported Android version
            if (!ActionableUtils.isAtLeastApi(Build.VERSION_CODES.M)) {
                Log.e(TAG, "Wake lock management requires Android M (API 23) or higher")
                return false
            }

            // Get the UID for the target package
            val uid = ActionableUtils.getUidForPackage(context, packageName)
            if (uid == -1) {
                Log.e(TAG, "Could not find UID for package $packageName")
                return false
            }

            // Get the AppOpsManager service
            val appOpsManager = ActionableUtils.getSystemService<AppOpsManager>(
                context, Context.APP_OPS_SERVICE
            ) ?: return false
            
            // Use reflection to set the app op mode
            // AppOpsManager.OP_WAKE_LOCK = 40
            // AppOpsManager.MODE_IGNORED = 1 (deny the operation)
            val appOpsClass = Class.forName(AppOpsManager::class.java.name)
            val setModeMethod = ActionableUtils.getMethodByReflection(
                appOpsClass.name,
                "setMode",
                Int::class.java,
                Int::class.java,
                String::class.java,
                Int::class.java
            ) ?: return false
            
            // Execute the operation
            setModeMethod.invoke(appOpsManager, 40, uid, packageName, 1)
            Log.d(TAG, "Successfully restricted wake locks for $packageName")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to manage wake locks: ${e.message}", e)
            return false
        }
    }
} 