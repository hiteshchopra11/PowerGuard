package com.hackathon.powergaurd.actionable

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import com.hackathon.powergaurd.models.ActionResponse
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method

class StandbyBucketHandlerTest {

    private lateinit var context: Context
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var handler: StandbyBucketHandler

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)
        context = mockk(relaxed = true)
        usageStatsManager = mockk(relaxed = true)

        every { context.getSystemService(Context.USAGE_STATS_SERVICE) } returns usageStatsManager

        handler = StandbyBucketHandler(context)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `handleActionable returns false when package name is null`() = runBlocking {
        val actionable = ActionResponse.Actionable(
            type = ActionableTypes.SET_STANDBY_BUCKET,
            app = null,
            newMode = "restricted"
        )
        val result = handler.handleActionable(actionable)
        assertFalse(result)
    }

    @Test
    fun `handleActionable returns false when package name is blank`() = runBlocking {
        val actionable = ActionResponse.Actionable(
            type = ActionableTypes.SET_STANDBY_BUCKET,
            app = "",
            newMode = "restricted"
        )
        val result = handler.handleActionable(actionable)
        assertFalse(result)
    }

    @Test
    fun `handleActionable uses fallback when API level is below P`() = runBlocking {
        val originalSdkInt = Build.VERSION.SDK_INT
        setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), Build.VERSION_CODES.O)

        val actionable = ActionResponse.Actionable(
            type = ActionableTypes.SET_STANDBY_BUCKET,
            app = "com.example.app",
            newMode = "restricted"
        )

        try {
            val result = handler.handleActionable(actionable)
            assertFalse(result)
        } finally {
            setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), originalSdkInt)
        }
    }

    @Test
    fun `handleActionable tries direct API and reflection on API 28+`() = runBlocking {
        val originalSdkInt = Build.VERSION.SDK_INT
        setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), Build.VERSION_CODES.P)

        val actionable = ActionResponse.Actionable(
            type = ActionableTypes.SET_STANDBY_BUCKET,
            app = "com.example.app",
            newMode = "restricted"
        )

        try {
            val mockMethod = mockk<Method>(relaxed = true)
            val methodField =
                StandbyBucketHandler::class.java.getDeclaredField("setAppStandbyBucketMethod")
            methodField.isAccessible = true
            methodField.set(handler, mockMethod)

            // You can mock the `invoke` if needed
            every {
                mockMethod.invoke(any(), "com.example.app", any())
            } returns Unit

            val result = handler.handleActionable(actionable)
            assertTrue(result)
        } finally {
            setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), originalSdkInt)
        }
    }

    @Test
    fun `getBucketTypeFromName returns correct bucket values`() {
        val method = StandbyBucketHandler::class.java.getDeclaredMethod(
            "getBucketTypeFromName",
            String::class.java
        )
        method.isAccessible = true

        assertEquals(
            StandbyBucketHandler.BucketTypes.BUCKET_ACTIVE,
            method.invoke(handler, "active")
        )
        assertEquals(
            StandbyBucketHandler.BucketTypes.BUCKET_WORKING_SET,
            method.invoke(handler, "working_set")
        )
        assertEquals(
            StandbyBucketHandler.BucketTypes.BUCKET_WORKING_SET,
            method.invoke(handler, "working")
        )
        assertEquals(
            StandbyBucketHandler.BucketTypes.BUCKET_FREQUENT,
            method.invoke(handler, "frequent")
        )
        assertEquals(StandbyBucketHandler.BucketTypes.BUCKET_RARE, method.invoke(handler, "rare"))
        assertEquals(
            StandbyBucketHandler.BucketTypes.BUCKET_RESTRICTED,
            method.invoke(handler, "restricted")
        )
        assertEquals(
            StandbyBucketHandler.BucketTypes.BUCKET_RESTRICTED,
            method.invoke(handler, "unknown")
        )
    }

    /**
     * Helper to change final static values using reflection (e.g., SDK_INT)
     */
    private fun setFinalStatic(field: java.lang.reflect.Field, newValue: Any) {
        field.isAccessible = true
        val modifiersField = java.lang.reflect.Field::class.java.getDeclaredField("modifiers")
        modifiersField.isAccessible = true
        modifiersField.setInt(field, field.modifiers and java.lang.reflect.Modifier.FINAL.inv())
        field.set(null, newValue)
    }
}
