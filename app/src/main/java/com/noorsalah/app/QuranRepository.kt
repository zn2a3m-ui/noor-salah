package com.noorsalah.app

import android.content.Context
import org.json.JSONObject
import java.text.Normalizer

data class QuranSurah(
    val number: Int,
    val name: String,
    val ayahCount: Int,
    val startLine: Int,
    val revelationType: String
)

data class QuranAyah(
    val surahNumber: Int,
    val number: Int,
    val text: String
)

data class QuranSearchResult(
    val surahNumber: Int,
    val surahName: String,
    val ayahNumber: Int,
    val text: String
)

/**
 * Reads the original Tanzil text without changing its contents.
 * Only the publisher's blank and comment lines are excluded from the
 * in-memory verse index; the packaged source asset remains byte-for-byte intact.
 */
class QuranRepository(private val context: Context) {
    @Volatile private var cachedMetadata: JSONObject? = null
    @Volatile private var cachedSurahs: List<QuranSurah>? = null
    @Volatile private var cachedAyahs: List<String>? = null

    private fun metadata(): JSONObject {
        cachedMetadata?.let { return it }
        synchronized(this) {
            cachedMetadata?.let { return it }
            val json = context.assets.open("quran-surah-index.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            return JSONObject(json).also { cachedMetadata = it }
        }
    }

    fun juzStart(juzNumber: Int): Pair<Int, Int> = metadataLocation("juz", juzNumber)
    fun pageStart(pageNumber: Int): Pair<Int, Int> = metadataLocation("pages", pageNumber)
    fun hizbQuarterStart(quarterNumber: Int): Pair<Int, Int> = metadataLocation("hizbQuarters", quarterNumber)

    private fun metadataLocation(collection: String, index: Int): Pair<Int, Int> {
        val entries = metadata().getJSONArray(collection)
        require(index in 1..entries.length()) { "Invalid $collection index: $index" }
        val entry = entries.getJSONObject(index - 1)
        return entry.getInt("surah") to entry.getInt("ayah")
    }

    fun loadSurahs(): List<QuranSurah> {
        cachedSurahs?.let { return it }
        synchronized(this) {
            cachedSurahs?.let { return it }
            val array = metadata().getJSONArray("suras")
            val parsed = (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                QuranSurah(
                    number = item.getInt("number"),
                    name = item.getString("name"),
                    ayahCount = item.getInt("ayahs"),
                    startLine = item.getInt("start"),
                    revelationType = item.getString("type")
                )
            }
            check(parsed.size == 114) { "Quran metadata must contain 114 surahs." }
            var expectedStart = 0
            parsed.forEachIndexed { index, surah ->
                check(surah.number == index + 1 && surah.startLine == expectedStart) {
                    "Quran surah metadata is incomplete or out of order."
                }
                expectedStart += surah.ayahCount
            }
            check(expectedStart == 6236) { "Quran metadata must index 6,236 ayahs." }
            cachedSurahs = parsed
            return parsed
        }
    }

    private fun loadAyahLines(): List<String> {
        cachedAyahs?.let { return it }
        synchronized(this) {
            cachedAyahs?.let { return it }
            val lines = context.assets.open("tanzil-uthmani-1.1.txt")
                .bufferedReader(Charsets.UTF_8).useLines { sequence ->
                    sequence.filter { it.isNotBlank() && !it.startsWith("#") }.toList()
                }
            check(lines.size == 6236) { "Verified Tanzil text must contain exactly 6,236 ayahs." }
            cachedAyahs = lines
            return lines
        }
    }

    fun loadAyahs(surahNumber: Int): List<QuranAyah> {
        val surah = loadSurahs().firstOrNull { it.number == surahNumber }
            ?: throw IllegalArgumentException("Unknown surah number: $surahNumber")
        val lines = loadAyahLines()
        val end = surah.startLine + surah.ayahCount
        check(end <= lines.size) { "Surah range exceeds the verified Quran text." }
        return (0 until surah.ayahCount).map { offset ->
            QuranAyah(surah.number, offset + 1, lines[surah.startLine + offset])
        }
    }

    fun search(query: String, maxResults: Int = 50): List<QuranSearchResult> {
        val needle = normalizeQuranSearchText(query)
        if (needle.length < 2) return emptyList()
        val lines = loadAyahLines()
        val results = ArrayList<QuranSearchResult>()
        for (surah in loadSurahs()) {
            for (offset in 0 until surah.ayahCount) {
                val verse = lines[surah.startLine + offset]
                if (normalizeQuranSearchText(verse).contains(needle)) {
                    results.add(QuranSearchResult(surah.number, surah.name, offset + 1, verse))
                    if (results.size >= maxResults) return results
                }
            }
        }
        return results
    }

}

internal fun normalizeQuranSearchText(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[أإآٱ]"), "ا")
        .replace('ى', 'ي')
        .replace('ؤ', 'و')
        .replace('ئ', 'ي')
        .replace("ـ", "")
        .trim()
