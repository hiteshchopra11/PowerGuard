package com.hackathon.powerguard.di

import android.content.Context
import com.hackathon.powerguard.PowerGuardOptimizer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppRepository(): AppRepository {
        return AppRepository()
    }

    @Provides
    @Singleton
    fun providePowerGuardOptimizer(@ApplicationContext context: Context): PowerGuardOptimizer {
        return PowerGuardOptimizer(context)
    }
}