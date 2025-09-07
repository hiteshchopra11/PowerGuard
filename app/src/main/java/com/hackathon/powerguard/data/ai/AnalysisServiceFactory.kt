package com.hackathon.powerguard.data.ai

import com.hackathon.powerguard.data.preferences.AnalysisPreferences
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Factory for creating the appropriate analysis service based on user preference.
 * This allows runtime switching between Firebase AI and Backend API services.
 */
@Singleton
class AnalysisServiceFactory @Inject constructor(
    @Named("firebase") private val firebaseService: PowerGuardAnalysisService,
    @Named("backend") private val backendService: PowerGuardAnalysisService,
    private val preferences: AnalysisPreferences
) {
    
    /**
     * Gets the current analysis service based on user preference
     */
    fun getCurrentService(): PowerGuardAnalysisService {
        return if (preferences.useBackendApi()) {
            backendService
        } else {
            firebaseService
        }
    }
    
    /**
     * Gets the Firebase AI service directly
     */
    fun getFirebaseService(): PowerGuardAnalysisService = firebaseService
    
    /**
     * Gets the Backend API service directly
     */
    fun getBackendService(): PowerGuardAnalysisService = backendService
}