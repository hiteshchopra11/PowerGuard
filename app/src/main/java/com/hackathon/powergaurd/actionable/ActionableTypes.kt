package com.hackathon.powergaurd.actionable

/** Constants for actionable types supported by the application. */
object ActionableTypes {

    // App control actions
    const val KILL_APP = "KILL_APP"
    const val RESTRICT_BACKGROUND_DATA = "RESTRICT_BACKGROUND_DATA"
    const val SET_STANDBY_BUCKET = "SET_STANDBY_BUCKET"
    const val MANAGE_WAKE_LOCKS = "MANAGE_WAKE_LOCKS"
    const val THROTTLE_CPU_USAGE = "THROTTLE_CPU_USAGE"

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
