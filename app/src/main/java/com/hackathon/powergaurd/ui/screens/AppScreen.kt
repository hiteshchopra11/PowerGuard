package com.hackathon.powergaurd.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

data class AppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val powerUsage: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(navController: NavController) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Simulated app list - in a real app, this would come from PackageManager
    val appsList = remember {
        listOf(
            AppInfo("com.google.android.youtube", "YouTube", false, "High"),
            AppInfo("com.spotify.music", "Spotify", false, "Medium"),
            AppInfo("com.instagram.android", "Instagram", false, "High"),
            AppInfo("com.whatsapp", "WhatsApp", false, "Low"),
            AppInfo("com.facebook.katana", "Facebook", false, "High"),
            AppInfo("com.google.android.gm", "Gmail", false, "Low"),
            AppInfo("com.android.chrome", "Chrome", false, "Medium")
        )
    }

    var filteredApps by remember { mutableStateOf(appsList) }

    // Filter apps based on search query
    filteredApps = if (searchQuery.isEmpty()) {
        appsList
    } else {
        appsList.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TopAppBar(
            title = { Text("Installed Apps") }
        )

        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSearch = { isSearchActive = false },
            active = isSearchActive,
            onActiveChange = { isSearchActive = it },
            placeholder = { Text("Search apps") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        ) {
            // Search suggestions can go here
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn {
            items(filteredApps) { app ->
                AppItem(app = app) {
                    navController.navigate("app_details/${app.packageName}")
                }
            }
        }
    }
}

@Composable
fun AppItem(app: AppInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            // App icon placeholder
            Card(
                modifier = Modifier.size(48.dp)
            ) {
                // This would be an actual app icon in a real app
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Power: ${app.powerUsage}",
                style = MaterialTheme.typography.bodyMedium,
                color = when (app.powerUsage) {
                    "High" -> MaterialTheme.colorScheme.error
                    "Medium" -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.secondary
                }
            )
        }
    }
}