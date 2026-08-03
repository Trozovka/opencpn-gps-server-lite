package com.trozovka.gpsserver.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.trozovka.gpsserver.core.entitlement.EntitlementHost
import com.trozovka.gpsserver.core.network.NetworkInterfaces
import com.trozovka.gpsserver.core.service.GpsServerService
import com.trozovka.gpsserver.core.settings.AppPreferences
import kotlinx.coroutines.delay

private val RunningColor = Color(0xFF16A34A)
private val StoppedColor = Color(0xFFF87171)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    preferences: AppPreferences,
    onStartRequested: () -> Unit = {},
    onStopRequested: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val tierName = EntitlementHost.current().tierName
    val isRunning by GpsServerService.isRunning.collectAsState()
    val latestFix by GpsServerService.latestFix.collectAsState()
    val latencyMillis by GpsServerService.latencyMillis.collectAsState()
    val connectedClient by GpsServerService.connectedClient.collectAsState()
    val sentStats by GpsServerService.sentStats.collectAsState()
    val startTimeMillis by GpsServerService.startTimeMillis.collectAsState()
    val debugLog by GpsServerService.debugLog.collectAsState()
    val capExpired by GpsServerService.capExpired.collectAsState()

    var debugLogExpanded by remember { mutableStateOf(false) }
    var selectionWarning by remember { mutableStateOf<String?>(null) }
    var selectedAddress by remember { mutableStateOf(preferences.boundAddress) }
    val addresses = remember { NetworkInterfaces.listIPv4Addresses() }

    LaunchedEffect(addresses) {
        if (selectedAddress == null && addresses.size == 1) {
            selectedAddress = addresses.first()
            preferences.boundAddress = addresses.first()
        }
    }

    // Ticks once a second purely to force recomposition of time-derived text (staleness, countdown).
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }

    val maxRuntimeMillis by produceState<Long?>(initialValue = null) {
        value = EntitlementHost.current().maxRuntimeMillis()
    }

    val runtimeLeftText = remember(tick, isRunning, startTimeMillis, maxRuntimeMillis) {
        val currentStartTime = startTimeMillis
        val currentMaxRuntime = maxRuntimeMillis
        when {
            !isRunning -> "--"
            currentMaxRuntime == null -> "Unlimited"
            currentStartTime == null -> "--"
            else -> {
                val elapsed = System.currentTimeMillis() - currentStartTime
                val remainingSeconds = ((currentMaxRuntime - elapsed) / 1000).coerceAtLeast(0)
                "%d:%02d".format(remainingSeconds / 60, remainingSeconds % 60)
            }
        }
    }

    val gpsStatus = when {
        !isRunning -> "-"
        latestFix == null -> "acquiring"
        else -> "locked"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OpenCPN GPS Server") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(tierName, style = MaterialTheme.typography.labelMedium)
            Text(
                "Backup/testing GNSS source -- not a certified primary navigation input",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text("GPS: $gpsStatus")
            Text("Network: ${if (connectedClient != null) "Connected" else "Disconnected"}")

            Button(
                onClick = {
                    if (isRunning) {
                        onStopRequested()
                    } else if (selectedAddress == null && addresses.size > 1) {
                        selectionWarning = "Select a network interface above before starting"
                    } else {
                        selectionWarning = null
                        onStartRequested()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) RunningColor else StoppedColor,
                ),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                Text(if (isRunning) "Stop" else "Start")
            }
            selectionWarning?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            IpPicker(
                addresses = addresses,
                selected = selectedAddress,
                onSelect = { address ->
                    selectedAddress = address
                    preferences.boundAddress = address
                    selectionWarning = null
                },
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Server port: ${preferences.port}")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Client: ${connectedClient?.address ?: "--"}")
                Text("Port: ${connectedClient?.port ?: "--"}")
            }

            TelemetryPanel(
                snapshot = TelemetrySnapshot(
                    fix = latestFix,
                    latencyMillis = latencyMillis,
                    sentenceCount = sentStats.sentenceCount,
                    byteCount = sentStats.byteCount,
                    runtimeLeftText = runtimeLeftText,
                    metricUnits = preferences.metricUnits,
                ),
                debugLogExpanded = debugLogExpanded,
                onToggleDebugLog = { debugLogExpanded = !debugLogExpanded },
                debugLog = debugLog,
            )
        }
    }

    if (capExpired) {
        AlertDialog(
            onDismissRequest = { GpsServerService.acknowledgeCapExpired() },
            title = { Text("Session limit reached") },
            text = { Text("$tierName sessions are capped at 1 minute. Upgrade to Pro for unlimited runtime.") },
            confirmButton = {
                Button(onClick = { GpsServerService.acknowledgeCapExpired() }) {
                    Text("OK")
                }
            },
        )
    }
}
