package com.noorsalah.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Online prayer-time source with a local cache. The provider identifies the
 * calculation method and verification status in every response. If the network
 * is unavailable, callers continue using the local calculation fallback.
 */
object PrayerTimesRemoteRepository {
    private const val PREFS = "MainActivity"
    private const val REFRESH_INTERVAL_MS = 18L * 60L * 60L * 1000L
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var refreshInFlight = false

    private data class RemoteDay(
        val date: String,
        val minutes: List<Int>,
        val hijriLabel: String,
        val methodName: String,
        val methodStatus: String,
        val authorityUrl: String,
        val pageUrl: String
    )

    fun refreshIfNeeded(
        context: Context,
        latitude: Double,
        longitude: Double,
        force: Boolean = false,
        onUpdated: (() -> Unit)? = null
    ) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val selectedMethod = prefs.getString("calculation_method", "الأردن (18° / 18°)") ?: "الأردن (18° / 18°)"
        if (!selectedMethod.startsWith("الأردن") || !isJordan(latitude, longitude)) return
        val today = Calendar.getInstance()
        val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val todayKey = dateKey(today)
        val tomorrowKey = dateKey(tomorrow)
        val oldLat = prefs.getString("prayer_remote_lat", null)?.toDoubleOrNull()
        val oldLon = prefs.getString("prayer_remote_lon", null)?.toDoubleOrNull()
        val sameLocation = oldLat != null && oldLon != null &&
            abs(oldLat - latitude) < 0.015 && abs(oldLon - longitude) < 0.015
        val hasBothDays = getTimes(appContext, today) != null && getTimes(appContext, tomorrow) != null
        val lastFetch = prefs.getLong("prayer_remote_fetch_time", 0L)
        val fresh = System.currentTimeMillis() - lastFetch < REFRESH_INTERVAL_MS

        if (!force && sameLocation && hasBothDays && fresh) return
        synchronized(this) {
            if (refreshInFlight) return
            refreshInFlight = true
        }

        executor.execute {
            var updated = 0
            try {
                val timezone = if (isJordan(latitude, longitude)) {
                    "Asia/Amman"
                } else {
                    TimeZone.getDefault().id
                }
                listOf(today, tomorrow).forEach { date ->
                    try {
                        val remote = fetchDay(latitude, longitude, timezone, date)
                        val editor = prefs.edit()
                            .putString("prayer_remote_times_${remote.date}", JSONArray(remote.minutes).toString())
                            .putString("prayer_remote_hijri_${remote.date}", remote.hijriLabel)
                            .putString("prayer_remote_method", remote.methodName)
                            .putString("prayer_remote_method_status", remote.methodStatus)
                            .putString("prayer_remote_authority_url", remote.authorityUrl)
                            .putString("prayer_remote_page_url", remote.pageUrl)
                            .putString("prayer_remote_license", "CC BY 4.0")
                        editor.apply()
                        updated++
                    } catch (_: Exception) {
                        // Keep the previous cached day and let the calculator be the fallback.
                    }
                }
                if (updated > 0) {
                    val fetchTime = if (updated == 2) System.currentTimeMillis()
                    else System.currentTimeMillis() - (REFRESH_INTERVAL_MS / 2)
                    prefs.edit()
                        .putString("prayer_remote_lat", latitude.toString())
                        .putString("prayer_remote_lon", longitude.toString())
                        .putLong("prayer_remote_fetch_time", fetchTime)
                        .apply()
                    Handler(Looper.getMainLooper()).post {
                        PrayerAlarmScheduler.scheduleTodayAndTomorrow(appContext)
                        onUpdated?.invoke()
                    }
                }
            } finally {
                refreshInFlight = false
            }
        }
    }

    fun getTimes(context: Context, date: Calendar): List<Int>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val selectedMethod = prefs.getString("calculation_method", "الأردن (18° / 18°)") ?: "الأردن (18° / 18°)"
        if (!selectedMethod.startsWith("الأردن")) return null
        val currentLat = prefs.getString("city_lat", "31.9539")?.toDoubleOrNull() ?: 31.9539
        val currentLon = prefs.getString("city_lon", "35.9106")?.toDoubleOrNull() ?: 35.9106
        if (!isJordan(currentLat, currentLon)) return null
        val cachedLat = prefs.getString("prayer_remote_lat", null)?.toDoubleOrNull() ?: return null
        val cachedLon = prefs.getString("prayer_remote_lon", null)?.toDoubleOrNull() ?: return null
        if (abs(currentLat - cachedLat) >= 0.015 || abs(currentLon - cachedLon) >= 0.015) return null
        val raw = prefs.getString("prayer_remote_times_${dateKey(date)}", null) ?: return null
        return try {
            val values = JSONArray(raw)
            if (values.length() != 6) return null
            (0 until 6).map { values.getInt(it) }.takeIf { it.all { minute -> minute in 0..1439 } }
        } catch (_: Exception) {
            null
        }
    }

    fun getHijriLabel(context: Context, date: Calendar): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("prayer_remote_hijri_${dateKey(date)}", null)

    fun getMethodLabel(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (getTimes(context, Calendar.getInstance()) == null) {
            return "المواقيت المحلية الحسابية • تقديرية أو حسب طريقة الحساب المختارة"
        }
        val method = prefs.getString("prayer_remote_method", null)
            ?: return "المواقيت المحلية الحسابية • تقديرية عند عدم توفر الإنترنت"
        val status = prefs.getString("prayer_remote_method_status", "")
        return if (status == "verified") {
            "$method • مطابقة للجدول المنشور"
        } else {
            "$method • طريقة حسابية من المصدر"
        }
    }

    fun getProviderLabel(context: Context): String =
        if (getTimes(context, Calendar.getInstance()) != null) {
            "مصدر البيانات: prayertime.si • CC BY 4.0"
        } else {
            "مصدر احتياطي: الحساب المحلي التقديري"
        }

    fun getSourceUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString("prayer_remote_page_url", null)
            ?.takeIf { it.startsWith("https://") }
            ?: "https://prayertime.si/"
    }

    fun getAuthorityUrl(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("prayer_remote_authority_url", null)
            ?.takeIf { it.startsWith("https://") }

    private fun fetchDay(
        latitude: Double,
        longitude: Double,
        timezone: String,
        date: Calendar
    ): RemoteDay {
        val day = dateKey(date)
        val query = buildString {
            append("lat=").append(latitude)
            append("&lon=").append(longitude)
            append("&timezone=").append(URLEncoder.encode(timezone, "UTF-8"))
            if (isJordan(latitude, longitude)) append("&country_code=JO")
            append("&date=").append(day)
        }
        val connection = (URL("https://prayertime.si/api/v1/times?$query").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "NoorSalah-Android")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Prayer times service returned HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(body)
            require(root.optString("date") == day) { "Prayer times date mismatch" }
            val times = root.getJSONObject("times")
            val minutes = listOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha")
                .map { parseTime(times.getString(it)) }
            val hijri = root.optJSONObject("hijri")
            val hijriLabel = if (hijri != null) {
                "${hijri.optInt("day")} ${hijri.optString("month_ar")} ${hijri.optInt("year")} هـ"
            } else ""
            val method = root.optJSONObject("method")
            return RemoteDay(
                date = day,
                minutes = minutes,
                hijriLabel = hijriLabel,
                methodName = method?.optString("name_ar").orEmpty().ifBlank {
                    method?.optString("name_en").orEmpty().ifBlank { "طريقة البلد" }
                },
                methodStatus = method?.optString("status").orEmpty(),
                authorityUrl = method?.optString("source_url").orEmpty(),
                pageUrl = root.optString("page")
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun parseTime(value: String): Int {
        val parts = value.trim().take(5).split(":")
        require(parts.size == 2)
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()
        require(hour in 0..23 && minute in 0..59)
        return hour * 60 + minute
    }

    private fun dateKey(date: Calendar): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date.time)

    private fun isJordan(latitude: Double, longitude: Double): Boolean =
        (latitude in 30.0..33.5 && longitude in 35.35..39.4) ||
            (latitude in 29.0..30.0 && longitude in 34.8..36.0)
}
