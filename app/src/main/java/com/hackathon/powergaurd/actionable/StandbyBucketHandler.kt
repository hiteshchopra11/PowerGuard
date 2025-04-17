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

/**
 * Handler for the SET_STANDBY_BUCKET actionable type. Moves apps to different standby buckets
 * to reduce their background activity levels.
 */
@Singleton
class StandbyBucketHandler @Inject constructor(@ApplicationContext private val context: Context) :
    ActionableHandler {

    private val TAG = "StandbyBucketHandler"

    override val actionableType: String = ActionableTypes.SET_STANDBY_BUCKET

    /**
     * Standby bucket constants (matching UsageStatsManager constants)
     */
    object BucketTypes {
        const val BUCKET_ACTIVE = 10
        const val BUCKET_WORKING_SET = 20
        const val BUCKET_FREQUENT = 30
        const val BUCKET_RARE = 40
        const val BUCKET_RESTRICTED = 50
    }

    // Cache the setAppStandbyBucket method
    private val setAppStandbyBucketMethod: Method? by lazy {
        try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
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

    private fun getUsageStatsManager(): UsageStatsManager? {
        return try {
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get UsageStatsManager", e)
            null
        }
    }

    override suspend fun handleActionable(actionable: Actionable): Boolean {
        val packageName = actionable.packageName ?: return false

        if (packageName.isBlank()) {
            Log.e(TAG, "Cannot set standby bucket: package name is blank")
            return false
        }

        // Determine target bucket from the action parameters
        val bucketName = actionable.newMode ?: "restricted"
        val bucketType = getBucketTypeFromName(bucketName)

        // Since we're on Android 15+, we can directly use the API
        return try {
            val usageStatsManager = getUsageStatsManager() ?: return false
            setAppStandbyBucketMethod?.invoke(packageName, bucketType)
            Log.d(TAG, "Successfully set standby bucket for $packageName to $bucketName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set standby bucket for $packageName: ${e.message}")
            false
        }
    }

    /**
     * Converts a bucket name string to the corresponding bucket type integer.
     */
    private fun getBucketTypeFromName(bucketName: String): Int {
        return when (bucketName.lowercase()) {
            "active" -> BucketTypes.BUCKET_ACTIVE
            "working_set", "working" -> BucketTypes.BUCKET_WORKING_SET
            "frequent" -> BucketTypes.BUCKET_FREQUENT
            "rare" -> BucketTypes.BUCKET_RARE
            "restricted" -> BucketTypes.BUCKET_RESTRICTED
            else -> BucketTypes.BUCKET_RESTRICTED // Default to most restrictive
        }
    }

    /**
     * Fallback approach for devices that don't support or allow direct setting of standby buckets
     */
    private fun applyFallbackRestriction(packageName: String): Boolean {
        Log.d(TAG, "Using fallback approach for app $packageName")

        // In a real app, we would implement alternative battery optimization approaches
        // For instance, we could add the app to the battery optimization list

        // In this implementation, we'll just log what we would do
        Log.i(TAG, "ACTION NEEDED: User should manually restrict background usage for $packageName")

        // Return false since we couldn't directly apply the restriction
        return false
    }
}