package com.noorsalah.app

import kotlin.math.*

object QiblaCalculator {
    private const val KAABA_LATITUDE = 21.4225
    private const val KAABA_LONGITUDE = 39.8262

    fun bearing(latitude: Double, longitude: Double): Double {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
        val kaabaLatitude = Math.toRadians(KAABA_LATITUDE)
        val currentLatitude = Math.toRadians(latitude)
        val longitudeDifference = Math.toRadians(KAABA_LONGITUDE - longitude)
        val y = sin(longitudeDifference)
        val x = cos(currentLatitude) * tan(kaabaLatitude) -
            sin(currentLatitude) * cos(longitudeDifference)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun trueHeading(magneticHeading: Float, declination: Float): Float =
        (magneticHeading + declination + 360f) % 360f
}
