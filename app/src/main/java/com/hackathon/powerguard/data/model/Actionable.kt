package com.hackathon.powerguard.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents an actionable item that can be executed to optimize battery or data usage.
 * 
 * Actionables are the core optimization mechanism in PowerGuard. They represent specific 
 * actions that can be taken to improve device performance, save battery, or reduce data usage.
 * Each actionable targets a specific app with a specific optimization strategy.
 * 
 * Actionables can be:
 * 1. Created by the app based on device analysis
 * 2. Received from the backend API
 * 3. Generated from user requests
 * 
 * The ActionableExecutor service routes actionables to the appropriate handler based on type.
 * Each handler implements the specific optimization logic for that actionable type.
 * 
 * The 4 most effective actionables with system privileges are:
 * 
 * 1. set_standby_bucket - Places apps in the RESTRICTED bucket using UsageStatsManager, 
 *    which limits background operations significantly. This reduces background CPU cycles, 
 *    network activity, and job scheduling. Can save up to 30% battery for apps with heavy 
 *    background activity by limiting how often they can sync, check for updates, or perform 
 *    background tasks. This is a less aggressive approach than force-stopping the app.
 * 
 * 2. restrict_background_data - Prevents apps from using network in the background via 
 *    NetworkPolicyManager while preserving foreground functionality. This can reduce data 
 *    usage by 95% for chatty apps that frequently communicate with servers. Also improves 
 *    battery life by eliminating unnecessary radio usage, which is one of the most 
 *    power-intensive operations on a device. Each background connection can consume up 
 *    to 7-8mAh of battery.
 * 
 * 3. kill_app - Immediately halts all resource consumption through ActivityManager's 
 *    forceStopPackage() method. This provides immediate 100% savings of whatever resources 
 *    the app was using (CPU, network, sensors). Most effective for apps currently consuming 
 *    significant resources. Saves battery by eliminating all background activity until the 
 *    user reopens the app. Essential for stopping misbehaving apps.
 * 
 * 4. manage_wake_locks - Controls apps keeping the device awake using AppOpsManager to deny 
 *    WAKE_LOCK operations. Wake locks prevent the device from entering low-power sleep state, 
 *    which can drain battery dramatically (up to 20% per hour if the device is kept fully awake). 
 *    By managing wake locks, the device can enter deep sleep properly, greatly extending standby time.
 */
data class Actionable(
    /**
     * Unique identifier for this actionable.
     */
    @SerializedName("id")
    val id: String,

    /**
     * The type of action to take (e.g., "kill_app", "restrict_background_data").
     * This must match one of the constants defined in ActionableTypes.
     */
    @SerializedName("type")
    val type: String,

    /**
     * Human-readable description of the action.
     */
    @SerializedName("description")
    val description: String,

    /**
     * The package name of the app this action applies to.
     */
    @SerializedName("package_name")
    val packageName: String,

    /**
     * Estimated battery savings in percentage.
     */
    @SerializedName("estimated_battery_savings")
    val estimatedBatterySavings: Float?,

    /**
     * Estimated data savings in MB.
     */
    @SerializedName("estimated_data_savings")
    val estimatedDataSavings: Float?,

    /**
     * Severity level (1-5, where 5 is most severe).
     */
    @SerializedName("severity")
    val severity: Int?,

    /**
     * New mode to be applied (e.g., for standby bucket: "restricted", "active", etc.).
     */
    @SerializedName("new_mode")
    val newMode: String?,

    /**
     * Whether the action should be enabled or disabled.
     */
    @SerializedName("enabled")
    val enabled: Boolean?,

    /**
     * Throttling level (1-10, where 10 is most restrictive).
     */
    @SerializedName("throttle_level")
    val throttleLevel: Int?,

    /**
     * Reason for suggesting this actionable.
     */
    @SerializedName("reason")
    val reason: String = "",
    
    /**
     * Additional parameters for this actionable.
     */
    @SerializedName("parameters")
    val parameters: Map<String, Any> = mapOf()
) 