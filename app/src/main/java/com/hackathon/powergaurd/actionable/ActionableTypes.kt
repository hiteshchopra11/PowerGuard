package com.hackathon.powergaurd.actionable

/** Constants for actionable types supported by the application. */
object ActionableTypes {

    // App control actions
    const val KILL_APP = "kill_app"
    const val RESTRICT_BACKGROUND = "restrict_background"
    const val OPTIMIZE_BATTERY = "optimize_battery"
    const val MARK_APP_INACTIVE = "mark_app_inactive"

    // System settings actions
    const val ENABLE_BATTERY_SAVER = "enable_battery_saver"
    const val ENABLE_DATA_SAVER = "enable_data_saver"
    const val ADJUST_SYNC_SETTINGS = "adjust_sync_settings"

    // Categorization actions
    const val CATEGORIZE_APP = "categorize_app"

    /** List of all supported actionable types. */
    val ALL_TYPES =
            listOf(
                    KILL_APP,
                    RESTRICT_BACKGROUND,
                    OPTIMIZE_BATTERY,
                    MARK_APP_INACTIVE,
                    ENABLE_BATTERY_SAVER,
                    ENABLE_DATA_SAVER,
                    ADJUST_SYNC_SETTINGS,
                    CATEGORIZE_APP
            )
}
