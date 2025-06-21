package com.hackathon.powergaurd.data.gemma

import android.content.Context
import android.os.Build
import javax.inject.Inject

/**
 * Utility class for retrieving device information
 */
class DeviceInfoProvider @Inject constructor(
    private val context: Context
) {
    /**
     * Gets the device manufacturer
     */
    fun getDeviceManufacturer(): String {
        return Build.MANUFACTURER
    }
    
    /**
     * Gets the device model
     */
    fun getDeviceModel(): String {
        return Build.MODEL
    }
    
    /**
     * Gets the Android OS version
     */
    fun getOsVersion(): String {
        return Build.VERSION.RELEASE
    }
    
    /**
     * Gets the Android SDK version
     */
    fun getSdkVersion(): Int {
        return Build.VERSION.SDK_INT
    }
    
    /**
     * Gets the device language
     */
    fun getDeviceLanguage(): String {
        return context.resources.configuration.locales[0].language
    }
    
    /**
     * Gets the device country
     */
    fun getDeviceCountry(): String {
        return context.resources.configuration.locales[0].country
    }
} 