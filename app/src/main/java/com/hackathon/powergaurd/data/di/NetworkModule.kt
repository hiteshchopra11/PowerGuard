package com.hackathon.powergaurd.data.di

import com.hackathon.powergaurd.data.remote.PowerGuardApiService
import com.hackathon.powergaurd.data.remote.PowerGuardRepository
import com.hackathon.powergaurd.data.remote.RetrofitClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt module for providing network-related dependencies. */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideRetrofitClient(): RetrofitClient {
        return RetrofitClient()
    }

    @Provides
    @Singleton
    fun providePowerGuardApiService(retrofitClient: RetrofitClient): PowerGuardApiService {
        return retrofitClient.apiService
    }

    @Provides
    @Singleton
    fun providePowerGuardRepository(apiService: PowerGuardApiService): PowerGuardRepository {
        return PowerGuardRepository(apiService)
    }
}
