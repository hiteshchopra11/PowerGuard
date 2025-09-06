package com.hackathon.powerguard.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dynamic package name resolver that uses the device's PackageManager
 * to find package names by app display names instead of hardcoded mappings.
 */
@Singleton
class PackageNameResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    // Cache to avoid repeated PackageManager queries
    private var appNameToPackageCache: Map<String, String>? = null
    
    /**
     * Extracts package name from app description by searching installed apps
     * @param description The description containing app name
     * @return Package name if found, null otherwise
     */
    suspend fun extractPackageNameFromDescription(description: String): String? {
        return withContext(Dispatchers.IO) {
            val appCache = getOrBuildAppCache()
            
            // Try exact matches first (case insensitive)
            appCache.entries.find { (appName, _) ->
                description.contains(appName, ignoreCase = true)
            }?.value ?: 
            
            // Try partial matches for common app name variations
            findBestPartialMatch(description, appCache)
        }
    }
    
    /**
     * Gets all installed apps with their display names and package names
     * @return Map of app display name to package name
     */
    suspend fun getAllInstalledApps(): Map<String, String> {
        return withContext(Dispatchers.IO) {
            getOrBuildAppCache()
        }
    }
    
    /**
     * Clears the cache to refresh app list (useful when apps are installed/uninstalled)
     */
    fun clearCache() {
        appNameToPackageCache = null
    }
    
    private fun getOrBuildAppCache(): Map<String, String> {
        return appNameToPackageCache ?: buildAppCache().also { 
            appNameToPackageCache = it 
        }
    }
    
    private fun buildAppCache(): Map<String, String> {
        val packageManager = context.packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        
        return installedApps
            .filter { app ->
                // Only include user-installed apps and system apps that are commonly referenced
                (app.flags and ApplicationInfo.FLAG_SYSTEM == 0) || 
                isCommonSystemApp(app.packageName)
            }
            .mapNotNull { app ->
                try {
                    val appName = packageManager.getApplicationLabel(app).toString()
                    // Filter out generic names
                    if (appName.isNotBlank() && appName != app.packageName) {
                        appName to app.packageName
                    } else null
                } catch (e: Exception) {
                    null
                }
            }
            .toMap()
    }
    
    private fun findBestPartialMatch(description: String, appCache: Map<String, String>): String? {
        val descriptionLower = description.lowercase()
        
        // Look for partial matches with common app name patterns
        return appCache.entries.find { (appName, _) ->
            val appNameLower = appName.lowercase()
            
            // Check if app name is a significant part of the description
            when {
                // Direct substring match
                descriptionLower.contains(appNameLower) -> true
                
                // Handle common variations
                handleCommonVariations(descriptionLower, appNameLower) -> true
                
                // Split and check individual words for compound app names
                appNameLower.split(" ").any { word ->
                    word.length >= 3 && descriptionLower.contains(word)
                } -> true
                
                else -> false
            }
        }?.value
    }
    
    private fun handleCommonVariations(description: String, appName: String): Boolean {
        return when {
            // Handle "Google" prefix variations
            appName.startsWith("google ") && 
            description.contains(appName.removePrefix("google ")) -> true
            
            // Handle "Microsoft" prefix variations  
            appName.startsWith("microsoft ") && 
            description.contains(appName.removePrefix("microsoft ")) -> true
            
            // Handle common abbreviations
            (appName == "whatsapp" && description.contains("whatsapp")) ||
            (appName == "youtube" && description.contains("youtube")) ||
            (appName == "instagram" && description.contains("insta")) ||
            (appName == "facebook" && description.contains("fb")) -> true
            
            else -> false
        }
    }
    
    private fun isCommonSystemApp(packageName: String): Boolean {
        return packageName in setOf(
            "com.android.chrome",
            "com.google.android.gm", // Gmail
            "com.google.android.apps.maps", // Google Maps
            "com.google.android.youtube",
            "com.google.android.apps.photos", // Google Photos
            "com.google.android.calendar",
            "com.google.android.contacts",
            "com.google.android.apps.docs", // Google Drive
            "com.android.settings",
            "com.android.systemui",
            "com.google.android.apps.messaging", // Messages
            "com.android.dialer", // Phone
            "com.google.android.apps.translate"
        )
    }
}