package com.noorsalah.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerTimesRemoteRepositoryTest {
    @Test
    fun parsesPrayerTimesInTwentyFourHourFormat() {
        assertEquals(335, PrayerTimesRemoteRepository.parseTime("05:35"))
        assertEquals(1068, PrayerTimesRemoteRepository.parseTime("17:48:00"))
        assertEquals(0, PrayerTimesRemoteRepository.parseTime("00:00"))
        assertEquals(1439, PrayerTimesRemoteRepository.parseTime("23:59"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedPrayerTime() {
        PrayerTimesRemoteRepository.parseTime("25:70")
    }

    @Test
    fun recognizesJordanCoordinatesWithoutIncludingJerusalem() {
        assertTrue(PrayerTimesRemoteRepository.isJordan(31.9539, 35.9106))
        assertTrue(PrayerTimesRemoteRepository.isJordan(29.5267, 35.0078))
        assertFalse(PrayerTimesRemoteRepository.isJordan(31.7683, 35.2137))
        assertFalse(PrayerTimesRemoteRepository.isJordan(59.91, 10.75))
    }
}
