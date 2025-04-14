package com.hackathon.powergaurd.data.gemma

import android.content.Context
import com.hackathon.powergaurd.data.model.BatteryInfo
import com.hackathon.powergaurd.data.model.DeviceData
import com.hackathon.powergaurd.data.model.MemoryInfo
import com.hackathon.powergaurd.data.model.CpuInfo
import com.hackathon.powergaurd.data.model.NetworkInfo
import com.hackathon.powergaurd.data.model.DataUsage
import com.hackathon.powergaurd.data.model.SettingsInfo
import com.hackathon.powergaurd.data.model.DeviceInfo
import com.powergaurd.llm.GemmaConfig
import com.powergaurd.llm.GemmaInferenceSDK
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GemmaRepositoryTest {

    private lateinit var context: Context
    private lateinit var config: GemmaConfig
    private lateinit var sdk: GemmaInferenceSDK
    private lateinit var repository: GemmaRepository
    
    @Before
    fun setup() {
        context = mockk(relaxed = true)
        config = mockk(relaxed = true)
        sdk = mockk(relaxed = true)
        
        // Create a repository with mocked dependencies
        repository = object : GemmaRepository(context, config) {
            override val sdk: GemmaInferenceSDK = this@GemmaRepositoryTest.sdk
        }
    }
    
    @Test
    fun `test analyzeDeviceData falls back to simulation when SDK fails`() = runTest {
        // Mock the SDK to return null
        coEvery { sdk.generateJsonResponse(any()) } returns null
        
        // Create a sample device data
        val deviceData = createSampleDeviceData()
        
        // Call the function under test
        val result = repository.analyzeDeviceData(deviceData)
        
        // Verify the result
        assertTrue(result.isSuccess)
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals("Analysis (simulated)", response!!.message)
    }
    
    @Test
    fun `test analyzeDeviceData uses SDK response when available`() = runTest {
        // Create a mock JSON response
        val jsonResponse = JSONObject().apply {
            put("success", true)
            put("batteryScore", 90)
            put("dataScore", 85)
            put("performanceScore", 95)
            put("insights", JSONObject().toString())
            put("actionable", JSONObject().toString())
            put("estimatedSavings", JSONObject().apply {
                put("batteryMinutes", 45)
                put("dataMB", 250)
            })
        }
        
        // Capture the prompt sent to the SDK
        val promptSlot = slot<String>()
        
        // Mock the SDK to return a valid response
        coEvery { 
            sdk.generateJsonResponse(capture(promptSlot)) 
        } returns jsonResponse
        
        // Create a sample device data
        val deviceData = createSampleDeviceData()
        
        // Call the function under test
        val result = repository.analyzeDeviceData(deviceData)
        
        // Verify the result
        assertTrue(result.isSuccess)
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals("Analysis powered by Gemma", response!!.message)
        
        // Verify that the prompt contains expected data
        val prompt = promptSlot.captured
        assertTrue(prompt.contains("PowerGuard AI"))
        assertTrue(prompt.contains("Device: Test Manufacturer Test Model"))
    }
    
    private fun createSampleDeviceData(): DeviceData {
        return DeviceData(
            deviceId = "test-device-id",
            timestamp = System.currentTimeMillis(),
            battery = BatteryInfo(
                level = 75,
                temperature = 30.0f,
                voltage = 4200,
                isCharging = false,
                chargingType = "USB",
                health = 2
            ),
            memory = MemoryInfo(
                totalRam = 4 * 1024 * 1024 * 1024L,
                availableRam = 2 * 1024 * 1024 * 1024L,
                lowMemory = false,
                threshold = 1 * 1024 * 1024 * 1024L
            ),
            cpu = CpuInfo(
                usage = 25.0f,
                temperature = 40.0f,
                frequencies = listOf(1_500_000L, 2_000_000L)
            ),
            network = NetworkInfo(
                type = "WiFi",
                strength = 3,
                isRoaming = false,
                dataUsage = DataUsage(
                    foreground = 100 * 1024 * 1024L,
                    background = 200 * 1024 * 1024L,
                    rxBytes = 150 * 1024 * 1024L,
                    txBytes = 50 * 1024 * 1024L
                )
            ),
            apps = emptyList(),
            settings = SettingsInfo(
                batteryOptimization = true,
                dataSaver = false,
                powerSaveMode = false,
                adaptiveBattery = true,
                autoSync = true
            ),
            deviceInfo = DeviceInfo(
                manufacturer = "Test Manufacturer",
                model = "Test Model",
                osVersion = "13",
                sdkVersion = 33,
                screenOnTime = 3 * 60 * 60 * 1000L
            ),
            prompt = "Test prompt"
        )
    }
} 