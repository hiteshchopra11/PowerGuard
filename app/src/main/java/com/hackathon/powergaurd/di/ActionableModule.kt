package com.hackathon.powergaurd.di

import android.content.Context
import com.hackathon.powergaurd.actionable.ActionableExecutor
import com.hackathon.powergaurd.actionable.AppInactiveHandler
import com.hackathon.powergaurd.actionable.EnableBatterySaverHandler
import com.hackathon.powergaurd.actionable.EnableDataSaverHandler
import com.hackathon.powergaurd.actionable.KillAppHandler
import com.hackathon.powergaurd.actionable.StandbyBucketHandler
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

    /** Provides the StandbyBucketHandler as a singleton. */
    @Provides
    @Singleton
    fun provideStandbyBucketHandler(
        @ApplicationContext context: Context
    ): StandbyBucketHandler {
        return StandbyBucketHandler(context)
    }

    /** Provides the AppInactiveHandler as a singleton. */
    @Provides
    @Singleton
    fun provideAppInactiveHandler(
        @ApplicationContext context: Context
    ): AppInactiveHandler {
        return AppInactiveHandler(context)
    }

    /** Provides the ActionableExecutor as a singleton. */
    @Provides
    @Singleton
    fun provideActionableExecutor(
        killAppHandler: KillAppHandler,
        enableBatterySaverHandler: EnableBatterySaverHandler,
        enableDataSaverHandler: EnableDataSaverHandler,
        standbyBucketHandler: StandbyBucketHandler,
        appInactiveHandler: AppInactiveHandler
    ): ActionableExecutor {
        return ActionableExecutor(
            killAppHandler,
            enableBatterySaverHandler,
            enableDataSaverHandler,
            standbyBucketHandler,
            appInactiveHandler
        )
    }
}
