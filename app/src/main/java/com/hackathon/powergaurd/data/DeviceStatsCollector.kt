package com.hackathon.powergaurd.data

import android.content.Context
import android.os.BatteryManager
import android.provider.Settings
import com.hackathon.powergaurd.models.AppNetworkInfo
import com.hackathon.powergaurd.models.AppUsageInfo
import com.hackathon.powergaurd.models.BatteryStats
import com.hackathon.powergaurd.models.NetworkUsage
import com.hackathon.powergaurd.models.WakeLockInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class DeviceStatsCollector @Inject constructor(
    private val context: Context
) {
    suspend fun collectAppUsage(): List<AppUsageInfo> {
        // In a real app, we would use UsageStatsManager to collect actual usage stats
        // For the hackathon, we'll simulate data
        return listOf(
            AppUsageInfo(
                packageName = "com.google.android.youtube",
                appName = "YouTube",
                foregroundTimeMs = 3_600_000, // 1 hour
                backgroundTimeMs = 1_800_000, // 30 minutes
                lastUsed = System.currentTimeMillis() - 3_600_000, // 1 hour ago
                launchCount = 5
            ),
            AppUsageInfo(
                packageName = "com.instagram.android",
                appName = "Instagram",
                foregroundTimeMs = 2_700_000, // 45 minutes
                backgroundTimeMs = 5_400_000, // 1.5 hours
                lastUsed = System.currentTimeMillis() - 1_800_000, // 30 minutes ago
                launchCount = 8
            ),
            AppUsageInfo(
                packageName = "com.spotify.music",
                appName = "Spotify",
                foregroundTimeMs = 1_800_000, // 30 minutes
                backgroundTimeMs = 7_200_000, // 2 hours
                lastUsed = System.currentTimeMillis() - 900_000, // 15 minutes ago
                launchCount = 3
            ),
            AppUsageInfo(
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                foregroundTimeMs = 1_200_000, // 20 minutes
                backgroundTimeMs = 43_200_000, // 12 hours
                lastUsed = System.currentTimeMillis() - 600_000, // 10 minutes ago
                launchCount = 12
            ),
            AppUsageInfo(
                packageName = "com.android.chrome",
                appName = "Chrome",
                foregroundTimeMs = 2_400_000, // 40 minutes
                backgroundTimeMs = 300_000, // 5 minutes
                lastUsed = System.currentTimeMillis() - 5_400_000, // 1.5 hours ago
                launchCount = 7
            )
        )
    }

    suspend fun collectBatteryStats(): BatteryStats {
        // In a real app, we would use BatteryManager to collect actual battery stats
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        // For the hackathon, we'll mix real data with simulated data
        return BatteryStats(
            level = if (level > 0) level else Random.nextInt(20, 90),
            temperature = Random.nextFloat() * 10 + 30, // 30-40°C
            isCharging = Random.nextBoolean(),
            chargingType = if (Random.nextBoolean()) "AC" else "USB",
            voltage = Random.nextInt(3700, 4200),
            health = "Good",
            estimatedRemainingTime = if (Random.nextBoolean())
                Random.nextLong(1800000, 86400000) else null // 30 minutes to 24 hours
        )
    }

    suspend fun collectNetworkUsage(): NetworkUsage {
        // In a real app, we would use NetworkStatsManager to collect actual network stats
        // For the hackathon, we'll simulate data
        return NetworkUsage(
            appNetworkUsage = listOf(
                AppNetworkInfo(
                    packageName = "com.google.android.youtube",
                    dataUsageBytes = 150_000_000, // 150MB
                    wifiUsageBytes = 500_000_000 // 500MB
                ),
                AppNetworkInfo(
                    packageName = "com.instagram.android",
                    dataUsageBytes = 85_000_000, // 85MB
                    wifiUsageBytes = 120_000_000 // 120MB
                ),
                AppNetworkInfo(
                    packageName = "com.spotify.music",
                    dataUsageBytes = 45_000_000, // 45MB
                    wifiUsageBytes = 320_000_000 // 320MB
                ),
                AppNetworkInfo(
                    packageName = "com.whatsapp",
                    dataUsageBytes = 12_000_000, // 12MB
                    wifiUsageBytes = 35_000_000 // 35MB
                ),
                AppNetworkInfo(
                    packageName = "com.android.chrome",
                    dataUsageBytes = 67_000_000, // 67MB
                    wifiUsageBytes = 230_000_000 // 230MB
                )
            ),
            wifiConnected = true,
            mobileDataConnected = false,
            networkType = "WiFi"
        )
    }

    suspend fun collectWakeLocks(): List<WakeLockInfo> {
        // In a real app, we would need system privileges to collect wake lock info
        // For the hackathon, we'll simulate data
        return listOf(
            WakeLockInfo(
                packageName = "com.spotify.music",
                wakeLockName = "SpotifyWakeLock",
                timeHeldMs = 7_200_000 // 2 hours
            ),
            WakeLockInfo(
                packageName = "com.google.android.gms",
                wakeLockName = "GmsWakeLock",
                timeHeldMs = 3_600_000 // 1 hour
            ),
            WakeLockInfo(
                packageName = "com.whatsapp",
                wakeLockName = "WhatsAppWakeLock",
                timeHeldMs = 1_800_000 // 30 minutes
            )
        )
    }

    fun getDeviceId(): String {
        // In a real app, we would get a unique but non-PII device identifier
        // For the hackathon, we'll use the Android ID
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }
}
