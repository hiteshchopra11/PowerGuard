package com.hackathon.powergaurd.ui.navigation

sealed class Screen(val route: String, val title: String) {
    object Dashboard : Screen("dashboard", "Dashboard")
    object Apps : Screen("apps", "Apps")
    object Battery : Screen("battery", "Battery")
    object Settings : Screen("settings", "Settings")
}