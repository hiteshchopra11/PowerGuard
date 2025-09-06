package com.hackathon.powerguard.ui.navigation

sealed class Screen(val route: String, val title: String) {
    object Dashboard : Screen("dashboard", "Dashboard")
    object Explore : Screen("explore", "Explore")
    object History : Screen("history", "History")
}
