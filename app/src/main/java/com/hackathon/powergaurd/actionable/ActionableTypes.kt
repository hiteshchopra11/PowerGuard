package com.hackathon.powergaurd.actionable

/** Constants for actionable types supported by the application. */
object ActionableTypes {

    // App control actions
    const val KILL_APP = "kill_app"
    const val RESTRICT_BACKGROUND_DATA = "restrict_background_data"
    const val SET_STANDBY_BUCKET = "set_standby_bucket"
    const val MANAGE_WAKE_LOCKS = "manage_wake_locks"
    const val THROTTLE_CPU_USAGE = "throttle_cpu_usage"

    /** List of all supported actionable types. */
    val ALL_TYPES =
        listOf(
            KILL_APP,
            RESTRICT_BACKGROUND_DATA,
            SET_STANDBY_BUCKET,
            MANAGE_WAKE_LOCKS,
            THROTTLE_CPU_USAGE
        )
}
