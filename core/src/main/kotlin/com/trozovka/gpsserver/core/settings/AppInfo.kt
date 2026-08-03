package com.trozovka.gpsserver.core.settings

import android.content.Context

/** Reads the running app's own version dynamically, so it can never go stale in the UI. */
object AppInfo {
    fun versionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
}
