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
    fun automaticallySelectsCalculationMethodFromCountry() {
        assertEquals("الأردن (18° / 18°)", PrayerTimesRemoteRepository.methodLabelForCountryCode("JO"))
        assertEquals("أم القرى (18.5° / 90 دقيقة)", PrayerTimesRemoteRepository.methodLabelForCountryCode("SA"))
        assertEquals("الهيئة المصرية (19.5° / 17.5°)", PrayerTimesRemoteRepository.methodLabelForCountryCode("EG"))
        assertEquals("ديانت تركيا (18° / 17°)", PrayerTimesRemoteRepository.methodLabelForCountryCode("TR"))
        assertEquals("سنغافورة (20° / 18°)", PrayerTimesRemoteRepository.methodLabelForCountryCode("SG"))
        assertEquals("أمريكا الشمالية ISNA (15° / 15°)", PrayerTimesRemoteRepository.methodLabelForCountryCode("US"))
        assertEquals("رابطة العالم الإسلامي (18° / 17°)", PrayerTimesRemoteRepository.methodLabelForCountryCode("FR"))
    }

    @Test
    fun mapsAutomaticallySelectedMethodsToProviderIds() {
        assertEquals("JORDAN", PrayerTimesRemoteRepository.methodIdFor("الأردن (18° / 18°)"))
        assertEquals("MWL", PrayerTimesRemoteRepository.methodIdFor("رابطة العالم الإسلامي (18° / 17°)"))
        assertEquals("UMM_AL_QURA", PrayerTimesRemoteRepository.methodIdFor("أم القرى (18.5° / 90 دقيقة)"))
        assertEquals("EGYPT", PrayerTimesRemoteRepository.methodIdFor("الهيئة المصرية (19.5° / 17.5°)"))
        assertEquals("ISNA", PrayerTimesRemoteRepository.methodIdFor("أمريكا الشمالية ISNA (15° / 15°)"))
        assertEquals("DIYANET", PrayerTimesRemoteRepository.methodIdFor("ديانت تركيا (18° / 17°)"))
        assertEquals("SINGAPORE", PrayerTimesRemoteRepository.methodIdFor("سنغافورة (20° / 18°)"))
    }

    @Test
    fun recognizesJordanCoordinatesWithoutIncludingJerusalem() {
        assertTrue(PrayerTimesRemoteRepository.isJordan(31.9539, 35.9106))
        assertTrue(PrayerTimesRemoteRepository.isJordan(29.5267, 35.0078))
        assertFalse(PrayerTimesRemoteRepository.isJordan(31.7683, 35.2137))
        assertFalse(PrayerTimesRemoteRepository.isJordan(59.91, 10.75))
    }
}
