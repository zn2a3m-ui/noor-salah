package com.noorsalah.app

import org.junit.Assert.assertEquals
import org.junit.Test

class QuranRepositoryTest {
    @Test
    fun normalizesArabicDiacriticsAndAlifVariantsForSearch() {
        assertEquals(
            "الله يهدي الي الصراط",
            normalizeQuranSearchText("اللَّهُ يَهْدِي إِلَى الصِّرَاطِ")
        )
    }

    @Test
    fun normalizesHamzaAndTatweelWithoutChangingPublishedText() {
        assertEquals(
            "مومنون",
            normalizeQuranSearchText("مُؤْمِنُون")
        )
        assertEquals(
            "بسم الله",
            normalizeQuranSearchText("بِسْمِ اللَّـهِ")
        )
    }
}
