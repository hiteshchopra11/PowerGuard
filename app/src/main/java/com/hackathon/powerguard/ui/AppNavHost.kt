package com.hackathon.powerguard.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hackathon.powerguard.ui.navigation.Screen
import com.hackathon.powerguard.ui.screens.BatteryScreen
import com.hackathon.powerguard.ui.screens.HistoryScreen
import com.hackathon.powerguard.ui.screens.SettingsScreen
import com.hackathon.powerguard.ui.screens.dashboard.DashboardScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    showSnackbar: (String) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier
    ) {
        composable(Screen.Dashboard.route) { DashboardScreen(showSnackbar = showSnackbar) }
        composable(Screen.Battery.route) { BatteryScreen() }
        composable(Screen.History.route) { HistoryScreen() }
        composable(Screen.Settings.route) { SettingsScreen() }
    }
}

@Preview
@Composable
fun PreviewAppNavHost() {
    val navController = rememberNavController()
    AppNavHost(
        navController = navController,
        showSnackbar = {}
    )
}
