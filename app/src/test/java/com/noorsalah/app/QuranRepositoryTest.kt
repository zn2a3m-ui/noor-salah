package com.noorsalah.app

import org.junit.Assert.assertEquals
import org.junit.Test

class QuranRepositoryTest {
    @Test
    fun normalizesArabicDiacriticsAndAlifVariantsForSearch() {
        assertEquals(
            "الله يهدي الى الصراط",
            normalizeQuranSearchText("اللَّهُ يَهْدِي إِلَى الصِّرَاطِ")
        )
    }

    @Test
    fun normalizesHamzaAndTatweelWithoutChangingPublishedText() {
        val repository = QuranRepository(android.test.mock.MockContext())
        assertEquals(
            "مومنون",
            repository.normalizeForSearch("مُؤْمِنُون")
        )
        assertEquals(
            "بسم الله",
            repository.normalizeForSearch("بِسْمِ اللَّـهِ")
        )
    }
}
