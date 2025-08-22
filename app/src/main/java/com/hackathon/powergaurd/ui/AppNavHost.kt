package com.hackathon.powergaurd.ui

import androidx.compose.runtime.Composable

import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hackathon.powergaurd.ui.navigation.Screen
import com.hackathon.powergaurd.ui.screens.ExploreScreen
import com.hackathon.powergaurd.ui.screens.HistoryScreen
import com.hackathon.powergaurd.ui.screens.DashboardScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.hackathon.powergaurd.ui.viewmodels.HistoryViewModel

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    showSnackbar: (String) -> Unit,
    openPromptInput: Boolean = false,
    refreshTrigger: Boolean = false,
    settingsTrigger: Boolean = false
) {
    // Initialize HistoryViewModel at the app level to ensure it's available
    // This prevents the "HistoryViewModel not initialized yet" error
    val historyViewModel: HistoryViewModel = hiltViewModel()
    
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier
    ) {
        composable(Screen.Dashboard.route) { 
            DashboardScreen(
                modifier = modifier,
                showSnackbar = showSnackbar,
                openPromptInput = openPromptInput,
                refreshTrigger = refreshTrigger,
                settingsTrigger = settingsTrigger
            ) 
        }
        composable(Screen.Explore.route) { ExploreScreen() }
        composable(Screen.History.route) { HistoryScreen(viewModel = historyViewModel) }
    }
}

@Preview
@Composable
fun PreviewAppNavHost() {
    val navController = rememberNavController()
    AppNavHost(
        navController = navController,
        showSnackbar = {},
        settingsTrigger = false
    )
}
