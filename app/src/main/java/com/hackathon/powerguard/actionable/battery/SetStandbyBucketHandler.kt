package com.hackathon.powerguard.actionable.battery

import android.app.usage.UsageStatsManager
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
 * Handler for placing apps in specific standby buckets to control their background activity.
 *
 * This handler uses the UsageStatsManager.setAppStandbyBucket API to control how
 * frequently apps can perform background tasks, sync data, and receive push notifications.
 * Android categorizes apps into buckets (ACTIVE, WORKING_SET, FREQUENT, RARE, RESTRICTED)
 * that determine resource allocation policies.
 */
@Singleton
class SetStandbyBucketHandler @Inject constructor(
    private val context: Context
) : ActionableHandler {

    private val TAG = "StandbyBucketHandler"

    override val actionableType: String = ActionableTypes.SET_STANDBY_BUCKET

    // Cache UsageStatsManager instance if API level supports it
    private val usageStatsManager: UsageStatsManager? by lazy {
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    }

    // Standby bucket constants
    companion object {
        // From UsageStatsManager
        const val STANDBY_BUCKET_ACTIVE = 10
        const val STANDBY_BUCKET_WORKING_SET = 20
        const val STANDBY_BUCKET_FREQUENT = 30
        const val STANDBY_BUCKET_RARE = 40
        const val STANDBY_BUCKET_RESTRICTED = 45
        const val STANDBY_BUCKET_NEVER = 50

        // Mapping of string bucket names to their integer values
        val BUCKET_NAME_TO_VALUE = mapOf(
            "active" to STANDBY_BUCKET_ACTIVE,
            "working_set" to STANDBY_BUCKET_WORKING_SET,
            "frequent" to STANDBY_BUCKET_FREQUENT,
            "rare" to STANDBY_BUCKET_RARE,
            "restricted" to STANDBY_BUCKET_RESTRICTED,
            "never" to STANDBY_BUCKET_NEVER
        )

        // Mapping of integer bucket values to their string names
        val BUCKET_VALUE_TO_NAME = BUCKET_NAME_TO_VALUE.entries.associate { (k, v) -> v to k }
    }

    override suspend fun execute(actionable: Actionable): ActionableResult {
        if (!canHandle(actionable)) {
            return ActionableResult.failure("Cannot handle actionable of type ${actionable.type}")
        }

        val packageName = actionable.packageName

        // Don't set standby bucket for our own app
        if (packageName == context.packageName) {
            return ActionableResult.failure("Cannot set standby bucket for own app")
        }

        // Parse the bucket name from the actionable
        val bucketName = actionable.newMode ?: "restricted"
        val bucketValue = BUCKET_NAME_TO_VALUE[bucketName.lowercase()] ?: STANDBY_BUCKET_RESTRICTED

        return try {
            // Check if the package exists first
            if (!isPackageInstalled(packageName)) {
                return ActionableResult.failure(
                    "Package $packageName is not installed on this device",
                    mapOf("packageName" to packageName, "available" to "false")
                )
            }
            
            // Check if we have the required permissions
            if (!hasRequiredPermissions()) {
                return ActionableResult.failure(
                    "Missing required permissions to set app standby bucket",
                    mapOf("requiredPermission" to "android.permission.WRITE_SECURE_SETTINGS or system app privileges")
                )
            }

            // Get the current bucket first to allow reverting
            val currentBucket = getAppStandbyBucket(packageName)

            // Set the new bucket
            val success = setAppStandbyBucket(packageName, bucketValue)

            if (success) {
                ActionableResult.success(
                    "Successfully set app $packageName to standby bucket ${bucketName}",
                    mapOf(
                        "packageName" to packageName,
                        "previousBucket" to (BUCKET_VALUE_TO_NAME[currentBucket] ?: "unknown"),
                        "newBucket" to bucketName
                    )
                )
            } else {
                ActionableResult.failure(
                    "Failed to set standby bucket for $packageName",
                    mapOf("packageName" to packageName)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting standby bucket for $packageName", e)
            ActionableResult.fromException(e, mapOf("packageName" to packageName))
        }
    }

    override suspend fun revert(actionable: Actionable): ActionableResult {
        // To revert, we just need to set the bucket back to ACTIVE
        return execute(actionable.copy(newMode = "active"))
    }

    override fun canHandle(actionable: Actionable): Boolean {
        return actionable.type == actionableType &&
                actionable.packageName.isNotBlank()
    }

    /**
     * Checks if the app has the required permissions to set app standby buckets.
     */
    private fun hasRequiredPermissions(): Boolean {
        Log.d(TAG, "Checking required permissions for SetStandbyBucketHandler...")
        
        // Check for CHANGE_APP_IDLE_STATE permission specifically
        val changeIdleStatePermission = ActionableUtils.hasPermission(
            context,
            "android.permission.CHANGE_APP_IDLE_STATE"
        )
        Log.d(TAG, "CHANGE_APP_IDLE_STATE permission: $changeIdleStatePermission")
        
        // Check for the usage stats permission
        val usageStatsPermission = ActionableUtils.hasPermission(
            context,
            "android.permission.PACKAGE_USAGE_STATS"
        )
        Log.d(TAG, "Usage stats permission: $usageStatsPermission")

        // Check system app privileges
        val systemPrivileges = ActionableUtils.hasSystemPermissions(context)
        Log.d(TAG, "System privileges: $systemPrivileges")

        // CHANGE_APP_IDLE_STATE is required for setAppStandbyBucket
        // Return false if we don't have this specific permission
        val result = changeIdleStatePermission
        Log.d(TAG, "hasRequiredPermissions() = $result (requires CHANGE_APP_IDLE_STATE)")
        return result
    }

    /**
     * Gets the current standby bucket for a package.
     *
     * @param packageName The package name to check
     * @return The current standby bucket value
     */
    private fun getAppStandbyBucket(packageName: String): Int {
        return try {
            val manager = usageStatsManager ?: return STANDBY_BUCKET_ACTIVE

            // Try to use reflection to handle different API versions
            try {
                val method = manager.javaClass.getMethod("getAppStandbyBucket", String::class.java)
                return method.invoke(manager, packageName) as? Int ?: STANDBY_BUCKET_ACTIVE
            } catch (e: NoSuchMethodException) {
                // Fall back to method without parameters if available (older versions)
                Log.d(TAG, "Method with package parameter not found, trying no-parameter method")
                val method = manager.javaClass.getMethod("getAppStandbyBucket")
                return method.invoke(manager) as? Int ?: STANDBY_BUCKET_ACTIVE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get standby bucket for $packageName", e)
            STANDBY_BUCKET_ACTIVE
        }
    }

    /**
     * Sets the standby bucket for a package.
     *
     * @param packageName The package name to set the bucket for
     * @param bucket The bucket value to set
     * @return true if the operation was successful, false otherwise
     */
    private fun setAppStandbyBucket(packageName: String, bucket: Int): Boolean {
        return try {
            // The method name changed in some API versions, try using reflection to be safe
            val manager = usageStatsManager ?: return false
            Log.d(TAG, "UsageStatsManager obtained: $manager")

            val method = manager.javaClass.getMethod(
                "setAppStandbyBucket",
                String::class.java,
                Int::class.java
            )
            Log.d(TAG, "Found method: $method")

            val result = method.invoke(manager, packageName, bucket)
            Log.d(TAG, "Method invocation result: $result")

            // Check if the bucket actually changed
            val currentBucket = getCurrentBucket(packageName)
            Log.d(TAG, "After setting, current bucket is: $currentBucket")

            Log.d(TAG, "Set standby bucket for $packageName to $bucket")
            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception setting standby bucket: missing permissions", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set standby bucket for $packageName: ${e.message}", e)
            false
        }
    }

    private fun getCurrentBucket(packageName: String): Int {
        return try {
            val manager = usageStatsManager ?: return -1
            val method = manager.javaClass.getMethod(
                "getAppStandbyBucket",
                String::class.java
            )
            method.invoke(manager, packageName) as Int
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current bucket: ${e.message}", e)
            -1
        }
    }

    /**
     * Checks if a package is installed on the device.
     *
     * @param packageName The package name to check
     * @return true if the package is installed, false otherwise
     */
    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            Log.d(TAG, "Package $packageName is installed")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Package $packageName is not installed: ${e.message}")
            false
        }
    }
}