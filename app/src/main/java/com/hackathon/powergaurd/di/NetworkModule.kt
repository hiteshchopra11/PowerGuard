package com.hackathon.powergaurd.di

import com.hackathon.powergaurd.network.ApiConfig
import com.hackathon.powergaurd.network.ApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt module for providing network-related dependencies. */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** Provides the API service as a singleton. */
    @Provides
    @Singleton
    fun provideApiService(apiConfig: ApiConfig): ApiService {
        return apiConfig.createApiService()
    }
}
