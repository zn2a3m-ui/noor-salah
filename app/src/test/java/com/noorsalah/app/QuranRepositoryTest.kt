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
    @Test
    fun resolvesAyahsToTheCorrectPageAtPageBoundaries() {
        val starts = listOf(
            1 to 1,
            2 to 1,
            2 to 6,
            2 to 17,
            3 to 1
        )

        assertEquals(1, findMushafPage(starts, 1, 7))
        assertEquals(2, findMushafPage(starts, 2, 5))
        assertEquals(3, findMushafPage(starts, 2, 6))
        assertEquals(3, findMushafPage(starts, 2, 16))
        assertEquals(4, findMushafPage(starts, 2, 17))
        assertEquals(5, findMushafPage(starts, 3, 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnorderedMushafPageMetadata() {
        findMushafPage(listOf(1 to 1, 2 to 6, 2 to 1), 2, 7)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidQuranLocation() {
        findMushafPage(listOf(1 to 1), 0, 1)
    }

}
