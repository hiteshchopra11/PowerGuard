package com.powergaurd.llm

import android.content.Context
import android.os.BatteryManager
import com.google.ai.client.generativeai.GenerativeModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GemmaInferenceSDKTest {
    
    private lateinit var mockContext: Context
    private lateinit var mockModelManager: ModelManager
    private lateinit var mockInferenceEngine: InferenceEngine
    private lateinit var mockResponseParser: ResponseParser
    private lateinit var sdk: GemmaInferenceSDK
    private lateinit var testConfig: GemmaConfig
    
    @Before
    fun setUp() {
        mockContext = mockk<Context>(relaxed = true)
        testConfig = GemmaConfig(
            modelName = "test-model",
            maxTokens = 64,
            temperature = 0.5f,
            enableLogging = false
        )
        
        mockModelManager = mockk<ModelManager>()
        mockInferenceEngine = mockk<InferenceEngine>()
        mockResponseParser = mockk<ResponseParser>()
        
        // Create a spied SDK instance with mocked dependencies
        sdk = spyk(
            GemmaInferenceSDK(mockContext, testConfig),
            recordPrivateCalls = true
        )
        
        // Set up mock behavior
        coEvery { mockModelManager.initialize() } returns Unit
        coEvery { mockModelManager.getModel() } returns mockk<GenerativeModel>()
        coEvery { mockInferenceEngine.generateText(any(), any()) } returns "Test response"
        
        // Set up battery level mock
        val batteryManager = mockk<BatteryManager>()
        every { mockContext.getSystemService(Context.BATTERY_SERVICE) } returns batteryManager
        every { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) } returns 50
    }
    
    @Test
    fun `initialization succeeds with valid config`() = runTest {
        coEvery { mockModelManager.initialize() } returns Unit
        
        val result = sdk.initialize()
        assertTrue("Initialization should succeed", result)
    }
    
    @Test
    fun `generateResponse returns text when initialized`() = runTest {
        // Setup
        coEvery { mockModelManager.initialize() } returns Unit
        coEvery { mockInferenceEngine.generateText(any(), any()) } returns "Test response"
        
        // Initialize SDK
        sdk.initialize()
        
        // Test
        val response = sdk.generateResponse("Test prompt")
        assertEquals("Test response", response)
    }
    
    @Test
    fun `generateJsonResponse parses response to JSON`() = runTest {
        // Setup
        coEvery { mockModelManager.initialize() } returns Unit
        coEvery { mockInferenceEngine.generateText(any(), any()) } returns """{"key":"value"}"""
        coEvery { mockResponseParser.parseJsonResponse(any()) } returns JSONObject("""{"key":"value"}""")
        
        // Initialize SDK
        sdk.initialize()
        
        // Test
        val jsonResponse = sdk.generateJsonResponse("Test prompt")
        assertNotNull("JSON response should not be null", jsonResponse)
        assertEquals("value", jsonResponse?.optString("key"))
    }
    
    @Test
    fun `generateFromData formats data and generates response`() = runTest {
        // Setup
        val testData = mapOf(
            "key1" to "value1",
            "key2" to mapOf("subkey" to "subvalue")
        )
        
        coEvery { mockModelManager.initialize() } returns Unit
        coEvery { mockInferenceEngine.generateText(any(), any()) } returns """{"result":"success"}"""
        coEvery { mockResponseParser.parseJsonResponse(any()) } returns JSONObject("""{"result":"success"}""")
        
        // Initialize SDK
        sdk.initialize()
        
        // Test
        val jsonResponse = sdk.generateFromData(testData, "Test goal")
        assertNotNull("JSON response should not be null", jsonResponse)
        assertEquals("success", jsonResponse?.optString("result"))
    }
    
    @Test
    fun `shutdown releases resources`() = runTest {
        // Setup
        coEvery { mockModelManager.initialize() } returns Unit
        coEvery { mockModelManager.release() } returns Unit
        
        // Initialize and then shutdown
        sdk.initialize()
        sdk.shutdown()
        
        // Test that generateResponse after shutdown returns null
        coEvery { mockInferenceEngine.generateText(any(), any()) } returns null
        
        val response = sdk.generateResponse("Test prompt")
        assertNull("Response after shutdown should be null", response)
    }
    
    @Test
    fun `isLowBattery returns correct state`() {
        // Test with battery level 50%
        val batteryManager = mockk<BatteryManager>()
        every { mockContext.getSystemService(Context.BATTERY_SERVICE) } returns batteryManager
        every { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) } returns 50
        
        var result = sdk.isLowBattery()
        assertTrue("Battery at 50% should not be low", !result)
        
        // Test with battery level 10%
        every { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) } returns 10
        
        result = sdk.isLowBattery()
        assertTrue("Battery at 10% should be low", result)
    }
    
    @Test
    fun `generateEfficientResponse uses reduced parameters`() = runTest {
        // Setup
        coEvery { mockModelManager.initialize() } returns Unit
        coEvery { mockInferenceEngine.generateTextEfficient(any()) } returns "Efficient response"
        
        // Initialize SDK
        sdk.initialize()
        
        // Test
        val response = sdk.generateEfficientResponse("Test prompt")
        assertEquals("Efficient response", response)
    }
    
    @Test
    fun `test loading states during initialization`() = runTest {
        // Setup
        coEvery { mockModelManager.initialize() } returns Unit
        
        // Before initialization
        assertEquals(GemmaInferenceSDK.LoadingState.NOT_INITIALIZED, sdk.loadingState.value)
        
        // During initialization
        sdk.initialize()
        
        // After initialization
        assertEquals(GemmaInferenceSDK.LoadingState.READY, sdk.loadingState.value)
        
        // After shutdown
        sdk.shutdown()
        assertEquals(GemmaInferenceSDK.LoadingState.NOT_INITIALIZED, sdk.loadingState.value)
    }
} 