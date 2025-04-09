package com.hackathon.powergaurd.actionable

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.hackathon.powergaurd.data.model.Actionable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for the restrict_background_data actionable type.
 * 
 * This handler restricts background data usage for specific apps using Android's
 * network policy management system. Background data consumption is one of the main
 * sources of battery drain and unwanted data usage when the user is not actively
 * using an application.
 * 
 * Implementation details:
 * - Uses NetworkPolicyManager to set POLICY_REJECT_METERED_BACKGROUND for the target app
 * - Requires system privileges to function properly
 * - Falls back to guiding the user to data usage settings if direct API access fails
 * 
 * Key benefits:
 * - Directly reduces cellular data consumption
 * - Improves battery life by preventing unnecessary network activity
 * - Works at a more granular level than global data saver mode
 * 
 * Note: This actionable can be toggled on/off by setting the "enabled" property.
 */
@Singleton
class RestrictBackgroundDataHandler @Inject constructor(@ApplicationContext private val context: Context) :
    ActionableHandler {

    private val TAG = "BackgroundDataHandler"

    override val actionableType: String = ActionableTypes.RESTRICT_BACKGROUND_DATA

    override suspend fun handleActionable(actionable: Actionable): Boolean {
        val packageName = actionable.packageName

        if (packageName.isBlank()) {
            Log.e(TAG, "Cannot restrict background data: package name is blank")
            return false
        }

        // Enable or disable background data restriction
        val enabled = actionable.enabled ?: true

        try {
            Log.d(TAG, "Attempting to ${if (enabled) "restrict" else "allow"} background data for app: $packageName")

            // Get the UID for the package
            val uid = ActionableUtils.getUidForPackage(context, packageName)
            if (uid == -1) {
                Log.e(TAG, "Could not find UID for package $packageName")
                return false
            }

            // Try to access NetworkPolicyManager using reflection
            try {
                // Get the NetworkPolicyManager class and "from" method
                val policyManagerClass = Class.forName("android.net.NetworkPolicyManager")
                val getSystemServiceMethod = ActionableUtils.getMethodByReflection(
                    policyManagerClass.name, 
                    "from", 
                    Context::class.java
                ) ?: return openDataSettings()
                
                // Get the policy manager instance
                val policyManager = getSystemServiceMethod.invoke(null, context)
                
                // Get the setUidPolicy method
                val setUidPolicyMethod = ActionableUtils.getMethodByReflection(
                    policyManagerClass.name,
                    "setUidPolicy",
                    Int::class.java,
                    Int::class.java
                ) ?: return openDataSettings()
                
                // Set the policy based on the enabled flag
                // NetworkPolicyManager.POLICY_REJECT_METERED_BACKGROUND = 1
                // NetworkPolicyManager.POLICY_NONE = 0
                val policy = if (enabled) 1 else 0
                setUidPolicyMethod.invoke(policyManager, uid, policy)
                
                Log.d(TAG, "Successfully ${if (enabled) "restricted" else "allowed"} background data for $packageName")
                return true
                
            } catch (e: Exception) {
                Log.e(TAG, "Error manipulating background data policy: ${e.message}")
                return openDataSettings()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to manipulate background data policy: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Opens data usage settings as a fallback when direct manipulation fails.
     * 
     * @return false to indicate the operation didn't complete programmatically
     */
    private fun openDataSettings(): Boolean {
        try {
            val intent = Intent(Settings.ACTION_DATA_USAGE_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            
            Log.i(TAG, "ACTION NEEDED: User should manually restrict background data in settings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open data usage settings: ${e.message}")
        }
        return false
    }
} 