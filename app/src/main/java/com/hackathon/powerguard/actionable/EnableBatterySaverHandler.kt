package com.hackathon.powerguard.actionable

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.hackathon.powerguard.data.model.Actionable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.reflect.Method
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for the enable_battery_saver actionable type. Always enables battery saver mode.
 */
@Singleton
class EnableBatterySaverHandler
@Inject
constructor(@ApplicationContext private val context: Context) : ActionableHandler {

    private val TAG = "BatterySaverHandler"

    override val actionableType: String = ActionableTypes.ENABLE_BATTERY_SAVER

    // Cache the setPowerSaveMode method using reflection
    private val setPowerSaveModeMethod: Method? by lazy {
        try {
            PowerManager::class.java.getMethod("setPowerSaveMode", Boolean::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get setPowerSaveMode method", e)
            null
        }
    }

    override suspend fun handleActionable(actionable: Actionable): Boolean {
        try {
            Log.d(TAG, "Attempting to enable battery saver mode")

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

            // Try the hidden API method first
            setPowerSaveModeMethod?.let { method ->
                try {
                    method.invoke(powerManager, true)
                    Log.d(TAG, "Successfully enabled battery saver mode via reflection")
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to enable battery saver via reflection", e)
                }
            }

            // Fallback: Write directly to settings (requires WRITE_SECURE_SETTINGS)
            try {
                val success = Settings.Global.putInt(context.contentResolver, "low_power", 1)
                if (success) {
                    Log.d(TAG, "Successfully enabled battery saver mode via settings")
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enable battery saver via settings", e)
            }

            // Final fallback: Notify user
            Log.i(TAG, "ACTION NEEDED: User should manually enable battery saver mode")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error handling enable battery saver action", e)
            return false
        }
    }
}
