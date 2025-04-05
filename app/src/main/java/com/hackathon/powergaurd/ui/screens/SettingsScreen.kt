package com.hackathon.powergaurd.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hackathon.powergaurd.workers.DataCollectionWorker
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val workManager = WorkManager.getInstance(context)

    var autoOptimize by remember { mutableStateOf(true) }
    var updateFrequency by remember { mutableFloatStateOf(30f) }
    var autoApplyAI by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Text(
                    text = "Data Collection",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Auto Optimize",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Automatically apply optimizations periodically",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Switch(
                        checked = autoOptimize,
                        onCheckedChange = {
                            autoOptimize = it
                            // In a real app, this would update app preferences and
                            // enable/disable the WorkManager tasks
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Update frequency: ${updateFrequency.roundToInt()} minutes",
                    style = MaterialTheme.typography.bodyMedium
                )

                Slider(
                    value = updateFrequency,
                    onValueChange = {
                        updateFrequency = it
                        // Schedule data collection with new frequency
                        val constraints = Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .setRequiresBatteryNotLow(true)
                            .build()

                        val dataCollectionRequest =
                            PeriodicWorkRequestBuilder<DataCollectionWorker>(
                                updateFrequency.roundToInt().toLong(), TimeUnit.MINUTES,
                                5, TimeUnit.MINUTES // Flex period
                            )
                                .setConstraints(constraints)
                                .build()

                        workManager.enqueueUniquePeriodicWork(
                            "data_collection_work",
                            ExistingPeriodicWorkPolicy.UPDATE,
                            dataCollectionRequest
                        )
                    },
                    valueRange = 15f..60f,
                    steps = 3
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "AI Optimization",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Auto-apply AI Recommendations",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Automatically apply AI-recommended optimizations",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Switch(
                        checked = autoApplyAI,
                        onCheckedChange = { autoApplyAI = it }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "AI PowerGuard v1.0",
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = "© 2025 PowerGuard Team",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "Created for Hackathon 2025",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )
            }
        }
    }
}