package com.hackathon.powerguard.actionable.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.hackathon.powerguard.actionable.ActionableHandler
import com.hackathon.powerguard.actionable.ActionableTypes
import com.hackathon.powerguard.actionable.ActionableUtils
import com.hackathon.powerguard.actionable.model.ActionableResult
import com.hackathon.powerguard.data.model.Actionable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for restricting background data usage of specific apps.
 * 
 * This handler uses the NetworkPolicyManager to prevent apps from using
 * mobile data in the background, which can significantly reduce data usage
 * while preserving foreground functionality.
 */
@Singleton
class RestrictBackgroundDataHandler @Inject constructor(
    private val context: Context
) : ActionableHandler {

    private val TAG = "RestrictDataHandler"
    
    override val actionableType: String = ActionableTypes.RESTRICT_BACKGROUND_DATA
    
    // NetworkPolicyManager constants
    companion object {
        // NetworkPolicyManager class name for reflection
        private const val NETWORK_POLICY_MANAGER_CLASS = "android.net.NetworkPolicyManager"
        
        // NetworkPolicyManager.Policy constants
        private const val POLICY_NONE = 0
        private const val POLICY_REJECT_METERED_BACKGROUND = 1
        
        // Intent constants for user actions
        private const val DATA_USAGE_SETTINGS_ACTION = Settings.ACTION_DATA_USAGE_SETTINGS
    }
    
    // Network policy manager instance - initialized lazily via reflection
    private val networkPolicyManager: Any? by lazy {
        try {
            val serviceClass = Class.forName(NETWORK_POLICY_MANAGER_CLASS)
            val getServiceMethod = serviceClass.getMethod("from", Context::class.java)
            getServiceMethod.invoke(null, context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get NetworkPolicyManager", e)
            null
        }
    }
    
    override suspend fun execute(actionable: Actionable): ActionableResult {
        if (!canHandle(actionable)) {
            return ActionableResult.failure("Cannot handle actionable of type ${actionable.type}")
        }
        
        val packageName = actionable.packageName
        
        // Don't restrict our own app
        if (packageName == context.packageName) {
            return ActionableResult.failure("Cannot restrict own app")
        }
        
        // Determine if we should restrict or unrestrict based on the 'enabled' parameter
        val shouldRestrict = actionable.enabled != false // Default to true if not specified
        
        return try {
            // Check if we have the required permissions
            if (!hasRequiredPermissions()) {
                // If we don't have system permissions, guide the user to settings
                return createUserGuidanceResult(
                    actionable,
                    shouldRestrict,
                    "Missing required permissions to restrict background data directly"
                )
            }
            
            val uid = ActionableUtils.getPackageUid(context, packageName)
            if (uid < 0) {
                return ActionableResult.failure(
                    "Could not find UID for package: $packageName",
                    mapOf("packageName" to packageName)
                )
            }
            
            // Check current policy
            val currentPolicy = getUidPolicy(uid)
            val isCurrentlyRestricted = currentPolicy and POLICY_REJECT_METERED_BACKGROUND != 0
            
            // Only change policy if needed
            if (shouldRestrict == isCurrentlyRestricted) {
                return ActionableResult.success(
                    "Background data already ${if (shouldRestrict) "restricted" else "allowed"} for $packageName",
                    mapOf(
                        "packageName" to packageName,
                        "alreadySet" to "true"
                    )
                )
            }
            
            // Set the new policy
            val newPolicy = if (shouldRestrict) {
                currentPolicy or POLICY_REJECT_METERED_BACKGROUND
            } else {
                currentPolicy and POLICY_REJECT_METERED_BACKGROUND.inv()
            }
            
            val success = setUidPolicy(uid, newPolicy)
            
            if (success) {
                ActionableResult.success(
                    "Successfully ${if (shouldRestrict) "restricted" else "allowed"} background data for $packageName",
                    mapOf(
                        "packageName" to packageName,
                        "restricted" to shouldRestrict.toString()
                    )
                )
            } else {
                // If direct method fails, fall back to user guidance
                createUserGuidanceResult(
                    actionable,
                    shouldRestrict,
                    "Failed to set background data policy directly"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restricting background data for $packageName", e)
            // If an exception occurs, fall back to user guidance
            createUserGuidanceResult(
                actionable,
                shouldRestrict,
                "Error: ${e.message}"
            )
        }
    }
    
    override suspend fun revert(actionable: Actionable): ActionableResult {
        // To revert, we just need to invert the 'enabled' parameter
        return execute(actionable.copy(enabled = !actionable.enabled.isTrue()))
    }
    
    override fun canHandle(actionable: Actionable): Boolean {
        return actionable.type == actionableType && actionable.packageName.isNotBlank()
    }
    
    /**
     * Checks if the app has the required permissions to restrict background data.
     */
    private fun hasRequiredPermissions(): Boolean {
        // Need system privileges and NetworkPolicyManager available
        return ActionableUtils.hasSystemPermissions(context) && networkPolicyManager != null
    }
    
    /**
     * Gets the current policy for a UID.
     * 
     * @param uid The UID to check
     * @return The current policy
     */
    private fun getUidPolicy(uid: Int): Int {
        return try {
            val manager = networkPolicyManager ?: return POLICY_NONE
            
            val method = manager.javaClass.getMethod("getUidPolicy", Int::class.java)
            method.invoke(manager, uid) as? Int ?: POLICY_NONE
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get UID policy for UID $uid", e)
            POLICY_NONE
        }
    }
    
    /**
     * Sets the policy for a UID.
     * 
     * @param uid The UID to set the policy for
     * @param policy The policy to set
     * @return true if the operation was successful, false otherwise
     */
    private fun setUidPolicy(uid: Int, policy: Int): Boolean {
        return try {
            val manager = networkPolicyManager ?: return false
            
            val method = manager.javaClass.getMethod("setUidPolicy", Int::class.java, Int::class.java)
            method.invoke(manager, uid, policy)
            
            Log.d(TAG, "Set UID policy for UID $uid to $policy")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set UID policy for UID $uid", e)
            false
        }
    }
    
    /**
     * Creates a result with user guidance for when direct API access isn't possible.
     * 
     * @param actionable The actionable being executed
     * @param shouldRestrict Whether data should be restricted or not
     * @param reason The reason why direct access isn't possible
     * @return An ActionableResult with user guidance
     */
    private fun createUserGuidanceResult(
        actionable: Actionable,
        shouldRestrict: Boolean,
        reason: String
    ): ActionableResult {
        val action = if (shouldRestrict) "restrict" else "allow"
        val description = 
            "To $action background data for ${actionable.packageName}, please follow these steps:\n" +
            "1. Go to Settings > Data Usage\n" +
            "2. Select '${extractAppName(actionable.packageName)}'\n" +
            "3. ${if (shouldRestrict) "Enable" else "Disable"} 'Restrict background data'"
        
        // Create an intent that will take the user to data usage settings
        val settingsIntent = Intent(DATA_USAGE_SETTINGS_ACTION)
        
        // Try to add the specific package if supported
        try {
            settingsIntent.data = Uri.parse("package:${actionable.packageName}")
        } catch (e: Exception) {
            // Ignore and use general settings intent
        }

        return ActionableResult.success(
            "Please manually $action background data for this app",
            mapOf(
                "packageName" to actionable.packageName,
                "requiresUserAction" to "true",
                "reason" to reason,
                "userGuidance" to description,
                "settingsAction" to settingsIntent.action.toString()
            )
        )
    }
    
    /**
     * Extracts a user-friendly app name from a package name.
     * 
     * @param packageName The package name
     * @return A user-friendly app name
     */
    private fun extractAppName(packageName: String): String {
        return try {
            val packageManager = context.packageManager
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            // If we can't get the app name, use the last part of the package name
            packageName.split(".").last()
        }
    }
    
    /**
     * Extension function to handle nullable booleans.
     */
    private fun Boolean?.isTrue() = this ?: true
}