package com.hackathon.powergaurd

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.hackathon.powergaurd.services.PowerGuardService
import com.hackathon.powergaurd.theme.PowerGuardTheme
import com.hackathon.powergaurd.ui.AppNavHost
import com.hackathon.powergaurd.ui.BottomNavBar
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
        
        // Check if we need to focus on the prompt input
        val openDashboard = intent.getBooleanExtra("OPEN_DASHBOARD", false)
        
        setContent { 
            PowerGuardTheme { 
                PowerGuardAppUI(
                    openPromptInput = openDashboard,
                    onLlmTestClick = { launchLlmTestActivity() }
                ) 
            } 
        }

        // Request permissions
        requestRequiredPermissions()
        
        // Set up back press handling
        setupBackPressHandling()
    }
    
    /**
     * Launch the LLM test activity
     */
    private fun launchLlmTestActivity() {
        val intent = Intent(this, LLMTestActivity::class.java)
        startActivity(intent)
    }
    
    private fun setupBackPressHandling() {
        // Register a callback for back press events
        onBackPressedDispatcher.addCallback(this) {
            if (doubleBackToExitPressedOnce) {
                finish()
            } else {
                doubleBackToExitPressedOnce = true
                Toast.makeText(this@MainActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
                
                // Reset after 2 seconds
                android.os.Handler(mainLooper).postDelayed({
                    doubleBackToExitPressedOnce = false
                }, 2000)
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
        val appOpsManager = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startForegroundService(serviceIntent)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PowerGuardAppUI(openPromptInput: Boolean = false, onLlmTestClick: () -> Unit) {
    // Use remember functions to store the state of the NavController and SnackbarHostState
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    var showMenu by remember { mutableStateOf(false) }

    // Use rememberCoroutineScope to create a CoroutineScope that is scoped to the composition
    val coroutineScope = rememberCoroutineScope()
    
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
                title = { Text("PowerGuard") },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("LLM Test") },
                                onClick = {
                                    showMenu = false
                                    onLlmTestClick()
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
            openPromptInput = openPromptInput
        )
    }
}
