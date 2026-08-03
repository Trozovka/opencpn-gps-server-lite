package com.trozovka.gpsserver.core.nmea

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class NmeaFormatterTest {

    @Test
    fun `checksum matches hand-computed XOR for simple strings`() {
        assertEquals(0x41, NmeaFormatter.checksum("A"))
        assertEquals(0x03, NmeaFormatter.checksum("AB"))
    }

    @Test
    fun `checksum matches the canonical NMEA GGA textbook example`() {
        val body = "GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,"
        assertEquals(0x47, NmeaFormatter.checksum(body))
    }

    private fun sampleFix(): GpsFix {
        // 1996-03-23 12:35:19 UTC, matching the canonical GGA/RMC textbook examples'
        // lat/lon/time so the degrees-minutes formatting can be checked against them.
        val instant = Instant.parse("1994-03-23T12:35:19Z")
        return GpsFix(
            timestampMillis = instant.toEpochMilli(),
            latitude = 48.0 + 7.038 / 60.0,
            longitude = 11.0 + 31.000 / 60.0,
            altitudeMeters = 545.4,
            speedMetersPerSecond = 5.0f,
            bearingDegrees = 90.0f,
            satellitesUsed = 8,
        )
    }

    @Test
    fun `gga formats lat-lon-time matching the canonical example and carries a self-consistent checksum`() {
        val sentence = NmeaFormatter.gga(sampleFix())

        assertTrue(sentence.startsWith("\$GPGGA,123519.00,4807.0380,N,01131.0000,E,1,08,"))
        assertTrue(sentence.endsWith("\r\n"))
        assertSelfConsistentChecksum(sentence)
    }

    @Test
    fun `rmc uses UTC date-time and a self-consistent checksum`() {
        val sentence = NmeaFormatter.rmc(sampleFix())

        assertTrue(sentence.startsWith("\$GPRMC,123519.00,A,4807.0380,N,01131.0000,E,"))
        assertTrue(sentence.contains(",230394,")) // ddMMyy for 1994-03-23
        assertSelfConsistentChecksum(sentence)
    }

    @Test
    fun `vtg and gsa are well-formed with self-consistent checksums`() {
        assertSelfConsistentChecksum(NmeaFormatter.vtg(sampleFix()))
        assertSelfConsistentChecksum(NmeaFormatter.gsa(sampleFix()))
    }

    @Test
    fun `southern and western hemispheres are marked correctly`() {
        val southWestFix = sampleFix().copy(latitude = -33.5, longitude = -70.25)
        val gga = NmeaFormatter.gga(southWestFix)

        assertTrue(gga.contains(",S,"))
        assertTrue(gga.contains(",W,"))
    }

    @Test
    fun `missing optional fields are left empty, never fabricated`() {
        val fixWithoutExtras = sampleFix().copy(
            altitudeMeters = null,
            speedMetersPerSecond = null,
            bearingDegrees = null,
            satellitesUsed = null,
        )

        val gga = NmeaFormatter.gga(fixWithoutExtras)
        val rmc = NmeaFormatter.rmc(fixWithoutExtras)

        assertTrue(gga.contains(",00,")) // unknown satellite count is zeroed, per spec, not guessed
        assertTrue(rmc.contains(",,,")) // empty speed/course fields rather than fabricated values
        assertSelfConsistentChecksum(gga)
        assertSelfConsistentChecksum(rmc)
    }

    private fun assertSelfConsistentChecksum(sentence: String) {
        assertTrue(sentence.startsWith("$"))
        val body = sentence.substringAfter("$").substringBefore("*")
        val claimedChecksum = sentence.substringAfter("*").trim().toInt(16)
        assertEquals(NmeaFormatter.checksum(body), claimedChecksum)
    }
}
