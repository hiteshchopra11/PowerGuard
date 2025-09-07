package com.hackathon.powerguard.data.ai

import com.hackathon.powerguard.data.preferences.AnalysisPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for AnalysisServiceFactory
 */
class AnalysisServiceFactoryTest {

    @Test
    fun `getCurrentService returns Firebase service when useBackendApi is false`() {
        // Given
        val firebaseService = mockk<PowerGuardAnalysisService>()
        val backendService = mockk<PowerGuardAnalysisService>()
        val preferences = mockk<AnalysisPreferences>()
        
        every { preferences.useBackendApi() } returns false
        every { firebaseService.getServiceType() } returns PowerGuardAnalysisService.ServiceType.FIREBASE_AI
        
        val factory = AnalysisServiceFactory(firebaseService, backendService, preferences)
        
        // When
        val result = factory.getCurrentService()
        
        // Then
        assertEquals(firebaseService, result)
        assertEquals(PowerGuardAnalysisService.ServiceType.FIREBASE_AI, result.getServiceType())
    }

    @Test
    fun `getCurrentService returns Backend service when useBackendApi is true`() {
        // Given
        val firebaseService = mockk<PowerGuardAnalysisService>()
        val backendService = mockk<PowerGuardAnalysisService>()
        val preferences = mockk<AnalysisPreferences>()
        
        every { preferences.useBackendApi() } returns true
        every { backendService.getServiceType() } returns PowerGuardAnalysisService.ServiceType.BACKEND_API
        
        val factory = AnalysisServiceFactory(firebaseService, backendService, preferences)
        
        // When
        val result = factory.getCurrentService()
        
        // Then
        assertEquals(backendService, result)
        assertEquals(PowerGuardAnalysisService.ServiceType.BACKEND_API, result.getServiceType())
    }

    @Test
    fun `getFirebaseService always returns Firebase service`() {
        // Given
        val firebaseService = mockk<PowerGuardAnalysisService>()
        val backendService = mockk<PowerGuardAnalysisService>()
        val preferences = mockk<AnalysisPreferences>()
        
        val factory = AnalysisServiceFactory(firebaseService, backendService, preferences)
        
        // When
        val result = factory.getFirebaseService()
        
        // Then
        assertEquals(firebaseService, result)
    }

    @Test
    fun `getBackendService always returns Backend service`() {
        // Given
        val firebaseService = mockk<PowerGuardAnalysisService>()
        val backendService = mockk<PowerGuardAnalysisService>()
        val preferences = mockk<AnalysisPreferences>()
        
        val factory = AnalysisServiceFactory(firebaseService, backendService, preferences)
        
        // When
        val result = factory.getBackendService()
        
        // Then
        assertEquals(backendService, result)
    }
}