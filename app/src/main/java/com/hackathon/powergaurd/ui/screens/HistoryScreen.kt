package com.hackathon.powergaurd.ui.screens

import android.util.Log
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hackathon.powergaurd.data.local.entity.DeviceInsightEntity
import com.hackathon.powergaurd.data.local.entity.DeviceActionableEntity
import com.hackathon.powergaurd.ui.viewmodels.HistoryViewModel
import com.hackathon.powergaurd.ui.viewmodels.getFormattedDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Screen that displays the history of insights and actionables for device optimization. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val historyState by viewModel.historyState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Insights", "Actions")
    
    // Debug output of current state
    LaunchedEffect(historyState) {
        Log.d("HistoryScreen", "Current state: insights=${historyState.insights.size}, actions=${historyState.actionables.size}, loading=${historyState.isLoading}")
    }
    
    // Force refresh when screen is first displayed
    LaunchedEffect(Unit) {
        viewModel.refreshInsights()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("History") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab row for switching between Insights and Actions
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                        icon = {
                            Icon(
                                imageVector = if (index == 0) 
                                    Icons.Default.Lightbulb 
                                else 
                                    Icons.Default.Assignment,
                                contentDescription = title
                            )
                        }
                    )
                }
            }

            if (historyState.isLoading) {
                // Loading state
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Loading history...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                // Display the appropriate content based on the selected tab
                when (selectedTab) {
                    0 -> {
                        // Insights tab
                        if (historyState.insights.isEmpty()) {
                            // Empty state for insights
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No insights available yet. PowerGuard is still learning about your device.",
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )
                            }
                        } else {
                            // Show insights
                            InsightsHistoryList(historyState.insights)
                        }
                    }
                    1 -> {
                        // Actions tab
                        if (historyState.actionables.isEmpty()) {
                            // Empty state for actions
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No actions available yet. PowerGuard is still learning about your device.",
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )
                            }
                        } else {
                            // Show actionables
                            ActionablesHistoryList(historyState.actionables)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InsightsHistoryList(insights: List<DeviceInsightEntity>) {
    // Debug logging for insight items
    LaunchedEffect(insights) {
        Log.d("HistoryScreen", "InsightsHistoryList: Showing ${insights.size} insights")
        insights.forEachIndexed { index, insight ->
            Log.d("HistoryScreen", "Insight #$index - Type: ${insight.insightType}, Title: ${insight.insightTitle}, ID: ${insight.id}")
        }
    }
    
    if (insights.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No insights available yet",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    
    // Group insights by date
    val groupedInsights = insights.groupBy { insight ->
        insight.getFormattedDate().substringBefore(" ")
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        groupedInsights.forEach { (date, insightsForDate) ->
            item {
                // Date header
                Text(
                    text = date,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(insightsForDate) { insight -> 
                InsightCard(insight) 
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
        
        // Add some space at the bottom
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun ActionablesHistoryList(actionables: List<DeviceActionableEntity>) {
    // Debug logging for actionable items
    LaunchedEffect(actionables) {
        Log.d("HistoryScreen", "ActionablesHistoryList: Showing ${actionables.size} actionables")
        actionables.forEachIndexed { index, actionable ->
            Log.d("HistoryScreen", "Actionable #$index - Type: ${actionable.actionableType}, Description: ${actionable.description}, ID: ${actionable.id}")
        }
    }
    
    if (actionables.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No actions available yet",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    
    // Group actionables by date
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val groupedActionables = actionables.groupBy { actionable ->
        dateFormat.format(Date(actionable.timestamp))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        groupedActionables.forEach { (date, actionablesForDate) ->
            item {
                // Date header
                Text(
                    text = date,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(actionablesForDate) { actionable -> 
                ActionableCard(actionable) 
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
        
        // Add some space at the bottom
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun InsightCard(insight: DeviceInsightEntity) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Type and severity indicator with "INSIGHT" label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (insight.insightType) {
                            "BATTERY" -> Icons.Default.BatteryAlert
                            "DATA" -> Icons.Default.DataUsage
                            else -> Icons.Default.Info
                        },
                        contentDescription = null,
                        tint = getSeverityColor(insight.severity)
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = insight.insightTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = getSeverityColor(insight.severity)
                        )
                        Text(
                            text = "INSIGHT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                // Time
                Text(
                    text = insight.getFormattedDate().substringAfter(" "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            // Show insights with proper bullet points
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Box(
                    modifier = Modifier.width(20.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Text(
                        text = "•", 
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 18.sp
                    )
                }
                Text(
                    insight.insightDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun ActionableCard(actionable: DeviceActionableEntity) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(actionable.timestamp))
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Type indicator and time with "ACTION" label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Assignment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = actionable.actionableType.split("_").joinToString(" ") { it.capitalize() },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "ACTION",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                // Time
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            // Show actionables with proper bullet points
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Box(
                    modifier = Modifier.width(20.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Text(
                        text = "•", 
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 18.sp
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        actionable.description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    if (actionable.reason.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Reason: ${actionable.reason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun getSeverityColor(severity: String) = when (severity.uppercase()) {
    "HIGH" -> MaterialTheme.colorScheme.error
    "MEDIUM" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}

// Helper extension to capitalize strings
private fun String.capitalize(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

@Preview(showBackground = true)
@Composable
fun PreviewHistoryScreen() {
    // Create sample data for preview
    val sampleInsight = DeviceInsightEntity(
        id = 1,
        insightType = "BATTERY",
        insightTitle = "High Battery Drain",
        insightDescription = "Your device is experiencing higher than normal battery drain from background apps.",
        severity = "HIGH",
        timestamp = System.currentTimeMillis()
    )
    
    val sampleActionable = DeviceActionableEntity(
        id = 1,
        actionableId = "1",
        actionableType = "kill_app",
        packageName = "com.example.highusage",
        description = "Force stop YouTube to prevent background battery drain",
        reason = "High battery usage detected",
        newMode = "",
        timestamp = System.currentTimeMillis()
    )

    // Mock UI for preview
    MaterialTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp)) {
                Text("Sample Insight Card", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                InsightCard(sampleInsight)
                
                Spacer(Modifier.height(16.dp))
                
                Text("Sample Actionable Card", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                ActionableCard(sampleActionable)
            }
        }
    }
}
