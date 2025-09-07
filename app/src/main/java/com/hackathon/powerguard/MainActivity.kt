package com.hackathon.powerguard

import android.Manifest
import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hackathon.powerguard.services.PowerGuardService
import com.hackathon.powerguard.theme.PowerGuardTheme
import com.hackathon.powerguard.ui.AppNavHost
import com.hackathon.powerguard.ui.BottomNavBar
import com.hackathon.powerguard.ui.navigation.Screen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.values.all { it }
            if (!allGranted) {
                // Handle permission denial
                showAppSettings()
            } else {
                // Permissions granted, start service
                startPowerGuardService()
            }
        }
    
    private var doubleBackToExitPressedOnce = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Check if we need to focus on the prompt input
        val openDashboard = intent.getBooleanExtra("OPEN_DASHBOARD", false)
        
        setContent { 
            PowerGuardTheme { 
                PowerGuardAppUI(
                    openPromptInput = openDashboard,
                    onRefreshData = { refreshDeviceData() }
                ) 
            } 
        }

        // Request permissions
        requestRequiredPermissions()
        
        // Set up back press handling
        setupBackPressHandling()
    }
    
    
    /**
     * Refresh device data in the app
     */
    private fun refreshDeviceData() {
        // We can't directly access the ViewModel here because it's tied to the Compose lifecycle
        // Instead, we'll pass a "refresh" flag through the NavController if needed,
        // or just show a toast message for now
        Toast.makeText(this, "Refreshing device data...", Toast.LENGTH_SHORT).show()
    }
    
    private fun setupBackPressHandling() {
        // Register a callback for back press events
        onBackPressedDispatcher.addCallback(this) {
            if (doubleBackToExitPressedOnce) {
                finish()
            } else {
                doubleBackToExitPressedOnce = true
                Toast.makeText(this@MainActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
                
                // Reset after 2 seconds using coroutines
                lifecycleScope.launch {
                    kotlinx.coroutines.delay(2000)
                    doubleBackToExitPressedOnce = false
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Since we're on Android 15+, we always need these permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.FOREGROUND_SERVICE)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // If we have permissions to request, request them
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // We already have the permissions, start service
            startPowerGuardService()
        }

        // For special permissions that need to be granted via system UI
        requestUsageStatsPermission()
    }

    private fun requestUsageStatsPermission() {
        if (!hasUsageStatsPermission()) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)
        } else {
            // Permission already granted, proceed with service startup
            startPowerGuardService()
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOpsManager = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode =
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun showAppSettings() {
        val intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        startActivity(intent)
    }

    private fun startPowerGuardService() {
        // For system apps, we want to start the service at boot
        // But for demonstration, we'll start it when the app is launched
        Log.d("MainActivity", "Starting PowerGuardService")
        val serviceIntent = Intent(this, PowerGuardService::class.java)

        // For Android 12+ (API 31+), we need to be more explicit about foreground service starts
        startForegroundService(serviceIntent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PowerGuardAppUI(
    openPromptInput: Boolean = false,
    onRefreshData: () -> Unit
) {
    // Store NavController in a variable so we can use it
    val navController = rememberNavController()

    // Get current back stack entry as state
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    // State for whether the menu is shown
    var showMenu by remember { mutableStateOf(false) }

    // State for the SnackbarHostState
    val snackbarHostState = remember { SnackbarHostState() }

    // State for triggering refreshes
    var refreshTrigger by remember { mutableStateOf(false) }

    // State for triggering settings bottom sheet
    var settingsTrigger by remember { mutableStateOf(false) }

    // Use rememberCoroutineScope to create a CoroutineScope that is scoped to the composition
    val coroutineScope = rememberCoroutineScope()

    // Inference mode switch state: false = AI (default), true = Backend
    var useBackend by remember { mutableStateOf(false) }

    // If openPromptInput is true, we navigate to the dashboard
    LaunchedEffect(openPromptInput) {
        if (openPromptInput) {
            navController.navigate("dashboard") {
                // Pop up to the dashboard destination
                popUpTo("dashboard") { inclusive = true }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Power Guard",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    // Refresh button
                    IconButton(onClick = {
                        // Toggle refresh trigger to notify DashboardScreen
                        refreshTrigger = !refreshTrigger
                        onRefreshData()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Refreshing data...")
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }

                    // Settings button for test values (previously in DashboardScreen)
                    IconButton(onClick = {
                        // We'll communicate to DashboardScreen to show bottom sheet
                        // using a separate trigger
                        // Check if we're on dashboard screen
                        if (currentBackStackEntry?.destination?.route == Screen.Dashboard.route) {
                            // Only show this when on dashboard screen
                            settingsTrigger = !settingsTrigger
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Test Settings"
                        )
                    }

                    // More options menu
                    Box {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("About") },
                                onClick = {
                                    showMenu = false
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("PowerGuard v1.0.0")
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Use backend (cloud)") },
                                onClick = {
                                    useBackend = !useBackend
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (useBackend) "Switched to Backend" else "Switched to AI (on-device)"
                                        )
                                    }
                                },
                                trailingIcon = {
                                    Switch(
                                        checked = useBackend,
                                        onCheckedChange = { checked ->
                                            useBackend = checked
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(
                                                    if (useBackend) "Switched to Backend" else "Switched to AI (on-device)"
                                                )
                                            }
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = { BottomNavBar(navController = navController) }
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
            showSnackbar = { message ->
                coroutineScope.launch { snackbarHostState.showSnackbar(message) }
            },
            openPromptInput = openPromptInput,
            refreshTrigger = refreshTrigger,
            settingsTrigger = settingsTrigger,
            useBackend = useBackend
        )
    }
}
