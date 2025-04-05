package com.hackathon.powergaurd.actionable

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.hackathon.powergaurd.models.ActionResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.reflect.Method
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for the enable_battery_saver actionable type. Enables battery saver mode or requests the
 * user to enable it.
 */
@Singleton
class EnableBatterySaverHandler
@Inject
constructor(@ApplicationContext private val context: Context) : ActionableHandler {

    private val TAG = "BatterySaverHandler"

    override val actionableType: String = ActionableTypes.ENABLE_BATTERY_SAVER

    // Cache the setPowerSaveMode method
    private val setPowerSaveModeMethod: Method? by lazy {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val method = PowerManager::class.java.getMethod("setPowerSaveMode", Boolean::class.java)
            method
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get setPowerSaveMode method", e)
            null
        }
    }

    override suspend fun handleActionable(actionable: ActionResponse.Actionable): Boolean {
        val enabled = actionable.enabled ?: true

        try {
            Log.d(TAG, "Attempting to ${if (enabled) "enable" else "disable"} battery saver mode")

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

            // Try direct method first (requires WRITE_SECURE_SETTINGS permission)
            val method = setPowerSaveModeMethod
            if (method != null) {
                try {
                    method.invoke(powerManager, enabled)
                    Log.d(
                            TAG,
                            "Successfully ${if (enabled) "enabled" else "disabled"} battery saver mode"
                    )
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set power save mode directly", e)
                    // Fall through to alternate methods
                }
            }

            // Alternative: Try to write to settings (requires WRITE_SECURE_SETTINGS permission)
            try {
                val value = if (enabled) 1 else 0
                val successful = Settings.Global.putInt(context.contentResolver, "low_power", value)

                if (successful) {
                    Log.d(
                            TAG,
                            "Successfully ${if (enabled) "enabled" else "disabled"} battery saver mode via settings"
                    )
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set power save mode via settings", e)
            }

            // If both direct methods failed, we'll need to guide the user
            Log.d(TAG, "Direct battery saver control not available; user guidance needed")

            // In a real app, this would show a notification to the user
            // For now, we'll just log this
            Log.i(
                    TAG,
                    "ACTION NEEDED: User should ${if (enabled) "enable" else "disable"} battery saver mode manually"
            )

            // Return false to indicate we couldn't directly perform the action
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error handling battery saver action", e)
            return false
        }
    }
}
