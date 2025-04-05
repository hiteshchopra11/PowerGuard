package com.hackathon.powergaurd.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hackathon.powergaurd.PowerGuardOptimizer
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(showSnackbar: (String) -> Unit) {
    val context = LocalContext.current
    val optimizer = remember { PowerGuardOptimizer(context) }
    var batteryLevel by remember { mutableStateOf(0) }
    var isCharging by remember { mutableStateOf(false) }

    // Simulating data updates
    LaunchedEffect(Unit) {
        while (true) {
            batteryLevel = (30..95).random()
            isCharging = (0..1).random() == 1
            delay(10000) // Refresh every 10 seconds
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TopAppBar(
            title = { Text("AI PowerGuard Dashboard") }
        )

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

                Text("Current level: $batteryLevel%")
                Text("Status: ${if (isCharging) "Charging" else "Discharging"}")

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        optimizer.optimizeCharging(80)
                        showSnackbar("Battery optimization applied")
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Optimize Charging")
                }
            }
        }

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
                        "Network Usage",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("High usage apps detected: YouTube, Instagram")

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        optimizer.restrictBackgroundData("com.google.android.youtube", true)
                        showSnackbar("Network restrictions applied")
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Restrict Background Data")
                }
            }
        }

        // System Performance Card
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
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "System Performance",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Wake locks detected: Spotify, Gmail")

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        optimizer.manageWakeLock("com.spotify.music", "timeout", 30 * 60 * 1000)
                        showSnackbar("Wake lock timeouts applied")
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Manage Wake Locks")
                }
            }
        }

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
                    "Based on your usage patterns, we've detected that YouTube keeps a wake lock even when paused, and Spotify uses significant background data. We recommend restricting background activities for these apps to improve battery life by approximately 25%.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        // Apply all optimizations
                        optimizer.setAppBackgroundRestriction(
                            "com.google.android.youtube",
                            "strict"
                        )
                        optimizer.manageWakeLock("com.spotify.music", "disable")
                        showSnackbar("All optimizations applied")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply All Optimizations")
                }
            }
        }
    }
}
