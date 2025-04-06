package com.hackathon.powergaurd

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PowerGuardApp : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Log app initialization
        Log.d(TAG, "Initializing PowerGuard App")
        
        // Any necessary initialization can go here
    }

    companion object {
        private const val TAG = "PowerGuardApp"
    }
}
