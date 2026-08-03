package com.trozovka.gpsserver.core.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

private fun configureOsmdroid(context: Context) {
    val config = Configuration.getInstance()
    config.userAgentValue = context.packageName
    val basePath = File(context.cacheDir, "osmdroid")
    config.osmdroidBasePath = basePath
    config.osmdroidTileCache = File(basePath, "tiles")
}

/** Lightweight background position map -- mainly to eyeball sanity before trusting the feed underway. */
@Composable
fun MiniMap(
    latitude: Double?,
    longitude: Double?,
    autoCenter: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView = remember {
        configureOsmdroid(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(14.0)
        }
    }
    val marker = remember { Marker(mapView) }

    DisposableEffect(Unit) {
        onDispose { mapView.onDetach() }
    }

    LaunchedEffect(latitude, longitude) {
        if (latitude != null && longitude != null) {
            val point = GeoPoint(latitude, longitude)
            marker.position = point
            if (marker !in mapView.overlays) {
                mapView.overlays.add(marker)
            }
            if (autoCenter) {
                mapView.controller.animateTo(point)
            }
            mapView.invalidate()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
