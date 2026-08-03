package com.trozovka.gpsserver.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trozovka.gpsserver.core.entitlement.EntitlementHost
import com.trozovka.gpsserver.core.service.GpsServerService

@Composable
fun MainScreen(
    onStartRequested: () -> Unit = {},
    onStopRequested: () -> Unit = {},
) {
    var running by remember { mutableStateOf(false) }
    val tierName = EntitlementHost.current().tierName

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("OpenCPN GPS Server", style = MaterialTheme.typography.headlineSmall)
            Text(tierName, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = {
                running = !running
                if (running) onStartRequested() else onStopRequested()
            }) {
                Text(if (running) "Stop" else "Start")
            }
            Text(if (running) "Running on port ${GpsServerService.DEFAULT_PORT}" else "Stopped")
        }
    }
}
