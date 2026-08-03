package com.trozovka.gpsserver.core.nmea

/** A single position fix, tier/Android-agnostic so NmeaFormatter stays a pure, unit-testable function set. */
data class GpsFix(
    val timestampMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val satellitesUsed: Int?,
)
