package com.trozovka.gpsserver.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.trozovka.gpsserver.core.nmea.GpsFix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wraps FusedLocationProviderClient (preferred) with a LocationManager/GPS_PROVIDER fallback if
 * Play services is unavailable or the fused request fails, per the project spec. Callers must
 * have already obtained ACCESS_FINE_LOCATION (and ACCESS_BACKGROUND_LOCATION for screen-off use)
 * before calling [start] -- that's enforced by MainActivity's permission flow, not here.
 */
@SuppressLint("MissingPermission")
class LocationSource(private val context: Context) {

    private val _fixes = MutableStateFlow<GpsFix?>(null)
    val fixes: StateFlow<GpsFix?> = _fixes.asStateFlow()

    @Volatile
    private var satellitesUsed: Int? = null
    private var usingFallback = false

    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(context) }
    private val locationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::onNewLocation)
        }
    }

    private val fallbackListener = LocationListener { location -> onNewLocation(location) }

    private val gnssStatusCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            for (i in 0 until status.satelliteCount) {
                if (status.usedInFix(i)) used++
            }
            satellitesUsed = used
        }
    }

    fun start() {
        registerGnssStatusCallback()

        val playServicesAvailable = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

        if (!playServicesAvailable) {
            Log.w(TAG, "Play services unavailable, using LocationManager fallback")
            startFallback()
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, TARGET_INTERVAL_MILLIS)
            .setMinUpdateIntervalMillis(MIN_INTERVAL_MILLIS)
            .setMaxUpdateDelayMillis(0L)
            .build()

        fusedClient.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
            .addOnFailureListener { e ->
                Log.w(TAG, "Fused location request failed, using LocationManager fallback", e)
                startFallback()
            }
    }

    private fun startFallback() {
        usingFallback = true
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                TARGET_INTERVAL_MILLIS,
                0f,
                fallbackListener,
                Looper.getMainLooper(),
            )
        } catch (e: Exception) {
            Log.e(TAG, "LocationManager fallback also failed", e)
        }
    }

    private fun registerGnssStatusCallback() {
        try {
            locationManager.registerGnssStatusCallback(gnssStatusCallback, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            Log.w(TAG, "Could not register GNSS status callback", e)
        }
    }

    private fun onNewLocation(location: Location) {
        _fixes.value = GpsFix(
            timestampMillis = location.time,
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeMeters = if (location.hasAltitude()) location.altitude else null,
            speedMetersPerSecond = if (location.hasSpeed()) location.speed else null,
            bearingDegrees = if (location.hasBearing()) location.bearing else null,
            satellitesUsed = satellitesUsed,
        )
    }

    fun stop() {
        fusedClient.removeLocationUpdates(fusedCallback)
        if (usingFallback) {
            locationManager.removeUpdates(fallbackListener)
        }
        locationManager.unregisterGnssStatusCallback(gnssStatusCallback)
    }

    companion object {
        private const val TAG = "LocationSource"
        private const val TARGET_INTERVAL_MILLIS = 1000L
        private const val MIN_INTERVAL_MILLIS = 500L
    }
}
