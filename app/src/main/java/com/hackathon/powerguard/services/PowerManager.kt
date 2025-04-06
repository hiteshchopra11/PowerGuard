package com.hackathon.powerguard.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.os.PowerManager as SystemPowerManager

/** Manager class for power-related operations */
@Singleton
class PowerManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val systemPowerManager =
        context.getSystemService(Context.POWER_SERVICE) as SystemPowerManager

    /** Check if the device is in power save mode */
    fun isPowerSaveMode(): Boolean {
        return systemPowerManager.isPowerSaveMode
    }

    /** Check if the app is ignored for battery optimizations */
    fun isIgnoringBatteryOptimizations(packageName: String): Boolean {
        return systemPowerManager.isIgnoringBatteryOptimizations(packageName)
    }
}
