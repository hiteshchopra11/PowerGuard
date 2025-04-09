package com.hackathon.powergaurd.di

import android.content.Context
import com.hackathon.powergaurd.actionable.ActionableExecutor
import com.hackathon.powergaurd.actionable.KillAppHandler
import com.hackathon.powergaurd.actionable.ManageWakeLocksHandler
import com.hackathon.powergaurd.actionable.RestrictBackgroundDataHandler
import com.hackathon.powergaurd.actionable.SetStandbyBucketHandler
import com.hackathon.powergaurd.actionable.ThrottleCpuUsageHandler
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

    /** Provides the ManageWakeLocksHandler as a singleton. */
    @Provides
    @Singleton
    fun provideManageWakeLocksHandler(
        @ApplicationContext context: Context
    ): ManageWakeLocksHandler {
        return ManageWakeLocksHandler(context)
    }

    /** Provides the RestrictBackgroundDataHandler as a singleton. */
    @Provides
    @Singleton
    fun provideRestrictBackgroundDataHandler(
        @ApplicationContext context: Context
    ): RestrictBackgroundDataHandler {
        return RestrictBackgroundDataHandler(context)
    }

    /** Provides the SetStandbyBucketHandler as a singleton. */
    @Provides
    @Singleton
    fun provideSetStandbyBucketHandler(
        @ApplicationContext context: Context
    ): SetStandbyBucketHandler {
        return SetStandbyBucketHandler(context)
    }


    /** Provides the ThrottleCpuUsageHandler as a singleton. */
    @Provides
    @Singleton
    fun provideThrottleCpuUsageHandler(
        @ApplicationContext context: Context
    ): ThrottleCpuUsageHandler {
        return ThrottleCpuUsageHandler(context)
    }

    /** Provides the ActionableExecutor as a singleton. */
    @Provides
    @Singleton
    fun provideActionableExecutor(
        killAppHandler: KillAppHandler,
        manageWakeLocksHandler: ManageWakeLocksHandler,
        restrictBackgroundDataHandler: RestrictBackgroundDataHandler,
        setStandbyBucketHandler: SetStandbyBucketHandler,
        throttleCpuUsageHandler: ThrottleCpuUsageHandler
    ): ActionableExecutor {
        return ActionableExecutor(
            killAppHandler,
            manageWakeLocksHandler,
            restrictBackgroundDataHandler,
            setStandbyBucketHandler,
            throttleCpuUsageHandler
        )
    }
}
