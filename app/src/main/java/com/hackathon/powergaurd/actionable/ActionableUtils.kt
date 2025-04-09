package com.hackathon.powergaurd.actionable

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import java.lang.reflect.Method

/**
 * Utility class providing common operations for actionable handlers
 * to perform system-level operations in a safe, reflective manner.
 * 
 * This centralized approach ensures consistent error handling and logging
 * across all actionable implementations.
 */
object ActionableUtils {
    const val TAG = "ActionableUtils"

    /**
     * Gets process IDs for a specific UID.
     * 
     * @param uid The application's UID to find processes for
     * @return List of process IDs belonging to the specified UID
     */
    fun getPidsForUid(uid: Int): List<Int> {
        val pids = mutableListOf<Int>()
        try {
            val processClass = Class.forName("android.os.Process")
            val method = processClass.getMethod("getPidsForUid", Int::class.java)
            val result = method.invoke(null, uid) as IntArray
            pids.addAll(result.toList())
        } catch (e: Exception) {
            Log.e(TAG, "Error getting PIDs for UID: ${e.message}")
        }
        return pids
    }

    /**
     * Gets the UID for a package name.
     * 
     * @param context Android context
     * @param packageName The package name to find the UID for
     * @return The UID or -1 if not found
     */
    fun getUidForPackage(context: Context, packageName: String): Int {
        return try {
            context.packageManager.getPackageUid(packageName, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting UID for package $packageName: ${e.message}")
            -1
        }
    }

    /**
     * Uses reflection to safely call a method that might not be publicly accessible.
     * 
     * @param T The expected return type
     * @param obj The object to call the method on (null for static methods)
     * @param methodName Name of the method to call
     * @param parameterTypes Array of parameter types
     * @param args Arguments to pass to the method
     * @return The result of the method call or null if failed
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> callMethodByReflection(
        obj: Any?, 
        methodName: String, 
        parameterTypes: Array<Class<*>>, 
        vararg args: Any?
    ): T? {
        return try {
            val clazz = obj?.javaClass ?: return null
            val method = clazz.getMethod(methodName, *parameterTypes)
            method.invoke(obj, *args) as T
        } catch (e: Exception) {
            Log.e(TAG, "Reflection error calling $methodName: ${e.message}")
            null
        }
    }

    /**
     * Gets a system service with type safety.
     * 
     * @param T The type of system service to retrieve
     * @param context Android context
     * @param serviceName The name of the system service
     * @return The system service or null if not available
     */
    inline fun <reified T> getSystemService(context: Context, serviceName: String): T? {
        return try {
            context.getSystemService(serviceName) as? T
        } catch (e: Exception) {
            Log.e(TAG, "Error getting system service $serviceName: ${e.message}")
            null
        }
    }

    /**
     * Executes a command with root privileges (if available).
     * 
     * @param command The command to execute
     * @return true if command executed successfully, false otherwise
     */
    fun executeRootCommand(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = process.outputStream
            
            os.write("$command\n".toByteArray())
            os.write("exit\n".toByteArray())
            os.flush()
            os.close()
            
            val exitValue = process.waitFor()
            exitValue == 0
        } catch (e: Exception) {
            Log.e(TAG, "Error executing root command: ${e.message}")
            false
        }
    }

    /**
     * Sets the process group for a specific process ID.
     * 
     * @param context Android context
     * @param pid The process ID
     * @param priority The process priority to set
     * @return true if operation succeeded, false otherwise
     */
    fun setProcessGroup(context: Context, pid: Int, priority: Int): Boolean {
        val activityManager = getSystemService<ActivityManager>(context, Context.ACTIVITY_SERVICE) ?: return false
        
        return try {
            val method = ActivityManager::class.java.getMethod("setProcessGroup", 
                Int::class.java, Int::class.java)
            method.invoke(activityManager, pid, priority)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting process group for pid $pid: ${e.message}")
            false
        }
    }

    /**
     * Gets a Method object via reflection, allowing access to APIs that might not be publicly available.
     * 
     * @param className Fully qualified class name
     * @param methodName Method name to get
     * @param parameterTypes Array of parameter types
     * @return The Method object or null if not found
     */
    fun getMethodByReflection(
        className: String,
        methodName: String,
        vararg parameterTypes: Class<*>
    ): Method? {
        return try {
            val clazz = Class.forName(className)
            clazz.getMethod(methodName, *parameterTypes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get method $methodName from $className: ${e.message}")
            null
        }
    }
    
    /**
     * Checks if the device is running a specific Android API level or higher.
     * 
     * @param apiLevel The API level to check against
     * @return true if running on the specified API level or higher, false otherwise
     */
    fun isAtLeastApi(apiLevel: Int): Boolean {
        return Build.VERSION.SDK_INT >= apiLevel
    }
} 