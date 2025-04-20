package com.hackathon.powergaurd.actionable

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log

/**
 * Utility functions for actionable handlers.
 */
object ActionableUtils {
    private const val TAG = "ActionableUtils"

    /**
     * Gets the UID for a package.
     *
     * @param context The application context
     * @param packageName The package name to get the UID for
     * @return The UID for the specified package, or -1 if the package is not found
     */
    fun getPackageUid(context: Context, packageName: String): Int {
        return try {
            context.packageManager.getPackageUid(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Package not found: $packageName", e)
            -1
        } catch (e: Exception) {
            Log.e(TAG, "Error getting UID for package $packageName", e)
            -1
        }
    }

    /**
     * Checks if the current app has the specified permission.
     *
     * @param context The application context
     * @param permission The permission to check
     * @return true if the permission is granted, false otherwise
     */
    fun hasPermission(context: Context, permission: String): Boolean {
        return context.checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if the current app is running as a system app.
     *
     * @param context The application context
     * @return true if the app is a system app, false otherwise
     */
    fun isSystemApp(context: Context): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if system app", e)
            false
        }
    }

    /**
     * Checks if the current app has the required permissions to use system APIs.
     *
     * @param context The application context
     * @return true if the app has system permissions, false otherwise
     */
    fun hasSystemPermissions(context: Context): Boolean {
        val criticalPermissions = listOf(
            "android.permission.PACKAGE_USAGE_STATS",
            "android.permission.WRITE_SECURE_SETTINGS"
        )

        val hasAllPermissions = criticalPermissions.all { hasPermission(context, it) }
        val isSystemOrRoot = isSystemApp(context) || isRootProcess()

        return hasAllPermissions || isSystemOrRoot
    }

    /**
     * Checks if the current process is running as root.
     *
     * @return true if the process is running as root, false otherwise
     */
    private fun isRootProcess(): Boolean {
        return Process.myUid() == 0
    }

    /**
     * Safely calls a method using reflection to avoid compile-time dependencies on hidden APIs.
     *
     * @param target The target object to call the method on
     * @param methodName The name of the method to call
     * @param parameterTypes The parameter types of the method
     * @param args The arguments to pass to the method
     * @return The result of the method call cast to type T, or null if the call fails
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> callMethodSafely(
        target: Any,
        methodName: String,
        parameterTypes: Array<Class<*>>,
        vararg args: Any?
    ): T? {
        return try {
            // Use getDeclaredMethod to access non-public methods
            val method = target.javaClass.getDeclaredMethod(methodName, *parameterTypes)
            method.isAccessible = true

            // Invoke the method and cast the result
            val result = method.invoke(target, *args)
            result as? T
        } catch (e: Exception) {
            // Log the detailed error for debugging
            Log.e(TAG, "Error calling method $methodName: ${e.cause?.message ?: e.message}", e)
            null
        }
    }
}