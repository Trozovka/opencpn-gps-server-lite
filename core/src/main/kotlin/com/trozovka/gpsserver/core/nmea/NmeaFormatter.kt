package com.trozovka.gpsserver.core.nmea

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Pure NMEA 0183 sentence generation from a [GpsFix] -- no Android dependency, so this is
 * unit-testable on the plain JVM. Fields Android can't supply (HDOP/PDOP/VDOP, satellite PRNs)
 * are left empty rather than fabricated, per NMEA's own convention for unknown fields.
 */
object NmeaFormatter {

    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss.SS").withZone(ZoneOffset.UTC)
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("ddMMyy").withZone(ZoneOffset.UTC)
    private const val METERS_PER_SECOND_TO_KNOTS = 1.9438444924406
    private const val METERS_PER_SECOND_TO_KMH = 3.6

    fun gga(fix: GpsFix): String {
        val instant = Instant.ofEpochMilli(fix.timestampMillis)
        val (latField, latHemi) = formatLatitude(fix.latitude)
        val (lonField, lonHemi) = formatLongitude(fix.longitude)
        val satellites = (fix.satellitesUsed ?: 0).coerceIn(0, 99)
        val altitude = fix.altitudeMeters?.let { "%.1f".format(it) } ?: ""

        val body = "GPGGA,${TIME_FORMAT.format(instant)},$latField,$latHemi,$lonField,$lonHemi," +
            "1,${"%02d".format(satellites)},,$altitude,M,,M,,"
        return withChecksum(body)
    }

    fun rmc(fix: GpsFix): String {
        val instant = Instant.ofEpochMilli(fix.timestampMillis)
        val (latField, latHemi) = formatLatitude(fix.latitude)
        val (lonField, lonHemi) = formatLongitude(fix.longitude)
        val speedKnots = fix.speedMetersPerSecond?.let { "%.1f".format(it * METERS_PER_SECOND_TO_KNOTS) } ?: ""
        val course = fix.bearingDegrees?.let { "%.1f".format(it) } ?: ""

        val body = "GPRMC,${TIME_FORMAT.format(instant)},A,$latField,$latHemi,$lonField,$lonHemi," +
            "$speedKnots,$course,${DATE_FORMAT.format(instant)},,,A"
        return withChecksum(body)
    }

    fun vtg(fix: GpsFix): String {
        val course = fix.bearingDegrees?.let { "%.1f".format(it) } ?: ""
        val speedKnots = fix.speedMetersPerSecond?.let { "%.1f".format(it * METERS_PER_SECOND_TO_KNOTS) } ?: ""
        val speedKmh = fix.speedMetersPerSecond?.let { "%.1f".format(it * METERS_PER_SECOND_TO_KMH) } ?: ""

        val body = "GPVTG,$course,T,,M,$speedKnots,N,$speedKmh,K,A"
        return withChecksum(body)
    }

    fun gsa(fix: GpsFix): String {
        val fixType = if (fix.altitudeMeters != null) 3 else 2
        val body = "GPGSA,A,$fixType,,,,,,,,,,,,,,,"
        return withChecksum(body)
    }

    private fun formatLatitude(latitude: Double): Pair<String, Char> {
        val hemisphere = if (latitude >= 0) 'N' else 'S'
        val absValue = abs(latitude)
        val degrees = absValue.toInt()
        val minutes = (absValue - degrees) * 60.0
        return "%02d%07.4f".format(degrees, minutes) to hemisphere
    }

    private fun formatLongitude(longitude: Double): Pair<String, Char> {
        val hemisphere = if (longitude >= 0) 'E' else 'W'
        val absValue = abs(longitude)
        val degrees = absValue.toInt()
        val minutes = (absValue - degrees) * 60.0
        return "%03d%07.4f".format(degrees, minutes) to hemisphere
    }

    /** XOR of every byte in the sentence body (between '$' and '*'). Internal so tests can
     * verify it directly against hand-computed values, independent of sentence-building. */
    internal fun checksum(body: String): Int {
        var result = 0
        for (char in body) result = result xor char.code
        return result
    }

    private fun withChecksum(body: String): String {
        return "$" + body + "*" + "%02X".format(checksum(body)) + "\r\n"
    }
}
