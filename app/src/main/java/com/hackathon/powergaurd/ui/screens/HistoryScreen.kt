package com.hackathon.powergaurd.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hackathon.powergaurd.data.repository.ActionHistoryItem
import com.hackathon.powergaurd.ui.viewmodels.HistoryViewModel
import com.hackathon.powergaurd.util.formatTimestamp
import java.text.SimpleDateFormat
import java.util.*

/** Screen that displays the history of actions taken by PowerGuard. */
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val historyState by viewModel.historyState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Title row with clear button
        Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Optimization History", style = MaterialTheme.typography.headlineMedium)

            IconButton(onClick = { viewModel.clearHistory() }) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear History")
            }
        }

        if (historyState.isLoading) {
            // Loading state
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (historyState.items.isEmpty()) {
            // Empty state
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                        text = "No optimization history yet",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Group history items by date
            val groupedItems =
                    historyState.items.groupBy { item ->
                        formatTimestamp(item.timestamp, "MMMM d, yyyy")
                    }

            LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupedItems.forEach { (date, items) ->
                    item {
                        // Date header
                        Text(
                                text = date,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(items) { historyItem -> HistoryItemCard(historyItem) }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(historyItem: ActionHistoryItem) {
    Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Status indicator and time
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                // Status indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                            modifier =
                                    Modifier.size(12.dp)
                                            .background(
                                                    color =
                                                            if (historyItem.succeeded) {
                                                                MaterialTheme.colorScheme.primary
                                                            } else {
                                                                MaterialTheme.colorScheme.error
                                                            },
                                                    shape = MaterialTheme.shapes.small
                                            )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                            text = if (historyItem.succeeded) "Success" else "Failed",
                            style = MaterialTheme.typography.labelMedium
                    )
                }

                // Time
                Text(
                        text = formatTimestamp(historyItem.timestamp, "h:mm a"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Summary
            Text(text = historyItem.summary, style = MaterialTheme.typography.bodyMedium)

            // Optional app details
            if (historyItem.appPackage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                        text = "App: ${historyItem.appPackage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun formatTimestamp(timestamp: Long, pattern: String): String {
    val dateFormat = SimpleDateFormat(pattern, Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}
