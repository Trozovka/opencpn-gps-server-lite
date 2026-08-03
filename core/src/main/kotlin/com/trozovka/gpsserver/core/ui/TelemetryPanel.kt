package com.trozovka.gpsserver.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.trozovka.gpsserver.core.nmea.GpsFix

data class TelemetrySnapshot(
    val fix: GpsFix?,
    val latencyMillis: Long?,
    val sentenceCount: Long,
    val byteCount: Long,
    val runtimeLeftText: String,
    val metricUnits: Boolean,
)

private const val STALE_THRESHOLD_MILLIS = 5000L

@Composable
fun TelemetryPanel(
    snapshot: TelemetrySnapshot,
    debugLogExpanded: Boolean,
    onToggleDebugLog: () -> Unit,
    debugLog: List<String>,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            val fix = snapshot.fix
            val nowMillis = System.currentTimeMillis()
            val ageSeconds = fix?.let { (nowMillis - it.timestampMillis) / 1000.0 }
            val isStale = ageSeconds == null || ageSeconds > STALE_THRESHOLD_MILLIS / 1000.0

            TelemetryRow(
                "GPS Time",
                if (fix != null) "%.0fs ago".format(ageSeconds) else "--",
                valueColor = if (isStale) MaterialTheme.colorScheme.error else null,
            )
            TelemetryRow("Runtime Left", snapshot.runtimeLeftText)
            TelemetryRow("Lat/Lon", fix?.let { "%.5f, %.5f".format(it.latitude, it.longitude) } ?: "--")
            TelemetryRow("Speed", formatSpeed(fix?.speedMetersPerSecond, snapshot.metricUnits))
            TelemetryRow("Altitude", formatAltitude(fix?.altitudeMeters, snapshot.metricUnits))
            TelemetryRow("Heading", fix?.bearingDegrees?.let { "%.0f°".format(it) } ?: "--")
            TelemetryRow("Sent", "${snapshot.sentenceCount} sentences / ${snapshot.byteCount} bytes")
            TelemetryRow(
                "Latency",
                snapshot.latencyMillis?.let { "${it}ms" } ?: "--",
                valueColor = if ((snapshot.latencyMillis ?: 0) > 500) MaterialTheme.colorScheme.error else null,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Debug log", style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = onToggleDebugLog) {
                    Icon(
                        if (debugLogExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (debugLogExpanded) "Collapse debug log" else "Expand debug log",
                    )
                }
            }

            if (debugLogExpanded) {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    items(debugLog) { line ->
                        Text(line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String, valueColor: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatSpeed(metersPerSecond: Float?, metric: Boolean): String {
    if (metersPerSecond == null) return "--"
    return if (metric) {
        "%.1f km/h".format(metersPerSecond * 3.6)
    } else {
        "%.1f kn".format(metersPerSecond * 1.9438444924406)
    }
}

private fun formatAltitude(meters: Double?, metric: Boolean): String {
    if (meters == null) return "--"
    return if (metric) {
        "%.0f m".format(meters)
    } else {
        "%.0f ft".format(meters * 3.280839895)
    }
}
