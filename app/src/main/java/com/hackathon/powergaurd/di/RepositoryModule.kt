package com.hackathon.powergaurd.di

import com.hackathon.powergaurd.data.repository.ActionHistoryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideActionHistoryRepository(): ActionHistoryRepository {
        return ActionHistoryRepository()
    }
}
