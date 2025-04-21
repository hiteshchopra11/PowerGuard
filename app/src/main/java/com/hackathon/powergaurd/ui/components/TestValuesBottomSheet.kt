package com.hackathon.powergaurd.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            var batteryLevelText by remember { mutableStateOf("") }
            
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
            
            OutlinedTextField(
                value = batteryLevelText,
                onValueChange = { batteryLevelText = it },
                label = { Text("Custom Battery Level (1-100%)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            
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
                    }
                    
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