package com.hackathon.powergaurd.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hackathon.powergaurd.ui.viewmodels.AppUsageInfo
import com.hackathon.powergaurd.ui.viewmodels.ExploreViewModel
import com.hackathon.powergaurd.ui.viewmodels.ExploreUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Battery", "Data")

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("Explore Device") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab row for switching between Battery and Data sections
            TabRow(
                selectedTabIndex = selectedTab
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                        icon = {
                            Icon(
                                imageVector = if (index == 0) 
                                    Icons.Default.BatteryChargingFull 
                                else 
                                    Icons.Default.DataUsage,
                                contentDescription = title
                            )
                        }
                    )
                }
            }

            if (uiState.isLoading) {
                // Show loading
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.error != null) {
                // Show error
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(uiState.error ?: "Unknown error")
                }
            } else {
                // Show content based on selected tab
                when (selectedTab) {
                    0 -> BatteryContent(uiState, paddingValues)
                    1 -> DataUsageContent(uiState)
                }
            }
        }
    }
}

@Composable
private fun BatteryContent(
    uiState: ExploreUiState,
    paddingValues: androidx.compose.foundation.layout.PaddingValues
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
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
                    if (uiState.chargingType.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Charging Type")
                            Text(
                                uiState.chargingType,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Battery Status")
                        Text(if (uiState.isCharging) "Charging" else "Discharging")
                    }

                    if (uiState.remainingBatteryTime > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Estimated Time Remaining")
                            Text(
                                formatBatteryTime(uiState.remainingBatteryTime),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Battery usage of apps
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
                    Text(
                        text = "Battery Usage by Apps",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (uiState.topBatteryApps.isEmpty()) {
                        Text(
                            text = "No battery usage data available",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        uiState.topBatteryApps.take(5).forEachIndexed { index, app ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}. ${app.appName}",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "${"%.1f".format(app.batteryPercentage)}%",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            if (index < uiState.topBatteryApps.size - 1) {
                                Divider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DataUsageContent(uiState: ExploreUiState) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Data Usage Overview Card
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
                        // Data usage circle indicator
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

                            // Data usage percentage (for demo, using 65%)
                            val usagePercentage = 0.65f
                            drawArc(
                                color = Color(0xFF3F51B5),
                                startAngle = -90f,
                                sweepAngle = 360f * usagePercentage,
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
                                text = "2.6 GB",
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Text(
                                text = "Used",
                                style = MaterialTheme.typography.bodyMedium
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
                                text = "Total Plan",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "4 GB",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        Column {
                            Text(
                                text = "Remaining",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "1.4 GB",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        Column {
                            Text(
                                text = "Days Left",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "12 days",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }

        // Network Connection Info
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
                            imageVector = Icons.Default.NetworkCell,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Network Connection",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Connection Type")
                        Text(
                            uiState.networkType ?: "Unknown",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Signal Strength")
                        Text("${uiState.signalStrength ?: 0}/4")
                    }
                }
            }
        }

        // Data usage by apps
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
                    Text(
                        text = "Data Usage by Apps",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // For demo purposes, using dummy data
                    val dataApps = listOf(
                        AppDataUsage("YouTube", "450 MB"),
                        AppDataUsage("Instagram", "320 MB"),
                        AppDataUsage("Chrome", "280 MB"),
                        AppDataUsage("Spotify", "180 MB"),
                        AppDataUsage("Gmail", "120 MB")
                    )

                    dataApps.forEachIndexed { index, app ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}. ${app.appName}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = app.dataUsed,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        if (index < dataApps.size - 1) {
                            Divider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun getBatteryHealthString(health: Int): String {
    return when (health) {
        1 -> "Unknown"
        2 -> "Good"
        3 -> "Overheated"
        4 -> "Dead"
        5 -> "Over Voltage"
        6 -> "Unspecified Failure"
        7 -> "Cold"
        else -> "Unknown"
    }
}

private fun formatBatteryTime(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) {
        "$hours h $mins min"
    } else {
        "$mins min"
    }
}

// Model for app data usage
data class AppDataUsage(
    val appName: String,
    val dataUsed: String
)

@Preview(showBackground = true)
@Composable
fun PreviewExploreScreen() {
    // For preview only
    ExploreScreen()
}