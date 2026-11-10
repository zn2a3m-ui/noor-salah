package com.noorsalah.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import ws.quran.qvp.QvpAtlas
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
import org.json.JSONObject
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

    fun loadAtlas(callback: (Result<QvpAtlas>) -> Unit) {
        worker.execute {
            val result = runCatching {
                root.mkdirs()
                val atlasFile = File(root, "atlas.qva")
                val fullPackReady = isFullPackInstalled() && atlasFile.isFile
                if (!fullPackReady) {
                    val expected = manifestEntry("atlas.qva")
                    if (!matchesManifest(atlasFile, expected)) {
                        downloadFile("$CDN_BASE/atlas.qva", atlasFile) { bytes ->
                            matchesManifest(bytes, expected)
                        }
                    }
                }
                val atlas = QvpAtlas(atlasFile.readBytes())
                if (atlas.pageCount() != PAGE_COUNT) {
                    atlas.close()
                    throw IOException("The Mushaf atlas does not contain 604 pages.")
                }
                atlas
            }
            main.post { callback(result) }
        }
    }

    fun loadPage(pageNumber: Int, callback: (Result<QvpPage>) -> Unit) {
        require(pageNumber in 1..PAGE_COUNT) { "Mushaf page must be between 1 and 604." }
        worker.execute {
            val result = runCatching {
                root.mkdirs()
                val pageFile = pageFile(pageNumber)
                val wordsFile = File(root, "${pageName(pageNumber)}.words.json")
                val fullPackReady = isFullPackInstalled() && validPageFile(pageFile) &&
                    wordsFile.isFile && wordsFile.length() > 0L
                if (!fullPackReady) {
                    val pageName = "${pageName(pageNumber)}.qvp"
                    val pageDigest = manifestEntry(pageName)
                    if (!matchesManifest(pageFile, pageDigest)) {
                        downloadFile("$CDN_BASE/$pageName", pageFile) { bytes ->
                            isValidPageBytes(bytes) && matchesManifest(bytes, pageDigest)
                        }
                    }
                    val wordsName = "${pageName(pageNumber)}.words.json"
                    val wordsDigest = manifestEntry(wordsName)
                    if (!matchesManifest(wordsFile, wordsDigest)) {
                        downloadFile("$CDN_BASE/$wordsName", wordsFile) { bytes ->
                            bytes.isNotEmpty() && bytes.first() == '{'.code.toByte() &&
                                matchesManifest(bytes, wordsDigest)
                        }
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
                    QuranMushafTarExtractor.extract(archive, staging)
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
            setRequestProperty("Accept-Encoding", "identity")
        }
        try {
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Mushaf download failed with HTTP ${connection.responseCode}.")
            }
            val total = connection.contentLengthLong.takeIf { it > 0L } ?: ARCHIVE_SIZE
            var copied = 0L
            var lastReportedPercent = -1
            connection.inputStream.use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        val percent = ((copied * 100L) / total).toInt().coerceIn(0, 99)
                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            main.post { onProgress(percent) }
                        }
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
            setRequestProperty("Accept-Encoding", "identity")
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

    private data class ManifestFile(val bytes: Long, val sha256: String)

    private fun manifestEntry(name: String): ManifestFile {
        val file = File(root, "manifest.json")
        fun downloadManifest() {
            downloadFile("$CDN_BASE/manifest.json", file) { bytes ->
                runCatching {
                    val json = JSONObject(String(bytes, Charsets.UTF_8))
                    json.optString("version") == "v0.5.0" &&
                        (json.optJSONArray("files")?.length() ?: 0) > 0
                }.getOrDefault(false)
            }
        }
        val cachedJson = runCatching {
            if (!file.isFile || file.length() == 0L) throw IOException("Missing Mushaf manifest.")
            JSONObject(file.readText(Charsets.UTF_8))
        }.getOrNull()
        val json = cachedJson ?: run {
            file.delete()
            downloadManifest()
            JSONObject(file.readText(Charsets.UTF_8))
        }
        if (json.optString("version") != "v0.5.0") {
            file.delete()
            throw IOException("The Mushaf page manifest has an unexpected version.")
        }
        val files = json.optJSONArray("files")
            ?: throw IOException("The Mushaf page manifest has no file list.")
        for (index in 0 until files.length()) {
            val item = files.getJSONObject(index)
            if (item.optString("name") == name) {
                val bytes = item.optLong("bytes", -1L)
                val digest = item.optString("sha256")
                if (bytes > 0L && digest.matches(Regex("[0-9a-f]{64}"))) {
                    return ManifestFile(bytes, digest)
                }
            }
        }
        throw IOException("The Mushaf manifest does not contain $name.")
    }

    private fun matchesManifest(file: File, expected: ManifestFile): Boolean =
        file.isFile && file.length() == expected.bytes && sha256(file) == expected.sha256

    private fun matchesManifest(bytes: ByteArray, expected: ManifestFile): Boolean =
        bytes.size.toLong() == expected.bytes && sha256(bytes) == expected.sha256

    private fun isValidPageBytes(bytes: ByteArray): Boolean =
        bytes.size >= 8 && String(bytes.copyOfRange(0, 4), Charsets.US_ASCII) == "QVP1"

    private fun validPageFile(file: File): Boolean {
        if (!file.isFile || file.length() < 8L) return false
        return runCatching {
            FileInputStream(file).use { input ->
                val magic = ByteArray(4)
                input.read(magic) == 4 && String(magic, Charsets.US_ASCII) == "QVP1"
            }
        }.getOrDefault(false)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

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
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun pageFile(pageNumber: Int) = File(root, "${pageName(pageNumber)}.qvp")
    private fun pageName(pageNumber: Int) = "%03d".format(java.util.Locale.US, pageNumber)

    override fun close() {
        worker.shutdownNow()
    }
}
