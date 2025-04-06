package com.hackathon.powerguard.ui.screens.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hackathon.powerguard.PowerGuardOptimizer
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    showSnackbar: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
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
                title = { Text("AI PowerGuard Dashboard") },
                actions = {
                    IconButton(onClick = { viewModel.refreshData() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                    IconButton(onClick = { /* Navigate to settings */ }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
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
                showSnackbar = showSnackbar
            )
        }
    }
}

@Composable
private fun DashboardContent(
    uiState: DashboardUiState,
    isRefreshing: Boolean,
    paddingValues: PaddingValues,
    optimizer: PowerGuardOptimizer,
    showSnackbar: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp)
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

                    if (uiState.batteryScore > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Battery Health Score: ${uiState.batteryScore}/100",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            // Set charging threshold to 80% to preserve battery life
                            optimizer.optimizeCharging(80)
                            showSnackbar("Battery optimization applied")
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Optimize Charging")
                    }
                }
            }
        }

        item {
            // Network Optimization Card
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
                            "Network Usage (${uiState.networkType})",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (uiState.dataScore > 0) {
                        Text("Data Efficiency Score: ${uiState.dataScore}/100")
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (uiState.highUsageApps.isNotEmpty()) {
                        Text("High usage apps detected:")
                        uiState.highUsageApps.forEach { app ->
                            Text("• $app")
                        }
                    } else {
                        Text("No high data usage apps detected")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val highestDataApp = uiState.actionables
                        .firstOrNull { it.type == "DATA_RESTRICTION" }?.packageName
                        ?: (if (uiState.highUsageApps.isNotEmpty()) uiState.highUsageApps.first()
                            .substringBefore(" (") else "")

                    Button(
                        onClick = {
                            if (highestDataApp.isNotEmpty()) {
                                optimizer.restrictBackgroundData(highestDataApp, true)
                                showSnackbar("Network restrictions applied to highest usage app")
                            } else {
                                showSnackbar("No high usage apps to restrict")
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                        enabled = highestDataApp.isNotEmpty()
                    ) {
                        Text("Restrict Background Data")
                    }
                }
            }
        }

        item {
            // AI Summary
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
                        "AI Analysis Summary",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        uiState.aiSummary.ifEmpty {
                            "Analyzing your device usage patterns... Apply optimizations for improved battery life and performance."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (uiState.estimatedBatterySavings > 0 || uiState.estimatedDataSavings > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Estimated savings:",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (uiState.estimatedBatterySavings > 0) {
                            val hours = uiState.estimatedBatterySavings / 60
                            val minutes = uiState.estimatedBatterySavings % 60
                            Text("• Battery life: +${if (hours > 0) "$hours hrs " else ""}$minutes mins")
                        }

                        if (uiState.estimatedDataSavings > 0) {
                            Text("• Data usage: ${uiState.estimatedDataSavings} MB")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            // Apply all optimizations based on actionables
                            uiState.actionables.forEach { actionable ->
                                when (actionable.type) {
                                    "BATTERY_OPTIMIZATION" ->
                                        optimizer.setAppBackgroundRestriction(
                                            actionable.packageName,
                                            actionable.newMode
                                        )

                                    "DATA_RESTRICTION" ->
                                        optimizer.restrictBackgroundData(
                                            actionable.packageName,
                                            true
                                        )

                                    "WAKELOCK_MANAGEMENT" ->
                                        optimizer.manageWakeLock(
                                            actionable.packageName,
                                            actionable.newMode
                                        )
                                }
                            }
                            showSnackbar("All optimizations applied")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.actionables.isNotEmpty()
                    ) {
                        Text("Apply All Optimizations")
                    }
                }
            }
        }

        // Show insights if available
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
                                insight.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = severityColor
                            )
                            Text(
                                insight.description,
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
        batteryScore = 85,
        dataScore = 72,
        performanceScore = 80,
        estimatedBatterySavings = 90,
        estimatedDataSavings = 150,
        aiSummary = "Based on your usage patterns, we've detected that YouTube keeps a wake lock even when paused. We recommend restricting background activities to improve battery life by approximately 1 hour and 30 minutes."
    )

    DashboardContent(
        uiState = previewUiState,
        isRefreshing = false,
        paddingValues = PaddingValues(0.dp),
        optimizer = PowerGuardOptimizer(LocalContext.current),
        showSnackbar = {}
    )
}