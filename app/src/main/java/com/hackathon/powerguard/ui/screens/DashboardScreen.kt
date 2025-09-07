package com.hackathon.powerguard.ui.screens

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hackathon.powerguard.data.model.AnalysisResponse
import com.hackathon.powerguard.ui.components.TestValuesBottomSheet
import com.hackathon.powerguard.ui.viewmodels.DashboardUiState
import com.hackathon.powerguard.ui.viewmodels.DashboardViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    showSnackbar: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
    openPromptInput: Boolean = false,
    refreshTrigger: Boolean = false,
    settingsTrigger: Boolean = false,
    useBackend: Boolean = false
) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val analysisResponse by viewModel.analysisResponse.collectAsStateWithLifecycle()

    var showActionableDialog by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var promptText by remember { mutableStateOf("") }
    
    // Add state for test values bottom sheet
    var showTestValuesBottomSheet by remember { mutableStateOf(false) }
    
    // Previous refresh trigger value to detect changes
    var previousRefreshTrigger by remember { mutableStateOf(refreshTrigger) }
    
    // Previous settings trigger value to detect changes
    var previousSettingsTrigger by remember { mutableStateOf(settingsTrigger) }
    
    LaunchedEffect(isLoading, analysisResponse, showActionableDialog, isAnalyzing) {
        Log.d("DashboardScreen", "State changed: isLoading=$isLoading, hasResponse=${analysisResponse != null}, showDialog=$showActionableDialog, isAnalyzing=$isAnalyzing")
    }
    
    // Listen for refresh trigger changes
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger != previousRefreshTrigger) {
            previousRefreshTrigger = refreshTrigger
            viewModel.refreshData()
        }
    }
    
    // Listen for settings trigger changes
    LaunchedEffect(settingsTrigger) {
        if (settingsTrigger != previousSettingsTrigger) {
            previousSettingsTrigger = settingsTrigger
            showTestValuesBottomSheet = true
        }
    }
    // Listen for backend toggle and update ViewModel
    LaunchedEffect(useBackend) {
        // useBackend=true means switch off on-device AI
        viewModel.toggleInferenceMode(useAi = !useBackend)
    }
    
    // Declare FocusRequester
    val focusRequester = remember { FocusRequester() }

    // Handle error if needed
    LaunchedEffect(error) {
        if (error != null) {
            Log.e("DashboardScreen", "Error occurred: $error")
            // Only reset analyzing state, keep dialog visible if showing results
            isAnalyzing = false
        }
    }
    
    // Show loading dialog when analysis is in progress
    if (isAnalyzing) {
        LoadingAnalysisDialog(
            onDismissRequest = {
                // Only allow dismissal if not actively loading
                if (!isLoading) {
                    isAnalyzing = false
                    viewModel.clearAnalysisResponse()
                }
            }
        )
    }

    // Show analysis results dialog when response is available
    if (showActionableDialog && analysisResponse != null) {
        AnalysisDialog(
            response = analysisResponse!!,
            onDismissRequest = {
                showActionableDialog = false
                isAnalyzing = false
                viewModel.clearAnalysisResponse()
            },
            viewModel = viewModel
        )
    }

    // Show test values bottom sheet when settings is clicked
    if (showTestValuesBottomSheet) {
        TestValuesBottomSheet(
            viewModel = viewModel,
            onDismiss = { showTestValuesBottomSheet = false }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp) // 👈 Makes Scaffold handle insets
    )  { paddingValues ->
   
        if (isLoading && uiState.batteryLevel == 0) {
            // Show loading indicator only on initial load
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(1f))
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading device data...")
                Spacer(modifier = Modifier.weight(1f))
            }
        } else {
            DashboardContent(
                uiState = uiState,
                isRefreshing = isLoading,
                paddingValues = paddingValues,
                showSnackbar = showSnackbar,
                viewModel = viewModel,
                openPromptInput = openPromptInput,
                analysisResponse = analysisResponse,
                onShowAnalysisDialog = { show, analyzing ->
                    showActionableDialog = show
                    isAnalyzing = analyzing
                },
                focusRequester = focusRequester,
                promptText = promptText,
                onPromptChange = { promptText = it }
            )
        }
    }
    
    // Auto-refresh data every 5 minutes
    LaunchedEffect(Unit) {
        // Initial data load to populate UI (this just fetches device data, no LLM call)
        viewModel.fetchDeviceDataOnly()
        
        while (true) {
            delay(300000) // 5 minutes
            // Only refresh device data, not analysis
            viewModel.fetchDeviceDataOnly()
        }
    }
}

@Composable
private fun DashboardContent(
    uiState: DashboardUiState,
    isRefreshing: Boolean,
    paddingValues: PaddingValues,
    showSnackbar: (String) -> Unit,
    viewModel: DashboardViewModel,
    openPromptInput: Boolean,
    analysisResponse: AnalysisResponse?,
    onShowAnalysisDialog: (Boolean, Boolean) -> Unit,
    focusRequester: FocusRequester,
    promptText: String,
    onPromptChange: (String) -> Unit
) {
    // Track whether first API response has been received
    var hasReceivedFirstResponse by remember { mutableStateOf(false) }
    
    // Set to true once the first analysis response is received
    LaunchedEffect(analysisResponse) {
        if (analysisResponse != null) {
            hasReceivedFirstResponse = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Show linear progress indicator when refreshing
            if (isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
            
            // Only show gratification card after first API response
            if (hasReceivedFirstResponse) {
                GratificationCard()
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Prompt input card - moved above battery card
            PromptCard(
                promptText = promptText,
                onPromptChange = onPromptChange,
                onSubmit = {
                    if (promptText.isNotBlank()) {
                        Log.d("PROMPT_DEBUG", "Prompt Request - User Query: $promptText")
                        viewModel.submitPrompt(promptText)
                        onShowAnalysisDialog(true, true) // Show dialog in analyzing state
                    } else {
                        showSnackbar("Please enter a prompt first")
                    }
                },
                focusRequester = focusRequester,
                isLoading = isRefreshing
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Battery status and optimization
            BatteryStatusCard(
                batteryLevel = uiState.batteryLevel,
                isCharging = uiState.isCharging,
                chargingType = uiState.chargingType,
                batteryTemperature = uiState.batteryTemperature,
                onOptimize = {
                    Log.d("PROMPT_DEBUG", "Button Pressed Optimize Battery")
                    // Call API with exact prompt "Optimize battery"
                    viewModel.submitPrompt("Optimize Battery")
                    onShowAnalysisDialog(true, true) // Show dialog in analyzing state
                },
                viewModel = viewModel
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Network usage and optimization
            NetworkUsageCard(
                networkType = uiState.networkType,
                highUsageApps = uiState.highUsageApps,
                onOptimize = {
                    // Call API with exact prompt "Optimize data"
                    Log.d("PROMPT_DEBUG", "Button Pressed Optimize Data")
                    viewModel.submitPrompt("Optimize Data")
                    onShowAnalysisDialog(true, true) // Show dialog in analyzing state
                }
            )
            
            // Add bottom padding
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
    
    // Show prompt input field if requested
    LaunchedEffect(openPromptInput) {
        if (openPromptInput) {
            focusRequester.requestFocus()
        }
    }
}

@Composable
fun GratificationCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Celebration,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    "Woaah! You saved:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    // Use dynamic values from API when available
                    "36 mins of battery and 460 MB of data in last 1 day",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
fun BatteryStatusCard(
    batteryLevel: Int,
    isCharging: Boolean,
    chargingType: String,
    batteryTemperature: Float,
    onOptimize: () -> Unit,
    viewModel: DashboardViewModel? = null
) {
    // Get custom battery level if available and greater than 0
    val customBatteryLevel = viewModel?.customBatteryLevel?.collectAsState()?.value ?: 0
    val displayBatteryLevel = if (customBatteryLevel > 0) customBatteryLevel else batteryLevel
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.BatteryAlert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Battery Status",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )

                Button(
                    onClick = onOptimize,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        "Optimize\nBattery",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text("Current level: $displayBatteryLevel%")
            if (customBatteryLevel > 0) {
                Text(
                    "(Custom battery level set in settings)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text("Status: ${if (isCharging) "Charging ($chargingType)" else "Discharging"}")
            Text("Temperature: ${batteryTemperature}°C")
        }
    }
}

@Composable
fun NetworkUsageCard(
    networkType: String,
    highUsageApps: List<String>,
    onOptimize: () -> Unit
) {
    var networkSectionExpanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(targetValue = if (networkSectionExpanded) 180f else 0f)
    
    // Create a simulated network speed that changes
    var networkSpeed by remember { mutableFloatStateOf(0f) }
    
    // Update the network speed every few seconds
    LaunchedEffect(Unit) {
        while(true) {
            // Generate a random realistic network speed based on connection type
            networkSpeed = when (networkType) {
                "WiFi" -> (2.5f + (Math.random() * 8).toFloat()).coerceAtMost(10f) // 2.5-10 Mbps for WiFi
                "4G" -> (1.5f + (Math.random() * 4).toFloat()).coerceAtMost(5f) // 1.5-5 Mbps for 4G
                "3G" -> (0.4f + (Math.random() * 1).toFloat()).coerceAtMost(1.5f) // 0.4-1.5 Mbps for 3G
                "2G" -> (0.05f + (Math.random() * 0.1).toFloat()).coerceAtMost(0.15f) // 50-150 Kbps for 2G
                else -> (Math.random() * 3).toFloat() // Fallback
            }
            delay(3000) // Update every 3 seconds
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.NetworkCheck,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Network Usage",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )

                Button(
                    onClick = onOptimize,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        "Optimize\nData",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text("Connection: $networkType")
            
            // Display network speed instead of signal strength
            val speedText = if (networkSpeed < 1.0f) {
                "${(networkSpeed * 1000).toInt()} Kbps"
            } else {
                "${String.format("%.1f", networkSpeed)} Mbps"
            }
            Text("Current Speed: $speedText")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clickable { networkSectionExpanded = !networkSectionExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (networkSectionExpanded) "Hide details" else "Show details",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = if (networkSectionExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.rotate(arrowRotation)
                )
            }

            AnimatedVisibility(
                visible = networkSectionExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    if (highUsageApps.isNotEmpty()) {
                        Text("Top data usage:", style = MaterialTheme.typography.titleSmall)
                        highUsageApps.forEach { app ->
                            Text("• $app")
                        }
                    } else {
                        Text("No data usage information available")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun PromptCard(
    promptText: String,
    onPromptChange: (String) -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
    isLoading: Boolean = false
) {
    // Create a list of rotating placeholder texts
    val placeholders = listOf(
        "Which app is draining my battery?",
        "Which apps consume the most battery?",
        "Save battery but keep WhatsApp running",
        "Make my battery last 3 hours",
        "What's using my data?",
        "I have 500MB left, help me save it",
        "Going on a trip with 10% battery, need help"
    )
    
    // Set up rotating index for placeholders
    var currentPlaceholderIndex by remember { mutableIntStateOf(0) }
    
    // Bottom sheet state
    var showBottomSheet by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Rotate the placeholder every 3 seconds
    LaunchedEffect(Unit) {
        while(true) {
            delay(3000) // 3 seconds
            currentPlaceholderIndex = (currentPlaceholderIndex + 1) % placeholders.size
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                "Ask PowerGuard",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = promptText,
                onValueChange = onPromptChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = { 
                    // Animated content transition for placeholders
                    AnimatedContent(
                        targetState = currentPlaceholderIndex,
                        transitionSpec = {
                            // Use simpler enter/exit transitions
                            (fadeIn(animationSpec = tween(300)) + 
                            slideInHorizontally(
                                initialOffsetX = { width -> width },
                                animationSpec = tween(durationMillis = 500)
                            )) togetherWith
                            (fadeOut(animationSpec = tween(300)) + 
                            slideOutHorizontally(
                                targetOffsetX = { width -> -width },
                                animationSpec = tween(durationMillis = 500)
                            ))
                        }
                    ) { index ->
                        Text(placeholders[index])
                    }
                },
                maxLines = 2
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = { showBottomSheet = true },
                    label = { Text("What can I ask?") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = "Examples",
                            Modifier.size(18.dp)
                        )
                    }
                )
                
                Button(
                    onClick = onSubmit,
                    enabled = !isLoading
                ) {
                    // Show loading indicator or send icon based on loading state
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send"
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isLoading) "Sending..." else "Send")
                }
            }
        }
    }
    
    // Show bottom sheet with examples when clicked
    if (showBottomSheet) {
        ExamplesBottomSheet(
            onDismiss = { showBottomSheet = false },
            sheetState = bottomSheetState,
            onPromptSelected = { selectedPrompt ->
                onPromptChange(selectedPrompt)
                showBottomSheet = false
            }
        )
    }
}

@Composable
fun LoadingAnalysisDialog(onDismissRequest: () -> Unit) {
    val animatedDots = remember { mutableStateOf("") }
    
    // Animated dots effect
    LaunchedEffect(Unit) {
        while (true) {
            for (i in 0..3) {
                animatedDots.value = ".".repeat(i)
                delay(500)
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Making Things Better",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(60.dp),
                    strokeWidth = 4.dp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "We're optimizing your device just for you${animatedDots.value}",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Crafting the perfect recommendations to enhance your experience",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    )
}

@Composable
fun AnalysisDialog(
    response: AnalysisResponse,
    onDismissRequest: () -> Unit,
    viewModel: DashboardViewModel
) {
    // Track execution states
    val isExecuting by viewModel.isExecuting.collectAsStateWithLifecycle()
    val executionResults by viewModel.executionResults.collectAsStateWithLifecycle()
    
    // Local state for showing success message
    var showSuccessMessage by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf("") }
    
    // Clear execution results when dialog opens
    LaunchedEffect(Unit) {
        viewModel.clearExecutionResults()
    }
    
    // Debug log the response contents
    LaunchedEffect(response) {
        Log.d("PROMPT_DEBUG", """
            ╔════════════════════════════════════════════
            ║ Analysis Response Details
            ╠════════════════════════════════════════════
            ║ INSIGHTS (${response.insights.size}):
            ║ ${response.insights.joinToString("\n║ ") { 
                "• Type: ${it.type}\n║   Title: ${it.title}\n║   Description: ${it.description}" 
            }}
            ╠════════════════════════════════════════════
            ║ ACTIONABLES (${response.actionable.size}):
            ║ ${response.actionable.joinToString("\n║ ") { 
                "• Type: ${it.type}\n║   Description: ${it.description}\n║   Reason: ${it.reason}" 
            }}
            ╚════════════════════════════════════════════
        """.trimIndent())
    }
    
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Device Optimization Insights",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Success message if shown
                AnimatedVisibility(
                    visible = showSuccessMessage,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Celebration,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = successMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                
                // INSIGHTS SECTION
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Assignment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Insights:",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Display insights with proper bullet points
                        if (response.insights.isEmpty()) {
                            Text(
                                text = "No insights available at this time.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            response.insights.forEach { insight ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.width(24.dp),
                                        contentAlignment = Alignment.TopStart
                                    ) {
                                        Text(
                                            text = "•", 
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontSize = 18.sp
                                        )
                                    }
                                    Text(
                                        text = insight.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // ACTIONS SECTION - Only show if there are actionables
                if (response.actionable.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.OfflineBolt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Suggested Actions:",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                // Apply All button at the top level
                                Button(
                                    onClick = {
                                        viewModel.executeAllActionablesAsync(response.actionable) { results ->
                                            val successCount = results.values.count { it }
                                            successMessage = if (successCount == response.actionable.size) {
                                                "All actions applied successfully!"
                                            } else {
                                                "$successCount of ${response.actionable.size} actions applied."
                                            }
                                            showSuccessMessage = true
                                            // No automatic dismissal, dialog stays open
                                        }
                                    },
                                    enabled = !isExecuting,
                                    modifier = Modifier
                                        .height(28.dp)
                                        .padding(start = 8.dp)
                                        .animateContentSize(
                                            animationSpec = tween(
                                                durationMillis = 300,
                                                easing = FastOutSlowInEasing
                                            )
                                        ),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    if (isExecuting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Apply All",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Display actionables - show all actionables instead of limiting to 3
                            response.actionable.forEach { actionable ->
                                val isActionExecuted = executionResults[actionable.id] == true
                                val isActionFailed = executionResults[actionable.id] == false
                                
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = when {
                                            isActionExecuted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                            isActionFailed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = actionable.description,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            if (actionable.reason.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "Reason: ${actionable.reason}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            // Display actionable type for debugging
                                            Text(
                                                text = "Type: ${actionable.type}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        
                                        // Apply button for individual actionable
                                        Button(
                                            onClick = {
                                                viewModel.executeActionableAsync(actionable) { success ->
                                                    if (success) {
                                                        successMessage = "Action applied successfully!"
                                                        showSuccessMessage = true
                                                    }
                                                }
                                            },
                                            enabled = !isExecuting && !isActionExecuted,
                                            modifier = Modifier.padding(start = 8.dp)
                                        ) {
                                            Text(
                                                text = if (isActionExecuted) "Applied" else "Apply", 
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = "Close")
            }
        }
    )
}