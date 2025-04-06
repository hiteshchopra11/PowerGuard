package com.hackathon.powerguard

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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hackathon.powerguard.services.PowerGuardService
import com.hackathon.powerguard.theme.PowerGuardTheme
import com.hackathon.powerguard.ui.AppNavHost
import com.hackathon.powerguard.ui.BottomNavBar
import com.hackathon.powerguard.workers.DataCollectionWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.values.all { it }
            if (!allGranted) {
                // Handle permission denial
                showAppSettings()
            } else {
                // Permissions granted, schedule data collection and start service
                scheduleDataCollection()
                startPowerGuardService()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PowerGuardTheme { PowerGuardAppUI() } }

        // Request permissions
        requestRequiredPermissions()
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Check for runtime permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.FOREGROUND_SERVICE)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = NotificationManagerCompat.from(this)
            if (!notificationManager.areNotificationsEnabled()) {
                // Notifications are not enabled, you can prompt the user to enable them
                // You can use an AlertDialog or a custom UI to prompt the user
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                startActivity(intent)
            }
        }

        // If we have permissions to request, request them
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // We already have the permissions, schedule data collection and start service
            scheduleDataCollection()
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
            // Permission already granted, proceed with data collection
            scheduleDataCollection()
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

    fun scheduleDataCollection() {
        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

        val dataCollectionRequest =
            PeriodicWorkRequestBuilder<DataCollectionWorker>(
                30,
                TimeUnit.MINUTES,
                5,
                TimeUnit.MINUTES // Flex period for battery optimization
            )
                .setConstraints(constraints)
                .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "data_collection_work",
                ExistingPeriodicWorkPolicy.UPDATE,
                dataCollectionRequest
            )
    }

    private fun startPowerGuardService() {
        // For system apps, we want to start the service at boot
        // But for demonstration, we'll start it when the app is launched
        Log.d("MainActivity", "Starting PowerGuardService")
        val serviceIntent = Intent(this, PowerGuardService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}

@Composable
fun PowerGuardAppUI() {
    // Use remember functions to store the state of the NavController and SnackbarHostState
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    // Use rememberCoroutineScope to create a CoroutineScope that is scoped to the composition
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = { BottomNavBar(navController = navController) }
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
            showSnackbar = { message ->
                coroutineScope.launch { snackbarHostState.showSnackbar(message) }
            }
        )
    }
}
