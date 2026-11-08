package com.noorsalah.app

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerTimesCalculatorTest {
    private fun ammanDate(): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Amman")).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 8, 12, 0, 0)
        }

    @Test
    fun returnsSixTimesIncludingSunriseInChronologicalOrder() {
        val times = PrayerTimesCalculator.calculate(
            ammanDate(), 31.9539, 35.9106, "الأردن (18° / 18°)", 0
        )

        assertEquals(6, times.size)
        assertTrue(times.all { it in 0..1439 })
        assertTrue(times.zipWithNext().all { (first, second) -> first < second })
    }

    @Test
    fun manualOffsetMovesEveryCalculatedTimeBySameAmount() {
        val base = PrayerTimesCalculator.calculate(
            ammanDate(), 31.9539, 35.9106, "الأردن (18° / 18°)", 0
        )
        val adjusted = PrayerTimesCalculator.calculate(
            ammanDate(), 31.9539, 35.9106, "الأردن (18° / 18°)", 7
        )

        base.zip(adjusted).forEach { (original, changed) ->
            assertEquals((original + 7) % 1440, changed)
        }
    }

    @Test
    fun ummAlQuraIshaUsesNinetyMinutesAfterSunset() {
        val times = PrayerTimesCalculator.calculate(
            ammanDate(), 31.9539, 35.9106, "أم القرى (18.5° / 90 دقيقة)", 0
        )

        assertEquals((times[4] + 90) % 1440, times[5])
    }
}
