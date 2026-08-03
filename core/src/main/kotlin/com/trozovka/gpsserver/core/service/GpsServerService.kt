package com.trozovka.gpsserver.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.trozovka.gpsserver.core.location.LocationSource
import com.trozovka.gpsserver.core.nmea.GpsFix
import com.trozovka.gpsserver.core.nmea.NmeaFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Foreground service owning the TCP server socket, the wake lock that keeps it running with
 * the screen off, and the location callback that supplies each second's NMEA fix.
 */
class GpsServerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var boundAddress: String? = null
    private lateinit var locationSource: LocationSource

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        acquireWakeLock()
        locationSource = LocationSource(applicationContext)
        locationSource.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val port = intent?.getIntExtra(EXTRA_PORT, DEFAULT_PORT) ?: DEFAULT_PORT
        val bindAddress = intent?.getStringExtra(EXTRA_BIND_ADDRESS)
        if (serverJob == null) {
            startServer(port, bindAddress)
        }
        return START_STICKY
    }

    private fun startServer(port: Int, bindAddress: String?) {
        serverJob = scope.launch {
            try {
                // Explicit IPv4 wildcard: an address-less InetSocketAddress(port) binds the
                // IPv6 wildcard on this hardware, which didn't accept plain IPv4 connections
                // from OpenCPN/nc in testing.
                val socketAddress = InetSocketAddress(bindAddress ?: "0.0.0.0", port)
                ServerSocket().apply {
                    reuseAddress = true
                    bind(socketAddress)
                }.use { server ->
                    boundAddress = "${server.inetAddress.hostAddress}:$port"
                    while (isActive) {
                        val client = server.accept()
                        handleClient(client)
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "Server loop ended", e)
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { client ->
            client.tcpNoDelay = true
            val out = client.getOutputStream()
            try {
                while (scope.isActive) {
                    locationSource.fixes.value?.let { writeFix(out, it) }
                    delay(1000)
                }
            } catch (e: IOException) {
                Log.i(TAG, "Client disconnected: ${e.message}")
            }
        }
    }

    private fun writeFix(out: OutputStream, fix: GpsFix) {
        val sentences = NmeaFormatter.gga(fix) +
            NmeaFormatter.rmc(fix) +
            NmeaFormatter.vtg(fix) +
            NmeaFormatter.gsa(fix)
        out.write(sentences.toByteArray())
        out.flush()

        val latencyMillis = System.currentTimeMillis() - fix.timestampMillis
        _latencyMillis.value = latencyMillis
        if (latencyMillis > LATENCY_WARNING_THRESHOLD_MILLIS) {
            Log.w(TAG, "Fix-to-wire latency high: ${latencyMillis}ms")
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:GpsServerWakeLock").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GPS Server",
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OpenCPN GPS Server running")
            .setContentText("Streaming NMEA to connected clients")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        serverJob?.cancel()
        scope.cancel()
        locationSource.stop()
        releaseWakeLock()
        _latencyMillis.value = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "GpsServerService"
        private const val CHANNEL_ID = "gps_server_channel"
        private const val NOTIFICATION_ID = 1
        private const val LATENCY_WARNING_THRESHOLD_MILLIS = 500L
        const val DEFAULT_PORT = 10110
        const val EXTRA_PORT = "extra_port"
        const val EXTRA_BIND_ADDRESS = "extra_bind_address"
        const val ACTION_STOP = "com.trozovka.gpsserver.core.action.STOP"

        private val _latencyMillis = MutableStateFlow<Long?>(null)

        /** Most recent fix-to-wire latency, for the telemetry panel (Milestone 4). */
        val latencyMillis: StateFlow<Long?> = _latencyMillis.asStateFlow()
    }
}
