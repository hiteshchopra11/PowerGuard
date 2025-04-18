package com.hackathon.powergaurd.llm

import android.util.Log
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A dummy implementation of LLMService for testing
 * Replace this with your actual LLM integration
 */
@Singleton
class DummyLLMService @Inject constructor() : LLMService {
    
    companion object {
        private const val TAG = "PowerGuard-DummyLLM"
    }
    
    /**
     * Simulates LLM completion
     * @param prompt The prompt to send to the LLM
     * @return A dummy response
     */
    override suspend fun getCompletion(prompt: String): String {
        // Log the prompt for debugging
        Log.d(TAG, "Prompt: $prompt")
        
        // Simulate network delay
        delay(500)
        
        // Return dummy responses based on prompt content
        return when {
            prompt.contains("category") && prompt.contains("extract") -> {
                // This is the first LLM query (QueryAnalyzer)
                val lowercasePrompt = prompt.lowercase()
                
                // First check for Category 2: Predictive Queries - these take priority
                if (lowercasePrompt.contains("can i") || 
                     lowercasePrompt.contains("will i") || 
                     lowercasePrompt.contains("is my") || 
                     lowercasePrompt.contains("do i") || 
                     lowercasePrompt.contains("enough") ||
                     (lowercasePrompt.contains("given") && lowercasePrompt.contains("current")) ||
                     (lowercasePrompt.contains("watch") && lowercasePrompt.contains("hours"))) {
                    
                    val app = when {
                        lowercasePrompt.contains("netflix") -> "Netflix"
                        lowercasePrompt.contains("youtube") -> "YouTube"
                        lowercasePrompt.contains("spotify") -> "Spotify"
                        lowercasePrompt.contains("maps") -> "Maps"
                        lowercasePrompt.contains("call of duty") -> "Call of Duty"
                        else -> "Unknown App"
                    }
                    
                    val appCategory = when (app) {
                        "Netflix", "YouTube" -> "streaming"
                        "Spotify" -> "music"
                        "Maps" -> "navigation"
                        "Call of Duty" -> "games"
                        else -> "unknown"
                    }
                    
                    val resourceType = when {
                        lowercasePrompt.contains("battery") -> "battery"
                        lowercasePrompt.contains("data") -> "data"
                        else -> "battery"
                    }
                    
                    // Extract duration
                    val hourPattern = "(\\d+)\\s*hour".toRegex()
                    val minutePattern = "(\\d+)\\s*minute".toRegex()
                    
                    val hourMatch = hourPattern.find(lowercasePrompt)
                    val minuteMatch = minutePattern.find(lowercasePrompt)
                    
                    val duration = when {
                        hourMatch != null -> {
                            val hours = hourMatch.groupValues[1].toIntOrNull() ?: 1
                            """
                            "duration": {
                                "value": $hours,
                                "unit": "hours"
                            }
                            """
                        }
                        minuteMatch != null -> {
                            val minutes = minuteMatch.groupValues[1].toIntOrNull() ?: 30
                            """
                            "duration": {
                                "value": $minutes,
                                "unit": "minutes"
                            }
                            """
                        }
                        else -> {
                            """
                            "duration": {
                                "value": 1,
                                "unit": "hours"
                            }
                            """
                        }
                    }
                    
                    """
                    {
                        "category": 2,
                        "extractedParams": {
                            "apps": ["$app"],
                            "app_categories": ["$appCategory"],
                            $duration,
                            "resource_type": ["$resourceType"]
                        }
                    }
                    """.trimIndent()
                }
                // Category 1: Information Queries
                else if (lowercasePrompt.contains("show") || 
                    lowercasePrompt.contains("list") || 
                    lowercasePrompt.contains("which") || 
                    lowercasePrompt.contains("what") || 
                    lowercasePrompt.contains("how much")) {
                    
                    val resourceType = when {
                        lowercasePrompt.contains("battery") -> "battery"
                        lowercasePrompt.contains("data") -> "data"
                        lowercasePrompt.contains("memory") -> "memory"
                        else -> "battery"
                    }
                    
                    val limit = when {
                        lowercasePrompt.contains("top 3") || lowercasePrompt.contains("top three") -> 3
                        lowercasePrompt.contains("top 5") || lowercasePrompt.contains("top five") -> 5
                        lowercasePrompt.contains("top 10") || lowercasePrompt.contains("top ten") -> 10
                        else -> null
                    }
                    
                    val timePeriodJson = if (lowercasePrompt.contains("today") || 
                        lowercasePrompt.contains("24 hours") || 
                        lowercasePrompt.contains("yesterday")) {
                        """
                        ,"time_period": {
                            "value": 1,
                            "unit": "day"
                        }
                        """
                    } else ""
                    
                    """
                    {
                        "category": 1,
                        "extractedParams": {
                            "resource_type": ["$resourceType"]${if (limit != null) ", \"limit\": $limit" else ""}$timePeriodJson
                        }
                    }
                    """.trimIndent()
                }
                // Category 3: Optimization Requests
                else if (lowercasePrompt.contains("optimize") || 
                         lowercasePrompt.contains("save") || 
                         lowercasePrompt.contains("preserve") || 
                         lowercasePrompt.contains("make") || 
                         lowercasePrompt.contains("last")) {
                    
                    val resourceType = when {
                        lowercasePrompt.contains("battery") || lowercasePrompt.contains("power") -> "battery"
                        lowercasePrompt.contains("data") -> "data"
                        else -> "battery"
                    }
                    
                    val context = when {
                        lowercasePrompt.contains("traveling") || lowercasePrompt.contains("trip") -> "traveling"
                        lowercasePrompt.contains("commute") -> "commute"
                        lowercasePrompt.contains("work") -> "work"
                        lowercasePrompt.contains("gaming") || lowercasePrompt.contains("game") -> "gaming"
                        else -> "general"
                    }
                    
                    val priorityApps = mutableListOf<String>()
                    if (lowercasePrompt.contains("maps") || lowercasePrompt.contains("navigation")) {
                        priorityApps.add("Maps")
                    }
                    if (lowercasePrompt.contains("messaging") || lowercasePrompt.contains("whatsapp")) {
                        priorityApps.add("WhatsApp")
                    }
                    if (lowercasePrompt.contains("essential")) {
                        priorityApps.add("Phone")
                        priorityApps.add("Messages")
                    }
                    if (lowercasePrompt.contains("instagram")) {
                        priorityApps.add("Instagram")
                    }
                    
                    val priorityCategories = mutableListOf<String>()
                    if (lowercasePrompt.contains("messaging")) {
                        priorityCategories.add("messaging")
                    }
                    if (lowercasePrompt.contains("navigation")) {
                        priorityCategories.add("navigation")
                    }
                    
                    val priorityAppsJson = if (priorityApps.isNotEmpty()) {
                        """, "priority_apps": ${priorityApps.map { "\"$it\"" }}"""
                    } else ""
                    
                    val priorityCategoriesJson = if (priorityCategories.isNotEmpty()) {
                        """, "priority_apps_categories": ${priorityCategories.map { "\"$it\"" }}"""
                    } else ""
                    
                    """
                    {
                        "category": 3,
                        "extractedParams": {
                            "resource_type": ["$resourceType"],
                            "context": "$context"$priorityAppsJson$priorityCategoriesJson
                        }
                    }
                    """.trimIndent()
                }
                // Category 4: Monitoring Triggers
                else if (lowercasePrompt.contains("notify") || 
                         lowercasePrompt.contains("alert") || 
                         lowercasePrompt.contains("warn") || 
                         lowercasePrompt.contains("tell") || 
                         lowercasePrompt.contains("let me know")) {
                    
                    val resourceType = when {
                        lowercasePrompt.contains("battery") -> "battery"
                        lowercasePrompt.contains("data") -> "data"
                        else -> "battery"
                    }
                    
                    val thresholds = mutableMapOf<String, Int>()
                    
                    // Extract battery percentage
                    val batteryPattern = "(\\d+)%".toRegex()
                    val batteryMatch = batteryPattern.find(lowercasePrompt)
                    if (batteryMatch != null) {
                        val percentage = batteryMatch.groupValues[1].toIntOrNull() ?: 20
                        thresholds["battery"] = percentage
                    } else if (lowercasePrompt.contains("battery")) {
                        thresholds["battery"] = 20
                    }
                    
                    // Extract data limit
                    val gbPattern = "(\\d+)\\s*gb".toRegex()
                    val mbPattern = "(\\d+)\\s*mb".toRegex()
                    
                    val gbMatch = gbPattern.find(lowercasePrompt)
                    val mbMatch = mbPattern.find(lowercasePrompt)
                    
                    if (gbMatch != null) {
                        val gb = gbMatch.groupValues[1].toIntOrNull() ?: 1
                        thresholds["data"] = gb * 1000
                    } else if (mbMatch != null) {
                        val mb = mbMatch.groupValues[1].toIntOrNull() ?: 500
                        thresholds["data"] = mb
                    } else if (lowercasePrompt.contains("data")) {
                        thresholds["data"] = 1000
                    }
                    
                    val apps = mutableListOf<String>()
                    if (lowercasePrompt.contains("spotify")) {
                        apps.add("Spotify")
                    }
                    if (lowercasePrompt.contains("tiktok")) {
                        apps.add("TikTok")
                    }
                    if (lowercasePrompt.contains("chrome")) {
                        apps.add("Chrome")
                    }
                    if (lowercasePrompt.contains("video call")) {
                        apps.add("Zoom")
                    }
                    
                    val conditionType = when {
                        lowercasePrompt.contains("while") -> "while_using"
                        lowercasePrompt.contains("exceeds") || lowercasePrompt.contains("more than") -> "exceeds_usage"
                        else -> "reaches_threshold"
                    }
                    
                    val thresholdsJson = if (thresholds.isNotEmpty()) {
                        """, "thresholds": {
                            ${thresholds.entries.joinToString(", ") { "\"${it.key}\": ${it.value}" }}
                        }"""
                    } else ""
                    
                    val appsJson = if (apps.isNotEmpty()) {
                        """, "apps": [${apps.joinToString(", ") { "\"$it\"" }}]"""
                    } else ""
                    
                    """
                    {
                        "category": 4,
                        "extractedParams": {
                            "resource_type": ["$resourceType"]$thresholdsJson$appsJson,
                            "condition_type": "$conditionType"
                        }
                    }
                    """.trimIndent()
                }
                // Default fallback
                else {
                    """
                    {
                        "category": 1,
                        "extractedParams": {
                            "resource_type": ["battery"]
                        }
                    }
                    """.trimIndent()
                }
            }
            prompt.contains("INFORMATION query") -> {
                // This is an information query response
                if (prompt.contains("data")) {
                    "The top data-consuming apps are: 1. YouTube (450MB), 2. TikTok (320MB), 3. Chrome (180MB), 4. Instagram (120MB), 5. Spotify (90MB)."
                } else if (prompt.contains("memory")) {
                    "Your phone used an average of 3.2GB of memory in the last 24 hours. Top memory users: Chrome (420MB), Facebook (350MB), Maps (280MB)."
                } else {
                    "The top battery-consuming apps are: 1. TikTok (15%), 2. Chrome (12%), 3. Maps (8%), 4. Facebook (6%), 5. Gmail (4%)."
                }
            }
            prompt.contains("PREDICTIVE query") -> {
                // This is a predictive query response
                if (prompt.contains("Netflix") || prompt.contains("YouTube")) {
                    "Yes, you can stream video for the requested duration with your current battery level (68%). Confidence: High. Your battery should last approximately 5 more hours at the current usage rate."
                } else if (prompt.contains("Maps") || prompt.contains("navigation")) {
                    "Yes, you have enough data for your commute. Maps typically uses about 20MB per hour, and you have 2.5GB remaining in your plan. Confidence: High."
                } else if (prompt.contains("Spotify") || prompt.contains("music")) {
                    "Yes, you can stream music for 4 hours. Your battery is at 53% and Spotify typically consumes about 10% battery per hour when streaming. Confidence: Medium."
                } else if (prompt.contains("game") || prompt.contains("gaming")) {
                    "Yes, but with caution. Your battery is at 42% and gaming is power-intensive. You should be able to play for 45 minutes, but I recommend connecting to a charger. Confidence: Medium."
                } else {
                    "Based on your current resources and historical usage patterns, you should have sufficient battery/data for your planned activity. Confidence: Medium."
                }
            }
            prompt.contains("OPTIMIZATION request") -> {
                // This is an optimization request response
                if (prompt.contains("travel") || prompt.contains("trip")) {
                    """
                    Here are 4 ways to optimize your battery while traveling:
                    1. Enable Battery Saver mode (saves ~25%)
                    2. Reduce screen brightness to 50% (saves ~15%)
                    3. Put unused apps in deep sleep (saves ~10%)
                    4. Turn off background data for non-essential apps (saves ~8%)
                    
                    Note: Maps will continue running as requested, but may consume significant battery when navigating.
                    """.trimIndent()
                } else if (prompt.contains("data")) {
                    """
                    To make your data last longer while keeping messaging apps working:
                    1. Set all video apps to "Data Saver" mode (saves ~40%)
                    2. Disable background data for social media apps (saves ~20%)
                    3. Pre-download music/podcasts over WiFi (saves ~15%)
                    4. Use lighter versions of apps where available (saves ~10%)
                    5. Set auto-updates to WiFi only (saves ~5%)
                    
                    Your messaging apps will continue to work normally.
                    """.trimIndent()
                } else {
                    """
                    Here are 5 ways to save battery:
                    1. Reduce screen brightness (saves ~15%)
                    2. Enable dark mode (saves ~12% on OLED screens)
                    3. Close Chrome and TikTok (saves ~20%)
                    4. Disable unnecessary location services (saves ~8%)
                    5. Enable Battery Saver mode (saves ~15%)
                    
                    Priority apps will continue to function normally.
                    """.trimIndent()
                }
            }
            prompt.contains("MONITORING trigger") -> {
                // This is a monitoring trigger response
                if (prompt.contains("battery") && prompt.contains("Spotify")) {
                    "I'll notify you when your battery reaches 20% while using Spotify. This will help you avoid unexpected battery drain during music playback."
                } else if (prompt.contains("data") && prompt.contains("3GB")) {
                    "Alert set: You'll be notified when your data usage exceeds 3GB today. Your current usage is 1.8GB (60% of your alert threshold)."
                } else if (prompt.contains("TikTok") && prompt.contains("data")) {
                    "Monitoring activated: You'll be alerted if TikTok uses more than 500MB of data. TikTok has used 210MB so far today."
                } else if (prompt.contains("Chrome") && prompt.contains("battery")) {
                    "Alert set: You'll be notified when Chrome consumes more than 15% of your battery. Currently, Chrome accounts for 7% of your battery usage."
                } else if (prompt.contains("video call")) {
                    "Monitoring activated: You'll be alerted when your battery drops below 30% during video calls to avoid unexpected disconnections."
                } else {
                    "Monitoring alert has been set based on your specifications. You'll be notified when the specified condition is met."
                }
            }
            else -> {
                // Generic response
                "I analyzed your device data and found some battery-consuming apps you might want to check. The top 3 are TikTok, Chrome, and Maps."
            }
        }
    }
} 