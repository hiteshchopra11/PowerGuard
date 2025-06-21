package com.hackathon.powergaurd.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamplesBottomSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState,
    onPromptSelected: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "💡 Try asking me:",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Information Queries
            Text(
                text = "🔍 Information Queries",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            
            ClickablePrompt("• \"Show me top 3 data consuming apps\"", onPromptSelected)
            ClickablePrompt("• \"Which apps consume the most battery?\"", onPromptSelected)
            ClickablePrompt("• \"Find out which apps are draining my data\"", onPromptSelected)
            
            // Predictive Queries
            Text(
                text = "🔮 Predictive Queries",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            
            ClickablePrompt("• \"Can I watch Netflix for 5 hours without charging?\"", onPromptSelected)
            ClickablePrompt("• \"Is my battery enough for 3 hours of Netflix?\"", onPromptSelected)
            ClickablePrompt("• \"Will my battery last for 2 hours of Google Maps?\"", onPromptSelected)
            
            // Optimization Queries
            Text(
                text = "⚡ Optimization Requests",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            
            ClickablePrompt("• \"Save battery but keep WhatsApp and Maps running\"", onPromptSelected)
            ClickablePrompt("• \"Optimize my battery for a 6-hour journey\"", onPromptSelected)
            ClickablePrompt("• \"Save battery during flight but keep Spotify running\"", onPromptSelected)
            
            // Monitoring Queries
            Text(
                text = "🔔 Monitoring Requests",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            
            ClickablePrompt("• \"Notify me when battery drops to 35% while gaming\"", onPromptSelected)
            ClickablePrompt("• \"Alert me if my data usage exceeds 1GB today\"", onPromptSelected)
            
            // Past Usage Pattern Queries
            Text(
                text = "📊 Usage Pattern Optimization",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            
            ClickablePrompt("• \"Optimize data based on my past usage patterns\"", onPromptSelected)
            ClickablePrompt("• \"Optimize battery based on how I typically use my phone\"", onPromptSelected)
            
            // Quick Tip Section - Fixed layout to prevent truncation
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "🧠 Quick Tip:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "You can just ask in plain language — no special commands needed.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ClickablePrompt(text: String, onPromptSelected: (String) -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // Extract the text without bullet point and quotes
                val cleanText = text.removePrefix("• ")
                    .removeSurrounding("\"")
                onPromptSelected(cleanText)
            }
            .padding(vertical = 4.dp)
    )
} 