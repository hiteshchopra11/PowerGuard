package com.hackathon.powergaurd.di

import android.content.Context
import com.hackathon.powergaurd.collector.UsageDataCollector
import com.hackathon.powergaurd.data.local.PowerGuardDatabase
import com.hackathon.powergaurd.data.local.dao.DeviceInsightDao
import com.hackathon.powergaurd.data.local.dao.DeviceActionableDao
import com.hackathon.powergaurd.data.remote.PowerGuardApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun providePowerGuardDatabase(@ApplicationContext context: Context): PowerGuardDatabase {
        return PowerGuardDatabase.getInstance(context)
    }
    
    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }
}