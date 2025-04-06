package com.hackathon.powergaurd.actionable

import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.hackathon.powergaurd.models.ActionResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for the ADJUST_SYNC_SETTINGS actionable type. Controls auto-sync settings for accounts.
 * This requires the WRITE_SYNC_SETTINGS permission.
 */
@Singleton
class SyncSettingsHandler @Inject constructor(@ApplicationContext private val context: Context) :
    ActionableHandler {

    private val TAG = "SyncSettingsHandler"

    override val actionableType: String = ActionableTypes.ADJUST_SYNC_SETTINGS

    override suspend fun handleActionable(actionable: ActionResponse.Actionable): Boolean {
        val enabled = actionable.enabled ?: false
        val packageName = actionable.app

        try {
            // If we're targeting all accounts
            if (packageName == null || packageName.isBlank()) {
                return setGlobalSync(enabled)
            }

            // If we're targeting a specific app/account
            return setSyncForPackage(packageName, enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling sync settings action", e)
            return false
        }
    }

    /**
     * Sets the global sync setting for all accounts.
     */
    private fun setGlobalSync(enabled: Boolean): Boolean {
        try {
            Log.d(TAG, "Attempting to ${if (enabled) "enable" else "disable"} global auto-sync")

            // First, try to set the global sync setting
            try {
                ContentResolver.setMasterSyncAutomatically(enabled)
                val currentSetting = ContentResolver.getMasterSyncAutomatically()

                if (currentSetting == enabled) {
                    Log.d(TAG, "Successfully set global auto-sync to $enabled")
                    return true
                } else {
                    Log.e(TAG, "Failed to set global auto-sync: value not applied")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception when setting global auto-sync: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting global auto-sync: ${e.message}")
            }

            // If direct method failed, guide the user
            Log.i(
                TAG,
                "ACTION NEEDED: User should manually ${if (enabled) "enable" else "disable"} " +
                        "auto-sync in Settings > Accounts"
            )

            // In a real app, this would show a notification to the user or open settings
            // openSyncSettings()

            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error setting global sync", e)
            return false
        }
    }

    /**
     * Sets the sync setting for a specific package/account.
     */
    private fun setSyncForPackage(packageName: String, enabled: Boolean): Boolean {
        try {
            Log.d(
                TAG,
                "Attempting to ${if (enabled) "enable" else "disable"} auto-sync for $packageName"
            )

            // Get accounts provided by this package
            val accountManager = AccountManager.get(context)
            val accounts = accountManager.accounts

            var foundMatchingAccount = false
            var allSucceeded = true

            // For each account, check if it's associated with this package
            for (account in accounts) {
                try {
                    // Get auth token types for this account (may throw SecurityException)
                    val authTokenTypes = accountManager.getAuthenticatorTypes()

                    // Find authenticators matching our package
                    val matchingAuthenticators = authTokenTypes.filter {
                        it.packageName == packageName
                    }

                    if (matchingAuthenticators.isNotEmpty()) {
                        foundMatchingAccount = true

                        // For each matching authenticator, get all sync adapters and set sync
                        for (authenticator in matchingAuthenticators) {
                            val syncAdapters = getSyncAdapters(authenticator.type)

                            for (authority in syncAdapters) {
                                try {
                                    ContentResolver.setSyncAutomatically(
                                        account,
                                        authority,
                                        enabled
                                    )
                                    Log.d(
                                        TAG,
                                        "Set sync for account ${account.name}, authority $authority to $enabled"
                                    )
                                } catch (e: Exception) {
                                    Log.e(
                                        TAG,
                                        "Failed to set sync for account ${account.name}, authority $authority",
                                        e
                                    )
                                    allSucceeded = false
                                }
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "Security exception when accessing account types: ${e.message}")
                    allSucceeded = false
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing account ${account.name}: ${e.message}")
                    allSucceeded = false
                }
            }

            if (foundMatchingAccount && allSucceeded) {
                Log.d(TAG, "Successfully set sync for all accounts related to $packageName")
                return true
            } else if (foundMatchingAccount) {
                Log.d(TAG, "Partially set sync for accounts related to $packageName")
                return true
            } else {
                Log.d(TAG, "No accounts found for package $packageName")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting sync for package $packageName", e)
            return false
        }
    }

    /**
     * Gets a list of sync adapter authorities for a specific account type.
     * This is a replacement for ContentResolver.getSyncAdapterPackages which might not be available.
     */
    private fun getSyncAdapters(accountType: String): List<String> {
        val authorities = mutableListOf<String>()

        try {
            // Get all sync adapter types
            val syncAdapters = ContentResolver.getSyncAdapterTypes()

            // Filter by account type
            val matchingAdapters = syncAdapters.filter { it.accountType == accountType }

            // Extract authorities
            authorities.addAll(matchingAdapters.map { it.authority })
        } catch (e: Exception) {
            Log.e(TAG, "Error getting sync adapters for account type $accountType", e)
        }

        return authorities
    }

    /**
     * Opens the sync settings screen. This can be used to guide the user to the correct
     * settings page if direct control is not available.
     */
    fun openSyncSettings() {
        try {
            val intent = Intent(Settings.ACTION_SYNC_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open sync settings", e)
        }
    }
} 