package com.hackathon.powergaurd.ui.navigation

sealed class Screen(val route: String, val title: String) {
    object Dashboard : Screen("dashboard", "Dashboard")
    object Battery : Screen("battery", "Battery")
    object Settings : Screen("settings", "Settings")
    object History : Screen("history", "History")
}
