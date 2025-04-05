package com.hackathon.powergaurd.di

import android.content.Context
import com.hackathon.powergaurd.services.ActionableExecutor
import com.hackathon.powergaurd.services.PowerManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun providePowerManager(@ApplicationContext context: Context): PowerManager {
        return PowerManager(context)
    }

    @Provides
    @Singleton
    fun provideActionableExecutor(
            @ApplicationContext context: Context,
            powerManager: PowerManager
    ): ActionableExecutor {
        return ActionableExecutor(context, powerManager)
    }
}
