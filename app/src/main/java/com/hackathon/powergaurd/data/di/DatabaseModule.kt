package com.hackathon.powergaurd.data.di

import android.content.Context
import com.hackathon.powergaurd.data.local.InsightsDBRepository
import com.hackathon.powergaurd.data.local.PowerGuardDatabase
import com.hackathon.powergaurd.data.local.dao.DeviceInsightDao
import com.hackathon.powergaurd.data.remote.PowerGuardRepository
import com.hackathon.powergaurd.domain.usecase.AnalyzeDeviceDataUseCase
import com.hackathon.powergaurd.domain.usecase.GetAllActionableUseCase
import com.hackathon.powergaurd.domain.usecase.GetCurrentInsightsUseCase
import com.hackathon.powergaurd.domain.usecase.GetPastInsightsUseCase
import com.hackathon.powergaurd.domain.usecase.SaveInsightsUseCase
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
    fun providePowerGuardDatabase(@ApplicationContext context: Context): PowerGuardDatabase {
        return PowerGuardDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideDeviceInsightDao(database: PowerGuardDatabase): DeviceInsightDao {
        return database.deviceInsightDao()
    }

    @Provides
    @Singleton
    fun provideInsightsDBRepository(deviceInsightDao: DeviceInsightDao): InsightsDBRepository {
        return InsightsDBRepository(deviceInsightDao)
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
        repository: PowerGuardRepository,
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