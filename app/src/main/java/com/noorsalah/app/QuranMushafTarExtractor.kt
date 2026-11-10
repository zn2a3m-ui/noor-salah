package com.noorsalah.app

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream

/**
 * Extracts only the signed Mushaf page payload from the pinned USTAR archive.
 * Paths are reduced to the archive's single top-level directory and then allow-listed.
 */
internal object QuranMushafTarExtractor {
    fun extract(archive: File, destination: File) {
        destination.mkdirs()
        GZIPInputStream(BufferedInputStream(FileInputStream(archive))).use { input ->
            val header = ByteArray(512)
            while (readTarBlock(input, header)) {
                if (header.all { it == 0.toByte() }) break
                val name = tarString(header, 0, 100)
                val prefix = tarString(header, 345, 155)
                val fullName = if (prefix.isEmpty()) name else "$prefix/$name"
                val relative = fullName.substringAfter('/', fullName)
                val size = tarOctal(header, 124, 12)
                val type = header[156].toInt().toChar()
                val allowed = relative.matches(Regex("\\d{3}\\.qvp")) ||
                    relative.matches(Regex("\\d{3}\\.words\\.json")) ||
                    relative == "atlas.qva" || relative == "atlas.json" ||
                    relative == "ornaments.qvo" || relative == "VERSION.json"
                if ((type == '\u0000' || type == '0') && allowed) {
                    val output = File(destination, relative)
                    output.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(output)).use { stream ->
                        copyExactly(input, stream, size)
                    }
                } else {
                    skipExactly(input, size)
                }
                val padding = (512L - size % 512L) % 512L
                skipExactly(input, padding)
            }
        }
    }

    private fun readTarBlock(input: GZIPInputStream, block: ByteArray): Boolean {
        var offset = 0
        while (offset < block.size) {
            val count = input.read(block, offset, block.size - offset)
            if (count < 0) {
                if (offset == 0) return false
                throw IOException("The Mushaf archive ended inside a tar header.")
            }
            offset += count
        }
        return true
    }

    private fun tarString(bytes: ByteArray, offset: Int, length: Int): String =
        String(bytes, offset, length, Charsets.US_ASCII).substringBefore('\u0000').trim()

    private fun tarOctal(bytes: ByteArray, offset: Int, length: Int): Long {
        val value = String(bytes, offset, length, Charsets.US_ASCII)
            .trim { it == '\u0000' || it == ' ' }
        return if (value.isEmpty()) 0L else value.toLong(8)
    }

    private fun copyExactly(input: GZIPInputStream, output: BufferedOutputStream, size: Long) {
        var remaining = size
        val buffer = ByteArray(8192)
        while (remaining > 0L) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count < 0) throw IOException("The Mushaf archive ended inside a file.")
            output.write(buffer, 0, count)
            remaining -= count
        }
    }

    private fun skipExactly(input: GZIPInputStream, size: Long) {
        var remaining = size
        val buffer = ByteArray(8192)
        while (remaining > 0L) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count < 0) throw IOException("The Mushaf archive ended while skipping a file.")
            remaining -= count
        }
    }
}
