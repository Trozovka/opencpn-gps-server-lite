package com.trozovka.gpsserver.core.entitlement

import androidx.compose.runtime.Composable

/**
 * What this build/session is allowed to do. The free tier's implementation lives here;
 * the Pro tier's Gumroad-backed implementation lives only in the private pro repo and is
 * installed into [EntitlementHost] by that app's own Application class.
 */
interface EntitlementManager {
    val tierName: String

    /** Null means unlimited runtime. */
    suspend fun maxRuntimeMillis(): Long?

    /**
     * Tier-specific content appended to the shared Settings screen -- e.g. Pro's license/
     * account section. Default is empty so :core's Settings screen needs no per-tier forking.
     */
    @Composable
    fun SettingsExtras() {}
}

class FreeEntitlementManager : EntitlementManager {
    override val tierName = "Free"

    override suspend fun maxRuntimeMillis(): Long = FREE_RUNTIME_CAP_MILLIS

    companion object {
        const val FREE_RUNTIME_CAP_MILLIS = 60_000L
    }
}
