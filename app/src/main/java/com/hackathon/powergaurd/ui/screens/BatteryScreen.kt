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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.dp
import com.hackathon.powergaurd.PowerGuardOptimizer
import com.hackathon.powergaurd.models.BatteryAppUsage
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryScreen() {
    val context = LocalContext.current
    val optimizer = remember { PowerGuardOptimizer(context) }

    var batteryLevel by remember { mutableStateOf(75) }
    var temperature by remember { mutableStateOf(32.5f) }
    var maxChargeLevel by remember { mutableFloatStateOf(80f) }
    var remainingTime by remember { mutableStateOf("3h 45m") }

    // Simulated app battery usage data
    val appUsageList = remember {
        listOf(
            BatteryAppUsage("com.google.android.youtube", "YouTube", 28f),
            BatteryAppUsage("com.instagram.android", "Instagram", 19f),
            BatteryAppUsage("com.spotify.music", "Spotify", 12f),
            BatteryAppUsage("com.whatsapp", "WhatsApp", 9f),
            BatteryAppUsage("com.android.chrome", "Chrome", 7f),
            BatteryAppUsage("com.google.android.gm", "Gmail", 5f),
            BatteryAppUsage("com.android.systemui", "System UI", 4f)
        )
    }

    // Simulate battery changes
    LaunchedEffect(Unit) {
        while (true) {
            // Adjust battery level
            batteryLevel = (batteryLevel - Random.nextInt(0, 2)).coerceAtLeast(20)

            // Adjust temperature
            temperature = (temperature + Random.nextFloat() * 0.4f - 0.2f).coerceIn(30f, 40f)

            // Set remaining time if battery level drops below 30
            if (batteryLevel < 30) {
                remainingTime = "1h 20m"
            }

            delay(60000) // Update every minute
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Battery Optimization") }
            )
        }
    ) { paddingValues ->
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
                                        batteryLevel > 60 -> Color(0xFF4CAF50)
                                        batteryLevel > 30 -> Color(0xFFFFC107)
                                        else -> Color(0xFFF44336)
                                    },
                                    startAngle = -90f,
                                    sweepAngle = 360f * (batteryLevel / 100f),
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
                                    text = "$batteryLevel%",
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                Text(
                                    text = remainingTime,
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
                                    text = "Temperature",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${"%.1f".format(temperature)}°C",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (temperature > 38f)
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
                                    text = "Discharging",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }

                            Column {
                                Text(
                                    text = "Health",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Good",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }

            // Smart Charging Card
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
                                text = "Smart Charging",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Limit maximum charge level to extend battery lifespan",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Maximum charge: ${maxChargeLevel.roundToInt()}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // App Usage Section
            item {
                Text(
                    text = "Battery Usage by App",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            // App usage items
            items(appUsageList) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "${app.percentUsage.roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    LinearProgressIndicator(
                        progress = app.percentUsage / 100f,
                        modifier = Modifier.width(100.dp),
                        color = when {
                            app.percentUsage > 20f -> MaterialTheme.colorScheme.error
                            app.percentUsage > 10f -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }

            // Add some space at the bottom for better UX
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}