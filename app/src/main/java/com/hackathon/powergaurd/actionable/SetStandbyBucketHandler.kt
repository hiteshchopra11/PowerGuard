package com.hackathon.powergaurd.actionable

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.hackathon.powergaurd.data.model.Actionable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for the set_standby_bucket actionable type.
 * 
 * This handler moves apps to different standby buckets to control their background
 * activity levels. Android's App Standby Buckets is a sophisticated system that
 * categorizes apps based on how frequently and recently they're used:
 * 
 * Bucket hierarchy (from least to most restricted):
 * - ACTIVE: Currently being used or recently used
 * - WORKING_SET: Used regularly
 * - FREQUENT: Used often, but not recently
 * - RARE: Rarely used
 * - RESTRICTED: Most restricted, minimal background work allowed
 * 
 * This actionable allows placing any app into a specific bucket, effectively 
 * controlling how aggressively Android restricts its background activities.
 * 
 * Requires Android P (API 28) or higher to function properly with UsageStatsManager.
 * Fallback approaches are attempted on older Android versions.
 */
@Singleton
class SetStandbyBucketHandler @Inject constructor(@ApplicationContext private val context: Context) :
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

    override suspend fun handleActionable(actionable: Actionable): Boolean {
        val packageName = actionable.packageName

        if (packageName.isBlank()) {
            Log.e(TAG, "Cannot set standby bucket: package name is blank")
            return false
        }

        // Determine target bucket from the action parameters
        val bucketName = actionable.newMode ?: "restricted"
        val bucketType = getBucketTypeFromName(bucketName)

        // Check if we're on Android P or higher where the API is available
        if (!ActionableUtils.isAtLeastApi(Build.VERSION_CODES.P)) {
            Log.d(TAG, "App standby buckets not available below Android P (API 28)")
            return applyFallbackRestriction(packageName)
        }

        try {
            Log.d(
                TAG,
                "Attempting to set app $packageName to standby bucket: $bucketName ($bucketType)"
            )

            // Get the UsageStatsManager
            val usageStatsManager = ActionableUtils.getSystemService<UsageStatsManager>(
                context, Context.USAGE_STATS_SERVICE
            ) ?: return false

            // Get the setAppStandbyBucket method
            val setAppStandbyBucketMethod = ActionableUtils.getMethodByReflection(
                UsageStatsManager::class.java.name,
                "setAppStandbyBucket",
                String::class.java,
                Int::class.java
            ) ?: return false

            // Invoke the method
            setAppStandbyBucketMethod.invoke(usageStatsManager, packageName, bucketType)
            Log.d(
                TAG,
                "Successfully set app $packageName to bucket $bucketName using reflection"
            )
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error handling set standby bucket action", e)
            
            Log.i(
                TAG, "ACTION NEEDED: Unable to set app standby bucket directly. " +
                    "User should manually restrict the app $packageName in Settings > Apps"
            )
            
            return applyFallbackRestriction(packageName)
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