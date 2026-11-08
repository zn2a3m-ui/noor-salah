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
    fun acceptsCompleteChronologicalPrayerDayForRequestedMonth() {
        assertTrue(
            PrayerTimesRemoteRepository.isValidPrayerDay(
                "2026-11-08",
                2026,
                11,
                listOf(300, 390, 690, 900, 1020, 1110)
            )
        )
    }

    @Test
    fun rejectsPrayerDayOutsideRequestedMonth() {
        assertFalse(
            PrayerTimesRemoteRepository.isValidPrayerDay(
                "2026-12-01",
                2026,
                11,
                listOf(300, 390, 690, 900, 1020, 1110)
            )
        )
    }

    @Test
    fun rejectsNonChronologicalOrMalformedPrayerTimes() {
        assertFalse(
            PrayerTimesRemoteRepository.isValidPrayerDay(
                "2026-11-08",
                2026,
                11,
                listOf(300, 390, 690, 850, 820, 1110)
            )
        )
        assertFalse(
            PrayerTimesRemoteRepository.isValidPrayerDay(
                "2026-02-30",
                2026,
                2,
                listOf(300, 390, 690, 900, 1020, 1110)
            )
        )
    }

    @Test
    fun mapsVisibleCalculationChoicesToProviderMethodIds() {
        assertEquals("JORDAN", PrayerTimesRemoteRepository.methodIdFor("الأردن (18° / 18°)"))
        assertEquals("MWL", PrayerTimesRemoteRepository.methodIdFor("رابطة العالم الإسلامي (18° / 17°)"))
        assertEquals("UMM_AL_QURA", PrayerTimesRemoteRepository.methodIdFor("أم القرى (18.5° / 90 دقيقة)"))
    }

    @Test
    fun recognizesJordanCoordinatesWithoutIncludingJerusalem() {
        assertTrue(PrayerTimesRemoteRepository.isJordan(31.9539, 35.9106))
        assertTrue(PrayerTimesRemoteRepository.isJordan(29.5267, 35.0078))
        assertFalse(PrayerTimesRemoteRepository.isJordan(31.7683, 35.2137))
        assertFalse(PrayerTimesRemoteRepository.isJordan(59.91, 10.75))
    }
}
