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

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    showSnackbar: (String) -> Unit,
    openPromptInput: Boolean = false
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier
    ) {
        composable(Screen.Dashboard.route) { 
            DashboardScreen(
                modifier = modifier,
                showSnackbar = showSnackbar,
                openPromptInput = openPromptInput
            ) 
        }
        composable(Screen.Explore.route) { ExploreScreen() }
        composable(Screen.History.route) { HistoryScreen() }
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
