package com.noorsalah.app

import java.util.Calendar
import kotlin.math.*

/**
 * Single source of truth for the app's calculated prayer times.
 * The result order is Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha.
 *
 * These are calculation estimates, not an official Jordanian timetable.
 */
object PrayerTimesCalculator {
    fun calculate(
        date: Calendar,
        latitude: Double,
        longitude: Double,
        calculationMethod: String,
        timeOffsetMinutes: Int
    ): List<Int> {
        val day = date.get(Calendar.DAY_OF_YEAR)
        val days = if (date.getActualMaximum(Calendar.DAY_OF_YEAR) == 366) 366.0 else 365.0
        val g = 2.0 * Math.PI / days * (day - 1)
        val eq = 229.18 * (
            0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) -
            0.014615 * cos(2 * g) - 0.040849 * sin(2 * g)
        )
        val dec = (
            0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) -
            0.006758 * cos(2 * g) + 0.000907 * sin(2 * g) -
            0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        )
        val lat = Math.toRadians(latitude)
        val zone = (date.get(Calendar.ZONE_OFFSET) + date.get(Calendar.DST_OFFSET)) / 60000.0
        val noon = 720.0 - 4.0 * longitude - eq + zone + timeOffsetMinutes

        fun hourAngle(altitude: Double): Double {
            val angle = Math.toRadians(altitude)
            val value = (sin(angle) - sin(lat) * sin(dec)) / (cos(lat) * cos(dec))
            return Math.toDegrees(acos(value.coerceIn(-1.0, 1.0))) * 4.0
        }

        val (fajrAngle, ishaAngle, ishaInterval, maghribAdjustment) = when {
            calculationMethod.startsWith("الأردن") -> listOf(18.0, 18.0, 0.0, 5.0)
            calculationMethod.startsWith("أم القرى") -> listOf(18.5, 0.0, 90.0, 0.0)
            calculationMethod.startsWith("الهيئة المصرية") -> listOf(19.5, 17.5, 0.0, 0.0)
            calculationMethod.startsWith("أمريكا الشمالية") -> listOf(15.0, 15.0, 0.0, 0.0)
            calculationMethod.startsWith("جامعة كراتشي") -> listOf(18.0, 18.0, 0.0, 0.0)
            calculationMethod.startsWith("ديانت تركيا") -> listOf(18.0, 17.0, 0.0, 0.0)
            calculationMethod.startsWith("سنغافورة") -> listOf(20.0, 18.0, 0.0, 0.0)
            else -> listOf(18.0, 17.0, 0.0, 0.0)
        }
        val fajr = noon - hourAngle(-fajrAngle)
        val sunrise = noon - hourAngle(-0.833)
        val dhuhr = noon + 2.0
        val asrAltitude = Math.toDegrees(atan(1.0 / (1.0 + tan(abs(lat - dec)))))
        val asr = noon + hourAngle(asrAltitude)
        val sunset = noon + hourAngle(-0.833)
        val maghrib = sunset + maghribAdjustment
        val isha = if (ishaInterval > 0.0) sunset + ishaInterval else noon + hourAngle(-ishaAngle)

        return listOf(fajr, sunrise, dhuhr, asr, maghrib, isha)
            .map { ((it.roundToInt() % 1440) + 1440) % 1440 }
    }
}
