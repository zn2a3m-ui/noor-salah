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
    private const val REFRESH_INTERVAL_MS = 24L * 60L * 60L * 1000L
    private val executor = Executors.newSingleThreadExecutor()
    private val refreshLock = Any()
    @Volatile private var refreshInFlight = false
    private data class PendingRefresh(
        val context: Context,
        val latitude: Double,
        val longitude: Double,
        val force: Boolean,
        val onUpdated: (() -> Unit)?
    )
    private var pendingRefresh: PendingRefresh? = null

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

    private data class JordanCity(
        val slug: String,
        val nameArabic: String,
        val latitude: Double,
        val longitude: Double
    )

    private val jordanCities = listOf(
        JordanCity("amman", "عمّان", 31.9552, 35.9450),
        JordanCity("irbid", "إربد", 32.5568, 35.8469),
        JordanCity("zarqa", "الزرقاء", 32.0728, 36.0880),
        JordanCity("aqaba", "العقبة", 29.5267, 35.0078),
        JordanCity("salt", "السلط", 32.0392, 35.7272),
        JordanCity("madaba", "مادبا", 31.7167, 35.8000),
        JordanCity("jerash", "جرش", 32.2808, 35.8993),
        JordanCity("mafraq", "المفرق", 32.3429, 36.2080),
        JordanCity("karak", "الكرك", 31.1800, 35.7048),
        JordanCity("maan", "معان", 30.1920, 35.7340),
        JordanCity("ajloun", "عجلون", 32.3333, 35.7520),
        JordanCity("tafileh", "الطفيلة", 30.8333, 35.6044)
    )

    fun nearestJordanCityName(latitude: Double, longitude: Double): String? {
        val nearest = jordanCities.minByOrNull {
            distanceKm(latitude, longitude, it.latitude, it.longitude)
        } ?: return null
        val distance = distanceKm(latitude, longitude, nearest.latitude, nearest.longitude)
        return when {
            distance <= 35.0 -> nearest.nameArabic
            distance <= 95.0 -> "بالقرب من ${nearest.nameArabic}"
            else -> null
        }
    }

    fun refreshIfNeeded(
        context: Context,
        latitude: Double,
        longitude: Double,
        force: Boolean = false,
        onUpdated: (() -> Unit)? = null
    ) {
        if (!latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("prayer_remote_consent", false)) return
        val selectedMethod = prefs.getString("calculation_method", "الأردن (18° / 18°)") ?: "الأردن (18° / 18°)"
        val methodId = methodIdFor(selectedMethod)
        val cachedMethodId = prefs.getString("prayer_remote_method_key", null)
        if (cachedMethodId != methodId) clearRemoteDayCache(prefs)
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
        val sameMethodAttempt = prefs.getString("prayer_remote_attempt_method_key", null) == methodId
        val hasWeekCached = dates.all { getTimes(appContext, it) != null }
        val now = System.currentTimeMillis()
        val lastFetch = prefs.getLong("prayer_remote_fetch_time", 0L)
        val fresh = now - lastFetch < REFRESH_INTERVAL_MS
        val lastAttempt = prefs.getLong("prayer_remote_last_attempt", 0L)
        val retryCooling = now - lastAttempt < 30L * 60L * 1000L

        if (!force && sameLocation && sameMethodAttempt && ((hasWeekCached && fresh) || (!hasWeekCached && retryCooling))) return
        synchronized(refreshLock) {
            if (refreshInFlight) {
                pendingRefresh = PendingRefresh(appContext, latitude, longitude, force, onUpdated)
                return
            }
            refreshInFlight = true
        }
        prefs.edit()
            .putString("prayer_remote_attempt_lat", latitude.toString())
            .putString("prayer_remote_attempt_lon", longitude.toString())
            .putString("prayer_remote_attempt_method_key", methodId)
            .putLong("prayer_remote_last_attempt", now)
            .apply()

        val placeSlug = if (methodId == "JORDAN" && isJordan(latitude, longitude)) {
            placeSlugFor(prefs, latitude, longitude)
        } else null
        executor.execute {
            var updated = 0
            var allSucceeded = true
            var cachePlaceSlug: String? = null
            try {
                if (!isCurrentMethod(prefs, methodId)) return@execute
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
                                if (isCurrentMethod(prefs, methodId)) {
                                    monthDays.forEach { storeRemoteDay(prefs, it) }
                                    if (monthDays.isNotEmpty()) cachePlaceSlug = placeSlug
                                    updated += monthDays.size
                                } else {
                                    allSucceeded = false
                                    request.cancel(true)
                                }
                            } catch (_: Exception) {
                                allSucceeded = false
                                request.cancel(true)
                            }
                        }
                    } finally {
                        fetchPool.shutdownNow()
                    }
                }
                if (isCurrentMethod(prefs, methodId) &&
                    (placeSlug == null || !allSucceeded || updated == 0)) {
                    // Fall back to coordinate-based daily data if a city month page is unavailable.
                    if (placeSlug != null) cachePlaceSlug = null
                    val timezone = TimeZone.getDefault().id
                    val requestLatitude = String.format(Locale.US, "%.2f", latitude).toDouble()
                    val requestLongitude = String.format(Locale.US, "%.2f", longitude).toDouble()
                    val fetchPool = Executors.newFixedThreadPool(dates.size)
                    try {
                        val requests = dates.map { date ->
                            fetchPool.submit<RemoteDay> {
                                fetchDay(requestLatitude, requestLongitude, timezone, date, methodId)
                            }
                        }
                        requests.forEach { request ->
                            try {
                                val remote = request.get(20, TimeUnit.SECONDS)
                                if (isCurrentMethod(prefs, methodId)) {
                                    storeRemoteDay(prefs, remote)
                                    updated++
                                } else {
                                    allSucceeded = false
                                    request.cancel(true)
                                }
                            } catch (_: Exception) {
                                allSucceeded = false
                                request.cancel(true)
                            }
                        }
                    } finally {
                        fetchPool.shutdownNow()
                    }
                }
                if (updated > 0 && isCurrentMethod(prefs, methodId)) {
                    val fetchTime = if (allSucceeded) System.currentTimeMillis()
                    else System.currentTimeMillis() - (REFRESH_INTERVAL_MS / 2)
                    prefs.edit()
                        .putString("prayer_remote_lat", latitude.toString())
                        .putString("prayer_remote_lon", longitude.toString())
                        .putString("prayer_remote_place_slug", cachePlaceSlug)
                        .putString("prayer_remote_method_key", methodId)
                        .putLong("prayer_remote_fetch_time", fetchTime)
                        .apply()
                    Handler(Looper.getMainLooper()).post {
                        PrayerAlarmScheduler.scheduleTodayAndTomorrow(appContext)
                        onUpdated?.invoke()
                    }
                }
            } finally {
                val next = synchronized(refreshLock) {
                    refreshInFlight = false
                    val queued = pendingRefresh
                    pendingRefresh = null
                    queued
                }
                if (next != null) {
                    refreshIfNeeded(
                        next.context,
                        next.latitude,
                        next.longitude,
                        next.force,
                        next.onUpdated
                    )
                }
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
        val methodId = methodIdFor(selectedMethod)
        if (methodId != "JORDAN" || !isJordan(latitude, longitude)) return
        if (prefs.getString("prayer_remote_method_key", null) != methodId) clearRemoteDayCache(prefs)
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
                    if (!isCurrentMethod(prefs, methodId)) return@execute
                    monthDays.forEach { storeRemoteDay(prefs, it) }
                    if (monthDays.isNotEmpty() && isCurrentMethod(prefs, methodId)) {
                        prefs.edit()
                            .putString("prayer_remote_lat", latitude.toString())
                            .putString("prayer_remote_lon", longitude.toString())
                            .putString("prayer_remote_place_slug", placeSlug)
                            .putString("prayer_remote_method_key", methodId)
                            .putString("prayer_remote_attempt_method_key", methodId)
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
        val methodId = methodIdFor(selectedMethod)
        if (prefs.getString("prayer_remote_method_key", null) != methodId) return null
        val currentLat = prefs.getString("city_lat", "31.9539")?.toDoubleOrNull() ?: 31.9539
        val currentLon = prefs.getString("city_lon", "35.9106")?.toDoubleOrNull() ?: 35.9106
        if (!currentLat.isFinite() || !currentLon.isFinite() ||
            currentLat !in -90.0..90.0 || currentLon !in -180.0..180.0) return null
        val cachedPlace = prefs.getString("prayer_remote_place_slug", null)
        val currentPlace = if (methodId == "JORDAN" && isJordan(currentLat, currentLon)) {
            placeSlugFor(prefs, currentLat, currentLon)
        } else null
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
            (0 until 6).map { values.getInt(it) }.takeIf { minutes ->
                minutes.all { it in 0..1439 } &&
                    minutes.zipWithNext().all { (first, second) -> first < second }
            }
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
        return when (status) {
            "fitted" -> "$method • مطابقة لجدول الجهة المنشور"
            "verified" -> "$method • موثقة من مصدر الجهة"
            "library" -> "$method • من مكتبة الطرق لدى المصدر"
            "unverified" -> "$method • لم تُتحقق مطابقته محلياً"
            else -> "$method • طريقة حسابية من المصدر"
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

    internal fun methodIdFor(label: String): String = when {
        label.startsWith("الأردن") -> "JORDAN"
        label.startsWith("رابطة") -> "MWL"
        label.startsWith("أم القرى") -> "UMM_AL_QURA"
        label.startsWith("الهيئة المصرية") -> "EGYPT"
        label.startsWith("أمريكا الشمالية") -> "ISNA"
        label.startsWith("ديانت تركيا") -> "DIYANET"
        label.startsWith("سنغافورة") -> "SINGAPORE"
        else -> "MWL"
    }

    private fun isCurrentMethod(prefs: android.content.SharedPreferences, methodId: String): Boolean {
        val selected = prefs.getString("calculation_method", "الأردن (18° / 18°)") ?: "الأردن (18° / 18°)"
        return methodIdFor(selected) == methodId
    }

    private fun clearRemoteDayCache(prefs: android.content.SharedPreferences) {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith("prayer_remote_times_") || it.startsWith("prayer_remote_hijri_") }
            .forEach { editor.remove(it) }
        editor.remove("prayer_remote_method_key")
            .remove("prayer_remote_method")
            .remove("prayer_remote_method_status")
            .remove("prayer_remote_authority_url")
            .remove("prayer_remote_page_url")
            .remove("prayer_remote_license")
            .remove("prayer_remote_place_slug")
            .remove("prayer_remote_fetch_time")
            .remove("prayer_remote_lat")
            .remove("prayer_remote_lon")
            .apply()
    }

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
        jordanCities.firstOrNull { cityName.contains(it.nameArabic) }?.let { return it.slug }
        val nearest = jordanCities.minByOrNull {
            distanceKm(latitude, longitude, it.latitude, it.longitude)
        } ?: return null
        return nearest.slug.takeIf {
            distanceKm(latitude, longitude, nearest.latitude, nearest.longitude) <= 95.0
        }
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
            val parsedDays = (0 until days.length()).map { index ->
                val entry = days.getJSONObject(index)
                val date = entry.getString("date")
                val times = entry.getJSONObject("times")
                val minutes = listOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha")
                    .map { parseTime(times.getString(it)) }
                require(isValidPrayerDay(date, year, month, minutes)) {
                    "Prayer timetable contains an invalid date or non-chronological times"
                }
                val hijri = entry.optJSONObject("hijri")
                    ?: throw IllegalStateException("Hijri date is missing from monthly timetable")
                require(hijri.optInt("day") in 1..30 &&
                    hijri.optInt("month") in 1..12 &&
                    hijri.optInt("year") in 1300..1600 &&
                    hijri.optString("month_ar").isNotBlank()) {
                    "Monthly timetable contains invalid Hijri metadata"
                }
                val hijriLabel = "${hijri.optInt("day")} ${hijri.optString("month_ar")} ${hijri.optInt("year")} هـ"
                RemoteDay(
                    date = date,
                    minutes = minutes,
                    hijriLabel = hijriLabel,
                    hijriJson = hijri.toString(),
                    methodName = method?.optString("name_ar").orEmpty().ifBlank {
                        method?.optString("name_en").orEmpty().ifBlank { "طريقة البلد" }
                    },
                    methodStatus = method?.optString("status").orEmpty(),
                    authorityUrl = method?.optString("source_url").orEmpty(),
                    pageUrl = pageUrl
                )
            }
            val expectedDays = Calendar.getInstance().apply {
                clear()
                set(year, month - 1, 1)
            }.getActualMaximum(Calendar.DAY_OF_MONTH)
            require(parsedDays.size == expectedDays) {
                "Monthly timetable must contain every day exactly once"
            }
            require(parsedDays.map { it.date }.distinct().size == expectedDays) {
                "Monthly timetable contains duplicate dates"
            }
            return parsedDays.sortedBy { it.date }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchDay(
        latitude: Double,
        longitude: Double,
        timezone: String,
        date: Calendar,
        methodId: String
    ): RemoteDay {
        val day = dateKey(date)
        val query = buildString {
            append("lat=").append(latitude)
            append("&lon=").append(longitude)
            append("&timezone=").append(URLEncoder.encode(timezone, "UTF-8"))
            if (isJordan(latitude, longitude)) append("&country_code=JO")
            append("&method=").append(URLEncoder.encode(methodId, "UTF-8"))
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

    internal fun isValidPrayerDay(
        date: String,
        expectedYear: Int,
        expectedMonth: Int,
        minutes: List<Int>
    ): Boolean {
        if (!date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return false
        val parsed = try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(date) ?: return false
        } catch (_: Exception) {
            return false
        }
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = parsed }
        return calendar.get(Calendar.YEAR) == expectedYear &&
            calendar.get(Calendar.MONTH) + 1 == expectedMonth &&
            minutes.size == 6 &&
            minutes.all { it in 0..1439 } &&
            minutes.zipWithNext().all { (first, second) -> first < second }
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
