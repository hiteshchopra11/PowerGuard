package com.hackathon.powergaurd.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.hackathon.powergaurd.ui.navigation.Screen

@Composable
fun BottomNavBar(navController: NavController) {
    val items =
        listOf(Screen.Dashboard, Screen.Apps, Screen.Battery, Screen.History, Screen.Settings)

    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        items.forEach { screen ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector =
                        when (screen) {
                            Screen.Dashboard -> Icons.Default.Dashboard
                            Screen.Apps -> Icons.Default.Apps
                            Screen.Battery -> Icons.Default.Battery4Bar
                            Screen.History -> Icons.Default.History
                            Screen.Settings -> Icons.Default.Settings
                        },
                        contentDescription = null
                    )
                },
                label = { Text(screen.title) },
                selected =
                currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                onClick = {
                    navController.navigate(screen.route) {
                        // Pop up to the start destination of the graph to
                        // avoid building up a large stack of destinations
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination when
                        // reselecting the same item
                        launchSingleTop = true
                        // Restore state when reselecting a previously selected item
                        restoreState = true
                    }
                }
            )
        }
    }
}
