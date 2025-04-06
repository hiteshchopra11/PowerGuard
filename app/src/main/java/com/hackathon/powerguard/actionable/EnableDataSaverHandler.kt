package com.hackathon.powerguard.actionable

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import com.hackathon.powerguard.data.model.Actionable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for the enable_data_saver actionable type. Always attempts to enable Data Saver mode,
 * though it must guide the user as it can't be toggled programmatically.
 */
@Singleton
class EnableDataSaverHandler @Inject constructor(@ApplicationContext private val context: Context) :
    ActionableHandler {

    private val TAG = "DataSaverHandler"

    override val actionableType: String = ActionableTypes.ENABLE_DATA_SAVER

    override suspend fun handleActionable(actionable: Actionable): Boolean {
        try {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val isDataSaverEnabled = try {
                // If status is anything other than DISABLED, Data Saver is enabled
                connectivityManager.getRestrictBackgroundStatus() !=
                        ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get Data Saver status", e)
                false
            }

            if (isDataSaverEnabled) {
                Log.d(TAG, "Data Saver is already enabled")
                return true
            }

            // Can't programmatically enable it — guide the user
            Log.i(
                TAG,
                "ACTION NEEDED: User should enable Data Saver manually in " +
                        "Settings > Network & Internet > Data Saver"
            )

            // Optionally open settings screen (if you want this automatically)
            // openDataSaverSettings()

            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error handling Data Saver action", e)
            return false
        }
    }

    /**
     * Opens the Data Saver settings screen.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    fun openDataSaverSettings() {
        try {
            val intent = Intent(Settings.ACTION_DATA_USAGE_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Data Saver settings", e)
        }
    }
}
