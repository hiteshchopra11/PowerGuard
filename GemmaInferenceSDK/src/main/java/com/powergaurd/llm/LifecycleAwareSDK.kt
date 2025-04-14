package com.powergaurd.llm

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Helper class for managing the lifecycle of the Gemma Inference SDK in Android applications.
 * This class automatically handles initialization and cleanup based on application lifecycle.
 */
class LifecycleAwareSDK(
    context: Context,
    config: GemmaConfig = GemmaConfig.DEFAULT
) : DefaultLifecycleObserver {
    
    private val sdk = GemmaInferenceSDK(context, config)
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }
    
    /**
     * Called when the application starts.
     * Initializes the SDK if not already initialized.
     */
    override fun onStart(owner: LifecycleOwner) {
        scope.launch {
            sdk.initialize()
        }
    }
    
    /**
     * Called when the application stops.
     * Optionally shuts down the SDK based on configuration.
     */
    override fun onStop(owner: LifecycleOwner) {
        if (sdk.loadingState.value == GemmaInferenceSDK.LoadingState.READY) {
            sdk.shutdown()
        }
    }
    
    /**
     * Unregisters this lifecycle observer.
     * Call this when you no longer need the SDK.
     */
    fun unregister() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
    }
    
    /**
     * Access the underlying SDK instance.
     *
     * @return The GemmaInferenceSDK instance
     */
    fun getSDK(): GemmaInferenceSDK = sdk
    
    companion object {
        /**
         * Creates and returns a LifecycleAwareSDK instance with the default configuration.
         *
         * @param context Application context
         * @return LifecycleAwareSDK instance
         */
        @JvmStatic
        fun create(context: Context): LifecycleAwareSDK {
            return LifecycleAwareSDK(context)
        }
        
        /**
         * Creates and returns a LifecycleAwareSDK instance with a battery-efficient configuration.
         *
         * @param context Application context
         * @return LifecycleAwareSDK instance with battery-efficient configuration
         */
        @JvmStatic
        fun createBatteryEfficient(context: Context): LifecycleAwareSDK {
            return LifecycleAwareSDK(context, GemmaConfig.BATTERY_EFFICIENT)
        }
    }
} 