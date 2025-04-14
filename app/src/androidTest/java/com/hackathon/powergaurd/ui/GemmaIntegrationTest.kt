package com.hackathon.powergaurd.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.hackathon.powergaurd.data.PowerGuardAnalysisRepository
import com.hackathon.powergaurd.ui.screens.InferenceModeIndicator
import com.hackathon.powergaurd.ui.screens.SettingsBottomSheet
import com.hackathon.powergaurd.ui.theme.PowerGuardTheme
import com.hackathon.powergaurd.ui.viewmodels.DashboardViewModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

/**
 * UI tests for Gemma integration features
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class GemmaIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Mock
    private lateinit var viewModel: DashboardViewModel

    @Mock
    private lateinit var repository: PowerGuardAnalysisRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        // Mock the ViewModel behavior for testing
        `when`(viewModel.isUsingGemma.value).thenReturn(true)
    }

    @Test
    fun verifyInferenceModeIndicator_showsCorrectMode() {
        // Test On-device mode
        composeTestRule.setContent {
            PowerGuardTheme {
                InferenceModeIndicator(isUsingGemma = true)
            }
        }
        
        composeTestRule.onNodeWithText("On-device AI").assertIsDisplayed()
        
        // Test Cloud mode
        composeTestRule.setContent {
            PowerGuardTheme {
                InferenceModeIndicator(isUsingGemma = false)
            }
        }
        
        composeTestRule.onNodeWithText("Cloud API").assertIsDisplayed()
    }

    @Test
    fun verifySettingsBottomSheet_showsGemmaToggle() {
        // Given
        `when`(viewModel.isUsingGemma.value).thenReturn(true)
        
        // When
        composeTestRule.setContent {
            PowerGuardTheme {
                SettingsBottomSheet(
                    viewModel = viewModel,
                    onDismiss = {}
                )
            }
        }
        
        // Then
        composeTestRule.onNodeWithText("Local AI Inference").assertIsDisplayed()
        composeTestRule.onNodeWithText("Use on-device Gemma SDK instead of backend API").assertIsDisplayed()
        composeTestRule.onNodeWithText("Current mode: Gemma SDK (On-device)").assertIsDisplayed()
        
        // When toggle changes
        `when`(viewModel.isUsingGemma.value).thenReturn(false)
        
        composeTestRule.setContent {
            PowerGuardTheme {
                SettingsBottomSheet(
                    viewModel = viewModel,
                    onDismiss = {}
                )
            }
        }
        
        // Then it should show the updated mode
        composeTestRule.onNodeWithText("Current mode: Backend API (Cloud)").assertIsDisplayed()
    }
} 