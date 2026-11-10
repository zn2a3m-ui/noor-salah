package com.noorsalah.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import ws.quran.qvp.QvpPage
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream

/**
 * Loads the publisher-derived Hafs/KFGQPC page data for the native QVP renderer.
 *
 * Pages are fetched individually on first use and cached. Readers may optionally download the
 * complete, checksum-verified data archive for offline access to all 604 pages.
 */
class QuranMushafDataRepository(context: Context) : Closeable {
    companion object {
        const val PAGE_COUNT = 604
        const val DATA_VERSION = "data-v0.5.0"
        private const val CDN_BASE = "https://cdn.quran.ws/qvp/v0.5.0"
        private const val ARCHIVE_URL =
            "https://github.com/quran-ws/quran-engine/releases/download/data-v0.5.0/quran-engine-pages-hafs-kfgqpc.tar.gz"
        private const val ARCHIVE_SIZE = 45898257L
        private const val ARCHIVE_SHA256 =
            "ffc2f4f86893357ad82b864a09e7813d604760596b84202bdab1dd47cad569db"
    }

    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "quran-mushaf-qvp-v0.5.0")
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun isPageCached(pageNumber: Int): Boolean =
        pageNumber in 1..PAGE_COUNT && validPageFile(pageFile(pageNumber))

    fun isFullPackInstalled(): Boolean {
        if (!File(root, ".tag").isFile || !File(root, "atlas.qva").isFile) return false
        val pages = root.listFiles { file -> file.isFile && file.name.matches(Regex("\\d{3}\\.qvp")) }
        val sidecars = root.listFiles { file -> file.isFile && file.name.matches(Regex("\\d{3}\\.words\\.json")) }
        return pages?.size == PAGE_COUNT && sidecars?.size == PAGE_COUNT &&
            runCatching { File(root, ".tag").readText(Charsets.UTF_8).trim() == DATA_VERSION }.getOrDefault(false)
    }

    fun loadPage(pageNumber: Int, callback: (Result<QvpPage>) -> Unit) {
        require(pageNumber in 1..PAGE_COUNT) { "Mushaf page must be between 1 and 604." }
        worker.execute {
            val result = runCatching {
                root.mkdirs()
                val pageFile = pageFile(pageNumber)
                if (!validPageFile(pageFile)) {
                    downloadFile("$CDN_BASE/${pageName(pageNumber)}.qvp", pageFile) { bytes ->
                        bytes.size >= 8 && String(bytes.copyOfRange(0, 4), Charsets.US_ASCII) == "QVP1"
                    }
                }
                val wordsFile = File(root, "${pageName(pageNumber)}.words.json")
                if (!wordsFile.isFile || wordsFile.length() == 0L) {
                    downloadFile("$CDN_BASE/${pageName(pageNumber)}.words.json", wordsFile) { bytes ->
                        bytes.isNotEmpty() && bytes.first() == '{'.code.toByte()
                    }
                }
                val page = QvpPage(pageFile.readBytes())
                if (page.pageNo != pageNumber || page.nAyahs <= 0 || page.nWords <= 0) {
                    page.close()
                    throw IOException(
                        "The downloaded Mushaf page does not match page $pageNumber or has incomplete page data."
                    )
                }
                val attached = page.attachWords(wordsFile.readBytes())
                if (attached != page.nWords) {
                    page.close()
                    throw IOException(
                        "The word data for page $pageNumber is incomplete ($attached/${page.nWords} words)."
                    )
                }
                page
            }
            main.post { callback(result) }
        }
    }

    fun downloadFullPack(
        onProgress: (Int) -> Unit,
        callback: (Result<Unit>) -> Unit
    ) {
        worker.execute {
            val result = runCatching {
                if (isFullPackInstalled()) {
                    main.post { onProgress(100) }
                    return@runCatching
                }
                val requiredFreeBytes = ARCHIVE_SIZE + 100L * 1024L * 1024L
                if (appContext.filesDir.usableSpace < requiredFreeBytes) {
                    throw IOException("المساحة الفارغة غير كافية. يلزم نحو 150 ميغابايت مؤقتاً لتنزيل صفحات المصحف وفك ضغطها.")
                }
                root.mkdirs()
                val archive = File(appContext.cacheDir, "quran-engine-pages-hafs-kfgqpc.tar.gz")
                downloadArchive(archive, onProgress)
                val actual = sha256(archive)
                if (archive.length() != ARCHIVE_SIZE || actual != ARCHIVE_SHA256) {
                    archive.delete()
                    throw IOException("The downloaded Mushaf data did not pass its SHA-256 check.")
                }
                val staging = File(appContext.cacheDir, "quran-mushaf-qvp-extract")
                staging.deleteRecursively()
                staging.mkdirs()
                try {
                    extractTarGz(archive, staging)
                    val pageFiles = staging.listFiles { file ->
                        file.isFile && file.name.matches(Regex("\\d{3}\\.qvp"))
                    } ?: emptyArray()
                    if (pageFiles.size != PAGE_COUNT || !File(staging, "atlas.qva").isFile) {
                        throw IOException("The Mushaf archive is incomplete; expected 604 pages and atlas.qva.")
                    }
                    staging.listFiles()?.forEach { source ->
                        if (source.isFile) {
                            val destination = File(root, source.name)
                            if (!source.renameTo(destination)) {
                                source.copyTo(destination, overwrite = true)
                                source.delete()
                            }
                        }
                    }
                    File(root, ".tag").writeText(DATA_VERSION, Charsets.UTF_8)
                } finally {
                    staging.deleteRecursively()
                    archive.delete()
                }
                if (!isFullPackInstalled()) {
                    throw IOException("The Mushaf pages could not be verified after extraction.")
                }
                main.post { onProgress(100) }
            }
            main.post { callback(result) }
        }
    }

    private fun downloadArchive(target: File, onProgress: (Int) -> Unit) {
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, target.name + ".part")
        target.delete()
        part.delete()
        val connection = (URL(ARCHIVE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 120000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "NoorSalah-Android")
        }
        try {
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Mushaf download failed with HTTP ${connection.responseCode}.")
            }
            val total = connection.contentLengthLong.takeIf { it > 0L } ?: ARCHIVE_SIZE
            var copied = 0L
            connection.inputStream.use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        val percent = ((copied * 100L) / total).toInt().coerceIn(0, 99)
                        main.post { onProgress(percent) }
                    }
                    output.fd.sync()
                }
            }
            if (copied != ARCHIVE_SIZE) {
                throw IOException("Mushaf download ended early ($copied bytes).")
            }
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
        } catch (error: Exception) {
            part.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadFile(url: String, target: File, validate: (ByteArray) -> Boolean) {
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, target.name + ".part")
        part.delete()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 60000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "NoorSalah-Android")
        }
        try {
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Mushaf page download failed with HTTP ${connection.responseCode}.")
            }
            connection.inputStream.use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            val bytes = part.readBytes()
            if (!validate(bytes)) throw IOException("Downloaded Mushaf page data is invalid.")
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
        } catch (error: Exception) {
            part.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun validPageFile(file: File): Boolean {
        if (!file.isFile || file.length() < 8L) return false
        return runCatching {
            FileInputStream(file).use { input ->
                val magic = ByteArray(4)
                input.read(magic) == 4 && String(magic, Charsets.US_ASCII) == "QVP1"
            }
        }.getOrDefault(false)
    }

    private fun extractTarGz(archive: File, destination: File) {
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

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun pageFile(pageNumber: Int) = File(root, "${pageName(pageNumber)}.qvp")
    private fun pageName(pageNumber: Int) = "%03d".format(java.util.Locale.US, pageNumber)

    override fun close() {
        worker.shutdownNow()
    }
}
