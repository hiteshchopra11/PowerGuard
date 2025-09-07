package com.hackathon.powerguard.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages preferences for PowerGuard analysis service selection
 */
@Singleton
class AnalysisPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "powerguard_analysis_prefs"
        private const val KEY_USE_BACKEND_API = "use_backend_api"
        private const val DEFAULT_USE_BACKEND_API = false
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Gets whether to use backend API (true) or Firebase AI (false)
     */
    fun useBackendApi(): Boolean {
        return prefs.getBoolean(KEY_USE_BACKEND_API, DEFAULT_USE_BACKEND_API)
    }

    /**
     * Sets whether to use backend API (true) or Firebase AI (false)
     */
    fun setUseBackendApi(useBackend: Boolean) {
        prefs.edit {
            putBoolean(KEY_USE_BACKEND_API, useBackend)
        }
    }
}