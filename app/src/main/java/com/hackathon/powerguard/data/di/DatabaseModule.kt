package com.hackathon.powerguard.data.di

import android.content.Context
import com.hackathon.powerguard.data.local.ActionablesDBRepository
import com.hackathon.powerguard.data.local.InsightsDBRepository
import com.hackathon.powerguard.data.local.PowerGuardDatabase
import com.hackathon.powerguard.data.local.dao.DeviceActionableDao
import com.hackathon.powerguard.data.local.dao.DeviceInsightDao
import com.hackathon.powerguard.data.PowerGuardAnalysisRepository
import com.hackathon.powerguard.domain.usecase.AnalyzeDeviceDataUseCase
import com.hackathon.powerguard.domain.usecase.GetAllActionableUseCase
import com.hackathon.powerguard.domain.usecase.GetCurrentInsightsUseCase
import com.hackathon.powerguard.domain.usecase.GetPastInsightsUseCase
import com.hackathon.powerguard.domain.usecase.SaveInsightsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // Database related providers
    @Provides
    @Singleton
    fun provideDeviceInsightDao(database: PowerGuardDatabase): DeviceInsightDao {
        return database.deviceInsightDao()
    }
    
    @Provides
    @Singleton
    fun provideDeviceActionableDao(database: PowerGuardDatabase): DeviceActionableDao {
        return database.deviceActionableDao()
    }

    @Provides
    @Singleton
    fun provideInsightsDBRepository(deviceInsightDao: DeviceInsightDao): InsightsDBRepository {
        return InsightsDBRepository(deviceInsightDao)
    }
    
    @Provides
    @Singleton
    fun provideActionablesDBRepository(deviceActionableDao: DeviceActionableDao): ActionablesDBRepository {
        return ActionablesDBRepository(deviceActionableDao)
    }

    // Use case providers
    @Provides
    @Singleton
    fun provideSaveInsightsUseCase(insightsDBRepository: InsightsDBRepository): SaveInsightsUseCase {
        return SaveInsightsUseCase(insightsDBRepository)
    }

    @Provides
    @Singleton
    fun provideGetPastInsightsUseCase(insightsDBRepository: InsightsDBRepository): GetPastInsightsUseCase {
        return GetPastInsightsUseCase(insightsDBRepository)
    }

    @Provides
    @Singleton
    fun provideAnalyzeDeviceDataUseCase(
        repository: PowerGuardAnalysisRepository,
        saveInsightsUseCase: SaveInsightsUseCase
    ): AnalyzeDeviceDataUseCase {
        return AnalyzeDeviceDataUseCase(repository, saveInsightsUseCase)
    }

    @Provides
    @Singleton
    fun provideGetAllActionableUseCase(): GetAllActionableUseCase {
        return GetAllActionableUseCase()
    }

    @Provides
    @Singleton
    fun provideGetCurrentInsightsUseCase(): GetCurrentInsightsUseCase {
        return GetCurrentInsightsUseCase()
    }
}