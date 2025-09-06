package com.hackathon.powerguard.di

import android.content.Context
import com.hackathon.powerguard.collector.UsageDataCollector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt module for providing data collection dependencies. */
@Module
@InstallIn(SingletonComponent::class)
object CollectorModule {

    /** Provides the UsageDataCollector as a singleton. */
    @Provides
    @Singleton
    fun provideUsageDataCollector(@ApplicationContext context: Context): UsageDataCollector {
        return UsageDataCollector(context)
    }
}
