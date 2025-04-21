package com.hackathon.powergaurd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hackathon.powergaurd.ui.viewmodels.DashboardViewModel

/**
 * Bottom sheet for setting test values for development and testing purposes
 */
@Composable
fun TestValuesBottomSheet(
    viewModel: DashboardViewModel,
    onDismiss: () -> Unit
) {
    // Collect current custom values
    val currentCustomBatteryLevel by viewModel.customBatteryLevel.collectAsState()
    val currentCustomIsCharging by viewModel.customIsCharging.collectAsState()
    
    BottomSheetContent(
        title = "Test Settings",
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            var totalDataMbText by remember { mutableStateOf("") }
            var currentDataMbText by remember { mutableStateOf("") }
            var batteryLevelText by remember { mutableStateOf(if (currentCustomBatteryLevel > 0) currentCustomBatteryLevel.toString() else "") }
            var isChargingEnabled by remember { mutableStateOf(currentCustomIsCharging != null) }
            var isCharging by remember { mutableStateOf(currentCustomIsCharging ?: false) }
            
            // New fields for past usage patterns
            var patternText by remember { mutableStateOf("") }
            val patternsList = remember { mutableStateListOf<String>() }
            
            Text(
                text = "These settings are for testing and development purposes only.",
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            OutlinedTextField(
                value = totalDataMbText,
                onValueChange = { totalDataMbText = it },
                label = { Text("Total Data Plan (MB)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = currentDataMbText,
                onValueChange = { currentDataMbText = it },
                label = { Text("Current Data Usage (MB)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Battery section header
            Text(
                text = "Battery Settings",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp, top = 16.dp)
            )
            
            OutlinedTextField(
                value = batteryLevelText,
                onValueChange = { batteryLevelText = it },
                label = { Text("Custom Battery Level (1-100%)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Charging state toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.BatteryChargingFull,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                
                Text(
                    text = "Override Charging State",
                    modifier = Modifier.weight(1f)
                )
                
                Switch(
                    checked = isChargingEnabled,
                    onCheckedChange = { enabled ->
                        isChargingEnabled = enabled
                        // If disabling the override, set to null
                        if (!enabled) {
                            isCharging = false
                        }
                    }
                )
            }
            
            // Only show the charging state toggle if the override is enabled
            if (isChargingEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Device is charging",
                        modifier = Modifier.weight(1f)
                    )
                    
                    Switch(
                        checked = isCharging,
                        onCheckedChange = { isCharging = it }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Past Usage Patterns Section
            Text(
                text = "Past Usage Patterns",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = patternText,
                    onValueChange = { patternText = it },
                    label = { Text("Enter usage pattern") },
                    modifier = Modifier.weight(1f)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                IconButton(
                    onClick = {
                        if (patternText.isNotBlank()) {
                            patternsList.add(patternText)
                            patternText = ""
                        }
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Pattern",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Display added patterns
            if (patternsList.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        items(patternsList) { pattern ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = pattern,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                IconButton(
                                    onClick = { patternsList.remove(pattern) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove Pattern",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No patterns added yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    // Parse and update test values
                    val totalDataMb = totalDataMbText.toFloatOrNull() ?: 0f
                    val currentDataMb = currentDataMbText.toFloatOrNull() ?: 0f
                    val batteryLevel = batteryLevelText.toIntOrNull() ?: 0
                    
                    // Update in ViewModel
                    viewModel.updateDataSettings(totalDataMb, currentDataMb)
                    
                    if (batteryLevel in 1..100) {
                        viewModel.updateCustomBatteryLevel(batteryLevel)
                    } else if (batteryLevelText.isEmpty()) {
                        // If the field is empty, reset to 0 (use real device value)
                        viewModel.updateCustomBatteryLevel(0)
                    }
                    
                    // Update charging state
                    if (isChargingEnabled) {
                        viewModel.updateCustomIsCharging(isCharging)
                    } else {
                        viewModel.updateCustomIsCharging(null)
                    }
                    
                    // Update past usage patterns
                    viewModel.updatePastUsagePatterns(patternsList.toList())
                    
                    // Refresh data with new values
                    viewModel.fetchDeviceDataOnly()
                    
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply Test Values")
            }
        }
    }
} 