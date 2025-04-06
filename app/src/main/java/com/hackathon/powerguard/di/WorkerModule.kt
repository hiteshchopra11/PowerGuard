package com.hackathon.powerguard.di

import android.content.Context
import com.hackathon.powerguard.worker.WorkManagerHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt module for providing worker-related dependencies. */
@Module
@InstallIn(SingletonComponent::class)
object WorkerModule {

    /** Provides the WorkManagerHelper as a singleton. */
    @Provides
    @Singleton
    fun provideWorkManagerHelper(@ApplicationContext context: Context): WorkManagerHelper {
        return WorkManagerHelper(context)
    }
}
