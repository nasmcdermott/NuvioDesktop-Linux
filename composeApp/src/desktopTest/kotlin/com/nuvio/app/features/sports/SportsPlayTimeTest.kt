package com.nuvio.app.features.sports

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class SportsPlayTimeTest {
    private val instant = java.time.Instant.parse("2026-11-19T00:00:00Z").toEpochMilli()

    @Test
    fun `24-hour format includes timezone abbreviation`() {
        assertEquals("19:00 EST", formatSportsPlayTime(instant, twelveHour = false, zone = ZoneId.of("America/New_York")))
    }

    @Test
    fun `12-hour format includes meridiem and timezone abbreviation`() {
        assertEquals("7:00 PM EST", formatSportsPlayTime(instant, twelveHour = true, zone = ZoneId.of("America/New_York")))
    }
}
