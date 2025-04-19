package com.hackathon.powergaurd.actionable

/**
 * Constants for the different types of actionable optimization strategies.
 *
 * Each constant represents a specific actionable that can be performed to
 * optimize battery life, data usage, or system performance.
 */
object ActionableTypes {
    /**
     * Places apps in specific standby buckets to control their background activity.
     *
     * Uses UsageStatsManager.setAppStandbyBucket() API.
     * Available buckets: ACTIVE, WORKING_SET, FREQUENT, RARE, RESTRICTED.
     *
     * Impact: High impact on battery with low user experience disruption.
     * Battery savings: Up to 30% for apps with heavy background activity.
     * Requires: Android P (API 28) or higher for full functionality.
     */
    const val SET_STANDBY_BUCKET = "set_standby_bucket"

    /**
     * Prevents specific apps from using data in the background.
     *
     * Uses NetworkPolicyManager.setUidPolicy() API.
     *
     * Impact: High impact on data usage with minimal UX effects.
     * Data savings: Up to 95% for chatty apps that frequently connect to servers.
     * Battery Impact: Also improves battery life by eliminating unnecessary radio usage.
     * Requires: System privileges to access NetworkPolicyManager.
     */
    const val RESTRICT_BACKGROUND_DATA = "restrict_background_data"

    /**
     * Force stops applications that are consuming excessive resources.
     *
     * Uses ActivityManager.forceStopPackage() API.
     *
     * Impact: Immediate but temporary relief; apps will restart when launched.
     * Battery savings: 100% for the stopped app until it's reopened.
     * Requires: System privileges to force stop other applications.
     */
    const val KILL_APP = "kill_app"

    /**
     * Controls apps keeping the device awake via wake locks.
     *
     * Uses AppOpsManager to deny wake lock operations.
     *
     * Impact: Targets one of the most common battery drain sources.
     * Battery savings: Up to 20% if wake locks prevent deep sleep.
     * Requires: Android M (API 23) or higher.
     */
    const val MANAGE_WAKE_LOCKS = "manage_wake_locks"

    /**
     * Limits CPU resources allocated to specific apps.
     *
     * Uses process groups and cgroups when available.
     *
     * Impact: Allows granular balancing between performance and battery life.
     * Battery savings: 40-60% for computation-intensive apps.
     * Requires: System privileges or root access for full functionality.
     */
    const val THROTTLE_CPU_USAGE = "throttle_cpu_usage"
}