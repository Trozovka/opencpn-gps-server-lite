package com.trozovka.gpsserver.core.entitlement

/**
 * Service-locator swapped in by each :app's Application class at startup, so :core's UI
 * and service never link against Gumroad or know which tier they're running under.
 */
object EntitlementHost {
    private var manager: EntitlementManager = FreeEntitlementManager()

    fun install(entitlementManager: EntitlementManager) {
        manager = entitlementManager
    }

    fun current(): EntitlementManager = manager
}
