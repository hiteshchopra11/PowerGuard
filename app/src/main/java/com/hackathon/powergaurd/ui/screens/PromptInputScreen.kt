package com.hackathon.powergaurd.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hackathon.powergaurd.llm.QueryProcessor
import com.hackathon.powergaurd.ui.viewmodels.PromptViewModel
import kotlinx.coroutines.launch

/**
 * Screen for user to input a prompt about battery or data usage
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptInputScreen(
    navigateBack: () -> Unit,
    showSnackbar: (String) -> Unit,
    viewModel: PromptViewModel = hiltViewModel()
) {
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val lastResponse by viewModel.lastResponse.collectAsStateWithLifecycle()
    
    val scope = rememberCoroutineScope()
    var promptText by remember { mutableStateOf("") }
    var showPreviousResponse by remember { mutableStateOf(false) }
    
    // Show previous response if available
    LaunchedEffect(lastResponse) {
        if (lastResponse != null) {
            showPreviousResponse = true
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ask PowerGuard") },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                if (showPreviousResponse && lastResponse != null) {
                    ResponseCard(lastResponse!!)
                } else {
                    InstructionsCard()
                }
            }
            
            Column {
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    label = { Text("Enter your question...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    enabled = !isProcessing,
                    maxLines = 3
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            promptText = ""
                            showPreviousResponse = false
                        },
                        enabled = !isProcessing && promptText.isNotEmpty()
                    ) {
                        Text("Clear")
                    }
                    
                    Button(
                        onClick = {
                            if (promptText.isNotBlank()) {
                                scope.launch {
                                    try {
                                        viewModel.processUserPrompt(promptText)
                                        promptText = ""
                                    } catch (e: Exception) {
                                        Log.e("PromptInputScreen", "Error processing prompt", e)
                                        showSnackbar("Error: ${e.message}")
                                    }
                                }
                            }
                        },
                        enabled = !isProcessing && promptText.isNotBlank()
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Filled.Send,
                                contentDescription = "Send",
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text("Send")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResponseCard(response: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "PowerGuard Response",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = response,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun InstructionsCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Ask about battery and data usage",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Try questions like:",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("• Which apps are draining my battery the most?")
            Text("• Can I watch Netflix for 2 hours with my current battery?")
            Text("• Save battery for my commute but keep Maps running")
            Text("• Alert me when TikTok uses more than 500MB data")
            
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "PowerGuard will analyze your device and provide personalized insights and recommendations.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
} 