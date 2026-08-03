package com.trozovka.gpsserver.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun IpPicker(
    addresses: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text("Server IP (this device)", style = MaterialTheme.typography.labelMedium)
        if (addresses.isEmpty()) {
            Text("No network interface found", style = MaterialTheme.typography.bodySmall)
        }
        addresses.forEach { address ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(address) }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(address)
                if (address == selected) {
                    Icon(Icons.Filled.Check, contentDescription = "Selected")
                }
            }
        }
    }
}
