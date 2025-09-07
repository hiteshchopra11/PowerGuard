package com.hackathon.powerguard

import android.app.Application
import com.google.firebase.FirebaseApp
import com.hackathon.powerguard.data.PowerGuardAnalysisRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PowerGuardApp : Application() {
    @Inject
    lateinit var analysisRepository: PowerGuardAnalysisRepository

    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
    }

    companion object {
        private const val TAG = "PowerGuardApp"
    }
}
