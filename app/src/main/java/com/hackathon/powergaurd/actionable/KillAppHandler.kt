package com.hackathon.powergaurd.actionable

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.hackathon.powergaurd.data.model.Actionable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for the kill_app actionable type.
 * 
 * This handler force stops applications that are consuming excessive system resources.
 * Force stopping an app completely terminates all its processes and services, which results
 * in immediate battery and resource savings. However, the app will need to cold start
 * the next time it's launched by the user.
 * 
 * Requires system or root privileges to function properly, as the forceStopPackage API
 * is protected and not accessible to regular applications.
 */
@Singleton
class KillAppHandler @Inject constructor(@ApplicationContext private val context: Context) :
    ActionableHandler {

    private val TAG = "KillAppHandler"

    override val actionableType: String = ActionableTypes.KILL_APP

    override suspend fun handleActionable(actionable: Actionable): Boolean {
        val packageName = actionable.packageName

        if (packageName.isBlank()) {
            Log.e(TAG, "Cannot kill app: package name is blank")
            return false
        }

        try {
            Log.d(TAG, "Attempting to force stop app: $packageName")

            val activityManager = ActionableUtils.getSystemService<ActivityManager>(
                context, Context.ACTIVITY_SERVICE
            ) ?: return false

            // Attempt to force stop the package using reflection
            val forceStopMethod = ActionableUtils.getMethodByReflection(
                ActivityManager::class.java.name,
                "forceStopPackage",
                String::class.java
            ) ?: return false

            forceStopMethod.invoke(activityManager, packageName)
            Log.d(TAG, "Successfully force stopped app: $packageName")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to force stop app: ${e.message}", e)
            return false
        }
    }
}
