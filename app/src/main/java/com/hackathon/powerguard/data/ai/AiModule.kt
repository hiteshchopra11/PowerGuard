package com.hackathon.powerguard.data.ai

import android.content.Context
import android.util.Log
import com.hackathon.powerguard.utils.PackageNameResolver
import com.powerguard.llm.AiConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing AI Inference related dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule {
    private const val TAG = "AiModule"
    
    // Standard model names for Gemini API
    private const val MODEL_GEMINI = "gemini-2.0-flash-exp"
    
    /**
     * Provides the AiConfig with appropriate settings for PowerGuard
     */
    @Provides
    @Singleton
    fun provideAiConfig(): AiConfig {
        // Use standard Gemini model names that are supported by Firebase AI Logic
        val modelName = MODEL_GEMINI
        Log.d(TAG, "Using Firebase AI Logic with model: $modelName")
        
        return AiConfig(
            modelName = modelName,
            enableLogging = true,
            temperature = 0.1f,  // Keep low temperature for deterministic responses
            topK = 20,  // Reduced from 40 to 20 for more focused token selection
            topP = 0.7f,  // Reduced from 0.8 to 0.7 for more predictable responses
            
        )
    }

    /**
     * Provides the AiRepository
     */
    @Provides
    @Singleton
    fun provideAiRepository(
        @ApplicationContext context: Context,
        config: AiConfig,
        packageNameResolver: PackageNameResolver
    ): AiRepository {
        return AiRepository(context, config, packageNameResolver)
    }
}