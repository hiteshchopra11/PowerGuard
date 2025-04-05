package com.hackathon.powergaurd.di

import android.content.Context
import com.hackathon.powergaurd.actionable.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt module for providing actionable-related dependencies. */
@Module
@InstallIn(SingletonComponent::class)
object ActionableModule {

    /** Provides the KillAppHandler as a singleton. */
    @Provides
    @Singleton
    fun provideKillAppHandler(@ApplicationContext context: Context): KillAppHandler {
        return KillAppHandler(context)
    }

    /** Provides the EnableBatterySaverHandler as a singleton. */
    @Provides
    @Singleton
    fun provideEnableBatterySaverHandler(
            @ApplicationContext context: Context
    ): EnableBatterySaverHandler {
        return EnableBatterySaverHandler(context)
    }

    /** Provides the EnableDataSaverHandler as a singleton. */
    @Provides
    @Singleton
    fun provideEnableDataSaverHandler(
            @ApplicationContext context: Context
    ): EnableDataSaverHandler {
        return EnableDataSaverHandler(context)
    }

    /** Provides the ActionableExecutor as a singleton. */
    @Provides
    @Singleton
    fun provideActionableExecutor(
            killAppHandler: KillAppHandler,
            enableBatterySaverHandler: EnableBatterySaverHandler,
            enableDataSaverHandler: EnableDataSaverHandler
    ): ActionableExecutor {
        return ActionableExecutor(killAppHandler, enableBatterySaverHandler, enableDataSaverHandler)
    }
}
