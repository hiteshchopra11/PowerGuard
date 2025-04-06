package com.hackathon.powerguard.di

import com.hackathon.powerguard.data.remote.repository.ActionHistoryRepository
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
