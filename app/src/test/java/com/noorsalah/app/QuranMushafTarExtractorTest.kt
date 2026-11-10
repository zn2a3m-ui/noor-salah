package com.noorsalah.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.GZIPOutputStream

class QuranMushafTarExtractorTest {
    @Test
    fun extractsOnlyAllowListedPageFilesAndStripsArchiveRoot() {
        val temp = Files.createTempDirectory("mushaf-tar-test").toFile()
        try {
            val archive = File(temp, "mushaf.tar.gz")
            val pageBytes = byteArrayOf('Q'.code.toByte(), 'V'.code.toByte(), 'P'.code.toByte(), '1'.code.toByte(), 7, 8)
            val wordsBytes = """{"1:1:1":{"rasm_uthmani":"بِسْمِ"}}""".toByteArray(StandardCharsets.UTF_8)
            val atlasBytes = byteArrayOf(11, 12, 13)

            GZIPOutputStream(FileOutputStream(archive)).use { gzip ->
                writeTarEntry(gzip, "quran-engine-pages-hafs-kfgqpc/001.qvp", pageBytes)
                writeTarEntry(gzip, "quran-engine-pages-hafs-kfgqpc/001.words.json", wordsBytes)
                writeTarEntry(gzip, "quran-engine-pages-hafs-kfgqpc/atlas.qva", atlasBytes)
                writeTarEntry(gzip, "quran-engine-pages-hafs-kfgqpc/surah-names/qvp/001.qvp", byteArrayOf(99))
                writeTarEntry(gzip, "quran-engine-pages-hafs-kfgqpc/../escape.qvp", byteArrayOf(100))
                gzip.write(ByteArray(1024))
            }

            val destination = File(temp, "extracted")
            QuranMushafTarExtractor.extract(archive, destination)

            assertArrayEquals(pageBytes, File(destination, "001.qvp").readBytes())
            assertArrayEquals(wordsBytes, File(destination, "001.words.json").readBytes())
            assertArrayEquals(atlasBytes, File(destination, "atlas.qva").readBytes())
            assertFalse(File(destination, "surah-names/qvp/001.qvp").exists())
            assertFalse(File(temp, "escape.qvp").exists())
            assertTrue(destination.isDirectory)
        } finally {
            temp.deleteRecursively()
        }
    }

    private fun writeTarEntry(output: GZIPOutputStream, path: String, data: ByteArray) {
        val header = ByteArray(512)
        writeText(header, 0, 100, path)
        writeOctal(header, 100, 8, 420)
        writeOctal(header, 108, 8, 0)
        writeOctal(header, 116, 8, 0)
        writeOctal(header, 124, 12, data.size.toLong())
        writeOctal(header, 136, 12, 0)
        for (index in 148 until 156) header[index] = ' '.code.toByte()
        header[156] = '0'.code.toByte()
        writeText(header, 257, 6, "ustar")
        writeText(header, 263, 2, "00")

        val checksum = header.sumOf { it.toInt() and 0xff }
        val checksumText = checksum.toString(8).padStart(6, '0')
        writeText(header, 148, 6, checksumText)
        header[154] = 0
        header[155] = ' '.code.toByte()

        output.write(header)
        output.write(data)
        val padding = (512 - data.size % 512) % 512
        if (padding > 0) output.write(ByteArray(padding))
    }

    private fun writeText(target: ByteArray, offset: Int, maxLength: Int, value: String) {
        val bytes = value.toByteArray(StandardCharsets.US_ASCII)
        require(bytes.size <= maxLength)
        bytes.copyInto(target, offset)
    }

    private fun writeOctal(target: ByteArray, offset: Int, length: Int, value: Long) {
        val text = value.toString(8).padStart(length - 1, '0')
        writeText(target, offset, length - 1, text)
    }
}
