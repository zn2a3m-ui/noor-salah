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
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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

    data class HijriDate(
        val day: Int,
        val month: Int,
        val monthArabic: String,
        val year: Int,
        val basis: String
    )

    private data class RemoteDay(
        val date: String,
        val minutes: List<Int>,
        val hijriLabel: String,
        val hijriJson: String,
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
        if (!prefs.getBoolean("prayer_remote_consent", false)) return
        val selectedMethod = prefs.getString("calculation_method", "الأردن (18° / 18°)") ?: "الأردن (18° / 18°)"
        if (!selectedMethod.startsWith("الأردن") || !isJordan(latitude, longitude)) return
        val today = Calendar.getInstance()
        val dates = (0..6).map { offset ->
            (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
        }
        val oldLat = (prefs.getString("prayer_remote_attempt_lat", null)
            ?: prefs.getString("prayer_remote_lat", null))?.toDoubleOrNull()
        val oldLon = (prefs.getString("prayer_remote_attempt_lon", null)
            ?: prefs.getString("prayer_remote_lon", null))?.toDoubleOrNull()
        val sameLocation = oldLat != null && oldLon != null &&
            abs(oldLat - latitude) < 0.015 && abs(oldLon - longitude) < 0.015
        val hasWeekCached = dates.all { getTimes(appContext, it) != null }
        val now = System.currentTimeMillis()
        val lastFetch = prefs.getLong("prayer_remote_fetch_time", 0L)
        val fresh = now - lastFetch < REFRESH_INTERVAL_MS
        val lastAttempt = prefs.getLong("prayer_remote_last_attempt", 0L)
        val retryCooling = now - lastAttempt < 30L * 60L * 1000L

        if (!force && sameLocation && ((hasWeekCached && fresh) || (!hasWeekCached && retryCooling))) return
        synchronized(this) {
            if (refreshInFlight) return
            refreshInFlight = true
        }
        prefs.edit()
            .putString("prayer_remote_attempt_lat", latitude.toString())
            .putString("prayer_remote_attempt_lon", longitude.toString())
            .putLong("prayer_remote_last_attempt", now)
            .apply()

        val placeSlug = placeSlugFor(prefs, latitude, longitude)
        executor.execute {
            var updated = 0
            var allSucceeded = true
            var cachePlaceSlug: String? = null
            try {
                if (placeSlug != null) {
                    // A city month endpoint returns the full Gregorian month and its Hijri dates.
                    val currentMonth = Calendar.getInstance()
                    val previousMonth = (currentMonth.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                    val nextMonth = (currentMonth.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                    val months = listOf(previousMonth, currentMonth, nextMonth)
                    val fetchPool = Executors.newFixedThreadPool(months.size)
                    try {
                        val requests = months.map { monthDate ->
                            val year = monthDate.get(Calendar.YEAR)
                            val month = monthDate.get(Calendar.MONTH) + 1
                            fetchPool.submit<List<RemoteDay>> {
                                fetchMonth("jordan", placeSlug, year, month)
                            }
                        }
                        requests.forEach { request ->
                            try {
                                val monthDays = request.get(20, TimeUnit.SECONDS)
                                monthDays.forEach { storeRemoteDay(prefs, it) }
                                if (monthDays.isNotEmpty()) cachePlaceSlug = placeSlug
                                updated += monthDays.size
                            } catch (_: Exception) {
                                allSucceeded = false
                                request.cancel(true)
                            }
                        }
                    } finally {
                        fetchPool.shutdownNow()
                    }
                }
                if (placeSlug == null || !allSucceeded || updated == 0) {
                    // Fall back to coordinate-based daily data if a city month page is unavailable.
                    if (placeSlug != null) cachePlaceSlug = null
                    val timezone = TimeZone.getDefault().id
                    val requestLatitude = String.format(Locale.US, "%.2f", latitude).toDouble()
                    val requestLongitude = String.format(Locale.US, "%.2f", longitude).toDouble()
                    val fetchPool = Executors.newFixedThreadPool(dates.size)
                    try {
                        val requests = dates.map { date ->
                            fetchPool.submit<RemoteDay> {
                                fetchDay(requestLatitude, requestLongitude, timezone, date)
                            }
                        }
                        requests.forEach { request ->
                            try {
                                val remote = request.get(20, TimeUnit.SECONDS)
                                storeRemoteDay(prefs, remote)
                                updated++
                            } catch (_: Exception) {
                                allSucceeded = false
                                request.cancel(true)
                            }
                        }
                    } finally {
                        fetchPool.shutdownNow()
                    }
                }
                if (updated > 0) {
                    val fetchTime = if (allSucceeded) System.currentTimeMillis()
                    else System.currentTimeMillis() - (REFRESH_INTERVAL_MS / 2)
                    prefs.edit()
                        .putString("prayer_remote_lat", latitude.toString())
                        .putString("prayer_remote_lon", longitude.toString())
                        .putString("prayer_remote_place_slug", cachePlaceSlug)
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

    private val monthFetchesInFlight = mutableSetOf<String>()

    fun refreshMonthIfNeeded(
        context: Context,
        latitude: Double,
        longitude: Double,
        year: Int,
        month: Int,
        onUpdated: (() -> Unit)? = null
    ) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("prayer_remote_consent", false)) return
        val selectedMethod = prefs.getString("calculation_method", "الأردن (18° / 18°)") ?: "الأردن (18° / 18°)"
        if (!selectedMethod.startsWith("الأردن") || !isJordan(latitude, longitude)) return
        val placeSlug = placeSlugFor(prefs, latitude, longitude) ?: return
        val firstDay = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val lastDay = (firstDay.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        }
        if (getTimes(appContext, firstDay) != null && getTimes(appContext, lastDay) != null) return
        val requestKey = "$placeSlug-$year-$month"
        synchronized(monthFetchesInFlight) {
            if (!monthFetchesInFlight.add(requestKey)) return
        }
        executor.execute {
            try {
                val alreadyCached = getTimes(appContext, firstDay) != null &&
                    getTimes(appContext, lastDay) != null
                if (!alreadyCached) {
                    val monthDays = fetchMonth("jordan", placeSlug, year, month)
                    monthDays.forEach { storeRemoteDay(prefs, it) }
                    if (monthDays.isNotEmpty()) {
                        prefs.edit()
                            .putString("prayer_remote_lat", latitude.toString())
                            .putString("prayer_remote_lon", longitude.toString())
                            .putString("prayer_remote_place_slug", placeSlug)
                            .putLong("prayer_remote_fetch_time", System.currentTimeMillis())
                            .apply()
                        Handler(Looper.getMainLooper()).post {
                            PrayerAlarmScheduler.scheduleTodayAndTomorrow(appContext)
                            onUpdated?.invoke()
                        }
                    }
                }
            } catch (_: Exception) {
                // The calendar keeps its Gregorian view and local prayer-time fallback.
            } finally {
                synchronized(monthFetchesInFlight) {
                    monthFetchesInFlight.remove(requestKey)
                }
            }
        }
    }

    fun getTimes(context: Context, date: Calendar): List<Int>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("prayer_remote_consent", false)) return null
        val selectedMethod = prefs.getString("calculation_method", "الأردن (18° / 18°)") ?: "الأردن (18° / 18°)"
        if (!selectedMethod.startsWith("الأردن")) return null
        val currentLat = prefs.getString("city_lat", "31.9539")?.toDoubleOrNull() ?: 31.9539
        val currentLon = prefs.getString("city_lon", "35.9106")?.toDoubleOrNull() ?: 35.9106
        if (!isJordan(currentLat, currentLon)) return null
        val cachedPlace = prefs.getString("prayer_remote_place_slug", null)
        val currentPlace = placeSlugFor(prefs, currentLat, currentLon)
        if (cachedPlace != null) {
            if (currentPlace != cachedPlace) return null
        } else {
            val cachedLat = prefs.getString("prayer_remote_lat", null)?.toDoubleOrNull() ?: return null
            val cachedLon = prefs.getString("prayer_remote_lon", null)?.toDoubleOrNull() ?: return null
            if (abs(currentLat - cachedLat) >= 0.015 || abs(currentLon - cachedLon) >= 0.015) return null
        }
        val raw = prefs.getString("prayer_remote_times_${dateKey(date)}", null) ?: return null
        return try {
            val values = JSONArray(raw)
            if (values.length() != 6) return null
            (0 until 6).map { values.getInt(it) }.takeIf { it.all { minute -> minute in 0..1439 } }
        } catch (_: Exception) {
            null
        }
    }

    fun getHijriDate(context: Context, date: Calendar): HijriDate? {
        if (getTimes(context, date) == null) return null
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("prayer_remote_hijri_data_${dateKey(date)}", null) ?: return null
        return try {
            val json = JSONObject(raw)
            HijriDate(
                day = json.getInt("day"),
                month = json.getInt("month"),
                monthArabic = json.optString("month_ar"),
                year = json.getInt("year"),
                basis = json.optString("basis")
            )
        } catch (_: Exception) {
            null
        }
    }

    fun getHijriLabel(context: Context, date: Calendar): String? {
        val hijri = getHijriDate(context, date) ?: return null
        val label = "${hijri.day} ${hijri.monthArabic} ${hijri.year} هـ"
        return if (hijri.basis == "umm-al-qura") "$label • أم القرى" else label
    }

    fun getMethodLabel(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (getTimes(context, Calendar.getInstance()) == null) {
            return "المواقيت المحلية الحسابية • تقديرية أو حسب طريقة الحساب المختارة"
        }
        val method = prefs.getString("prayer_remote_method", null)
            ?: return "المواقيت المحلية الحسابية • تقديرية عند عدم توفر الإنترنت"
        val status = prefs.getString("prayer_remote_method_status", "")
        return if (status == "verified" || status == "fitted") {
            "$method • طريقة موثقة من الجهة"
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

    private fun storeRemoteDay(prefs: android.content.SharedPreferences, remote: RemoteDay) {
        prefs.edit()
            .putString("prayer_remote_times_${remote.date}", JSONArray(remote.minutes).toString())
            .putString("prayer_remote_hijri_${remote.date}", remote.hijriLabel)
            .putString("prayer_remote_hijri_data_${remote.date}", remote.hijriJson)
            .putString("prayer_remote_method", remote.methodName)
            .putString("prayer_remote_method_status", remote.methodStatus)
            .putString("prayer_remote_authority_url", remote.authorityUrl)
            .putString("prayer_remote_page_url", remote.pageUrl)
            .putString("prayer_remote_license", "CC BY 4.0")
            .apply()
    }

    private fun placeSlugFor(
        prefs: android.content.SharedPreferences,
        latitude: Double,
        longitude: Double
    ): String? {
        val cityName = prefs.getString("city_name", "") ?: ""
        when {
            cityName.contains("عمّان") -> return "amman"
            cityName.contains("إربد") -> return "irbid"
            cityName.contains("الزرقاء") -> return "zarqa"
            cityName.contains("العقبة") -> return "aqaba"
        }
        data class City(val slug: String, val lat: Double, val lon: Double)
        val cities = listOf(
            City("amman", 31.9552, 35.9450),
            City("irbid", 32.5568, 35.8469),
            City("zarqa", 32.0728, 36.0880),
            City("aqaba", 29.5267, 35.0078),
            City("salt", 32.0392, 35.7272),
            City("madaba", 31.7167, 35.8000),
            City("jerash", 32.2808, 35.8993),
            City("mafraq", 32.3429, 36.2080),
            City("karak", 31.1800, 35.7048),
            City("maan", 30.1920, 35.7340)
        )
        val nearest = cities.minByOrNull { distanceKm(latitude, longitude, it.lat, it.lon) } ?: return null
        return nearest.slug.takeIf { distanceKm(latitude, longitude, nearest.lat, nearest.lon) <= 95.0 }
    }

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 6371.0 * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }

    private fun fetchMonth(country: String, place: String, year: Int, month: Int): List<RemoteDay> {
        val url = URL("https://prayertime.si/api/v1/month/$country/$place?year=$year&month=$month")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 12000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "NoorSalah-Android")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Prayer month service returned HTTP ${connection.responseCode}")
            }
            val root = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            val method = root.optJSONObject("method")
            val pageUrl = root.optString("page")
            val days = root.getJSONArray("days")
            return (0 until days.length()).map { index ->
                val entry = days.getJSONObject(index)
                val date = entry.getString("date")
                val times = entry.getJSONObject("times")
                val minutes = listOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha")
                    .map { parseTime(times.getString(it)) }
                val hijri = entry.optJSONObject("hijri")
                val hijriLabel = if (hijri != null) {
                    "${hijri.optInt("day")} ${hijri.optString("month_ar")} ${hijri.optInt("year")} هـ"
                } else ""
                RemoteDay(
                    date = date,
                    minutes = minutes,
                    hijriLabel = hijriLabel,
                    hijriJson = hijri?.toString().orEmpty(),
                    methodName = method?.optString("name_ar").orEmpty().ifBlank {
                        method?.optString("name_en").orEmpty().ifBlank { "طريقة البلد" }
                    },
                    methodStatus = method?.optString("status").orEmpty(),
                    authorityUrl = method?.optString("source_url").orEmpty(),
                    pageUrl = pageUrl
                )
            }
        } finally {
            connection.disconnect()
        }
    }

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
            connectTimeout = 8000
            readTimeout = 10000
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
                hijriJson = hijri?.toString().orEmpty(),
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

    internal fun parseTime(value: String): Int {
        val parts = value.trim().take(5).split(":")
        require(parts.size == 2)
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()
        require(hour in 0..23 && minute in 0..59)
        return hour * 60 + minute
    }

    private fun dateKey(date: Calendar): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date.time)

    internal fun isJordan(latitude: Double, longitude: Double): Boolean =
        (latitude in 30.0..33.5 && longitude in 35.35..39.4) ||
            (latitude in 29.0..30.0 && longitude in 34.8..36.0)
}
