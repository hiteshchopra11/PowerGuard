import android.util.Log
import com.hackathon.powergaurd.data.model.AppInfo
import com.hackathon.powergaurd.data.model.DeviceData

/**
 * Debug utility class for logging detailed information for prompt generation
 */
object PromptDebug {
    private const val TAG = "PowerGuard-PromptDebug"

    fun logAppInfo(app: AppInfo) {
        Log.d(TAG, "App: ${app.appName} (${app.packageName})")
        Log.d(TAG, "  System app: ${app.isSystemApp}")
        Log.d(TAG, "  Battery usage: ${app.batteryUsage}%")
        Log.d(TAG, "  CPU usage: ${app.cpuUsage}%")
        Log.d(TAG, "  Memory: ${formatBytes(app.memoryUsage)}")
        Log.d(TAG, "  Foreground time: ${formatDuration(app.foregroundTime)}")
        Log.d(TAG, "  Background time: ${formatDuration(app.backgroundTime)}")
        Log.d(TAG, "  App bucket: ${app.bucket}")

        // Log the new metrics we added
        Log.d(TAG, "  -- Additional Metrics --")

        // Alarm wakeups
        Log.d(TAG, "  Alarm wakeups: ${app.alarmWakeups}")

        // Data usage
        val dataUsage = app.dataUsage
        Log.d(TAG, "  Data usage: ${formatBytes(dataUsage.rxBytes + dataUsage.txBytes)}")
        Log.d(TAG, "  Foreground data: ${formatBytes(dataUsage.foreground)}")
        Log.d(TAG, "  Background data: ${formatBytes(dataUsage.background)}")
    }

    fun logDeviceData(data: DeviceData) {
        Log.d(TAG, "==== Device Data Summary ====")
        Log.d(TAG, "Device: ${data.deviceInfo.manufacturer} ${data.deviceInfo.model}")
        Log.d(TAG, "OS: Android ${data.deviceInfo.osVersion} (SDK ${data.deviceInfo.sdkVersion})")
        Log.d(TAG, "Battery: ${data.battery.level}% (${data.battery.temperature}°C)")

        // Count apps with significant metrics
        val appsWithAlarms = data.apps.count { it.alarmWakeups > 0 }

        Log.d(TAG, "Apps with alarm wakeups: $appsWithAlarms")

        // Memory stats
        val memoryUsage = (data.memory.totalRam - data.memory.availableRam).toFloat() / data.memory.totalRam * 100
        Log.d(TAG, "Memory: ${formatBytes(data.memory.totalRam - data.memory.availableRam)} of ${formatBytes(data.memory.totalRam)} (${memoryUsage.toInt()}%)")

        // Top battery drainers based on combined metrics
        data.apps.sortedByDescending {
            it.batteryUsage + (it.alarmWakeups * 0.5f)
        }.take(5).forEach { app ->
            Log.d(TAG, "Battery drainer: ${app.appName} - ${app.batteryUsage}%, alarms=${app.alarmWakeups}")
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }
}