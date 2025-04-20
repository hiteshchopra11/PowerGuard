package com.hackathon.powergaurd.di

import android.app.AlarmManager
import android.content.Context
import com.hackathon.powergaurd.actionable.ActionableExecutor
import com.hackathon.powergaurd.actionable.battery.KillAppHandler
import com.hackathon.powergaurd.actionable.battery.ManageWakeLocksHandler
import com.hackathon.powergaurd.actionable.data.RestrictBackgroundDataHandler
import com.hackathon.powergaurd.actionable.battery.SetStandbyBucketHandler
import com.hackathon.powergaurd.actionable.monitoring.BatteryAlertHandler
import com.hackathon.powergaurd.actionable.monitoring.DataAlertHandler
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

    /** Provides the AlarmManager for monitoring handlers. */
    @Provides
    @Singleton
    fun provideAlarmManager(@ApplicationContext context: Context): AlarmManager {
        return context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    /** Provides the BatteryAlertHandler as a singleton. */
    @Provides
    @Singleton
    fun provideBatteryAlertHandler(
        @ApplicationContext context: Context,
        alarmManager: AlarmManager
    ): BatteryAlertHandler {
        return BatteryAlertHandler(context, alarmManager)
    }

    /** Provides the DataAlertHandler as a singleton. */
    @Provides
    @Singleton
    fun provideDataAlertHandler(
        @ApplicationContext context: Context,
        alarmManager: AlarmManager
    ): DataAlertHandler {
        return DataAlertHandler(context, alarmManager)
    }

    /** Provides the ActionableExecutor as a singleton. */
    @Provides
    @Singleton
    fun provideActionableExecutor(
        killAppHandler: KillAppHandler,
        manageWakeLocksHandler: ManageWakeLocksHandler,
        restrictBackgroundDataHandler: RestrictBackgroundDataHandler,
        setStandbyBucketHandler: SetStandbyBucketHandler,
        batteryAlertHandler: BatteryAlertHandler,
        dataAlertHandler: DataAlertHandler
    ): ActionableExecutor {
        return ActionableExecutor(
            killAppHandler,
            manageWakeLocksHandler,
            restrictBackgroundDataHandler,
            setStandbyBucketHandler,
            batteryAlertHandler,
            dataAlertHandler
        )
    }
}