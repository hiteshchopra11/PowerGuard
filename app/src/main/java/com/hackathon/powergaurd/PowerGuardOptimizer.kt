package com.hackathon.powergaurd

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import android.app.ActivityManager
import android.net.ConnectivityManager
import android.util.Log

/**
 * Main class for AI-PowerGuard optimization actions
 * Compatible with API level 24+
 */
@Singleton
class PowerGuardOptimizer @Inject constructor(
    private val context: Context
) {

    /**
     * 1. Wake Lock Management
     * Monitors and prevents excessive wake locks
     *
     * @param packageName The package name of the app
     * @param timeoutMs Optional timeout value in milliseconds
     * @return true if the action was successful, false otherwise
     */
    fun manageWakeLocks(
        packageName: String,
        timeoutMs: Int = 60000
    ): Boolean {
        try {
            when {
                // If we don't have permission to access usage stats, we can't detect wake locks
                !hasUsageStatsPermission() -> {
                    requestUsageStatsPermission()
                    return false
                }

                else -> {
                    // With system privileges, we can use AppOpsManager to deny WAKE_LOCK operations
                    val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                    
                    // MODE_IGNORED = deny the operation
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            val appOpsClass = Class.forName(AppOpsManager::class.java.name)
                            val method = appOpsClass.getMethod("setMode", Int::class.java, 
                                Int::class.java, String::class.java, Int::class.java)
                            
                            // AppOpsManager.OP_WAKE_LOCK = 40
                            val packageUid = context.packageManager.getPackageUid(packageName, 0)
                            // AppOpsManager.MODE_IGNORED = 1
                            method.invoke(appOpsManager, 40, packageUid, packageName, 1)
                            
                            Log.d("PowerGuardOptimizer", "Successfully restricted wake locks for $packageName")
                            return true
                        } catch (e: Exception) {
                            Log.e("PowerGuardOptimizer", "Error restricting wake locks: ${e.message}")
                        }
                    }
                    
                    return false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * 2. Restrict Background Data
     * Restricts background data usage for specific apps
     *
     * @param packageName The package name of the app
     * @param enabled Whether background data restriction is enabled
     * @param scheduleTimeRanges Optional time ranges when restrictions should apply
     * @return true if the action was successful, false otherwise
     */
    fun restrictBackgroundData(
        packageName: String,
        enabled: Boolean,
        scheduleTimeRanges: List<TimeRange>? = null
    ): Boolean {
        try {
            if (enabled) {
                // With system privileges, we can use ConnectivityManager directly
                try {
                    // Use reflection since NetworkPolicyManager is not directly accessible
                    val policyManagerClass = Class.forName("android.net.NetworkPolicyManager")
                    val getSystemServiceMethod = policyManagerClass.getMethod("from", Context::class.java)
                    val policyManager = getSystemServiceMethod.invoke(null, context)
                    
                    val setUidPolicyMethod = policyManagerClass.getMethod("setUidPolicy", 
                        Int::class.java, Int::class.java)
                    
                    val uid = context.packageManager.getPackageUid(packageName, 0)
                    // POLICY_REJECT_METERED_BACKGROUND = 1
                    setUidPolicyMethod.invoke(policyManager, uid, 1)
                    
                    Log.d("PowerGuardOptimizer", "Successfully restricted background data for $packageName")
                    return true
                } catch (e: Exception) {
                    Log.e("PowerGuardOptimizer", "Error restricting background data: ${e.message}")
                    
                    // Fallback to opening settings
                    val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }
                
                return false
            } else {
                // Remove restriction
                try {
                    // Use reflection since NetworkPolicyManager is not directly accessible
                    val policyManagerClass = Class.forName("android.net.NetworkPolicyManager")
                    val getSystemServiceMethod = policyManagerClass.getMethod("from", Context::class.java)
                    val policyManager = getSystemServiceMethod.invoke(null, context)
                    
                    val setUidPolicyMethod = policyManagerClass.getMethod("setUidPolicy", 
                        Int::class.java, Int::class.java)
                    
                    val uid = context.packageManager.getPackageUid(packageName, 0)
                    // POLICY_NONE = 0
                    setUidPolicyMethod.invoke(policyManager, uid, 0)
                    
                    Log.d("PowerGuardOptimizer", "Successfully removed background data restriction for $packageName")
                    return true
                } catch (e: Exception) {
                    Log.e("PowerGuardOptimizer", "Error removing background data restriction: ${e.message}")
                }
                
                return false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * 3. Kill App
     * Force stops an application
     *
     * @param packageName The package name of the app to kill
     * @return true if the action was successful, false otherwise
     */
    fun killApp(packageName: String): Boolean {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            
            // Try to use the forceStopPackage method directly via reflection
            try {
                val method = ActivityManager::class.java.getMethod("forceStopPackage", String::class.java)
                method.invoke(activityManager, packageName)
                Log.d("PowerGuardOptimizer", "Successfully force stopped app: $packageName")
                return true
            } catch (e: Exception) {
                Log.e("PowerGuardOptimizer", "Error force stopping app: ${e.message}")
            }
            
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * 4. Optimize Battery Charging
     * Optimizes device charging behavior to extend battery lifespan
     * Note: Most devices don't allow programmatic control of charging without root
     *
     * @param maxChargeLevel Maximum battery level (0-100)
     * @param chargingSchedule Optional schedule for when to charge
     * @return true if the action was successful, false otherwise
     */
    fun optimizeBatteryCharging(
        maxChargeLevel: Int = 80,
        chargingSchedule: ChargingSchedule? = null
    ): Boolean {
        try {
            // With system privileges, try to directly control battery charging
            try {
                // For devices that support optimized battery charging
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Settings.Global.putInt(context.contentResolver, "adaptive_charging_enabled", 1)
                    Log.d("PowerGuardOptimizer", "Enabled adaptive charging via settings")
                    return true
                }
            } catch (e: Exception) {
                Log.e("PowerGuardOptimizer", "Failed to enable adaptive charging: ${e.message}")
            }
            
            // Fallback to enabling battery optimization
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            
            if (!powerManager.isPowerSaveMode) {
                try {
                    val method = PowerManager::class.java.getMethod("setPowerSaveMode", Boolean::class.java)
                    method.invoke(powerManager, true)
                    Log.d("PowerGuardOptimizer", "Enabled power save mode via reflection")
                    return true
                } catch (e: Exception) {
                    Log.e("PowerGuardOptimizer", "Failed to enable power save mode via reflection: ${e.message}")
                    
                    // Try writing directly to settings
                    try {
                        Settings.Global.putInt(context.contentResolver, "low_power", 1)
                        Log.d("PowerGuardOptimizer", "Enabled power save mode via settings")
                        return true
                    } catch (e: Exception) {
                        Log.e("PowerGuardOptimizer", "Failed to enable power save mode via settings: ${e.message}")
                    }
                    
                    // Last resort: Open settings
                    val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }
            }
            
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * 5. Force App Hibernation
     * Puts an app into hibernation state to reduce background activity
     *
     * @param packageName The package name of the app to hibernate
     * @return true if the action was successful, false otherwise
     */
    fun forceAppHibernation(packageName: String): Boolean {
        try {
            // For Android 12+ (API 31+), we can use App Hibernation APIs directly
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    // Use reflection to access the hidden AppHibernationManager class
                    val hibernationServiceClass = Class.forName("android.app.AppHibernationManager")
                    val hibernationManager = context.getSystemService(hibernationServiceClass)
                    
                    if (hibernationManager != null) {
                        val method = hibernationServiceClass.getMethod("setAppHibernationState", 
                            String::class.java, Boolean::class.java, Boolean::class.java)
                        
                        // First boolean: user-level hibernation, Second boolean: global hibernation
                        method.invoke(hibernationManager, packageName, true, true)
                        Log.d("PowerGuardOptimizer", "Successfully hibernated app: $packageName")
                        return true
                    }
                } catch (e: Exception) {
                    Log.e("PowerGuardOptimizer", "Error hibernating app: ${e.message}")
                }
            }
            
            // For pre-Android 12, implement custom hibernation:
            // 1. Restrict background data
            val dataRestricted = restrictBackgroundData(packageName, true)
            
            // 2. Set app to RESTRICTED standby bucket
            var bucketRestricted = false
            try {
                val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
                if (usageStatsManager != null) {
                    val method = usageStatsManager.javaClass.getMethod("setAppStandbyBucket", 
                        String::class.java, Int::class.java)
                    
                    // STANDBY_BUCKET_RESTRICTED = 50
                    method.invoke(usageStatsManager, packageName, 50)
                    bucketRestricted = true
                }
            } catch (e: Exception) {
                Log.e("PowerGuardOptimizer", "Error setting app standby bucket: ${e.message}")
            }
            
            return dataRestricted || bucketRestricted
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * 6. Throttle CPU Usage
     * Limits CPU usage for a specific app
     *
     * @param packageName The package name of the app to throttle
     * @param level Throttling level (1-10, where 10 is most restrictive)
     * @return true if the action was successful, false otherwise
     */
    fun throttleCpuUsage(packageName: String, level: Int = 5): Boolean {
        try {
            // Get the UIDs for the package
            val uid = context.packageManager.getPackageUid(packageName, 0)
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            
            // First approach: Try to use setProcessGroup to set process priority
            try {
                val pids = getPidsForUid(uid)
                if (pids.isNotEmpty()) {
                    for (pid in pids) {
                        // Map level (1-10) to Android process priorities
                        // ActivityManager.PROCESS_STATE_TOP to ActivityManager.PROCESS_STATE_CACHED_EMPTY
                        val priority = when {
                            level >= 8 -> 20 // PROCESS_STATE_CACHED_EMPTY (most restricted)
                            level >= 6 -> 19 // PROCESS_STATE_CACHED_ACTIVITY
                            level >= 4 -> 11 // PROCESS_STATE_SERVICE
                            level >= 2 -> 6  // PROCESS_STATE_IMPORTANT_FOREGROUND
                            else -> 2        // PROCESS_STATE_TOP (least restricted)
                        }
                        
                        val method = ActivityManager::class.java.getMethod("setProcessGroup", 
                            Int::class.java, Int::class.java)
                        method.invoke(activityManager, pid, priority)
                    }
                    
                    Log.d("PowerGuardOptimizer", "Successfully throttled CPU for $packageName")
                    return true
                }
            } catch (e: Exception) {
                Log.e("PowerGuardOptimizer", "Error setting process group: ${e.message}")
            }
            
            // Second approach: Try to use cgroups directly if we have root
            try {
                val cpusetPath = "/dev/cpuset/background/tasks"
                val process = Runtime.getRuntime().exec("su")
                val os = process.outputStream
                
                val pids = getPidsForUid(uid)
                for (pid in pids) {
                    os.write("echo $pid > $cpusetPath\n".toByteArray())
                }
                
                os.write("exit\n".toByteArray())
                os.flush()
                os.close()
                
                val exitValue = process.waitFor()
                if (exitValue == 0) {
                    Log.d("PowerGuardOptimizer", "Successfully throttled CPU for $packageName using cgroups")
                    return true
                }
            } catch (e: Exception) {
                Log.e("PowerGuardOptimizer", "Error using cgroups: ${e.message}")
            }
            
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    // Helper methods

    private fun hasUsageStatsPermission(): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
    
    private fun getPidsForUid(uid: Int): List<Int> {
        val pids = mutableListOf<Int>()
        try {
            val processClass = Class.forName("android.os.Process")
            val method = processClass.getMethod("getPidsForUid", Int::class.java)
            val result = method.invoke(null, uid) as IntArray
            pids.addAll(result.toList())
        } catch (e: Exception) {
            Log.e("PowerGuardOptimizer", "Error getting PIDs for UID: ${e.message}")
        }
        return pids
    }

    // Data classes for the API - using Calendar-compatible values for API 24

    data class TimeRange(
        val startHour: Int,
        val startMinute: Int,
        val endHour: Int,
        val endMinute: Int,
        val daysOfWeek: List<Int> = listOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY,
            Calendar.SUNDAY
        )
    )

    data class ChargingSchedule(
        val startHour: Int,
        val startMinute: Int,
        val endHour: Int,
        val endMinute: Int,
        val daysOfWeek: List<Int> = listOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY,
            Calendar.SUNDAY
        )
    )
}