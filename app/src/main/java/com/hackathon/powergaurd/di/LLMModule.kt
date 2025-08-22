package com.hackathon.powergaurd.di

import com.hackathon.powergaurd.llm.DummyLLMService
import com.hackathon.powergaurd.llm.LLMService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Dagger module for LLM-related dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LLMModule {
    
    companion object {
        /**
         * Determines whether to use the real Gemma implementation or the dummy one for testing
         * In a real app, this might come from a feature flag or build config
         */
        @Provides
        @Named("useRealLLM")
        fun provideUseRealLLM(): Boolean {
            // Set to true to use the real LLM implementation
            return true
        }
        
        /**
         * Provides the appropriate LLM service based on the useRealLLM flag
         */
        @Provides
        @Singleton
        fun provideLLMService(
            @Named("useRealLLM") useRealLLM: Boolean,
            dummyService: DummyLLMService
        ): LLMService {
            return dummyService // Temporarily using dummy service while fixing GemmaLLMService
        }
    }
    
    // The abstract binding is removed since we're providing the service
    // through the companion object method
} 