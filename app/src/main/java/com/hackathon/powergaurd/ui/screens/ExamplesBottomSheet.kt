package com.hackathon.powergaurd.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
    sheetState: SheetState
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Force the sheet to be fullscreen height
        dragHandle = {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 36.dp)
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
            
            Text("• \"Show me top 3 data consuming apps\"")
            Text("• \"Which apps consume the most battery?\"")
            Text("• \"Find out which apps are draining my data\"")
            
            // Predictive Queries
            Text(
                text = "🔮 Predictive Queries",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            
            Text("• \"Can I watch Netflix for 5 hours without charging?\"")
            Text("• \"Is my battery enough for 3 hours of Netflix?\"")
            Text("• \"Will my battery last for 2 hours of Google Maps?\"")
            
            // Optimization Queries
            Text(
                text = "⚡ Optimization Requests",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            
            Text("• \"Save battery but keep WhatsApp and Maps running\"")
            Text("• \"Optimize my battery for a 6-hour journey\"")
            Text("• \"Save battery during flight but keep Spotify running\"")
            
            // Monitoring Queries
            Text(
                text = "🔔 Monitoring Requests",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            
            Text("• \"Notify me when battery drops to 35% while gaming\"")
            Text("• \"Alert me if my data usage exceeds 1GB today\"")
            
            // Past Usage Pattern Queries
            Text(
                text = "📊 Usage Pattern Optimization",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            
            Text("• \"Optimize data based on my past usage patterns\"")
            Text("• \"Optimize battery based on how I typically use my phone\"")
            
            // Quick Tip Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🧠 Quick Tip:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
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