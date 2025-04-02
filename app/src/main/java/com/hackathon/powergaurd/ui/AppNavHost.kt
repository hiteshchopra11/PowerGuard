package com.hackathon.powergaurd.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.hackathon.powergaurd.ui.navigation.Screen
import com.hackathon.powergaurd.ui.screens.DashboardScreen
import com.hackathon.powergaurd.ui.screens.AppsScreen
import com.hackathon.powergaurd.ui.screens.BatteryScreen
import com.hackathon.powergaurd.ui.screens.SettingsScreen

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
        composable(Screen.Dashboard.route) {
            DashboardScreen(showSnackbar = showSnackbar)
        }
        composable(Screen.Apps.route) {
            AppsScreen(navController = navController)
        }
        composable(Screen.Battery.route) {
            BatteryScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
        composable("app_details/{packageName}") { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName") ?: ""
            AppDetailScreen(packageName = packageName, navController = navController)
        }
    }
}