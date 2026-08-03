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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Foreground service owning the TCP server socket, the wake lock that keeps it running with
 * the screen off, and (from Milestone 3 onward) the location callback. For Milestone 2 the
 * NMEA payload is a fixed sample sentence -- NmeaFormatter replaces it once real fixes are
 * wired in.
 */
class GpsServerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var boundAddress: String? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        acquireWakeLock()
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
                    out.write(HARDCODED_SENTENCE_BYTES)
                    out.flush()
                    delay(1000)
                }
            } catch (e: IOException) {
                Log.i(TAG, "Client disconnected: ${e.message}")
            }
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
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "GpsServerService"
        private const val CHANNEL_ID = "gps_server_channel"
        private const val NOTIFICATION_ID = 1
        const val DEFAULT_PORT = 10110
        const val EXTRA_PORT = "extra_port"
        const val EXTRA_BIND_ADDRESS = "extra_bind_address"
        const val ACTION_STOP = "com.trozovka.gpsserver.core.action.STOP"

        // Textbook NMEA GGA sample sentence with a verified checksum -- placeholder until
        // Milestone 3 replaces this with NmeaFormatter output from real fixes.
        private val HARDCODED_SENTENCE_BYTES =
            "\$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47\r\n".toByteArray()
    }
}
