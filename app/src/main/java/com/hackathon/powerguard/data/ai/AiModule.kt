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
import java.util.Properties
import javax.inject.Singleton

/**
 * Hilt module for providing AI Inference related dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule {
    private const val TAG = "AiModule"
    private const val PLACEHOLDER_KEY = "null"
    
    // Standard model names for Gemini API
    private const val MODEL_GEMINI_PRO = "models/gemini-2.0-flash"
    
    /**
     * Provides the AiConfig with appropriate settings for PowerGuard
     */
    @Provides
    @Singleton
    fun provideAiConfig(@ApplicationContext context: Context): AiConfig {
        // Try to load API key from properties
        val apiKey = try {
            val properties = Properties()
            context.assets.open("ai_api.properties").use { 
                properties.load(it)
            }
            val key = properties.getProperty("API_KEY", "")
            if (key.isBlank() || key == PLACEHOLDER_KEY) {
                Log.w(TAG, "Invalid API key in properties file, using default key")
                ""  // Use empty key to force error and detailed message
            } else {
                Log.d(TAG, "Using API key from properties file")
                key
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load API key from properties file: ${e.message}")
            ""  // Use empty key to force error and detailed message
        }
        
        if (apiKey.isBlank()) {
            Log.e(TAG, "API key is missing! Get a valid key from https://aistudio.google.com/app/apikey")
        }
        
        // Choose model based on device capabilities
        val isLowEndDevice = isLowEndDevice(context)
        
        // Use standard Gemini model names that are supported by the API
        val modelName = MODEL_GEMINI_PRO
        Log.d(TAG, "Using model: $modelName")
        
        return AiConfig(
            modelName = modelName,
            apiKey = apiKey, 
            enableLogging = true,
            maxTokens = 50000,  // Further reduced from 128 to 64 to prevent MAX_TOKENS errors
            temperature = 0.1f,  // Keep low temperature for deterministic responses
            topK = 20,  // Reduced from 40 to 20 for more focused token selection
            topP = 0.7f,  // Reduced from 0.8 to 0.7 for more predictable responses
            autoInitialize = true,
            releaseOnBackground = true  // Always release on background to free resources
        )
    }

    /**
     * Provides the JsonParser for parsing JSON responses
     */
    @Provides
    @Singleton
    fun provideJsonParser(): JsonParser {
        return JsonParser()
    }

    /**
     * Provides the DeviceInfoProvider
     */
    @Provides
    @Singleton
    fun provideDeviceInfoProvider(@ApplicationContext context: Context): DeviceInfoProvider {
        return DeviceInfoProvider(context)
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
    
    /**
     * Basic heuristic to detect if this is a low-end device
     */
    private fun isLowEndDevice(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        // Devices with less than 3GB RAM are considered low-end for LLM inference
        return memoryInfo.totalMem < 3L * 1024L * 1024L * 1024L
    }
}