package com.hackathon.powerguard.data.model

/**
 * Repository interface for device data analysis
 */
interface AnalysisRepository {
    /**
     * Analyzes device data and provides optimization recommendations
     * 
     * @param deviceData Device data to analyze
     * @return Result containing analysis response or error
     */
    suspend fun analyzeDeviceData(deviceData: DeviceData): Result<AnalysisResponse>
} 