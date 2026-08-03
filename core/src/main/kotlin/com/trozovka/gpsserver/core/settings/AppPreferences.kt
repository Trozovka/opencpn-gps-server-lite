package com.trozovka.gpsserver.core.settings

import android.content.Context
import com.trozovka.gpsserver.core.service.GpsServerService

/** Thin SharedPreferences wrapper for the handful of user-facing settings this app has. */
class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var port: Int
        get() = prefs.getInt(KEY_PORT, GpsServerService.DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_PORT, value).apply()

    var boundAddress: String?
        get() = prefs.getString(KEY_BOUND_ADDRESS, null)
        set(value) = prefs.edit().putString(KEY_BOUND_ADDRESS, value).apply()

    var autoCenterMap: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CENTER, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CENTER, value).apply()

    var metricUnits: Boolean
        get() = prefs.getBoolean(KEY_METRIC_UNITS, false)
        set(value) = prefs.edit().putBoolean(KEY_METRIC_UNITS, value).apply()

    companion object {
        private const val PREFS_NAME = "gps_server_prefs"
        private const val KEY_PORT = "port"
        private const val KEY_BOUND_ADDRESS = "bound_address"
        private const val KEY_AUTO_CENTER = "auto_center_map"
        private const val KEY_METRIC_UNITS = "metric_units"
    }
}
