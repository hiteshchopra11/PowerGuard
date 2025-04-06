package com.hackathon.powergaurd.actionable

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.hackathon.powergaurd.data.model.Actionable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.reflect.Method
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppInactiveHandler @Inject constructor(
    @ApplicationContext private val context: Context
) : ActionableHandler {

    private val TAG = "AppInactiveHandler"
    override val actionableType: String = ActionableTypes.MARK_APP_INACTIVE

    private val setAppInactiveMethod: Method? by lazy {
        try {
            UsageStatsManager::class.java.getMethod(
                "setAppInactive",
                String::class.java,
                Boolean::class.javaPrimitiveType
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get setAppInactive method", e)
            null
        }
    }

    private val setAppStandbyBucketMethod: Method? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                UsageStatsManager::class.java.getMethod(
                    "setAppStandbyBucket",
                    String::class.java,
                    Int::class.javaPrimitiveType
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get setAppStandbyBucket method", e)
                null
            }
        } else null
    }

    private fun getUsageStatsManager(): UsageStatsManager? {
        return try {
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get UsageStatsManager", e)
            null
        }
    }

    override suspend fun handleActionable(actionable: Actionable): Boolean {
        val packageName = actionable.packageName
        if (packageName.isBlank()) {
            Log.e(TAG, "Cannot mark app as inactive: package name is blank")
            return false
        }

        Log.d(TAG, "Attempting to mark app $packageName as inactive")

        try {
            setAppInactiveMethod?.let { method ->
                getUsageStatsManager()?.let { usm ->
                    method.invoke(usm, packageName, true)
                    Log.d(TAG, "Successfully marked app $packageName as inactive using reflection")
                    return true
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return applyViaStandbyBucket(packageName)
            }

            Log.i(
                TAG, "ACTION NEEDED: Unable to mark app as inactive directly. " +
                        "User should manually restrict the app $packageName in Settings > Apps"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error handling mark app inactive action", e)
        }

        return false
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun applyViaStandbyBucket(packageName: String): Boolean {
        try {
            Log.d(TAG, "Attempting to use standby bucket as fallback for app $packageName")

            val usm = getUsageStatsManager() ?: return false
            val method = setAppStandbyBucketMethod ?: return false

            try {
                method.invoke(usm, packageName, 50) // BUCKET_RESTRICTED = 50
                Log.d(TAG, "Successfully set app $packageName to RESTRICTED bucket")
                return true
            } catch (e: Exception) {
                try {
                    method.invoke(usm, packageName, 40) // BUCKET_RARE = 40
                    Log.d(TAG, "Successfully set app $packageName to RARE bucket")
                    return true
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed to set app to restrictive bucket: ${e2.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying via standby bucket", e)
        }

        return false
    }
}