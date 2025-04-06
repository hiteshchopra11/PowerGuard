package com.hackathon.powerguard.actionable

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.hackathon.powerguard.data.model.Actionable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.reflect.Method
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for the kill_app actionable type. Force stops applications that are consuming too many
 * resources.
 */
@Singleton
class KillAppHandler @Inject constructor(@ApplicationContext private val context: Context) :
    ActionableHandler {

    private val TAG = "KillAppHandler"

    override val actionableType: String = ActionableTypes.KILL_APP

    // Cache the forceStopPackage method
    private val forceStopMethod: Method? by lazy {
        try {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val method =
                ActivityManager::class.java.getMethod("forceStopPackage", String::class.java)
            method
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get forceStopPackage method", e)
            null
        }
    }

    override suspend fun handleActionable(actionable: Actionable): Boolean {
        val packageName = actionable.packageName

        if (packageName.isBlank()) {
            Log.e(TAG, "Cannot kill app: package name is blank")
            return false
        }

        try {
            Log.d(TAG, "Attempting to force stop app: $packageName")

            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

            val method = forceStopMethod ?: return false
            method.invoke(activityManager, packageName)

            Log.d(TAG, "Successfully force stopped app: $packageName")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to force stop app: ${e.message}", e)
            return false
        }
    }
}
