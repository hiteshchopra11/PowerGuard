package com.hackathon.powerguard.data.network.mapper

import com.hackathon.powerguard.data.model.*
import com.hackathon.powerguard.data.network.model.*

/**
 * Maps between domain models and API models
 */
object ModelMapper {
    
    /**
     * Converts DeviceData to ApiRequest for backend API
     */
    fun DeviceData.toApiRequest(): ApiRequest {
        return ApiRequest(
            deviceId = this.deviceId,
            timestamp = this.timestamp,
            battery = this.battery.toApiBatteryInfo(),
            memory = this.memory.toApiMemoryInfo(),
            cpu = this.cpu.toApiCpuInfo(),
            network = this.network.toApiNetworkInfo(),
            apps = this.apps.map { it.toApiAppInfo() },
            prompt = this.prompt
        )
    }
    
    /**
     * Converts ApiResponse to AnalysisResponse for domain usage
     */
    fun ApiResponse.toAnalysisResponse(): AnalysisResponse {
        return AnalysisResponse(
            id = this.id,
            success = this.success,
            timestamp = this.timestamp.toFloat(),
            message = this.message,
            responseType = this.responseType,
            actionable = this.actionable.map { it.toActionable() },
            insights = this.insights.map { it.toInsight() },
            batteryScore = this.batteryScore.toFloat(),
            dataScore = this.dataScore.toFloat(),
            performanceScore = this.performanceScore.toFloat(),
            estimatedSavings = this.estimatedSavings.toEstimatedSavings()
        )
    }
    
    // Private extension functions for individual model conversions
    
    private fun BatteryInfo.toApiBatteryInfo(): ApiBatteryInfo {
        return ApiBatteryInfo(
            level = this.level.toDouble(),
            temperature = this.temperature.toDouble(),
            voltage = this.voltage.toDouble(),
            isCharging = this.isCharging,
            chargingType = this.chargingType,
            health = this.health,
            capacity = if (this.capacity != -1L) this.capacity.toDouble() else 3000.0,
            currentNow = this.currentNow.toDouble()
        )
    }
    
    private fun MemoryInfo.toApiMemoryInfo(): ApiMemoryInfo {
        return ApiMemoryInfo(
            totalRam = this.totalRam,
            availableRam = this.availableRam,
            lowMemory = this.lowMemory,
            threshold = this.threshold
        )
    }
    
    private fun CpuInfo.toApiCpuInfo(): ApiCpuInfo {
        return ApiCpuInfo(
            usage = this.usage.toDouble(),
            temperature = this.temperature.toDouble(),
            frequencies = this.frequencies.map { it.toInt() }
        )
    }
    
    private fun NetworkInfo.toApiNetworkInfo(): ApiNetworkInfo {
        return ApiNetworkInfo(
            type = this.type,
            strength = this.strength.toDouble(),
            isRoaming = this.isRoaming,
            dataUsage = this.dataUsage.toApiDataUsage(),
            activeConnectionInfo = this.activeConnectionInfo,
            linkSpeed = this.linkSpeed.toDouble(),
            cellularGeneration = this.cellularGeneration
        )
    }
    
    private fun DataUsage.toApiDataUsage(): ApiDataUsage {
        return ApiDataUsage(
            foreground = this.foreground.toDouble(),
            background = this.background.toDouble(),
            rxBytes = this.rxBytes,
            txBytes = this.txBytes
        )
    }
    
    private fun AppInfo.toApiAppInfo(): ApiAppInfo {
        return ApiAppInfo(
            packageName = this.packageName,
            processName = this.processName,
            appName = this.appName,
            isSystemApp = this.isSystemApp,
            lastUsed = this.lastUsed,
            foregroundTime = this.foregroundTime,
            backgroundTime = this.backgroundTime,
            batteryUsage = this.batteryUsage.toDouble(),
            dataUsage = this.dataUsage.toApiDataUsage(),
            memoryUsage = this.memoryUsage.toDouble(),
            cpuUsage = this.cpuUsage.toDouble(),
            notifications = this.notifications,
            crashes = this.crashes,
            versionName = this.versionName,
            versionCode = this.versionCode,
            targetSdkVersion = this.targetSdkVersion,
            installTime = this.installTime,
            updatedTime = this.updatedTime,
            alarmWakeups = this.alarmWakeups,
            currentPriority = this.currentPriority,
            bucket = this.bucket
        )
    }
    
    private fun ApiActionable.toActionable(): Actionable {
        return Actionable(
            id = this.id,
            type = this.type,
            description = this.description,
            packageName = this.packageName,
            estimatedBatterySavings = this.estimatedBatterySavings.toFloat(),
            estimatedDataSavings = this.estimatedDataSavings.toFloat(),
            severity = this.severity,
            newMode = this.newMode,
            enabled = this.enabled,
            throttleLevel = null,
            reason = this.reason,
            parameters = this.parameters
        )
    }
    
    private fun ApiInsight.toInsight(): Insight {
        return Insight(
            type = this.type,
            title = this.title,
            description = this.description,
            severity = this.severity
        )
    }
    
    private fun ApiEstimatedSavings.toEstimatedSavings(): AnalysisResponse.EstimatedSavings {
        return AnalysisResponse.EstimatedSavings(
            batteryMinutes = this.batteryMinutes.toFloat(),
            dataMB = this.dataMB.toFloat()
        )
    }
}