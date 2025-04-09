package com.hackathon.powergaurd.ui.screens

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.with
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hackathon.powergaurd.PowerGuardOptimizer
import com.hackathon.powergaurd.ui.screens.ExamplesBottomSheet
import com.hackathon.powergaurd.ui.viewmodels.DashboardUiState
import com.hackathon.powergaurd.ui.viewmodels.DashboardViewModel
import kotlinx.coroutines.delay

@RequiresApi(Build.VERSION_CODES.P)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    showSnackbar: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
    openPromptInput: Boolean = false
) {
    val context = LocalContext.current
    val optimizer = PowerGuardOptimizer(context)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val analysisResponse by viewModel.analysisResponse.collectAsStateWithLifecycle()
    
    var showActionableDialog by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var promptText by remember { mutableStateOf("") }
    
    // Declare FocusRequester
    val focusRequester = remember { FocusRequester() }

    // Auto-refresh data every 5 minutes
    LaunchedEffect(Unit) {
        while (true) {
            delay(300000) // 5 minutes
            viewModel.refreshData()
        }
    }

    // Handle error if needed
    LaunchedEffect(error) {
        error?.let {
            showSnackbar(it)
            isAnalyzing = false
        }
    }
    
    // When analysis response changes and is not null
    LaunchedEffect(analysisResponse) {
        if (analysisResponse != null) {
            isAnalyzing = false
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp), // 👈 Makes Scaffold handle insets
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("PowerGuard Dashboard") }
            )
        }
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
                optimizer = optimizer,
                showSnackbar = showSnackbar,
                viewModel = viewModel,
                openPromptInput = openPromptInput,
                analysisResponse = analysisResponse,
                onShowAnalysisDialog = { show, analyzing ->
                    showActionableDialog = show
                    isAnalyzing = analyzing
                },
                showActionableDialog = showActionableDialog,
                isAnalyzing = isAnalyzing,
                focusRequester = focusRequester,
                promptText = promptText,
                onPromptChange = { promptText = it }
            )
        }
    }
    
    // Dialog for showing insights and actionables or loading state
    if (showActionableDialog) {
        if (isAnalyzing) {
            LoadingAnalysisDialog(
                onDismiss = { 
                    showActionableDialog = false
                    isAnalyzing = false
                }
            )
        } else if (analysisResponse != null) {
            AnalysisDialog(
                analysisResponse = analysisResponse!!,
                onDismiss = { 
                    showActionableDialog = false
                    focusRequester.freeFocus()
                    promptText = "" // Clear the prompt text
                }
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.P)
@Composable
private fun DashboardContent(
    uiState: DashboardUiState,
    isRefreshing: Boolean,
    paddingValues: PaddingValues,
    optimizer: PowerGuardOptimizer,
    showSnackbar: (String) -> Unit,
    viewModel: DashboardViewModel,
    openPromptInput: Boolean,
    analysisResponse: com.hackathon.powergaurd.data.model.AnalysisResponse?,
    onShowAnalysisDialog: (Boolean, Boolean) -> Unit,
    showActionableDialog: Boolean,
    isAnalyzing: Boolean,
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
                .padding(16.dp)
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
                focusRequester = focusRequester
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Battery status and optimization
            BatteryStatusCard(
                batteryLevel = uiState.batteryLevel,
                isCharging = uiState.isCharging,
                chargingType = uiState.chargingType,
                batteryTemperature = uiState.batteryTemperature,
                onOptimize = {
                    // Call API with exact prompt "Optimize battery"
                    viewModel.submitPrompt("Optimize battery")
                    onShowAnalysisDialog(true, true) // Show dialog in analyzing state
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Network usage and optimization
            NetworkUsageCard(
                networkType = uiState.networkType,
                networkStrength = uiState.networkStrength,
                highUsageApps = uiState.highUsageApps,
                onOptimize = {
                    // Call API with exact prompt "Optimize data"
                    viewModel.submitPrompt("Optimize data")
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
    onOptimize: () -> Unit
) {
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

            Text("Current level: $batteryLevel%")
            Text("Status: ${if (isCharging) "Charging ($chargingType)" else "Discharging"}")
            Text("Temperature: ${batteryTemperature}°C")
        }
    }
}

@Composable
fun NetworkUsageCard(
    networkType: String,
    networkStrength: Int,
    highUsageApps: List<String>,
    onOptimize: () -> Unit
) {
    var networkSectionExpanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(targetValue = if (networkSectionExpanded) 180f else 0f)
    
    // Create a simulated network speed that changes
    var networkSpeed by remember { mutableStateOf(0f) }
    
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
    focusRequester: FocusRequester
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
    val coroutineScope = rememberCoroutineScope()
    
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
                    onClick = onSubmit
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send")
                }
            }
        }
    }
    
    // Show bottom sheet with examples when clicked
    if (showBottomSheet) {
        ExamplesBottomSheet(
            onDismiss = { showBottomSheet = false },
            sheetState = bottomSheetState
        )
    }
}

@Composable
fun LoadingAnalysisDialog(onDismiss: () -> Unit) {
    val animatedDots = remember { mutableStateOf("") }
    
    // Animated dots effect
    LaunchedEffect(Unit) {
        while (true) {
            for (i in 0..3) {
                animatedDots.value = ".".repeat(i)
                kotlinx.coroutines.delay(500)
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Analyzing Device Data",
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
                    text = "Please wait while we decide a strategy for you${animatedDots.value}",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "This may take a few moments as we analyze your device usage patterns",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    )
}

@Composable
fun AnalysisDialog(
    analysisResponse: com.hackathon.powergaurd.data.model.AnalysisResponse,
    onDismiss: () -> Unit
) {
    // Debug log the response contents with HITESH tag
    LaunchedEffect(analysisResponse) {
        Log.d("PROMPT_DEBUG", """
            ╔════════════════════════════════════════════
            ║ Analysis Response Details
            ╠════════════════════════════════════════════
            ║ INSIGHTS (${analysisResponse.insights.size}):
            ║ ${analysisResponse.insights.joinToString("\n║ ") { 
                "• Type: ${it.type}\n║   Title: ${it.title}\n║   Description: ${it.description}" 
            }}
            ╠════════════════════════════════════════════
            ║ ACTIONABLES (${analysisResponse.actionable.size}):
            ║ ${analysisResponse.actionable.joinToString("\n║ ") { 
                "• Type: ${it.type}\n║   Description: ${it.description}\n║   Reason: ${it.reason}" 
            }}
            ╚════════════════════════════════════════════
        """.trimIndent())
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
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
                                imageVector = Icons.Default.Lightbulb,
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
                        if (analysisResponse.insights.isEmpty()) {
                            Text(
                                text = "No insights available at this time.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            analysisResponse.insights.forEach { insight ->
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
                
                // ACTIONS SECTION
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                                "Suggested Actions:",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Display actionables with proper bullet points
                        if (analysisResponse.actionable.isEmpty()) {
                            Text(
                                text = "No actions available at this time.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            analysisResponse.actionable.forEach { actionable ->
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
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "OK")
            }
        }
    )
}

// Preview composables for each card component
@Preview(showBackground = true)
@Composable
fun GratificationCardPreview() {
    MaterialTheme {
        Surface {
            GratificationCard()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BatteryStatusCardPreview() {
    MaterialTheme {
        Surface {
            BatteryStatusCard(
                batteryLevel = 75,
                isCharging = true,
                chargingType = "AC",
                batteryTemperature = 32.5f,
                onOptimize = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NetworkUsageCardPreview() {
    MaterialTheme {
        Surface {
            NetworkUsageCard(
                networkType = "WiFi",
                networkStrength = 3,
                highUsageApps = listOf("YouTube (250MB)", "Instagram (120MB)"),
                onOptimize = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PromptCardPreview() {
    MaterialTheme {
        Surface {
            PromptCard(
                promptText = "",
                onPromptChange = {},
                onSubmit = {},
                focusRequester = remember { FocusRequester() }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoadingAnalysisDialogPreview() {
    MaterialTheme {
        LoadingAnalysisDialog(onDismiss = {})
    }
}

@RequiresApi(Build.VERSION_CODES.P)
@Preview(showBackground = true)
@Composable
fun FullDashboardContentPreview() {
    MaterialTheme {
        Surface {
            // For preview only
            val previewUiState = DashboardUiState(
                batteryLevel = 75,
                isCharging = true,
                batteryTemperature = 32.5f,
                chargingType = "AC",
                networkType = "WiFi",
                networkStrength = 3,
                highUsageApps = listOf("YouTube (250MB)", "Instagram (120MB)"),
                insights = emptyList()
            )

            DashboardContent(
                uiState = previewUiState,
                isRefreshing = false,
                paddingValues = PaddingValues(0.dp),
                optimizer = PowerGuardOptimizer(LocalContext.current),
                showSnackbar = {},
                viewModel = hiltViewModel(),
                openPromptInput = false,
                analysisResponse = null,
                onShowAnalysisDialog = { _, _ -> },
                showActionableDialog = false,
                isAnalyzing = false,
                focusRequester = remember { FocusRequester() },
                promptText = "",
                onPromptChange = {}
            )
        }
    }
}