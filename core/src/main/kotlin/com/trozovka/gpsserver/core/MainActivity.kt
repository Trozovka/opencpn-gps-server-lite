package com.trozovka.gpsserver.core

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.trozovka.gpsserver.core.service.GpsServerService
import com.trozovka.gpsserver.core.settings.AppPreferences
import com.trozovka.gpsserver.core.ui.MainScreen
import com.trozovka.gpsserver.core.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    private var startAfterPermissions = false
    private lateinit var preferences: AppPreferences

    private val requestForegroundPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            requestBackgroundLocationIfNeeded()
        } else {
            startAfterPermissions = false
        }
    }

    private val requestBackgroundLocation = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        requestBatteryOptimizationExemptionIfNeeded()
    }

    private val batteryOptimizationSettings = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (startAfterPermissions) {
            startAfterPermissions = false
            startServer()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = AppPreferences(applicationContext)
        setContent {
            MaterialTheme {
                var showSettings by remember { mutableStateOf(false) }
                BackHandler(enabled = showSettings) { showSettings = false }
                if (showSettings) {
                    SettingsScreen(
                        preferences = preferences,
                        onBack = { showSettings = false },
                    )
                } else {
                    MainScreen(
                        preferences = preferences,
                        onStartRequested = ::beginStartFlow,
                        onStopRequested = ::stopServer,
                        onOpenSettings = { showSettings = true },
                    )
                }
            }
        }
    }


    private fun beginStartFlow() {
        startAfterPermissions = true
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
            needed += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }

        if (needed.isNotEmpty()) {
            requestForegroundPermissions.launch(needed.toTypedArray())
        } else {
            requestBackgroundLocationIfNeeded()
        }
    }

    private fun requestBackgroundLocationIfNeeded() {
        val backgroundGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        if (!backgroundGranted) {
            requestBackgroundLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            requestBatteryOptimizationExemptionIfNeeded()
        }
    }

    private fun requestBatteryOptimizationExemptionIfNeeded() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            batteryOptimizationSettings.launch(intent)
        } else {
            startAfterPermissions = false
            startServer()
        }
    }

    private fun startServer() {
        val intent = Intent(this, GpsServerService::class.java).apply {
            putExtra(GpsServerService.EXTRA_PORT, preferences.port)
            preferences.boundAddress?.let { putExtra(GpsServerService.EXTRA_BIND_ADDRESS, it) }
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopServer() {
        val intent = Intent(this, GpsServerService::class.java).apply {
            action = GpsServerService.ACTION_STOP
        }
        startService(intent)
    }
}
