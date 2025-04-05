package com.hackathon.powergaurd.actionable

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import com.hackathon.powergaurd.models.ActionResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for the enable_data_saver actionable type. Since Data Saver can't be directly controlled,
 * this guides the user to enable it.
 */
@Singleton
class EnableDataSaverHandler @Inject constructor(@ApplicationContext private val context: Context) :
        ActionableHandler {

    private val TAG = "DataSaverHandler"

    override val actionableType: String = ActionableTypes.ENABLE_DATA_SAVER

    @RequiresApi(Build.VERSION_CODES.N)
    override suspend fun handleActionable(actionable: ActionResponse.Actionable): Boolean {
        val enabled = actionable.enabled ?: true

        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                Log.d(TAG, "Data Saver not available on this Android version")
                return false
            }

            val connectivityManager =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            // Check current Data Saver status
            val isDataSaverEnabled =
                    try {
                        // RESTRICT_BACKGROUND_STATUS_DISABLED = 1
                        // Any other value indicates that Data Saver is enabled
                        connectivityManager.getRestrictBackgroundStatus() != 1
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to get Data Saver status", e)
                        false
                    }

            // If Data Saver is already in the desired state, we're done
            if (isDataSaverEnabled == enabled) {
                Log.d(TAG, "Data Saver is already ${if (enabled) "enabled" else "disabled"}")
                return true
            }

            // We can't directly control Data Saver, so we need to guide the user
            Log.d(TAG, "Direct Data Saver control not available; user guidance needed")

            // In a real app, this would show a notification to the user with instructions
            // and possibly open the Data Saver settings directly

            // For now, we'll just log this
            Log.i(
                    TAG,
                    "ACTION NEEDED: User should ${if (enabled) "enable" else "disable"} Data Saver mode manually" +
                            " in Settings > Network & Internet > Data Saver"
            )

            // Return false to indicate we couldn't directly perform the action
            // but the user guidance has been provided
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error handling Data Saver action", e)
            return false
        }
    }

    /**
     * Opens the Data Saver settings screen. This can be used to guide the user to the correct
     * settings page.
     */
    fun openDataSaverSettings() {
        try {
            val intent =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        Intent(Settings.ACTION_DATA_USAGE_SETTINGS)
                    } else {
                        Intent(Settings.ACTION_WIRELESS_SETTINGS)
                    }
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Data Saver settings", e)
        }
    }
}
