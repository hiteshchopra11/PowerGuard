package com.hackathon.powerguard.di

import com.hackathon.powerguard.data.PowerGuardAnalysisRepository
import com.hackathon.powerguard.data.ai.AiRepository
import com.hackathon.powerguard.models.AppUsageData
import com.hackathon.powerguard.models.BatteryOptimizationData
import com.hackathon.powerguard.models.NetworkUsageData
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides repository dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    
    @Provides
    @Singleton
    fun providesPowerGuardAnalysisRepository(
        aiRepository: AiRepository
    ): PowerGuardAnalysisRepository {
        // Using AiRepository (backed by AiInference).
        return PowerGuardAnalysisRepository(aiRepository)
    }
}

/**
 * Repository for app data.
 */
@Singleton
class AppDataRepository @Inject constructor() {
    fun getNetworkUsageData(): List<NetworkUsageData> {
        val currentTime = System.currentTimeMillis()
        return listOf(
            NetworkUsageData("com.android.chrome", "Chrome", 200L, 100L, 50L, currentTime),
            NetworkUsageData("com.google.android.youtube", "YouTube", 300L, 50L, 150L, currentTime),
            NetworkUsageData("com.google.android.gm", "Gmail", 50L, 20L, 10L, currentTime),
            NetworkUsageData("com.google.android.apps.maps", "Maps", 100L, 30L, 20L, currentTime),
            NetworkUsageData("com.others", "Others", 150L, 80L, 60L, currentTime)
        )
    }
    
    fun getAppUsageData(): List<AppUsageData> {
        val currentTime = System.currentTimeMillis()
        return listOf(
            AppUsageData("com.facebook.katana", "Social", 120L, 80L, 30L, 15.0, currentTime),
            AppUsageData("com.microsoft.office", "Productivity", 90L, 40L, 10L, 8.0, currentTime),
            AppUsageData("com.netflix.mediaclient", "Entertainment", 180L, 30L, 5L, 12.0, currentTime),
            AppUsageData("com.supercell.clashofclans", "Games", 60L, 10L, 5L, 10.0, currentTime),
            AppUsageData("com.other.apps", "Other", 30L, 20L, 5L, 5.0, currentTime)
        )
    }
    
    fun getBatteryOptimizationData(): List<BatteryOptimizationData> {
        val currentTime = System.currentTimeMillis()
        return listOf(
            BatteryOptimizationData(75, false, 32.5, 4200, 250L, currentTime),
            BatteryOptimizationData(85, true, 30.0, 4250, 200L, currentTime)
        )
    }
    
    /**
     * Saves usage data for later analysis
     * 
     * @param appUsageData List of app usage data
     * @param batteryInfo Battery optimization data
     * @param networkUsageData List of network usage data
     */
    fun saveUsageData(
        appUsageData: List<AppUsageData>,
        batteryInfo: BatteryOptimizationData,
        networkUsageData: List<NetworkUsageData>
    ) {
        // In a real app, we would store this data in a database
        // For now, we'll just log it
        // TODO: Implement actual data storage
    }
} 