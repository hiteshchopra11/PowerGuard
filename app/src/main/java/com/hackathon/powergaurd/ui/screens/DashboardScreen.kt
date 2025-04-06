package com.hackathon.powergaurd.ui.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hackathon.powergaurd.PowerGuardOptimizer
import com.hackathon.powergaurd.ui.viewmodels.DashboardUiState
import com.hackathon.powergaurd.ui.viewmodels.DashboardViewModel
import kotlinx.coroutines.delay

@RequiresApi(Build.VERSION_CODES.P)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    showSnackbar: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
    openPromptInput: Boolean = false
) {
    val context = LocalContext.current
    val optimizer = PowerGuardOptimizer(context)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

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
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PowerGuard Dashboard") },
                actions = {
                    IconButton(onClick = { viewModel.refreshData() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading && uiState.batteryLevel == 0) {
            // Show loading indicator only on initial load
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
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
                openPromptInput = openPromptInput
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
    openPromptInput: Boolean
) {
    var promptText by remember { mutableStateOf("") }

    // Create a reference for the prompt TextField to give it focus
    val promptFieldFocusRequester = remember { FocusRequester() }

    // When openPromptInput is true, scroll to the prompt section and focus on the text field
    LaunchedEffect(openPromptInput) {
        if (openPromptInput) {
            // Request focus on the prompt field
            promptFieldFocusRequester.requestFocus()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp),
        state = rememberLazyListState() // For scrolling control
    ) {
        if (isRefreshing) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
        }

        item {
            // Battery Status Card
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryAlert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Battery Status",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Current level: ${uiState.batteryLevel}%")
                    Text("Status: ${if (uiState.isCharging) "Charging (${uiState.chargingType})" else "Discharging"}")
                    Text("Temperature: ${uiState.batteryTemperature}°C")
                }
            }
        }

        item {
            // Network Usage Card
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Network Usage",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Connection: ${uiState.networkType}")
                    Text("Signal Strength: ${uiState.networkStrength}/4")

                    if (uiState.highUsageApps.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Top data usage:", style = MaterialTheme.typography.titleSmall)
                        uiState.highUsageApps.forEach { app ->
                            Text("• $app")
                        }
                    }
                }
            }
        }

        // Prompt Card
        item {
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
                        onValueChange = { promptText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(promptFieldFocusRequester),
                        placeholder = { Text("Enter your query about device performance...") },
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (promptText.isNotBlank()) {
                                viewModel.submitPrompt(promptText)
                                promptText = "" // Clear input after sending
                                showSnackbar("Analyzing your query...")
                            } else {
                                showSnackbar("Please enter a query")
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send")
                    }
                }
            }
        }

        if (uiState.insights.isNotEmpty()) {
            item {
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
                            "Insights",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        uiState.insights.take(3).forEach { insight ->
                            val severityColor = when (insight.severity) {
                                "HIGH" -> MaterialTheme.colorScheme.error
                                "MEDIUM" -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            }

                            Text(
                                insight.insightTitle,
                                style = MaterialTheme.typography.titleSmall,
                                color = severityColor
                            )
                            Text(
                                insight.insightDescription,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            if (insight != uiState.insights.take(3).lastOrNull()) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.P)
@Preview(showBackground = true)
@Composable
fun PreviewDashboardScreen() {
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
        openPromptInput = false
    )
}