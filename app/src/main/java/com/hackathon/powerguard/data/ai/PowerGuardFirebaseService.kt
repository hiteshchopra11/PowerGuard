package com.hackathon.powerguard.data.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.hackathon.powerguard.data.model.AnalysisResponse
import com.hackathon.powerguard.data.model.DeviceData
import com.hackathon.powerguard.utils.PackageNameResolver
import com.powerguard.llm.AiConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase AI implementation of PowerGuardAnalysisService.
 * Wraps the existing AiRepository to implement the common interface.
 */
@Singleton
class PowerGuardFirebaseService @Inject constructor(
    context: Context,
    config: AiConfig,
    packageNameResolver: PackageNameResolver
) : PowerGuardAnalysisService {

    // Delegate to the existing AiRepository implementation
    private val aiRepository = AiRepository(context, config, packageNameResolver)
    private val context = context

    override suspend fun analyzeDeviceData(deviceData: DeviceData): Result<AnalysisResponse> {
        return aiRepository.analyzeDeviceData(deviceData)
    }

    override suspend fun isServiceAvailable(): Boolean {
        return isNetworkAvailable()
    }

    override fun getServiceType(): PowerGuardAnalysisService.ServiceType {
        return PowerGuardAnalysisService.ServiceType.FIREBASE_AI
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        
        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}