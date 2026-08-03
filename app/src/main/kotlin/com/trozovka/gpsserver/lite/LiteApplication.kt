package com.trozovka.gpsserver.lite

import android.app.Application
import com.trozovka.gpsserver.core.entitlement.EntitlementHost
import com.trozovka.gpsserver.core.entitlement.FreeEntitlementManager

class LiteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        EntitlementHost.install(FreeEntitlementManager())
    }
}
