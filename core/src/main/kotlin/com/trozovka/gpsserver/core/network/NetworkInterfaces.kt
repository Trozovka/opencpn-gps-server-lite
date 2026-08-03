package com.trozovka.gpsserver.core.network

import java.net.Inet4Address
import java.net.NetworkInterface

/** Every non-loopback IPv4 address currently bound to this device (Wi-Fi, hotspot, USB tether). */
object NetworkInterfaces {

    fun listIPv4Addresses(): List<String> {
        return try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { iface -> iface.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .map { it.hostAddress }
                .filterNotNull()
                .distinct()
                .toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
