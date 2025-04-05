package com.hackathon.powergaurd.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hackathon.powergaurd.PowerGuardOptimizer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(packageName: String, navController: NavController) {
    val context = LocalContext.current
    val optimizer = remember { PowerGuardOptimizer(context) }

    // Get app name (would come from PackageManager in a real app)
    val appName = when (packageName) {
        "com.google.android.youtube" -> "YouTube"
        "com.spotify.music" -> "Spotify"
        "com.instagram.android" -> "Instagram"
        else -> packageName.substringAfterLast(".")
    }

    var selectedBackgroundOptionIndex by remember { mutableStateOf(0) }
    val backgroundOptions = listOf("None", "Moderate", "Strict")

    var selectedWakeLockOptionIndex by remember { mutableStateOf(0) }
    val wakeLockOptions = listOf("Allow", "30 min", "Disable")

    var selectedNetworkOptionIndex by remember { mutableStateOf(0) }
    val networkOptions = listOf("Allow", "Restrict", "Schedule")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appName) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // App header section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // This would be a real app icon in a production app
                    Card(
                        modifier = Modifier.size(64.dp)
                    ) {
                        // App icon placeholder
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = appName,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = packageName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Battery Optimization Section
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
                            imageVector = Icons.Default.Battery4Bar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Background Restriction",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Control how the app runs in the background to save battery",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        backgroundOptions.forEachIndexed { index, label ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = backgroundOptions.size
                                ),
                                onClick = { selectedBackgroundOptionIndex = index },
                                selected = index == selectedBackgroundOptionIndex
                            ) {
                                Text(label)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val restrictionLevel = when (selectedBackgroundOptionIndex) {
                                0 -> "none"
                                1 -> "moderate"
                                else -> "strict"
                            }
                            optimizer.setAppBackgroundRestriction(packageName, restrictionLevel)
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Apply")
                    }
                }
            }

            // Wake Lock Management Section
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
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Wake Lock Management",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Control how long the app can keep your device awake",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        wakeLockOptions.forEachIndexed { index, label ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = wakeLockOptions.size
                                ),
                                onClick = { selectedWakeLockOptionIndex = index },
                                selected = index == selectedWakeLockOptionIndex
                            ) {
                                Text(label)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val action = when (selectedWakeLockOptionIndex) {
                                0 -> "enable"
                                1 -> "timeout"
                                else -> "disable"
                            }
                            val timeout =
                                if (selectedWakeLockOptionIndex == 1) 30 * 60 * 1000L else 0
                            optimizer.manageWakeLock(packageName, action, timeout)
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Apply")
                    }
                }
            }

            // Network Usage Section
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
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Network Usage",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Control how the app uses network data",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        networkOptions.forEachIndexed { index, label ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = networkOptions.size
                                ),
                                onClick = { selectedNetworkOptionIndex = index },
                                selected = index == selectedNetworkOptionIndex
                            ) {
                                Text(label)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val enabled = selectedNetworkOptionIndex > 0
                            val timeRanges = if (selectedNetworkOptionIndex == 2) {
                                // Create a work hours schedule (9 AM - 5 PM, weekdays)
                                listOf(
                                    PowerGuardOptimizer.TimeRange(
                                        9, 0,
                                        17, 0,
                                        listOf(
                                            java.util.Calendar.MONDAY,
                                            java.util.Calendar.TUESDAY,
                                            java.util.Calendar.WEDNESDAY,
                                            java.util.Calendar.THURSDAY,
                                            java.util.Calendar.FRIDAY
                                        )
                                    )
                                )
                            } else null

                            optimizer.restrictBackgroundData(packageName, enabled, timeRanges)
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Apply")
                    }
                }
            }

            // AI Recommendation
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
                        text = "AI Recommendation",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val recommendation = when (packageName) {
                        "com.google.android.youtube" ->
                            "YouTube consumes significant battery and network resources. We recommend setting strict background restrictions and limiting network access."

                        "com.spotify.music" ->
                            "Spotify keeps wake locks active even when music is paused. Setting a 30-minute timeout for wake locks can save approximately 15% battery."

                        "com.instagram.android" ->
                            "Instagram has high background network usage. Restricting background data can save your mobile data and improve battery life."

                        else ->
                            "Based on usage patterns, we recommend moderate background restrictions and network scheduling for this app."
                    }

                    Text(
                        text = recommendation,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            // Apply optimal settings based on AI recommendation
                            when (packageName) {
                                "com.google.android.youtube" -> {
                                    optimizer.setAppBackgroundRestriction(packageName, "strict")
                                    optimizer.restrictBackgroundData(packageName, true)
                                }

                                "com.spotify.music" -> {
                                    optimizer.manageWakeLock(packageName, "timeout", 30 * 60 * 1000)
                                    optimizer.setAppBackgroundRestriction(packageName, "moderate")
                                }

                                "com.instagram.android" -> {
                                    optimizer.restrictBackgroundData(packageName, true)
                                    optimizer.setAppBackgroundRestriction(packageName, "moderate")
                                }

                                else -> {
                                    optimizer.setAppBackgroundRestriction(packageName, "moderate")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Apply AI Recommendation")
                    }
                }
            }
        }
    }
}
