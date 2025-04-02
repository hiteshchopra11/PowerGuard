package com.hackathon.powergaurd.data

import com.hackathon.powergaurd.models.ActionResponse
import com.hackathon.powergaurd.models.DeviceData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackendService @Inject constructor() {

    suspend fun sendDataForAnalysis(deviceData: DeviceData): ActionResponse {
        // In a real app, this would make an API call to the LLM-powered backend
        // For the hackathon, we'll simulate a response

        // Simulate network delay
        kotlinx.coroutines.delay(1000)

        return ActionResponse(
            actionables = listOf(
                ActionResponse.Actionable(
                    type = "app_mode_change",
                    app = "com.google.android.youtube",
                    newMode = "strict"
                ),
                ActionResponse.Actionable(
                    type = "disable_wakelock",
                    app = "com.spotify.music"
                ),
                ActionResponse.Actionable(
                    type = "restrict_background_data",
                    app = "com.instagram.android",
                    enabled = true
                )
            ),
            summary = "PowerGuard AI recommended restricting YouTube, disabling Spotify wake locks, and limiting Instagram background data. These changes could improve your battery life by up to 25%.",
            usagePatterns = mapOf(
                "com.google.android.youtube" to "High video streaming usage during evenings",
                "com.spotify.music" to "Keeps wake locks active even when paused",
                "com.instagram.android" to "Frequent background data refreshes"
            ),
            timestamp = System.currentTimeMillis()
        )
    }
}