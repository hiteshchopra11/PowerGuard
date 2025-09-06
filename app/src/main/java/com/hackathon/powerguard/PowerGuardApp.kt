package com.hackathon.powerguard

import android.app.Application
import android.util.Log
import com.hackathon.powerguard.data.PowerGuardAnalysisRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PowerGuardApp : Application() {
    @Inject
    lateinit var analysisRepository: PowerGuardAnalysisRepository
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        
        // Log app initialization
        Log.d(TAG, "Initializing PowerGuard App")
        
        // Initialize Gemma SDK
        applicationScope.launch {
            val success = analysisRepository.initializeGemma()
            Log.d(TAG, "Gemma SDK initialization ${if (success) "successful" else "failed"}")
        }
    }
    
    override fun onTerminate() {
        super.onTerminate()
        
        // Shutdown Gemma SDK
        analysisRepository.shutdownGemma()
        Log.d(TAG, "Gemma SDK resources released")
    }

    companion object {
        private const val TAG = "PowerGuardApp"
    }
}
