package com.hackathon.powergaurd.data.model

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