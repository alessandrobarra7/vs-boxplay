package com.boxplay.multitrack.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MultitrackTimeFormatTest {

    @Test
    fun zeroFormatsAsMinutesSeconds() {
        assertEquals("00:00.000", MultitrackTimeFormat.format(0L))
    }

    @Test
    fun subSecondFormatsMilliseconds() {
        assertEquals("00:00.350", MultitrackTimeFormat.format(350_000L))
    }

    @Test
    fun minutesAndSecondsFormatWithoutHours() {
        // 4 min 32.518s
        assertEquals("04:32.518", MultitrackTimeFormat.format(272_518_000L))
    }

    @Test
    fun oneHourSwitchesToHourFormat() {
        val oneHourUs = 3_600_000_000L
        assertEquals("01:00:00.000", MultitrackTimeFormat.format(oneHourUs))
    }

    @Test
    fun negativeDurationClampsToZero() {
        assertEquals("00:00.000", MultitrackTimeFormat.format(-500L))
    }
}
