package com.trozovka.gpsserver.core.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trozovka.gpsserver.core.entitlement.EntitlementHost
import com.trozovka.gpsserver.core.settings.AppInfo
import com.trozovka.gpsserver.core.settings.AppPreferences

private const val MIN_PORT = 1024
private const val MAX_PORT = 65535
private const val GITHUB_URL = "https://github.com/Trozovka"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var portText by remember { mutableStateOf(preferences.port.toString()) }
    var portError by remember { mutableStateOf<String?>(null) }
    var metricUnits by remember { mutableStateOf(preferences.metricUnits) }

    val tierName = EntitlementHost.current().tierName
    val maxRuntimeMillis by produceState<Long?>(initialValue = null) {
        value = EntitlementHost.current().maxRuntimeMillis()
    }
    val runtimeDescription = when (val runtime = maxRuntimeMillis) {
        null -> "$tierName: Unlimited (until stopped)"
        else -> "$tierName: fixed at ${runtime / 1000}s per session, not editable"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("Runtime")
            Text(runtimeDescription, style = MaterialTheme.typography.bodySmall)

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            OutlinedTextField(
                value = portText,
                onValueChange = { value ->
                    portText = value
                    val parsed = value.toIntOrNull()
                    portError = when {
                        parsed == null -> "Enter a number"
                        parsed !in MIN_PORT..MAX_PORT -> "Port must be $MIN_PORT-$MAX_PORT"
                        else -> null
                    }
                    if (parsed != null && portError == null) {
                        preferences.port = parsed
                    }
                },
                label = { Text("Server port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = portError != null,
                supportingText = { portError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Metric units (display only)")
                Checkbox(
                    checked = metricUnits,
                    onCheckedChange = {
                        metricUnits = it
                        preferences.metricUnits = it
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text("About", style = MaterialTheme.typography.titleMedium)
            Text(
                "OpenCPN GPS Server ${tierName} v${AppInfo.versionName(context)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text("Developed by Trozovka", style = MaterialTheme.typography.bodyMedium)
            Text(
                GITHUB_URL,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
                },
            )
            Text(
                "This app is a backup/testing GNSS source, not a certified primary navigation " +
                    "input. Don't rely on it as your only means of position-fixing underway.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
