package com.hackathon.powergaurd.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hackathon.powergaurd.ui.viewmodels.BatteryViewModel
import com.hackathon.powergaurd.ui.viewmodels.BatteryUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryScreen(
    viewModel: BatteryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Battery Information") },
                actions = {
                    IconButton(onClick = { viewModel.refreshBatteryData() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            // Show loading
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            // Show error
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(uiState.error ?: "Unknown error")
            }
        } else {
            // Show battery data
            BatteryContent(uiState, paddingValues)
        }
    }
}

@Composable
private fun BatteryContent(
    uiState: BatteryUiState,
    paddingValues: androidx.compose.foundation.layout.PaddingValues
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp)
    ) {
        // Battery Status Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(150.dp)
                            .padding(16.dp)
                    ) {
                        // Battery circle indicator
                        Canvas(modifier = Modifier.size(150.dp)) {
                            // Background circle
                            drawArc(
                                color = Color.LightGray.copy(alpha = 0.3f),
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = Offset(0f, 0f),
                                size = Size(size.width, size.height),
                                style = Stroke(width = 12f)
                            )

                            // Battery level arc
                            drawArc(
                                color = when {
                                    uiState.batteryLevel > 60 -> Color(0xFF4CAF50)
                                    uiState.batteryLevel > 30 -> Color(0xFFFFC107)
                                    else -> Color(0xFFF44336)
                                },
                                startAngle = -90f,
                                sweepAngle = 360f * (uiState.batteryLevel / 100f),
                                useCenter = false,
                                topLeft = Offset(0f, 0f),
                                size = Size(size.width, size.height),
                                style = Stroke(width = 12f)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${uiState.batteryLevel}%",
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Temperature",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${"%.1f".format(uiState.temperature)}°C",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (uiState.temperature > 38f)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Column {
                            Text(
                                text = "Status",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (uiState.isCharging) "Charging" else "Discharging",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        Column {
                            Text(
                                text = "Voltage",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${uiState.voltage / 1000.0}V",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }

        // Additional Battery Info Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Battery Details",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Only show battery health if available
                    if (uiState.health > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Health Status")
                            Text(getBatteryHealthString(uiState.health))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Only show charging type if available
                    if (uiState.isCharging && uiState.chargingType.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Charging Type")
                            Text(uiState.chargingType)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Only show capacity if available
                    if (uiState.capacity > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Battery Capacity")
                            Text("${uiState.capacity} mAh")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Only show current draw if available
                    if (uiState.currentNow != 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Current Draw")
                            Text("${uiState.currentNow} mA")
                        }
                    }
                }
            }
        }

        // Add some space at the bottom for better UX
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// Helper function to convert battery health code to string
private fun getBatteryHealthString(health: Int): String {
    return when (health) {
        1 -> "Unknown"
        2 -> "Good"
        3 -> "Overheat"
        4 -> "Dead"
        5 -> "Over voltage"
        6 -> "Unspecified failure"
        7 -> "Cold"
        else -> "Unknown"
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewBatteryScreen() {
    BatteryContent(
        BatteryUiState(
            batteryLevel = 75,
            temperature = 32.5f,
            voltage = 4200,
            isCharging = false,
            chargingType = "AC",
            health = 2,
            capacity = 4000,
            currentNow = 250,
            isLoading = false
        ),
        paddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp)
    )
}