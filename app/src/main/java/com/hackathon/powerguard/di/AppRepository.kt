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