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
import com.trozovka.gpsserver.core.entitlement.EntitlementHost
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

data class ConnectedClient(val address: String, val port: Int)
data class SentStats(val sentenceCount: Long, val byteCount: Long)

/**
 * Foreground service owning the TCP server socket, the wake lock that keeps it running with
 * the screen off, and the location callback that supplies each second's NMEA fix. Live state is
 * exposed via companion StateFlows rather than a bound-service API, since MainActivity only ever
 * starts this service and doesn't need a two-way binding for a single-consumer telemetry panel.
 */
class GpsServerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var locationSource: LocationSource

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        acquireWakeLock()
        locationSource = LocationSource(applicationContext)
        locationSource.start()
        _isRunning.value = true
        _startTimeMillis.value = System.currentTimeMillis()
        _capExpired.value = false
        scope.launch {
            locationSource.fixes.collect { _latestFix.value = it }
        }
        scope.launch {
            val maxRuntime = EntitlementHost.current().maxRuntimeMillis()
            if (maxRuntime != null) {
                delay(maxRuntime)
                _capExpired.value = true
                stopSelf()
            }
        }
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
                    while (isActive) {
                        val client = server.accept()
                        _connectedClient.value = ConnectedClient(
                            client.inetAddress.hostAddress ?: "?",
                            client.port,
                        )
                        handleClient(client)
                        _connectedClient.value = null
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
        val bytes = sentences.toByteArray()
        out.write(bytes)
        out.flush()

        val stats = _sentStats.value
        _sentStats.value = SentStats(stats.sentenceCount + 4, stats.byteCount + bytes.size)

        val newLines = sentences.split("\r\n").filter { it.isNotBlank() }
        _debugLog.value = (_debugLog.value + newLines).takeLast(MAX_DEBUG_LOG_LINES)

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
            .setSmallIcon(com.trozovka.gpsserver.core.R.drawable.ic_notification)
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
        _isRunning.value = false
        _startTimeMillis.value = null
        _latestFix.value = null
        _latencyMillis.value = null
        _connectedClient.value = null
        _sentStats.value = SentStats(0, 0)
        _debugLog.value = emptyList()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "GpsServerService"
        private const val CHANNEL_ID = "gps_server_channel"
        private const val NOTIFICATION_ID = 1
        private const val LATENCY_WARNING_THRESHOLD_MILLIS = 500L
        private const val MAX_DEBUG_LOG_LINES = 200
        const val DEFAULT_PORT = 10110
        const val EXTRA_PORT = "extra_port"
        const val EXTRA_BIND_ADDRESS = "extra_bind_address"
        const val ACTION_STOP = "com.trozovka.gpsserver.core.action.STOP"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _capExpired = MutableStateFlow(false)

        /** True once a runtime-capped tier's session hits its limit and auto-stops. */
        val capExpired: StateFlow<Boolean> = _capExpired.asStateFlow()

        fun acknowledgeCapExpired() {
            _capExpired.value = false
        }

        private val _startTimeMillis = MutableStateFlow<Long?>(null)
        val startTimeMillis: StateFlow<Long?> = _startTimeMillis.asStateFlow()

        private val _debugLog = MutableStateFlow<List<String>>(emptyList())
        val debugLog: StateFlow<List<String>> = _debugLog.asStateFlow()

        private val _latestFix = MutableStateFlow<GpsFix?>(null)
        val latestFix: StateFlow<GpsFix?> = _latestFix.asStateFlow()

        private val _latencyMillis = MutableStateFlow<Long?>(null)
        val latencyMillis: StateFlow<Long?> = _latencyMillis.asStateFlow()

        private val _connectedClient = MutableStateFlow<ConnectedClient?>(null)
        val connectedClient: StateFlow<ConnectedClient?> = _connectedClient.asStateFlow()

        private val _sentStats = MutableStateFlow(SentStats(0, 0))
        val sentStats: StateFlow<SentStats> = _sentStats.asStateFlow()
    }
}
