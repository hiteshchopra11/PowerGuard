package com.hackathon.powergaurd.actionable

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build // Ensure this is present
import android.util.Log
import androidx.annotation.RequiresApi
import com.hackathon.powergaurd.models.ActionResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.reflect.Method
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for the MARK_APP_INACTIVE actionable type. Marks apps as inactive to reduce background activity.
 * Uses reflection to access hidden APIs in the UsageStatsManager.
 */
@Singleton
class AppInactiveHandler @Inject constructor(@ApplicationContext private val context: Context) :
    ActionableHandler {

    private val TAG = "AppInactiveHandler"

    override val actionableType: String = ActionableTypes.MARK_APP_INACTIVE

    // Cache the setAppInactive method using reflection
    private val setAppInactiveMethod: Method? by lazy {
        try {
            // Try to get the method by reflection (it's hidden API)
            val usageStatsManager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val method = UsageStatsManager::class.java.getMethod(
                "setAppInactive",
                String::class.java,
                Boolean::class.javaPrimitiveType
            )
            method
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get setAppInactive method", e)
            null
        }
    }

    // Cache the setAppStandbyBucket method for fallback
    private val setAppStandbyBucketMethod: Method? by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return@lazy null
        }

        try {
            val usageStatsManager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val method = UsageStatsManager::class.java.getMethod(
                "setAppStandbyBucket",
                String::class.java,
                Int::class.javaPrimitiveType
            )
            method
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get setAppStandbyBucket method", e)
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getUsageStatsManager(): UsageStatsManager? {
        return try {
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get UsageStatsManager", e)
            null
        }
    }

    override suspend fun handleActionable(actionable: ActionResponse.Actionable): Boolean {
        val packageName = actionable.app ?: return false
        val shouldBeInactive = actionable.enabled ?: true

        if (packageName.isBlank()) {
            Log.e(TAG, "Cannot mark app as inactive: package name is blank")
            return false
        }

        // Check API level - UsageStatsManager is available from Android M (API 23)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Log.d(TAG, "UsageStatsManager not available below Android M (API 23)")
            return false
        }

        try {
            Log.d(
                TAG,
                "Attempting to mark app $packageName as ${if (shouldBeInactive) "inactive" else "active"}"
            )

            // Use reflection to access the hidden API
            val method = setAppInactiveMethod
            if (method != null) {
                try {
                    val usageStatsManager = getUsageStatsManager() ?: return false
                    method.invoke(usageStatsManager, packageName, shouldBeInactive)

                    Log.d(
                        TAG,
                        "Successfully marked app $packageName as ${if (shouldBeInactive) "inactive" else "active"}"
                    )
                    return true
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "Failed to mark app as ${if (shouldBeInactive) "inactive" else "active"} using reflection: ${e.message}"
                    )
                }
            }

            // If reflection method failed, fall back to standby bucket if available
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return applyViaStandbyBucket(packageName, shouldBeInactive)
            }

            // Both methods failed
            Log.i(
                TAG, "ACTION NEEDED: Unable to mark app as inactive directly. " +
                        "User should manually restrict the app $packageName in Settings > Apps"
            )

            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error handling mark app inactive action", e)
            return false
        }
    }

    /**
     * Fallback implementation using app standby buckets if available on API 28+
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun applyViaStandbyBucket(packageName: String, shouldBeInactive: Boolean): Boolean {
        try {
            Log.d(TAG, "Attempting to use standby bucket as fallback for app $packageName")

            val usm = getUsageStatsManager() ?: return false
            val bucketMethod = setAppStandbyBucketMethod ?: return false

            // If we want the app to be inactive, set it to BUCKET_RARE (or BUCKET_RESTRICTED if possible)
            if (shouldBeInactive) {
                try {
                    // Try for BUCKET_RESTRICTED (most restrictive)
                    bucketMethod.invoke(usm, packageName, 50) // BUCKET_RESTRICTED = 50
                    Log.d(TAG, "Successfully set app $packageName to RESTRICTED bucket as fallback")
                    return true
                } catch (e: Exception) {
                    try {
                        // Fall back to BUCKET_RARE
                        bucketMethod.invoke(usm, packageName, 40) // BUCKET_RARE = 40
                        Log.d(TAG, "Successfully set app $packageName to RARE bucket as fallback")
                        return true
                    } catch (e2: Exception) {
                        Log.e(TAG, "Failed to set app to restrictive bucket: ${e2.message}")
                    }
                }
            } else {
                // If we want the app to be active, set it to BUCKET_ACTIVE
                try {
                    bucketMethod.invoke(usm, packageName, 10) // BUCKET_ACTIVE = 10
                    Log.d(TAG, "Successfully set app $packageName to ACTIVE bucket as fallback")
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set app to active bucket: ${e.message}")
                }
            }

            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error applying via standby bucket", e)
            return false
        }
    }
}