package com.noorsalah.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QiblaCalculatorTest {
    @Test
    fun calculatesQiblaBearingFromAmman() {
        assertEquals(160.7, QiblaCalculator.bearing(31.9539, 35.9106), 0.2)
    }

    @Test
    fun convertsMagneticHeadingToTrueHeading() {
        assertEquals(5f, QiblaCalculator.trueHeading(355f, 10f), 0.001f)
        assertEquals(355f, QiblaCalculator.trueHeading(5f, -10f), 0.001f)
    }

    @Test
    fun normalizesTrueHeadingAcrossNorth() {
        val heading = QiblaCalculator.trueHeading(350f, 20f)
        assertTrue(heading in 9.999f..10.001f)
    }
}
