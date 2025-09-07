package com.hackathon.powerguard.data.ai

import com.hackathon.powerguard.data.model.AnalysisRepository
import com.hackathon.powerguard.data.model.AnalysisResponse
import com.hackathon.powerguard.data.model.DeviceData

/**
 * Common interface for PowerGuard AI analysis services.
 * Provides a contract for both Firebase AI and Backend API implementations.
 */
interface PowerGuardAnalysisService : AnalysisRepository {
    
    /**
     * Analyzes device data and provides optimization recommendations
     * 
     * @param deviceData Device data to analyze
     * @return Result containing analysis response or error
     */
    override suspend fun analyzeDeviceData(deviceData: DeviceData): Result<AnalysisResponse>
    
    /**
     * Indicates whether this service is available (has network connection, proper config, etc.)
     */
    suspend fun isServiceAvailable(): Boolean
    
    /**
     * Returns the service type for identification
     */
    fun getServiceType(): ServiceType
    
    enum class ServiceType {
        FIREBASE_AI,
        BACKEND_API
    }
}