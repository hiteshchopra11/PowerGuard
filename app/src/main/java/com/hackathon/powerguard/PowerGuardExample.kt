package com.hackathon.powerguard

import android.content.Context
import com.hackathon.powerguard.models.TimeRange
import java.util.Calendar

class PowerGuardExample {
    fun demonstrateApi(context: Context) {
        val optimizer = PowerGuardOptimizer(context)

        // 1. Restrict background for a social media app
        optimizer.setAppBackgroundRestriction("com.example.socialmedia", "strict")

        // 2. Manage wake locks for a music streaming app
        optimizer.manageWakeLock("com.example.musicapp", "timeout", 30 * 60 * 1000) // 30 minutes

        // 3. Restrict background data for a video app during work hours
        val workHours = TimeRange(
            9, 0, // 9:00 AM
            17, 0, // 5:00 PM
            listOf(
                Calendar.MONDAY,
                Calendar.TUESDAY,
                Calendar.WEDNESDAY,
                Calendar.THURSDAY,
                Calendar.FRIDAY
            )
        )
        optimizer.restrictBackgroundData("com.example.videoapp", true)

        // 4. Optimize charging to 80% maximum
        optimizer.optimizeCharging(80)

        // 5. Set sync frequency for email to once per hour
        optimizer.optimizeSyncSchedule("com.google", 60)
    }
} 