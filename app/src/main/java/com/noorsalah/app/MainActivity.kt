package com.noorsalah.app

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Environment
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.text.style.ClickableSpan
import android.text.method.LinkMovementMethod
import android.text.TextPaint
import android.text.Spanned
import android.text.SpannableStringBuilder
import android.widget.*
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.content.pm.PackageManager
import android.Manifest
import android.location.Location
import android.location.LocationManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import java.util.concurrent.Executor
import kotlin.math.*
import ws.quran.qvp.*

class MainActivity : Activity(), SensorEventListener {
    private var bg = Color.rgb(5, 31, 26)
    private var panel = Color.rgb(15, 45, 36)
    private var panelAlt = Color.rgb(20, 55, 44)
    private var gold = Color.rgb(222, 185, 105)
    private var cream = Color.rgb(247, 243, 229)
    private var muted = Color.rgb(184, 199, 184)
    private var isLightTheme = false
    private val creamOnDark = Color.rgb(247, 243, 229)
    private val goldOnDark = Color.rgb(222, 185, 105)
    private val mutedOnDark = Color.rgb(184, 199, 184)

    private fun applyThemeColors() {
        isLightTheme = getPreferences(0).getString("app_theme", "dark") == "light"
        if (isLightTheme) {
            bg = Color.rgb(246, 249, 245)
            panel = Color.rgb(255, 255, 255)
            panelAlt = Color.rgb(233, 241, 234)
            gold = Color.rgb(151, 111, 42)
            cream = Color.rgb(29, 48, 39)
            muted = Color.rgb(98, 116, 102)
        } else {
            bg = Color.rgb(5, 31, 26)
            panel = Color.rgb(15, 45, 36)
            panelAlt = Color.rgb(20, 55, 44)
            gold = Color.rgb(222, 185, 105)
            cream = Color.rgb(247, 243, 229)
            muted = Color.rgb(184, 199, 184)
        }
    }
    private lateinit var content: LinearLayout
    private lateinit var navigationBar: LinearLayout
    private var mainScroll: ScrollView? = null
    private val quranRepository by lazy(LazyThreadSafetyMode.NONE) { QuranRepository(this) }
    private val quranMushafDataRepository by lazy(LazyThreadSafetyMode.NONE) { QuranMushafDataRepository(this) }
    private var quranMushafPage: QvpPage? = null
    private var quranMushafAtlas: QvpAtlas? = null
    private var quranMushafView: QvpPageView? = null
    private var quranMushafRequestId = 0
    private var quranSelectedAyahHighlight = 0
    private val quranContinuousPages = mutableMapOf<Int, QvpPage>()
    private val quranContinuousViews = mutableMapOf<Int, QvpPageView>()
    private val quranContinuousPending = mutableMapOf<Int, Int>()
    private var quranContinuousFrames: List<FrameLayout> = emptyList()
    private var quranContinuousCurrentPage = -1
    private var quranContinuousSelectedPage = -1
    private var quranContinuousSelectedHighlight = 0
    private var quranMushafOfflineStatusView: TextView? = null
    private var quranMushafOfflineDownloadButton: Button? = null
    private var quranFontSize = 29f
    private var adhanPreviewPlayer: MediaPlayer? = null
    private var quranAudioPlayer: MediaPlayer? = null
    private var quranAudioSurahNumber = -1
    private var quranAudioLastSurahNumber = -1
    private var quranAudioPreparing = false
    private var quranAudioStatusView: TextView? = null
    private var quranAudioStatusText = "جاهز للتلاوة"
    private var quranRepeat = false
    private var quranRepeatAyah = false
    private var quranAudioQueue: List<Int> = emptyList()
    private var quranAudioQueueIndex = 0
    private var quranAudioQueueStartGlobal = 1
    private var quranAudioQueueReciter: QuranReciter? = null
    private data class QuranReciter(val name: String, val edition: String, val bitrate: Int)
    private val quranReciters = listOf(
        QuranReciter("ماهر المعيقلي", "ar.mahermuaiqly", 128),
        QuranReciter("عبدالرحمن السديس", "ar.sudais", 192),
        QuranReciter("أحمد بن علي العجمي", "ar.ajamy", 128)
    )
    private lateinit var sensorManager: SensorManager
    private var latitude = 31.9539
    private var longitude = 35.9106
    private var locationRequestInFlight = false
    private var locationAccuracyMeters: Float? = null
    private var heading = 0f
    private var magneticDeclination = 0f
    private var hasCompassReading = false
    private var page = "home"
    private var count = 0
    private var selectedDhikr = "سُبْحَانَ اللهِ وَبِحَمْدِهِ"
    private var tasbeehTarget = 100
    private var vibrationEnabled = true
    private var selectedDateMillis = 0L
    private var calendarMonthMillis = System.currentTimeMillis()
    private var timeOffsetMinutes = 0
    private var selectedMuezzin = "المؤذن الافتراضي"
    private var calculationMethod = "الأردن (18° / 18°)"
    private var use24Hour = true
    private var cityName = "عمّان، الأردن"
    private var locationMode = "gps"
    private val clockHandler = Handler(Looper.getMainLooper())
    private var countdownView: TextView? = null
    private var nextPrayerView: TextView? = null
    private val clockTick = object : Runnable {
        override fun run() { if (page == "home") refreshPrayerCountdown(); clockHandler.postDelayed(this, 30000L) }
    }
    private var arrow: QiblaCompassView? = null
    private var compassInfo: TextView? = null
    private var countView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyThemeColors()
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = if (isLightTheme) {
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                (if (android.os.Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
        } else 0
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        latitude = getPreferences(0).getString("city_lat", "31.9539")?.toDoubleOrNull() ?: 31.9539
        longitude = getPreferences(0).getString("city_lon", "35.9106")?.toDoubleOrNull() ?: 35.9106
        updateMagneticDeclination()
        locationAccuracyMeters = getPreferences(0).getFloat("location_accuracy_m", 0f).takeIf { it > 0f }
        cityName = getPreferences(0).getString("city_name", cityName) ?: cityName
        locationMode = getPreferences(0).getString("location_mode", "gps") ?: "gps"
        calculationMethod = getPreferences(0).getString("calculation_method", calculationMethod) ?: calculationMethod
        if (calculationMethod == "الأردن (18° / 17°)") {
            calculationMethod = "الأردن (18° / 18°)"
            getPreferences(0).edit().putString("calculation_method", calculationMethod).apply()
        }
        use24Hour = getPreferences(0).getBoolean("use_24_hour", true)
        selectedDhikr = getPreferences(0).getString("selected_dhikr", "سُبْحَانَ اللهِ وَبِحَمْدِهِ") ?: "سُبْحَانَ اللهِ وَبِحَمْدِهِ"
        count = getPreferences(0).getInt("tasbeeh_count_$selectedDhikr", getPreferences(0).getInt("tasbeeh", 0))
        tasbeehTarget = getPreferences(0).getInt("tasbeeh_target", 100)
        vibrationEnabled = getPreferences(0).getBoolean("tasbeeh_vibration", true)
        selectedDateMillis = getPreferences(0).getLong("selected_date", 0L).let { if (it == 0L) System.currentTimeMillis() else it }
        timeOffsetMinutes = getPreferences(0).getInt("time_offset", 0)
        selectedMuezzin = getPreferences(0).getString("muezzin", "المؤذن الافتراضي") ?: "المؤذن الافتراضي"
        quranFontSize = getPreferences(0).getFloat("quran_font_size", 29f).coerceIn(24f, 36f)
        val shell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val scroll = ScrollView(this).apply { addView(content); isFillViewport = true }
        mainScroll = scroll
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply {
            gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = shape(Color.rgb(7, 39, 32), 22, Color.rgb(42, 91, 72))
            elevation = dp(10).toFloat()
        }
        listOf(Triple("⌂", "الرئيسية", "home"), Triple("◉", "القبلة", "qibla"),
            Triple("▤", "القرآن", "quran"), Triple("✧", "الأذكار", "azkar"),
            Triple("⋯", "المزيد", "settings")).forEach { item ->
            val selected = item.third == page
            val cell = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(dp(2), dp(5), dp(2), dp(5))
                tag = item.third
                setOnClickListener { showPage(item.third) }
            }
            val glyph = TextView(this@MainActivity).apply {
                text = item.first; textSize = 22f; gravity = Gravity.CENTER
                setTextColor(if (selected) gold else muted)
            }
            val label = TextView(this@MainActivity).apply {
                text = item.second; textSize = 10f; gravity = Gravity.CENTER
                setTextColor(if (selected) gold else muted)
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            cell.addView(glyph); cell.addView(label)
            nav.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
        }
        navigationBar = nav
        shell.addView(nav, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(10), dp(5), dp(10), dp(10)) })
        setContentView(shell)
        val restorePage = getPreferences(0).getString("page_after_theme", "home") ?: "home"
        getPreferences(0).edit().remove("page_after_theme").apply()
        if (restorePage == "calendar") calendarMonthMillis = selectedDateMillis
        showPage(restorePage)
        requestLocation()
        PrayerAlarmScheduler.createChannel(this)
        PrayerAlarmScheduler.scheduleTodayAndTomorrow(this)
        PrayerTimesRemoteRepository.refreshIfNeeded(this, latitude, longitude) {
            PrayerWidgetProvider.updateAll(this)
            if (!isFinishing && (page == "home" || page == "calendar")) showPage(page)
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        clockHandler.post(clockTick)
        PrayerAlarmScheduler.scheduleTodayAndTomorrow(this)
        PrayerWidgetProvider.updateAll(this)
        PrayerTimesRemoteRepository.refreshIfNeeded(this, latitude, longitude) {
            PrayerWidgetProvider.updateAll(this)
            if (!isFinishing && (page == "home" || page == "calendar")) showPage(page)
        }
    }
    override fun onBackPressed() {
        if (page != "home") {
            showPage("home")
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        clockHandler.removeCallbacks(clockTick)
        stopAdhanPreview()
        stopQuranAudio()
    }

    override fun onDestroy() {
        releaseQuranMushafPage()
        quranMushafAtlas?.close()
        quranMushafAtlas = null
        quranMushafDataRepository.close()
        super.onDestroy()
    }

    private fun previewAdhan() {
        stopAdhanPreview()
        if (selectedMuezzin.startsWith("تنبيه النظام")) {
            Toast.makeText(this, "هذا الخيار يستخدم صوت إشعار الهاتف", Toast.LENGTH_SHORT).show()
            return
        }
        val customUri = getPreferences(0).getString("custom_adhan_uri", null)
        if (selectedMuezzin.startsWith("صوت مخصص") && customUri == null) {
            Toast.makeText(this, "ملف الأذان المخصص غير متاح. اختره من جديد.", Toast.LENGTH_LONG).show()
            return
        }
        val player = try {
            if (selectedMuezzin.startsWith("صوت مخصص")) {
                MediaPlayer.create(this, Uri.parse(customUri!!))
            } else {
                val resource = if (selectedMuezzin.startsWith("أذان قصير")) R.raw.adhan_short else R.raw.adhan_full
                MediaPlayer.create(this, resource)
            }
        } catch (_: Exception) {
            null
        }
        if (player == null) {
            Toast.makeText(this, "تعذّر تشغيل ملف الأذان", Toast.LENGTH_SHORT).show()
            return
        }
        adhanPreviewPlayer = player
        val previewVolume = getPreferences(0).getInt("adhan_volume", 100).coerceIn(0, 100) / 100f
        player.setVolume(previewVolume, previewVolume)
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        player.setOnCompletionListener {
            it.release()
            if (adhanPreviewPlayer === it) adhanPreviewPlayer = null
        }
        player.start()
    }

    private fun stopAdhanPreview() {
        adhanPreviewPlayer?.let {
            try { if (it.isPlaying) it.stop() } catch (_: Exception) {}
            it.release()
        }
        adhanPreviewPlayer = null
    }

    private fun openAdhanFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, 702)
        } catch (_: Exception) {
            Toast.makeText(this, "تعذّر فتح اختيار الملفات", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 702 || resultCode != RESULT_OK) return
        val resultData = data ?: return
        val uri = resultData.data ?: return
        try {
            val readPermission = resultData.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, readPermission)
            contentResolver.openFileDescriptor(uri, "r")?.use { }
                ?: throw IllegalStateException("Audio file is unavailable")
            getPreferences(0).edit()
                .putString("custom_adhan_uri", uri.toString())
                .putString("muezzin", "صوت مخصص من الجهاز")
                .apply()
            selectedMuezzin = "صوت مخصص من الجهاز"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.deleteNotificationChannel(PrayerAlarmScheduler.CHANNEL_CUSTOM_ID)
            }
            PrayerAlarmScheduler.createChannel(this)
            Toast.makeText(this, "تم حفظ ملف الأذان المخصص", Toast.LENGTH_SHORT).show()
            showPage("settings")
        } catch (_: Exception) {
            Toast.makeText(this, "تعذّر حفظ الملف. اختر ملفاً صوتياً يمكن الوصول إليه.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showPage(target: String) {
        releaseQuranMushafPage()
        if (target != "quran") stopQuranAudio()
        page = target
        content.removeAllViews()
        updateNavigationSelection()
        when (target) {
            "home" -> home()
            "qibla" -> qiblaScreen()
            "azkar" -> azkar()
            "quran" -> quran()
            "calendar" -> hijriCalendar()
            "settings" -> settings()
        }
        PrayerWidgetProvider.updateAll(this)
    }

    private fun updateNavigationSelection() {
        if (!::navigationBar.isInitialized) return
        for (i in 0 until navigationBar.childCount) {
            val cell = navigationBar.getChildAt(i) as? LinearLayout ?: continue
            val selected = cell.tag == if (page == "calendar") "home" else page
            val glyph = cell.getChildAt(0) as? TextView
            val label = cell.getChildAt(1) as? TextView
            glyph?.setTextColor(if (selected) goldOnDark else mutedOnDark)
            label?.setTextColor(if (selected) goldOnDark else mutedOnDark)
            label?.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            cell.background = if (selected) shape(Color.rgb(20, 67, 49), 15, Color.rgb(89, 127, 82)) else null
        }
    }

    private fun home() {
        text("☾  نور الصلاة", 28f, gold, true, Gravity.CENTER)
        text("رفيقك إلى الصلاة والذكر", 13f, muted, false, Gravity.CENTER)
        gap(16)
        card {
            line("⌖  $cityName", 13f, muted, false, Gravity.CENTER)
            line(SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar")).format(Date(selectedDateMillis)), 15f, cream, true, Gravity.CENTER)
            val selectedCalendar = java.util.Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
            hijriDateForCalendar(selectedCalendar)?.let { hijri ->
                line("${hijri.day} ${hijri.monthArabic} ${hijri.year} هـ", 13f, gold, true, Gravity.CENTER)
                if (hijri.basis.contains("umm-al-qura")) {
                    line("تقويم أم القرى • قد يختلف عن الرؤية المحلية", 10f, muted, false, Gravity.CENTER)
                }
            }
            line("تقبّل الله طاعتكم", 11f, gold, false, Gravity.CENTER)
        }
        gap(10)
        val next = nextPrayerInfo()
        val hero = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(22), dp(20), dp(22))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(8, 92, 65), Color.rgb(4, 48, 39), Color.rgb(3, 31, 27))
            ).apply { cornerRadius = dp(25).toFloat(); setStroke(dp(1), goldOnDark) }
            elevation = dp(5).toFloat()
        }
        val eyebrow = TextView(this@MainActivity).apply {
            text = "الصلاة القادمة"; textSize = 13f; setTextColor(Color.rgb(222, 220, 190)); gravity = Gravity.CENTER
        }
        val prayerName = TextView(this@MainActivity).apply {
            text = next.first; textSize = 31f; setTextColor(goldOnDark); gravity = Gravity.CENTER
            typeface = Typeface.create("serif", Typeface.BOLD); setPadding(0, dp(5), 0, dp(2))
        }
        nextPrayerView = prayerName
        val timer = TextView(this@MainActivity).apply {
            text = countdownLabel(next.second); textSize = 32f; setTextColor(creamOnDark)
            gravity = Gravity.CENTER; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(0, dp(4), 0, dp(4))
        }
        countdownView = timer
        hero.addView(eyebrow); hero.addView(prayerName); hero.addView(timer)
        val sub = TextView(this@MainActivity).apply {
            text = "الوقت المتبقي لدخول الصلاة"; textSize = 11f; setTextColor(mutedOnDark); gravity = Gravity.CENTER
        }
        hero.addView(sub)
        content.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val heading = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this@MainActivity).apply {
            text = "مواقيت الصلاة"; textSize = 20f; setTextColor(gold); typeface = Typeface.DEFAULT_BOLD
        }
        heading.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val badge = TextView(this@MainActivity).apply {
            text = "اليوم"; textSize = 11f; setTextColor(cream); setPadding(dp(10), dp(5), dp(10), dp(5))
            background = shape(panelAlt, 20)
        }
        heading.addView(badge)
        content.addView(heading); gap(8)

        val names = listOf("الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء")
        val symbols = listOf("☼", "◌", "☀", "◐", "☾", "☽")
        val times = prayerTimes()
        val nextName = next.first
        names.forEachIndexed { i, name ->
            val active = name == nextName
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(15), dp(12), dp(15), dp(12))
                background = shape(if (active) Color.rgb(15, 76, 55) else if (i % 2 == 0) panel else panelAlt,
                    15, if (active) goldOnDark else null)
            }
            val icon = TextView(this@MainActivity).apply {
                text = symbols[i]; textSize = 20f; setTextColor(if (active) goldOnDark else gold); gravity = Gravity.CENTER
            }
            val n = TextView(this@MainActivity).apply {
                text = name; textSize = 15f; setTextColor(if (active) creamOnDark else cream)
                typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            val t = TextView(this@MainActivity).apply {
                text = times[i]; textSize = 16f; setTextColor(if (active) goldOnDark else cream)
                typeface = Typeface.DEFAULT_BOLD
            }
            row.addView(icon, LinearLayout.LayoutParams(dp(34), -2))
            row.addView(n, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(t)
            content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        }
        gap(2)
        val prayerSource = TextView(this@MainActivity).apply {
            text = PrayerTimesRemoteRepository.getMethodLabel(this@MainActivity) + "\n" +
                PrayerTimesRemoteRepository.getProviderLabel(this@MainActivity)
            textSize = 10f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(8))
            if (PrayerTimesRemoteRepository.getTimes(
                    this@MainActivity,
                    java.util.Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                ) != null) {
                setOnClickListener {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(PrayerTimesRemoteRepository.getSourceUrl(this@MainActivity))))
                    } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, "تعذّر فتح مصدر المواقيت", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        content.addView(prayerSource, LinearLayout.LayoutParams(-1, -2))
        val remotePrefs = getPreferences(0)
        if (calculationMethod.startsWith("الأردن") &&
            PrayerTimesRemoteRepository.isJordan(latitude, longitude) &&
            !remotePrefs.getBoolean("prayer_remote_consent", false) &&
            !remotePrefs.getBoolean("prayer_remote_prompt_dismissed", false)) {
            card {
                line("تحسين دقة مواقيت الصلاة", 16f, gold, true)
                line("يمكن جلب مواقيت الأردن بطريقة الحساب التي تنسبها خدمة prayertime.si إلى وزارة الأوقاف، وتذكر أنها طابقتها مع جدول الجهة المنشور؛ وقد تختلف دقيقة أو دقيقتين عن أذان المسجد. عند التفعيل يُرسل اسم المدينة أو إحداثيات تقريبية بعد تقريبها إلى منزلتين عشريتين، حسب طريقة جلب البيانات.", 12f, muted)
                addView(button("تفعيل التحديث عبر الإنترنت") {
                    remotePrefs.edit().putBoolean("prayer_remote_consent", true)
                        .putBoolean("prayer_remote_prompt_dismissed", true).apply()
                    PrayerTimesRemoteRepository.refreshIfNeeded(this@MainActivity, latitude, longitude, force = true) {
                        if (!isFinishing && (page == "home" || page == "calendar")) showPage(page)
                    }
                    Toast.makeText(this@MainActivity, "بدأ تحديث المواقيت", Toast.LENGTH_SHORT).show()
                    showPage("home")
                }, LinearLayout.LayoutParams(-1, -2))
                addView(button("المتابعة بالحساب المحلي") {
                    remotePrefs.edit().putBoolean("prayer_remote_consent", false)
                        .putBoolean("prayer_remote_prompt_dismissed", true).apply()
                    showPage("home")
                }, LinearLayout.LayoutParams(-1, -2))
            }
        }
        gap(5)
        val quickTitle = TextView(this@MainActivity).apply {
            text = "مساحتك الإيمانية"; textSize = 19f; setTextColor(gold); typeface = Typeface.DEFAULT_BOLD
        }
        content.addView(quickTitle); gap(8)
        val quickGrid = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        val quickRows = listOf(
            listOf(Triple("◉", "اتجاه القبلة", "qibla"), Triple("▤", "القرآن الكريم", "quran")),
            listOf(Triple("✧", "الأذكار", "azkar"), Triple("▦", "التقويم الهجري", "calendar"))
        )
        quickRows.forEach { items ->
            val r = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            items.forEach { item ->
                val tile = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                    setPadding(dp(8), dp(15), dp(8), dp(15))
                    background = shape(panel, 17, Color.rgb(42, 91, 72))
                    setOnClickListener {
                        if (item.third == "calendar") calendarMonthMillis = selectedDateMillis
                        showPage(item.third)
                    }
                }
                val symbol = TextView(this@MainActivity).apply {
                    text = item.first; textSize = 26f; setTextColor(gold); gravity = Gravity.CENTER
                }
                val caption = TextView(this@MainActivity).apply {
                    text = item.second; textSize = 12f; setTextColor(cream); gravity = Gravity.CENTER
                }
                tile.addView(symbol); tile.addView(caption)
                r.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (item != items.first()) marginStart = dp(7)
                })
            }
            quickGrid.addView(r, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
        }
        content.addView(quickGrid)
        card {
            line("ذِكر اليوم", 14f, gold, true, Gravity.CENTER)
            line("سُبْحَانَ اللهِ وَبِحَمْدِهِ", 21f, cream, true, Gravity.CENTER)
            line("سبحان الله العظيم", 12f, muted, false, Gravity.CENTER)
        }
        text("قد تختلف الحسابات الفلكية دقيقة أو دقيقتين عن أذان المسجد؛ ويُعتمد إعلان وزارة الأوقاف للمناسبات المرتبطة بالرؤية.", 11f, muted, false, Gravity.CENTER)
    }

    private fun localUmmAlQuraDate(date: java.util.Calendar): PrayerTimesRemoteRepository.HijriDate? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return null
        return try {
            val islamic = android.icu.util.IslamicCalendar(java.util.Date(date.timeInMillis)).apply {
                setCalculationType(android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA)
                timeInMillis = date.timeInMillis
            }
            val month = islamic.get(android.icu.util.Calendar.MONTH) + 1
            val monthNames = listOf(
                "محرّم", "صفر", "ربيع الأول", "ربيع الآخر",
                "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان",
                "رمضان", "شوّال", "ذو القعدة", "ذو الحجة"
            )
            PrayerTimesRemoteRepository.HijriDate(
                day = islamic.get(android.icu.util.Calendar.DAY_OF_MONTH),
                month = month,
                monthArabic = monthNames.getOrElse(month - 1) { "" },
                year = islamic.get(android.icu.util.Calendar.YEAR),
                basis = "local-umm-al-qura"
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun hijriDateForCalendar(date: java.util.Calendar): PrayerTimesRemoteRepository.HijriDate? {
        val offset = getPreferences(0).getInt("hijri_day_offset", 0).coerceIn(-2, 2)
        val adjustedDate = (date.clone() as java.util.Calendar).apply {
            add(java.util.Calendar.DAY_OF_YEAR, offset)
        }
        return PrayerTimesRemoteRepository.getHijriDate(this, adjustedDate)
            ?: localUmmAlQuraDate(adjustedDate)
    }

    private fun adjustHijriOffset(delta: Int) {
        val prefs = getPreferences(0)
        val current = prefs.getInt("hijri_day_offset", 0).coerceIn(-2, 2)
        val updated = (current + delta).coerceIn(-2, 2)
        if (updated == current) {
            Toast.makeText(this, "الحد الأقصى للتصحيح يومان", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit().putInt("hijri_day_offset", updated).apply()
        showPage("settings")
    }

    private fun changeCalendarMonth(offset: Int) {
        val previousSelection = java.util.Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        val month = java.util.Calendar.getInstance().apply {
            timeInMillis = calendarMonthMillis
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            add(java.util.Calendar.MONTH, offset)
            set(java.util.Calendar.DAY_OF_MONTH,
                previousSelection.get(java.util.Calendar.DAY_OF_MONTH).coerceAtMost(getActualMaximum(java.util.Calendar.DAY_OF_MONTH)))
        }
        calendarMonthMillis = month.timeInMillis
        selectedDateMillis = month.timeInMillis
        getPreferences(0).edit().putLong("selected_date", selectedDateMillis).apply()
        showPage("calendar")
    }

    private fun hijriEvent(date: PrayerTimesRemoteRepository.HijriDate): String? = when {
        date.month == 1 && date.day == 1 -> "رأس السنة الهجرية"
        date.month == 1 && date.day == 10 -> "يوم عاشوراء"
        date.month == 3 && date.day == 12 -> "ذكرى المولد النبوي • تاريخ تقريبي"
        date.month == 7 && date.day == 27 -> "ذكرى الإسراء والمعراج • تاريخ تقريبي"
        date.month == 8 && date.day == 15 -> "النصف من شعبان • تاريخ تقريبي"
        date.month == 9 && date.day == 1 -> "بداية رمضان • يتأكد بثبوت الهلال"
        date.month == 9 && date.day == 27 -> "ليلة 27 رمضان • ليلة مرجوة"
        date.month == 10 && date.day == 1 -> "عيد الفطر • يتأكد بثبوت الهلال"
        date.month == 12 && date.day == 9 -> "يوم عرفة"
        date.month == 12 && date.day == 10 -> "عيد الأضحى • يتأكد بثبوت الهلال"
        else -> null
    }

    private fun hijriCalendar() {
        val month = java.util.Calendar.getInstance().apply {
            timeInMillis = calendarMonthMillis
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 12)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val year = month.get(java.util.Calendar.YEAR)
        val monthIndex = month.get(java.util.Calendar.MONTH)
        val daysInMonth = month.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val firstOffset = (month.get(java.util.Calendar.DAY_OF_WEEK) - java.util.Calendar.SATURDAY + 7) % 7
        val title = SimpleDateFormat("MMMM yyyy", Locale("ar")).format(month.time)
        val hijriOffset = getPreferences(0).getInt("hijri_day_offset", 0).coerceIn(-2, 2)
        val adjustedHeaderDate = (month.clone() as java.util.Calendar).apply {
            add(java.util.Calendar.DAY_OF_YEAR, hijriOffset)
        }
        val remoteFirstHijri = PrayerTimesRemoteRepository.getHijriDate(this, adjustedHeaderDate)
        if (remoteFirstHijri == null) {
            PrayerTimesRemoteRepository.refreshMonthIfNeeded(
                this,
                latitude,
                longitude,
                adjustedHeaderDate.get(java.util.Calendar.YEAR),
                adjustedHeaderDate.get(java.util.Calendar.MONTH) + 1
            ) {
                if (!isFinishing && page == "calendar") showPage("calendar")
            }
        }
        val firstHijri = hijriDateForCalendar(month)

        card {
            line("التقويم الهجري والميلادي", 21f, gold, true, Gravity.CENTER)
            line(if (firstHijri != null) "${firstHijri.monthArabic} ${firstHijri.year} هـ" else "بيانات الشهر الهجري عند توفر المصدر", 14f, muted, false, Gravity.CENTER)
            val header = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            header.addView(button("السابق") { changeCalendarMonth(-1) }, LinearLayout.LayoutParams(0, -2, 1f))
            val monthTitle = TextView(this@MainActivity).apply {
                text = title
                textSize = 17f
                setTextColor(cream)
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            header.addView(monthTitle, LinearLayout.LayoutParams(0, -2, 2f))
            header.addView(button("التالي") { changeCalendarMonth(1) }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(header, LinearLayout.LayoutParams(-1, -2))
            addView(button("العودة إلى شهر اليوم") {
                calendarMonthMillis = System.currentTimeMillis()
                selectedDateMillis = System.currentTimeMillis()
                getPreferences(0).edit().putLong("selected_date", selectedDateMillis).apply()
                showPage("calendar")
            }, LinearLayout.LayoutParams(-1, -2))
        }

        val grid = GridLayout(this).apply {
            columnCount = 7
            useDefaultMargins = false
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }
        fun cellParams(height: Int) = GridLayout.LayoutParams().apply {
            width = 0
            this.height = dp(height)
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }
        val weekdays = listOf("السبت", "الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة")
        weekdays.forEach { weekday ->
            val label = TextView(this).apply {
                text = weekday
                textSize = 10f
                setTextColor(gold)
                gravity = Gravity.CENTER
                setPadding(dp(1), dp(6), dp(1), dp(6))
            }
            grid.addView(label, cellParams(34))
        }
        repeat(firstOffset) {
            grid.addView(TextView(this), cellParams(60))
        }
        val today = java.util.Calendar.getInstance()
        val selected = java.util.Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        for (day in 1..daysInMonth) {
            val date = (month.clone() as java.util.Calendar).apply {
                set(java.util.Calendar.DAY_OF_MONTH, day)
            }
            val hijri = hijriDateForCalendar(date)
            val isToday = today.get(java.util.Calendar.YEAR) == year &&
                today.get(java.util.Calendar.MONTH) == monthIndex &&
                today.get(java.util.Calendar.DAY_OF_MONTH) == day
            val isSelected = selected.get(java.util.Calendar.YEAR) == year &&
                selected.get(java.util.Calendar.MONTH) == monthIndex &&
                selected.get(java.util.Calendar.DAY_OF_MONTH) == day
            val dayCell = TextView(this).apply {
                val eventMarker = if (hijri != null && hijriEvent(hijri) != null) " •" else ""
                text = if (hijri != null) "$day\n${hijri.day}$eventMarker" else day.toString()
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(if (isSelected || isToday) goldOnDark else cream)
                typeface = if (isSelected || isToday) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                background = when {
                    isSelected -> shape(Color.rgb(31, 79, 57), 12, goldOnDark)
                    isToday -> shape(Color.rgb(23, 62, 47), 12, Color.rgb(107, 143, 93))
                    else -> shape(panel, 12, Color.rgb(39, 78, 62))
                }
                setOnClickListener {
                    selectedDateMillis = date.timeInMillis
                    calendarMonthMillis = month.timeInMillis
                    getPreferences(0).edit().putLong("selected_date", selectedDateMillis).apply()
                    showPage("calendar")
                }
            }
            grid.addView(dayCell, cellParams(60))
        }
        content.addView(grid, LinearLayout.LayoutParams(-1, -2))
        text("• يوم مناسبة دينية", 10f, gold, false, Gravity.CENTER)
        if (firstHijri == null) {
            text("إذا كان مصدر المواقيت مفعّلاً، يجري تحميل بيانات الشهر الهجري عند توفر الاتصال.", 11f, muted, false, Gravity.CENTER)
        }
        gap(8)

        val selectedDate = java.util.Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        val selectedHijri = hijriDateForCalendar(selectedDate)
        card {
            line("تفاصيل اليوم المحدد", 17f, gold, true)
            line(SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar")).format(selectedDate.time), 15f, cream, true)
            if (selectedHijri != null) {
                line("${selectedHijri.day} ${selectedHijri.monthArabic} ${selectedHijri.year} هـ", 16f, gold, true)
                if (selectedHijri.basis == "umm-al-qura") {
                    line("بحسب تقويم أم القرى • قد يختلف عن إعلان الرؤية المحلية", 11f, muted)
                }
                if (selectedHijri.basis == "local-umm-al-qura") {
                    line("تقويم أم القرى محسوب محلياً؛ قد يختلف عن إعلان رؤية الهلال في بلدك.", 11f, muted)
                }
                hijriEvent(selectedHijri)?.let { line(it, 14f, cream, true) }
            } else {
                line("التاريخ الهجري لهذا اليوم غير محفوظ. فعّل مصدر المواقيت عبر الإنترنت من الصفحة الرئيسية للحصول على بيانات الشهر.", 12f, muted)
                addView(button("الذهاب لتفعيل مصدر المواقيت") { showPage("home") }, LinearLayout.LayoutParams(-1, -2))
            }
            line(PrayerTimesRemoteRepository.getMethodLabel(this@MainActivity), 11f, muted)
            if (PrayerTimesRemoteRepository.getTimes(this@MainActivity, selectedDate) != null) {
                addView(button("مصدر البيانات • prayertime.si") {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(
                            PrayerTimesRemoteRepository.getSourceUrl(this@MainActivity)
                        )))
                    } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, "تعذّر فتح المصدر حالياً", Toast.LENGTH_SHORT).show()
                    }
                }, LinearLayout.LayoutParams(-1, -2))
            }
            val names = listOf("الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء")
            val times = prayerTimesMinutes()
            names.forEachIndexed { index, name ->
                line("$name  •  ${formatPrayerTime(times[index])}", 13f, cream)
            }
        }
        text("قد يختلف التقويم الهجري المحسوب يوماً عن الرؤية المحلية. إعلان وزارة الأوقاف الأردنية هو المرجع لبداية الأشهر والمناسبات المرتبطة بثبوت الهلال.", 11f, muted, false, Gravity.CENTER)
        content.addView(button("العودة إلى الرئيسية") { showPage("home") }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun qiblaScreen() {
        text("القبلة", 27f, gold, true, Gravity.CENTER)
        text("اتجاه دقيق إلى الكعبة المشرفة أينما كنت", 13f, muted, false, Gravity.CENTER)
        gap(14)
        card {
            line("اتجاه مكة المكرمة", 14f, muted, false, Gravity.CENTER)
            line("${"%.1f".format(Locale.US, qibla())}°", 30f, gold, true, Gravity.CENTER)

            val compassView = QiblaCompassView(this@MainActivity).apply {
                updateReadings(heading, qibla().toFloat(), hasCompassReading, isLightTheme)
                elevation = dp(5).toFloat()
            }
            arrow = compassView
            addView(compassView, LinearLayout.LayoutParams(dp(286), dp(286)).apply {
                gravity = Gravity.CENTER
                topMargin = dp(8)
                bottomMargin = dp(12)
            })

            val infoView = TextView(this@MainActivity).apply {
                text = when {
                    sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) == null ->
                        "مستشعر اتجاه الهاتف غير متاح على هذا الجهاز"
                    hasCompassReading ->
                        "الشمال الحقيقي: ${"%.0f".format(Locale.US, heading)}°  •  اتجاه القبلة: ${"%.1f".format(Locale.US, qibla())}°"
                    else -> "جارٍ تحديد اتجاه الهاتف…"
                }
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(cream)
                setPadding(0, dp(4), 0, dp(6))
            }
            compassInfo = infoView
            addView(infoView)
            line("تدور الجهات مع حركة الهاتف لتبقى مطابقة للشمال والشرق والجنوب والغرب الحقيقي.", 11f, muted, false, Gravity.CENTER)
            line("تُصحّح القراءة وفق الانحراف المغناطيسي لموقعك، وقد تتأثر بالمعادن والمغناطيس القريب.", 11f, muted, false, Gravity.CENTER)
        }
        gap(10)
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) == null) {
            text("لا يتوفر مستشعر اتجاه في هذا الجهاز؛ ستبقى زاوية القبلة الرقمية ظاهرة، لكن اتجاه البوصلة لن يتبع حركة الهاتف.", 12f, muted, false, Gravity.CENTER)
        }
        card {
            line("لأفضل دقة", 16f, gold, true)
            line("أمسك الهاتف أفقياً وحرّكه على شكل رقم 8 لمعايرة المستشعر. أبعده عن المعادن والمغناطيس ومكبرات الصوت.", 13f, muted)
        }
    }

    private fun azkar() {
        text("الأذكار والتسبيح", 27f, gold, true, Gravity.CENTER)
        text("لحظات ذكر تطمئن بها القلوب", 13f, muted, false, Gravity.CENTER)
        gap(12)
        var selectedCategory = getPreferences(0).getString("azkar_category", "الصباح") ?: "الصباح"
        val categoryItems = linkedMapOf(
            "الصباح" to listOf("أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ", "اللَّهُمَّ بِكَ أَصْبَحْنَا وَبِكَ أَمْسَيْنَا", "رَضِيتُ بِاللَّهِ رَبًّا وَبِالْإِسْلَامِ دِينًا وَبِمُحَمَّدٍ نَبِيًّا", "سُبْحَانَ اللهِ وَبِحَمْدِهِ"),
            "المساء" to listOf("أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ", "اللَّهُمَّ بِكَ أَمْسَيْنَا وَبِكَ أَصْبَحْنَا", "رَضِيتُ بِاللَّهِ رَبًّا وَبِالْإِسْلَامِ دِينًا وَبِمُحَمَّدٍ نَبِيًّا", "أَسْتَغْفِرُ اللهَ وَأَتُوبُ إِلَيْهِ"),
            "بعد الصلاة" to listOf("أَسْتَغْفِرُ اللهَ", "اللَّهُمَّ أَنْتَ السَّلَامُ وَمِنْكَ السَّلَامُ", "سُبْحَانَ اللهِ", "الْحَمْدُ لِلَّهِ", "اللهُ أَكْبَرُ", "لَا إِلَهَ إِلَّا اللهُ وَحْدَهُ لَا شَرِيكَ لَهُ"),
            "النوم" to listOf("بِاسْمِكَ اللَّهُمَّ أَمُوتُ وَأَحْيَا", "اللَّهُمَّ قِنِي عَذَابَكَ يَوْمَ تَبْعَثُ عِبَادَكَ"),
            "الاستيقاظ" to listOf("الْحَمْدُ لِلَّهِ الَّذِي أَحْيَانَا بَعْدَمَا أَمَاتَنَا وَإِلَيْهِ النُّشُورُ"),
            "الاستغفار" to listOf("أَسْتَغْفِرُ اللهَ وَأَتُوبُ إِلَيْهِ", "أَسْتَغْفِرُ اللهَ"),
            "الصلاة على النبي" to listOf("اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ وَعَلَى آلِ مُحَمَّدٍ، كَمَا صَلَّيْتَ عَلَى إِبْرَاهِيمَ وَعَلَى آلِ إِبْرَاهِيمَ، إِنَّكَ حَمِيدٌ مَجِيدٌ. اللَّهُمَّ بَارِكْ عَلَى مُحَمَّدٍ وَعَلَى آلِ مُحَمَّدٍ، كَمَا بَارَكْتَ عَلَى إِبْرَاهِيمَ وَعَلَى آلِ إِبْرَاهِيمَ، إِنَّكَ حَمِيدٌ مَجِيدٌ."),
            "أدعية" to listOf("رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً وَفِي الْآخِرَةِ حَسَنَةً وَقِنَا عَذَابَ النَّارِ", "رَبِّ زِدْنِي عِلْمًا", "اللَّهُمَّ إِنِّي أَسْأَلُكَ الْهُدَى وَالتُّقَى وَالْعَفَافَ وَالْغِنَى")
        )
        val azkarSources = mapOf(
            "أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ" to "صحيح مسلم",
            "اللَّهُمَّ بِكَ أَصْبَحْنَا وَبِكَ أَمْسَيْنَا" to "سنن أبي داود والترمذي",
            "رَضِيتُ بِاللَّهِ رَبًّا وَبِالْإِسْلَامِ دِينًا وَبِمُحَمَّدٍ نَبِيًّا" to "سنن أبي داود والترمذي",
            "سُبْحَانَ اللهِ وَبِحَمْدِهِ" to "صحيح البخاري ومسلم",
            "أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ" to "صحيح مسلم",
            "اللَّهُمَّ بِكَ أَمْسَيْنَا وَبِكَ أَصْبَحْنَا" to "سنن أبي داود والترمذي",
            "أَسْتَغْفِرُ اللهَ" to "صحيح مسلم • أذكار دبر الصلاة",
            "اللَّهُمَّ أَنْتَ السَّلَامُ وَمِنْكَ السَّلَامُ" to "صحيح مسلم",
            "سُبْحَانَ اللهِ" to "صحيح البخاري ومسلم • التسبيح بعد الصلاة",
            "الْحَمْدُ لِلَّهِ" to "صحيح البخاري ومسلم • التسبيح بعد الصلاة",
            "اللهُ أَكْبَرُ" to "صحيح البخاري ومسلم • التسبيح بعد الصلاة",
            "لَا إِلَهَ إِلَّا اللهُ وَحْدَهُ لَا شَرِيكَ لَهُ" to "صحيح مسلم",
            "بِاسْمِكَ اللَّهُمَّ أَمُوتُ وَأَحْيَا" to "صحيح البخاري",
            "اللَّهُمَّ قِنِي عَذَابَكَ يَوْمَ تَبْعَثُ عِبَادَكَ" to "سنن أبي داود والترمذي",
            "الْحَمْدُ لِلَّهِ الَّذِي أَحْيَانَا بَعْدَمَا أَمَاتَنَا وَإِلَيْهِ النُّشُورُ" to "صحيح البخاري",
            "أَسْتَغْفِرُ اللهَ وَأَتُوبُ إِلَيْهِ" to "صحيح البخاري",
            "اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ وَعَلَى آلِ مُحَمَّدٍ، كَمَا صَلَّيْتَ عَلَى إِبْرَاهِيمَ وَعَلَى آلِ إِبْرَاهِيمَ، إِنَّكَ حَمِيدٌ مَجِيدٌ. اللَّهُمَّ بَارِكْ عَلَى مُحَمَّدٍ وَعَلَى آلِ مُحَمَّدٍ، كَمَا بَارَكْتَ عَلَى إِبْرَاهِيمَ وَعَلَى آلِ إِبْرَاهِيمَ، إِنَّكَ حَمِيدٌ مَجِيدٌ." to "صحيح البخاري ومسلم",
            "رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً وَفِي الْآخِرَةِ حَسَنَةً وَقِنَا عَذَابَ النَّارِ" to "القرآن الكريم • البقرة: 201",
            "رَبِّ زِدْنِي عِلْمًا" to "القرآن الكريم • طه: 114",
            "اللَّهُمَّ إِنِّي أَسْأَلُكَ الْهُدَى وَالتُّقَى وَالْعَفَافَ وَالْغِنَى" to "صحيح مسلم"
        )
        val categoryStrip = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val categoryContent = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        fun renderCategory() {
            categoryContent.removeAllViews()
            categoryItems[selectedCategory]?.forEach { phrase ->
                val item = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(13), dp(12), dp(13), dp(12))
                    background = shape(panel, 15, Color.rgb(42, 91, 72))
                    setOnClickListener {
                        val prefs = getPreferences(0)
                        prefs.edit().putInt("tasbeeh_count_$selectedDhikr", count).apply()
                        selectedDhikr = phrase
                        count = prefs.getInt("tasbeeh_count_$phrase", 0)
                        prefs.edit().putString("selected_dhikr", phrase).putInt("tasbeeh", count).apply()
                        showPage("azkar")
                    }
                }
                val phraseView = TextView(this@MainActivity).apply {
                    text = phrase; textSize = 17f; setTextColor(cream); gravity = Gravity.CENTER
                }
                val hintView = TextView(this@MainActivity).apply {
                    text = if (phrase == selectedDhikr) "الذكر المحدد للمسبحة" else "اضغط لاستخدامه في المسبحة"
                    textSize = 11f; setTextColor(if (phrase == selectedDhikr) gold else muted); gravity = Gravity.CENTER
                    setPadding(0, dp(5), 0, 0)
                }
                item.addView(phraseView); item.addView(hintView)
                azkarSources[phrase]?.let { source ->
                    val sourceView = TextView(this@MainActivity).apply {
                        text = "المصدر: $source"
                        textSize = 10f
                        setTextColor(muted)
                        gravity = Gravity.CENTER
                        setPadding(0, dp(5), 0, 0)
                    }
                    item.addView(sourceView)
                }
                categoryContent.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
            }
        }
        val categoryButtons = linkedMapOf<String, TextView>()
        categoryItems.keys.toList().forEach { label ->
            val chip = TextView(this@MainActivity).apply {
                text = label; textSize = 11f; gravity = Gravity.CENTER
                setTextColor(cream); setPadding(dp(8), dp(10), dp(8), dp(10))
                setOnClickListener {
                    selectedCategory = label
                    getPreferences(0).edit().putString("azkar_category", label).apply()
                    categoryButtons.forEach { (name, view) ->
                        val active = name == selectedCategory
                        view.background = shape(if (active) Color.rgb(25, 91, 64) else panelAlt, 18,
                            if (active) goldOnDark else Color.rgb(48, 99, 75))
                        view.setTextColor(if (active) goldOnDark else cream)
                    }
                    renderCategory()
                }
            }
            categoryButtons[label] = chip
            categoryStrip.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
        }
        categoryButtons.forEach { (name, view) ->
            val active = name == selectedCategory
            view.background = shape(if (active) Color.rgb(25, 91, 64) else panelAlt, 18,
                if (active) goldOnDark else Color.rgb(48, 99, 75))
            view.setTextColor(if (active) goldOnDark else cream)
        }
        val categoryScroll = HorizontalScrollView(this@MainActivity).apply {
            isHorizontalScrollBarEnabled = false
            addView(categoryStrip, android.view.ViewGroup.LayoutParams(-2, -2))
        }
        content.addView(categoryScroll, LinearLayout.LayoutParams(-1, -2))
        gap(10)
        renderCategory()
        content.addView(categoryContent)
        gap(8)
        card {
            line("الذكر الحالي", 15f, muted, false, Gravity.CENTER)
            val dhikrText = TextView(this@MainActivity).apply {
                text = selectedDhikr; textSize = 21f; gravity = Gravity.CENTER
                setTextColor(cream); typeface = Typeface.DEFAULT_BOLD
            }
            addView(dhikrText, LinearLayout.LayoutParams(-1, -2))
            addView(button("اختيار الذكر") {
                val choices = arrayOf(
                    "سُبْحَانَ اللهِ وَبِحَمْدِهِ",
                    "أَسْتَغْفِرُ اللهَ وَأَتُوبُ إِلَيْهِ",
                    "اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى نَبِيِّنَا مُحَمَّد",
                    "لَا إِلَهَ إِلَّا اللهُ",
                    "سُبْحَانَ اللهِ، وَالْحَمْدُ لِلَّهِ، وَاللهُ أَكْبَر"
                )
                AlertDialog.Builder(this@MainActivity).setTitle("اختر الذكر").setItems(choices) { _, which ->
                    val prefs = getPreferences(0)
                    prefs.edit().putInt("tasbeeh_count_$selectedDhikr", count).apply()
                    selectedDhikr = choices[which]
                    count = prefs.getInt("tasbeeh_count_$selectedDhikr", 0)
                    prefs.edit().putString("selected_dhikr", selectedDhikr).putInt("tasbeeh", count).apply()
                    showPage("azkar")
                }.setNegativeButton("إلغاء", null).show()
            }, LinearLayout.LayoutParams(-1, -2))
            gap(8)
            val counterShell = FrameLayout(this@MainActivity).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.rgb(14, 82, 58), Color.rgb(5, 39, 32))
                ).apply {
                    shape = GradientDrawable.OVAL
                    setStroke(dp(2), Color.rgb(177, 143, 77))
                }
                elevation = dp(5).toFloat()
            }
            val counter = TextView(this@MainActivity).apply {
                text = count.toString(); textSize = 45f; gravity = Gravity.CENTER
                setTextColor(goldOnDark); typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            }
            countView = counter
            counterShell.addView(counter, FrameLayout.LayoutParams(-1, -1))
            addView(counterShell, LinearLayout.LayoutParams(dp(154), dp(154)).apply {
                gravity = Gravity.CENTER; topMargin = dp(10); bottomMargin = dp(10)
            })
            val progress = TextView(this@MainActivity).apply {
                text = "الهدف: $tasbeehTarget  •  المتبقي: ${(tasbeehTarget - (count % tasbeehTarget)) % tasbeehTarget}"
                textSize = 13f; gravity = Gravity.CENTER; setTextColor(muted)
            }
            addView(progress, LinearLayout.LayoutParams(-1, -2))
            addView(button("تغيير الهدف") {
                val targets = arrayOf("33", "100", "1000", "هدف مخصص")
                AlertDialog.Builder(this@MainActivity).setTitle("هدف التسبيح").setItems(targets) { _, which ->
                    if (which < 3) {
                        tasbeehTarget = listOf(33, 100, 1000)[which]
                        getPreferences(0).edit().putInt("tasbeeh_target", tasbeehTarget).apply()
                        showPage("azkar")
                    } else {
                        val field = EditText(this@MainActivity).apply {
                            inputType = android.text.InputType.TYPE_CLASS_NUMBER
                            hint = "اكتب الهدف"
                        }
                        AlertDialog.Builder(this@MainActivity).setTitle("هدف مخصص").setView(field)
                            .setPositiveButton("حفظ") { _, _ ->
                                val value = field.text.toString().toIntOrNull()
                                if (value != null && value in 1..100000) {
                                    tasbeehTarget = value
                                    getPreferences(0).edit().putInt("tasbeeh_target", value).apply()
                                    showPage("azkar")
                                } else Toast.makeText(this@MainActivity, "أدخل رقماً من 1 إلى 100000", Toast.LENGTH_SHORT).show()
                            }.setNegativeButton("إلغاء", null).show()
                    }
                }.setNegativeButton("إلغاء", null).show()
            }, LinearLayout.LayoutParams(-1, -2))
            addView(button("تسبيح  +") {
                count++
                getPreferences(0).edit().putInt("tasbeeh", count).putInt("tasbeeh_count_$selectedDhikr", count).apply()
                counter.text = count.toString()
                progress.text = "الهدف: $tasbeehTarget • المتبقي: ${(tasbeehTarget - (count % tasbeehTarget)) % tasbeehTarget}"
                if (vibrationEnabled) {
                    try {
                        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                        if (android.os.Build.VERSION.SDK_INT >= 26) {
                            vibrator.vibrate(android.os.VibrationEffect.createOneShot(25, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(25)
                        }
                    } catch (_: Exception) {}
                }
                if (count % tasbeehTarget == 0) {
                    Toast.makeText(this@MainActivity, "ما شاء الله! أتممت هدف $tasbeehTarget", Toast.LENGTH_LONG).show()
                }
            }, LinearLayout.LayoutParams(-1, -2))
            addView(button("تصفير العداد") {
                AlertDialog.Builder(this@MainActivity).setTitle("تصفير المسبحة؟")
                    .setMessage("سيتم تصفير العدد المحفوظ.")
                    .setNegativeButton("إلغاء", null)
                    .setPositiveButton("تصفير") { _, _ ->
                        count = 0
                        getPreferences(0).edit().putInt("tasbeeh", 0).putInt("tasbeeh_count_$selectedDhikr", 0).apply()
                        showPage("azkar")
                    }.show()
            }, LinearLayout.LayoutParams(-1, -2))
            val vibrationSwitch = Switch(this@MainActivity).apply {
                text = "اهتزاز خفيف عند التسبيح"
                setTextColor(cream); isChecked = vibrationEnabled
                setOnCheckedChangeListener { _, checked ->
                    vibrationEnabled = checked
                    getPreferences(0).edit().putBoolean("tasbeeh_vibration", checked).apply()
                }
            }
            addView(vibrationSwitch, LinearLayout.LayoutParams(-1, -2))
        }
        gap(12)
        card {
            line("أذكار مختارة", 18f, gold, true)
            line("أستغفر الله العظيم وأتوب إليه", 17f, cream)
            line("لا إله إلا الله وحده لا شريك له", 17f, cream)
            line("اللهم صل وسلم على نبينا محمد", 17f, cream)
            line("سبحان الله والحمد لله ولا إله إلا الله والله أكبر", 17f, cream)
        }
        text("الأذكار المعروضة مختارات أولية؛ سيُستكمل فهرس أذكار الصباح والمساء وما بعد الصلاة مع توثيق النصوص والمصادر.", 12f, muted)
    }

    private fun quran() {
        text("القرآن الكريم", 27f, gold, true, Gravity.CENTER)
        text("مصحف المدينة النبوية • رواية حفص عن عاصم", 13f, muted, false, Gravity.CENTER)
        gap(12)

        card {
            line("صفحات المصحف الأصلية", 17f, gold, true)
            val offlineStatus = TextView(this@MainActivity).apply {
                text = if (quranMushafDataRepository.isFullPackInstalled()) {
                    "تم حفظ صفحات المصحف الـ604 على الجهاز."
                } else {
                    "تُحمّل الصفحة عند فتحها وتُحفظ تلقائياً. لحفظ الصفحات الـ604 كلها: تنزيل نحو 44 ميغابايت، ومساحة مؤقتة تقارب 150 ميغابايت أثناء التثبيت، ثم نحو 93 ميغابايت للصفحات."
                }
                textSize = 12f
                setTextColor(muted)
            }
            quranMushafOfflineStatusView = offlineStatus
            addView(offlineStatus, LinearLayout.LayoutParams(-1, -2))
            val fullPackInstalled = quranMushafDataRepository.isFullPackInstalled()
            val downloadButton = button(
                if (fullPackInstalled) "صفحات المصحف محفوظة دون إنترنت"
                else "تنزيل صفحات المصحف كاملة دون إنترنت"
            ) {
                if (!quranMushafDataRepository.isFullPackInstalled()) downloadFullQuranMushafPack()
            }.apply { isEnabled = !fullPackInstalled }
            quranMushafOfflineDownloadButton = downloadButton
            addView(downloadButton, LinearLayout.LayoutParams(-1, -2))
        }
        gap(12)

        val surahs = try {
            quranRepository.loadSurahs()
        } catch (_: Exception) {
            text("تعذّر تحميل فهرس المصحف. أعد فتح الصفحة بعد التحقق من اكتمال ملفات التطبيق.", 14f, muted)
            return
        }
        val prefs = getPreferences(0)
        val lastSurah = prefs.getInt("quran_last_surah", 1).coerceIn(1, 114)
        val lastAyah = prefs.getInt("quran_last_ayah", 1).coerceAtLeast(1)
        val lastInfo = surahs[lastSurah - 1]

        card {
            line("متابعة القراءة", 18f, gold, true)
            line("سورة ${lastInfo.name} • الآية $lastAyah", 14f, cream)
            addView(button("متابعة من آخر موضع") {
                quranSurah(lastSurah, lastAyah.coerceAtMost(lastInfo.ayahCount))
            }, LinearLayout.LayoutParams(-1, -2))
        }
        gap(10)

        val savedBookmarks = prefs.getStringSet("quran_bookmarks_v1", emptySet())?.toList().orEmpty()
            .mapNotNull { value ->
                val parts = value.split(":")
                if (parts.size == 2) {
                    val surahNumber = parts[0].toIntOrNull()
                    val ayahNumber = parts[1].toIntOrNull()
                    if (surahNumber != null && ayahNumber != null) surahNumber to ayahNumber else null
                } else null
            }
            .sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })

        card {
            line("علاماتي المرجعية", 18f, gold, true)
            if (savedBookmarks.isEmpty()) {
                line("لم تحفظ علامات بعد. اضغط على رقم أي آية واختر حفظ علامة مرجعية.", 13f, muted)
            } else {
                savedBookmarks.forEach { bookmark ->
                    val bookmarkSurah = surahs.getOrNull(bookmark.first - 1) ?: return@forEach
                    addView(button("سورة ${bookmarkSurah.name} • الآية ${bookmark.second}") {
                        quranSurah(bookmark.first, bookmark.second)
                    }, LinearLayout.LayoutParams(-1, -2))
                }
            }
        }
        gap(10)

        card {
            line("البحث في آيات القرآن", 17f, gold, true)
            val query = EditText(this@MainActivity).apply {
                hint = "اكتب كلمة أو جزءاً من آية"
                textSize = 15f
                setTextColor(cream)
                setHintTextColor(muted)
                setSingleLine(true)
                textDirection = View.TEXT_DIRECTION_RTL
                background = shape(panelAlt, 12, Color.rgb(68, 103, 80))
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            addView(query, LinearLayout.LayoutParams(-1, -2))
            gap(7)
            addView(button("بحث في المصحف") {
                val value = query.text.toString().trim()
                if (value.length < 2) {
                    Toast.makeText(this@MainActivity, "اكتب حرفين على الأقل للبحث", Toast.LENGTH_SHORT).show()
                } else {
                    quranSearchResults(value)
                }
            }, LinearLayout.LayoutParams(-1, -2))
        }
        gap(10)
        card {
            line("انتقال سريع داخل المصحف", 17f, gold, true)
            addView(button("اختيار الجزء • 30 جزءاً") {
                chooseQuranJuz()
            }, LinearLayout.LayoutParams(-1, -2))
            addView(button("اختيار صفحة المصحف • 604 صفحات") {
                chooseQuranPage()
            }, LinearLayout.LayoutParams(-1, -2))
        }
        gap(10)
        text("فهرس السور • 114 سورة", 18f, gold, true)
        surahs.forEach { surah ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(13), dp(10), dp(13), dp(10))
                background = shape(panel, 14, Color.rgb(42, 91, 72))
                setOnClickListener { quranSurah(surah.number, 1) }
            }
            val number = TextView(this).apply {
                text = "%03d".format(Locale.US, surah.number)
                textSize = 13f
                setTextColor(gold)
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(6), dp(8), dp(6))
                background = shape(panelAlt, 10, Color.rgb(89, 127, 82))
            }
            val details = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, 0, 0)
            }
            details.addView(TextView(this).apply {
                text = surah.name
                textSize = 19f
                setTextColor(cream)
                typeface = Typeface.DEFAULT_BOLD
            })
            details.addView(TextView(this).apply {
                text = "${surah.ayahCount} آية • ${if (surah.revelationType == "Meccan") "مكية" else "مدنية"}"
                textSize = 11f
                setTextColor(muted)
            })
            row.addView(number, LinearLayout.LayoutParams(dp(48), -2))
            row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
        }

        gap(10)
        card {
            line("مصدر المصحف وحقوق النص", 16f, gold, true)
            line("نص تنزيل العثماني • الإصدار 1.1 • CC BY 3.0. النص محفوظ كما ورد من المصدر دون تعديل.", 12f, muted)
            addView(button("عرض شروط استخدام تنزيل") {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://tanzil.net/docs/Text_License")))
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "تعذّر فتح الرابط حالياً", Toast.LENGTH_SHORT).show()
                }
            }, LinearLayout.LayoutParams(-1, -2))
            line("التلاوات الصوتية تُبث من Islamic Network بإذن أصحاب الحقوق، ولا تُخزّن ضمن التطبيق.", 12f, muted)
            addView(button("شروط استخدام التلاوات الصوتية") {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://alquran.cloud/terms-and-conditions")))
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "تعذّر فتح الرابط حالياً", Toast.LENGTH_SHORT).show()
                }
            }, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun showQuranVerseActions(surahNumber: Int, surahName: String, ayahNumber: Int) {
        val prefs = getPreferences(0)
        val bookmarkKey = "$surahNumber:$ayahNumber"
        val isBookmarked = prefs.getStringSet("quran_bookmarks_v1", emptySet())
            ?.contains(bookmarkKey) == true
        val choices = if (isBookmarked) {
            arrayOf(
                "إزالة العلامة المرجعية",
                "حفظ موضع متابعة القراءة",
                "إلغاء"
            )
        } else {
            arrayOf(
                "حفظ علامة مرجعية",
                "حفظ موضع متابعة القراءة",
                "إلغاء"
            )
        }
        AlertDialog.Builder(this)
            .setTitle("سورة $surahName • الآية $ayahNumber")
            .setItems(choices) { dialog, which ->
                when (which) {
                    0 -> {
                        val bookmarks = prefs.getStringSet("quran_bookmarks_v1", emptySet())
                            ?.toMutableSet() ?: mutableSetOf()
                        if (isBookmarked) {
                            bookmarks.remove(bookmarkKey)
                            prefs.edit()
                                .putStringSet("quran_bookmarks_v1", bookmarks)
                                .apply()
                            refreshContinuousQuranPageForAyah(surahNumber, ayahNumber)
                            Toast.makeText(this, "تمت إزالة العلامة المرجعية", Toast.LENGTH_SHORT).show()
                        } else {
                            bookmarks.add(bookmarkKey)
                            prefs.edit()
                                .putStringSet("quran_bookmarks_v1", bookmarks)
                                .putInt("quran_last_surah", surahNumber)
                                .putInt("quran_last_ayah", ayahNumber)
                                .apply()
                            refreshContinuousQuranPageForAyah(surahNumber, ayahNumber)
                            Toast.makeText(this, "تم حفظ العلامة المرجعية", Toast.LENGTH_SHORT).show()
                        }
                    }
                    1 -> {
                        prefs.edit()
                            .putInt("quran_last_surah", surahNumber)
                            .putInt("quran_last_ayah", ayahNumber)
                            .apply()
                        Toast.makeText(this, "تم حفظ موضع متابعة القراءة", Toast.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun refreshContinuousQuranPageForAyah(surahNumber: Int, ayahNumber: Int) {
        val pageNumber = quranRepository.pageFor(surahNumber, ayahNumber)
        if (pageNumber !in 1..604 || !quranContinuousPages.containsKey(pageNumber)) return
        if (quranContinuousSelectedPage == pageNumber) {
            clearQuranContinuousSelection()
        }
        val page = quranContinuousPages.remove(pageNumber) ?: return
        val pageView = quranContinuousViews.remove(pageNumber)
        pageView?.page = null
        page.close()
        quranContinuousFrames.getOrNull(pageNumber - 1)?.removeAllViews()
        loadContinuousQuranPage(pageNumber, quranMushafRequestId)
    }

    private fun showQuranAyahAudioActions(surahNumber: Int, ayahNumber: Int) {
        val surahName = quranRepository.loadSurahs().getOrNull(surahNumber - 1)?.name ?: "السورة"
        val choices = arrayOf(
            "استماع الآية كاملة مرة واحدة",
            "تكرار الآية للحفظ حتى الإيقاف",
            "تشغيل متتابع من هذه الآية حتى نهاية القرآن",
            "استماع سورة $surahName كاملة من بدايتها",
            "إلغاء"
        )
        AlertDialog.Builder(this)
            .setTitle("سورة $surahName • الآية $ayahNumber")
            .setItems(choices) { dialog, which ->
                when (which) {
                    0 -> playQuranAyah(surahNumber, ayahNumber, false)
                    1 -> playQuranAyah(surahNumber, ayahNumber, true)
                    2 -> playQuranFromGlobalAyah(quranRepository.globalAyahNumber(surahNumber, ayahNumber))
                    3 -> playQuranSurah(surahNumber)
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun quranSurah(surahNumber: Int, focusAyah: Int = 1, pageOverride: Int? = null) {
        releaseQuranMushafPage()
        if (quranAudioSurahNumber != -1 && quranAudioSurahNumber != surahNumber) stopQuranAudio()
        if (quranAudioLastSurahNumber != -1 && quranAudioLastSurahNumber != surahNumber) {
            quranAudioLastSurahNumber = -1
            quranAudioStatusText = "جاهز للتلاوة"
        }
        val surah = try {
            quranRepository.loadSurahs().first { it.number == surahNumber }
        } catch (_: Exception) {
            Toast.makeText(this, "تعذّر فتح السورة", Toast.LENGTH_SHORT).show()
            return
        }
        val pageNumber = try {
            pageOverride ?: quranMushafAtlas?.pageOf(surahNumber, focusAyah)
                ?: quranRepository.pageFor(surahNumber, focusAyah)
        } catch (_: Exception) {
            Toast.makeText(this, "تعذّر تحديد صفحة الآية", Toast.LENGTH_SHORT).show()
            return
        }
        showContinuousQuranReader(surahNumber, focusAyah, pageNumber)
        return
        val ayahs = try {
            quranRepository.loadPageAyahs(pageNumber)
        } catch (_: Exception) {
            Toast.makeText(this, "تعذّر تحميل صفحة المصحف الموثقة", Toast.LENGTH_LONG).show()
            return
        }
        // Keep the reading position current whenever a page is opened.
        // The explicit verse action remains available for users who prefer manual saving.
        getPreferences(0).edit()
            .putInt("quran_last_surah", surahNumber)
            .putInt("quran_last_ayah", focusAyah)
            .apply()
        val pageStartSurah = quranMushafAtlas?.pageRange(pageNumber)?.first?.first
            ?: ayahs.first().surahNumber
        val pageFirstSurah = quranRepository.loadSurahs().first { it.number == pageStartSurah }
        quranFontSize = getPreferences(0).getFloat("quran_font_size", 29f).coerceIn(24f, 36f)

        content.removeAllViews()
        text("القرآن الكريم", 25f, gold, true, Gravity.CENTER)
        content.addView(button("العودة إلى فهرس السور") { showPage("quran") }, LinearLayout.LayoutParams(-1, -2))
        text("صفحة المصحف $pageNumber", 24f, cream, true, Gravity.CENTER)
        text("تبدأ من سورة ${pageFirstSurah.name} • 604 صفحة", 12f, muted, false, Gravity.CENTER)
        gap(8)

        text("المس كلمات الآية للاستماع أو التكرار، والمس رقم الآية لحفظ موضعك.", 12f, muted, false, Gravity.CENTER)
        gap(8)

        val savedPrefs = getPreferences(0)
        val savedSurah = savedPrefs.getInt("quran_last_surah", -1)
        val savedAyah = savedPrefs.getInt("quran_last_ayah", -1)
        val bookmarkedAyahs = savedPrefs.getStringSet("quran_bookmarks_v1", emptySet()) ?: emptySet()

        val mushafText = SpannableStringBuilder()
        var focusMarkerOffset: Int? = null
        ayahs.forEach { ayah ->
            mushafText.append(ayah.text)
            mushafText.append("  ")
            val marker = "﴿${ayah.number}﴾"
            val markerStart = mushafText.length
            if (ayah.surahNumber == surahNumber && ayah.number == focusAyah) {
                focusMarkerOffset = markerStart
            }
            mushafText.append(marker)
            mushafText.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    val ayahSurah = quranRepository.loadSurahs().first { it.number == ayah.surahNumber }
                    showQuranVerseActions(ayah.surahNumber, ayahSurah.name, ayah.number)
                }

                override fun updateDrawState(ds: TextPaint) {
                    ds.color = when {
                        bookmarkedAyahs.contains("${ayah.surahNumber}:${ayah.number}") -> Color.rgb(35, 125, 77)
                        ayah.surahNumber == savedSurah && ayah.number == savedAyah -> Color.rgb(139, 91, 28)
                        else -> Color.rgb(151, 111, 42)
                    }
                    ds.isUnderlineText = false
                    ds.isFakeBoldText = true
                }
            }, markerStart, markerStart + marker.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            mushafText.append("  ")
        }

        val mushafPage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(22))
            background = shape(Color.rgb(247, 241, 222), 18, Color.rgb(184, 145, 77))
            elevation = dp(2).toFloat()
        }
        if (ayahs.firstOrNull()?.number == 1) {
            mushafPage.addView(TextView(this).apply {
                text = "۞  ${pageFirstSurah.name}  ۞"
                textSize = 19f
                setTextColor(Color.rgb(103, 77, 35))
                typeface = Typeface.create("serif", Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(15))
            }, LinearLayout.LayoutParams(-1, -2))

            // In the Madani Mushaf, the basmala is a separate line at the
            // beginning of every surah except Al-Fatihah (already in ayah 1)
            // and At-Tawbah (which has no basmala).
            if (pageFirstSurah.number != 1 && pageFirstSurah.number != 9) {
                val verifiedBasmala = quranRepository.loadAyahs(1).first().text
                mushafPage.addView(TextView(this).apply {
                    text = verifiedBasmala
                    textSize = quranFontSize
                    setTextColor(Color.rgb(42, 43, 35))
                    gravity = Gravity.CENTER
                    textDirection = View.TEXT_DIRECTION_RTL
                    typeface = Typeface.create("serif", Typeface.NORMAL)
                    setPadding(dp(2), 0, dp(2), dp(12))
                }, LinearLayout.LayoutParams(-1, -2))
            }
        }
        val mushafTextView = TextView(this).apply {
            text = mushafText
            textSize = quranFontSize
            setTextColor(Color.rgb(42, 43, 35))
            gravity = Gravity.CENTER
            textDirection = View.TEXT_DIRECTION_RTL
            typeface = Typeface.create("serif", Typeface.NORMAL)
            setLineSpacing(dp(12).toFloat(), 1.25f)
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = Color.TRANSPARENT
            setPadding(dp(2), dp(5), dp(2), dp(5))
        }
        mushafPage.addView(mushafTextView, LinearLayout.LayoutParams(-1, -2))
        val mushafContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        mushafContainer.addView(mushafPage, LinearLayout.LayoutParams(-1, -2))
        content.addView(mushafContainer, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(6)
            bottomMargin = dp(12)
        })
        var focusView: View? = mushafContainer

        val nextRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        nextRow.addView(button("الصفحة السابقة") {
            if (pageNumber > 1) {
                val location = quranPageStart(pageNumber - 1)
                quranSurah(location.first, location.second)
            }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        nextRow.addView(button("الصفحة التالية") {
            if (pageNumber < 604) {
                val location = quranPageStart(pageNumber + 1)
                quranSurah(location.first, location.second)
            }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
        content.addView(nextRow, LinearLayout.LayoutParams(-1, -2))

        gap(8)

        card {
            line("التلاوة الصوتية للسورة", 17f, gold, true)
            val prefs = getPreferences(0)
            val savedEdition = prefs.getString("quran_reciter", "ar.mahermuaiqly") ?: "ar.mahermuaiqly"
            val selectedReciterIndex = quranReciters.indexOfFirst { it.edition == savedEdition }.coerceAtLeast(0)
            val reciterPicker = Spinner(this@MainActivity).apply {
                adapter = ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    quranReciters.map { it.name }
                )
                setSelection(selectedReciterIndex)
                onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val chosen = quranReciters[position]
                        if (prefs.getString("quran_reciter", "ar.mahermuaiqly") != chosen.edition) {
                            prefs.edit().putString("quran_reciter", chosen.edition).apply()
                            if (quranAudioSurahNumber == surahNumber) playQuranFromGlobalAyah(quranAudioQueueStartGlobal)
                        }
                    }
                }
            }
            addView(reciterPicker, LinearLayout.LayoutParams(-1, -2))
            val repeatSwitch = Switch(this@MainActivity).apply {
                text = "تكرار السورة بعد انتهائها"
                setTextColor(cream)
                isChecked = prefs.getBoolean("quran_repeat", false)
                quranRepeat = isChecked
                setOnCheckedChangeListener { _, checked ->
                    quranRepeat = checked
                    prefs.edit().putBoolean("quran_repeat", checked).apply()
                }
            }
            addView(repeatSwitch, LinearLayout.LayoutParams(-1, -2))
            addView(button("تشغيل / متابعة التلاوة") {
                toggleQuranAudio(surahNumber)
            }, LinearLayout.LayoutParams(-1, -2))
            addView(button("إيقاف التلاوة") {
                stopQuranAudio()
                Toast.makeText(this@MainActivity, "تم إيقاف التلاوة", Toast.LENGTH_SHORT).show()
            }, LinearLayout.LayoutParams(-1, -2))
            addView(button("تنزيل السورة للاستماع دون إنترنت") {
                downloadQuranSurah(surahNumber)
            }, LinearLayout.LayoutParams(-1, -2))

            quranAudioStatusView = TextView(this@MainActivity).apply {
                text = if (quranAudioLastSurahNumber == surahNumber) quranAudioStatusText else "جاهز للتلاوة"
                textSize = 12f
                setTextColor(muted)
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, dp(3))
            }
            addView(quranAudioStatusView, LinearLayout.LayoutParams(-1, -2))
            line("تعمل التلاوة عبر الإنترنت أو من الآيات المحفوظة على الجهاز دون إنترنت. يلزم الاتصال عند البث أو التنزيل لأول مرة؛ وحقوق التسجيل محفوظة لأصحابها.", 11f, muted)
        }
        loadQvpMushafPage(pageNumber, surahNumber, focusAyah, mushafContainer)
        mainScroll?.post {
            focusView?.let { mainScroll?.smoothScrollTo(0, it.top) }
        }
    }


    private fun showContinuousQuranReader(surahNumber: Int, focusAyah: Int, startPage: Int) {
        releaseQuranMushafPage()
        val token = quranMushafRequestId
        quranContinuousCurrentPage = startPage
        content.removeAllViews()
        text("القرآن الكريم", 25f, gold, true, Gravity.CENTER)
        content.addView(button("العودة إلى فهرس السور") { showPage("quran") }, LinearLayout.LayoutParams(-1, -2))
        val pageLabel = TextView(this).apply {
            text = "قراءة متواصلة في المصحف"
            textSize = 14f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(5))
        }
        content.addView(pageLabel, LinearLayout.LayoutParams(-1, -2))
        text("اسحب للأعلى لمواصلة القراءة. المس كلمات الآية للاستماع أو التكرار، والمس رقمها للحفظ.", 12f, muted, false, Gravity.CENTER)
        gap(8)
        card {
            line("التلاوة الصوتية للسورة", 17f, gold, true)
            val prefs = getPreferences(0)
            val savedEdition = prefs.getString("quran_reciter", "ar.mahermuaiqly") ?: "ar.mahermuaiqly"
            val selectedIndex = quranReciters.indexOfFirst { it.edition == savedEdition }.coerceAtLeast(0)
            val picker = Spinner(this@MainActivity).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, quranReciters.map { it.name })
                setSelection(selectedIndex)
                onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val chosen = quranReciters[position]
                        if (prefs.getString("quran_reciter", "ar.mahermuaiqly") != chosen.edition) {
                            prefs.edit().putString("quran_reciter", chosen.edition).apply()
                            if (quranAudioSurahNumber == surahNumber) playQuranSurah(surahNumber)
                        }
                    }
                }
            }
            addView(picker, LinearLayout.LayoutParams(-1, -2))
            val repeatSwitch = Switch(this@MainActivity).apply {
                text = "تكرار السورة بعد انتهائها"
                setTextColor(cream)
                isChecked = prefs.getBoolean("quran_repeat", false)
                quranRepeat = isChecked
                setOnCheckedChangeListener { _, checked ->
                    quranRepeat = checked
                    prefs.edit().putBoolean("quran_repeat", checked).apply()
                }
            }
            addView(repeatSwitch, LinearLayout.LayoutParams(-1, -2))
            addView(button("تشغيل / متابعة التلاوة") { toggleQuranAudio(surahNumber) }, LinearLayout.LayoutParams(-1, -2))
            addView(button("إيقاف التلاوة") {
                stopQuranAudio()
                Toast.makeText(this@MainActivity, "تم إيقاف التلاوة", Toast.LENGTH_SHORT).show()
            }, LinearLayout.LayoutParams(-1, -2))
            addView(button("تنزيل السورة للاستماع دون إنترنت") { downloadQuranSurah(surahNumber) }, LinearLayout.LayoutParams(-1, -2))
            quranAudioStatusView = TextView(this@MainActivity).apply {
                text = if (quranAudioLastSurahNumber == surahNumber) quranAudioStatusText else "جاهز للتلاوة"
                textSize = 12f
                setTextColor(muted)
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, dp(3))
            }
            addView(quranAudioStatusView, LinearLayout.LayoutParams(-1, -2))
            line("تعمل التلاوة عبر الإنترنت أو من الآيات المحفوظة على الجهاز دون إنترنت. يلزم الاتصال عند البث أو التنزيل لأول مرة؛ وحقوق التسجيل محفوظة لأصحابها.", 11f, muted)
        }
        val pageWidth = (resources.displayMetrics.widthPixels - dp(12)).coerceAtLeast(dp(280))
        val estimatedHeight = (pageWidth * 1.42f).roundToInt()
        val host = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val frames = (1..604).map { number ->
            FrameLayout(this).apply {
                setBackgroundColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(-1, estimatedHeight)
                contentDescription = "صفحة المصحف " + number
                host.addView(this)
            }
        }
        quranContinuousFrames = frames
        content.addView(host, LinearLayout.LayoutParams(-1, -2))
        var lastVisiblePage = -1
        fun refreshWindow(scrollY: Int) {
            if (token != quranMushafRequestId || isFinishing || isDestroyed) return
            val localY = (scrollY - host.top).coerceAtLeast(0)
            val index = frames.indexOfFirst { it.top + it.height > localY }.let { if (it < 0) 603 else it }
            val visiblePage = index + 1
            if (visiblePage == lastVisiblePage) return
            lastVisiblePage = visiblePage
            quranContinuousCurrentPage = visiblePage
            pageLabel.text = "قراءة متواصلة في المصحف"
            val location = quranPageStart(visiblePage)
            getPreferences(0).edit().putInt("quran_last_surah", location.first).putInt("quran_last_ayah", location.second).apply()
            for (number in (visiblePage - 1).coerceAtLeast(1)..(visiblePage + 2).coerceAtMost(604)) {
                loadContinuousQuranPage(number, token)
            }
            quranContinuousPages.keys.filter { abs(it - visiblePage) > 3 }.forEach { number ->
                quranContinuousViews.remove(number)?.page = null
                quranContinuousPages.remove(number)?.close()
                frames[number - 1].removeAllViews()
                if (quranContinuousSelectedPage == number) {
                    clearQuranContinuousSelection()
                }
            }
        }
        mainScroll?.setOnScrollChangeListener { _, _, y, _, _ -> refreshWindow(y) }
        mainScroll?.post {
            if (token != quranMushafRequestId) return@post
            mainScroll?.scrollTo(0, host.top + frames[startPage - 1].top)
            refreshWindow(mainScroll?.scrollY ?: 0)
        }
    }

    private fun clearQuranContinuousSelection() {
        val selectedPage = quranContinuousSelectedPage
        val selectedHighlight = quranContinuousSelectedHighlight
        if (selectedPage > 0 && selectedHighlight != 0) {
            quranContinuousPages[selectedPage]?.removeHighlight(selectedHighlight)
            quranContinuousViews[selectedPage]?.invalidate()
        }
        quranContinuousSelectedPage = -1
        quranContinuousSelectedHighlight = 0
    }

    private fun loadContinuousQuranPage(pageNumber: Int, token: Int) {
        if (pageNumber !in 1..604 || quranContinuousPages.containsKey(pageNumber) || quranContinuousPending.containsKey(pageNumber)) return
        quranContinuousPending[pageNumber] = token
        quranMushafDataRepository.loadPage(pageNumber) { result ->
            if (quranContinuousPending[pageNumber] == token) {
                quranContinuousPending.remove(pageNumber)
            }
            if (token != quranMushafRequestId || isFinishing || isDestroyed || abs(pageNumber - quranContinuousCurrentPage) > 3) {
                result.getOrNull()?.close()
                return@loadPage
            }
            result.onSuccess { loaded ->
                val ink = 0x111111FF.toInt()
                loaded.theme(QvpTheme(ink = ink, diacritics = ink, dots = ink, waqf = ink, sifr = ink, ayahMark = ink, numeral = ink, headers = ink))
                val bookmarks = getPreferences(0).getStringSet("quran_bookmarks_v1", emptySet()) ?: emptySet()
                bookmarks.forEach { key ->
                    val parts = key.split(":")
                    val s = parts.getOrNull(0)?.toIntOrNull()
                    val a = parts.getOrNull(1)?.toIntOrNull()
                    if (s != null && a != null && loaded.ayahs.any { it.surah == s && it.ayah == a }) {
                        loaded.highlight(s.toString() + ":" + a, QvpHighlightStyle(mode = HighlightMode.BAND, band = 0x238b50a0.toInt()))
                    }
                }
                val pageView = QvpPageView(this).apply {
                    paperColor = Color.WHITE
                    padTop = dp(5).toFloat()
                    padBottom = dp(5).toFloat()
                    padSide = dp(2).toFloat()
                    reflowBreaks = QvpBreaks.PRINTED
                    onWordTap = { word, _ ->
                        clearQuranContinuousSelection()
                        quranContinuousSelectedPage = pageNumber
                        quranContinuousSelectedHighlight = loaded.highlight(
                            word.surah.toString() + ":" + word.ayah,
                            QvpHighlightStyle(mode = HighlightMode.BAND, band = 0xFFD54F66.toInt())
                        )
                        quranContinuousViews[pageNumber]?.invalidate()
                        showQuranAyahAudioActions(word.surah, word.ayah)
                    }
                    onDecorationTap = { decoration, _ ->
                        if (decoration.ayah > 0) {
                            val name = quranRepository.loadSurahs().getOrNull(decoration.surah - 1)?.name ?: "القرآن الكريم"
                            showQuranVerseActions(decoration.surah, name, decoration.ayah)
                        }
                    }
                    contentDescription = "صفحة المصحف " + pageNumber
                    page = loaded
                }
                pageView.setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            val zoomed = pageView.zoom.step > 0 || pageView.zoom.zoom > 1.01f
                            pageView.parent?.requestDisallowInterceptTouchEvent(zoomed)
                        }
                        android.view.MotionEvent.ACTION_POINTER_DOWN -> pageView.parent?.requestDisallowInterceptTouchEvent(true)
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> pageView.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
                quranContinuousPages[pageNumber] = loaded
                quranContinuousViews[pageNumber] = pageView
                val frame = quranContinuousFrames.getOrNull(pageNumber - 1) ?: return@onSuccess
                frame.removeAllViews()
                val width = (resources.displayMetrics.widthPixels - dp(12)).coerceAtLeast(dp(280))
                frame.layoutParams = frame.layoutParams.apply { height = (width * loaded.height / loaded.width).roundToInt() }
                frame.addView(pageView, FrameLayout.LayoutParams(-1, -1))
            }.onFailure {
                quranContinuousFrames.getOrNull(pageNumber - 1)?.let { frame ->
                    frame.removeAllViews()
                    frame.addView(TextView(this).apply {
                        text = "تعذّر تحميل صفحة المصحف " + pageNumber + ". تحقق من الإنترنت ثم أعد المحاولة."
                        setTextColor(muted)
                        gravity = Gravity.CENTER
                    }, FrameLayout.LayoutParams(-1, -1))
                }
            }
        }
    }

    private fun releaseQuranMushafPage() {
        quranMushafRequestId++
        mainScroll?.setOnScrollChangeListener(null as View.OnScrollChangeListener?)
        quranContinuousViews.values.forEach { it.page = null }
        quranContinuousPages.values.forEach { it.close() }
        quranContinuousViews.clear()
        quranContinuousPages.clear()
        quranContinuousPending.clear()
        quranContinuousFrames = emptyList()
        quranContinuousCurrentPage = -1
        clearQuranContinuousSelection()
        if (quranSelectedAyahHighlight != 0) {
            quranMushafPage?.removeHighlight(quranSelectedAyahHighlight)
            quranSelectedAyahHighlight = 0
        }
        quranMushafView?.page = null
        quranMushafView = null
        quranMushafPage?.close()
        quranMushafPage = null
    }

    private fun loadQvpMushafPage(
        pageNumber: Int,
        surahNumber: Int,
        focusAyah: Int,
        container: LinearLayout
    ) {
        val requestId = quranMushafRequestId
        fun loadPageData() {
            quranMushafDataRepository.loadPage(pageNumber) { result ->
                if (requestId != quranMushafRequestId || isFinishing || isDestroyed) {
                    result.getOrNull()?.close()
                    return@loadPage
                }
                result.onSuccess { loaded ->
                quranMushafView?.page = null
                quranMushafPage?.close()
                quranMushafPage = loaded

                val ink = 0xFF111111.toInt()
                loaded.theme(QvpTheme(
                    ink = ink,
                    diacritics = ink,
                    dots = ink,
                    waqf = ink,
                    sifr = ink,
                    ayahMark = ink,
                    numeral = ink,
                    headers = ink
                ))

                val bookmarkStyle = QvpHighlightStyle(
                    mode = HighlightMode.BAND,
                    band = 0x238b50a0.toInt()
                )
                val currentStyle = QvpHighlightStyle(
                    mode = HighlightMode.BAND,
                    band = 0xd6a32690.toInt()
                )
                val savedBookmarks = getPreferences(0)
                    .getStringSet("quran_bookmarks_v1", emptySet()) ?: emptySet()
                savedBookmarks.forEach { key ->
                    val parts = key.split(":")
                    val savedSurah = parts.getOrNull(0)?.toIntOrNull()
                    val savedAyah = parts.getOrNull(1)?.toIntOrNull()
                    if (savedSurah != null && savedAyah != null &&
                        loaded.ayahs.any { it.surah == savedSurah && it.ayah == savedAyah }) {
                        loaded.highlight("$savedSurah:$savedAyah", bookmarkStyle)
                    }
                }
                if (loaded.ayahs.any { it.surah == surahNumber && it.ayah == focusAyah }) {
                    loaded.highlight("$surahNumber:$focusAyah", currentStyle)
                }

                val pageView = QvpPageView(this).apply {
                    onSwipe = { direction ->
                        val targetPage = pageNumber + direction
                        if (targetPage in 1..604) {
                            val location = quranPageStart(targetPage)
                            quranSurah(location.first, location.second)
                        }
                    }
                    paperColor = Color.WHITE
                    padTop = dp(5).toFloat()
                    padBottom = dp(5).toFloat()
                    padSide = dp(2).toFloat()
                    reflowBreaks = QvpBreaks.PRINTED
                    onWordTap = { word, _ ->
                        if (quranSelectedAyahHighlight != 0) {
                            loaded.removeHighlight(quranSelectedAyahHighlight)
                        }
                        quranSelectedAyahHighlight = loaded.highlight(
                            "${word.surah}:${word.ayah}",
                            QvpHighlightStyle(
                                mode = HighlightMode.BAND,
                                band = 0x238b50a0.toInt()
                            )
                        )
                        showQuranAyahAudioActions(word.surah, word.ayah)
                    }
                    onDecorationTap = { decoration, _ ->
                        if (decoration.ayah > 0) {
                            val name = quranRepository.loadSurahs()
                                .getOrNull(decoration.surah - 1)?.name ?: "القرآن الكريم"
                            showQuranVerseActions(decoration.surah, name, decoration.ayah)
                        }
                    }
                    contentDescription = "صفحة المصحف $pageNumber"
                    page = loaded
                }
                pageView.setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            val zoomed = pageView.zoom.step > 0 || pageView.zoom.zoom > 1.01f
                            pageView.parent?.requestDisallowInterceptTouchEvent(zoomed)
                        }
                        android.view.MotionEvent.ACTION_POINTER_DOWN ->
                            pageView.parent?.requestDisallowInterceptTouchEvent(true)
                        android.view.MotionEvent.ACTION_UP,
                        android.view.MotionEvent.ACTION_CANCEL ->
                            pageView.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
                quranMushafView = pageView
                container.removeAllViews()
                val availableWidth = (resources.displayMetrics.widthPixels - dp(36)).coerceAtLeast(dp(280))
                val pageHeight = (availableWidth * loaded.height / loaded.width).roundToInt()
                container.addView(pageView, LinearLayout.LayoutParams(-1, pageHeight))
                }.onFailure {
                    Toast.makeText(
                        this,
                        "تعذّر تحميل صفحة المصحف الأصلية الآن. بقي النص الاحتياطي ظاهراً؛ تحقق من الإنترنت ثم أعد فتح الصفحة.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        val existingAtlas = quranMushafAtlas
        if (existingAtlas != null) {
            val atlasPage = existingAtlas.pageOf(surahNumber, focusAyah)
            if (atlasPage != null && atlasPage != pageNumber) {
                quranSurah(surahNumber, focusAyah, atlasPage)
            } else {
                loadPageData()
            }
        } else {
            quranMushafDataRepository.loadAtlas { atlasResult ->
                if (requestId != quranMushafRequestId || isFinishing || isDestroyed) {
                    atlasResult.getOrNull()?.close()
                    return@loadAtlas
                }
                atlasResult.onSuccess { atlas ->
                    quranMushafAtlas = atlas
                    val atlasPage = atlas.pageOf(surahNumber, focusAyah)
                    if (atlasPage != null && atlasPage != pageNumber) {
                        quranSurah(surahNumber, focusAyah, atlasPage)
                    } else {
                        loadPageData()
                    }
                }.onFailure {
                    loadPageData()
                }
            }
        }
    }

    private fun quranPageStart(pageNumber: Int): Pair<Int, Int> =
        quranMushafAtlas?.pageRange(pageNumber)?.first ?: quranRepository.pageStart(pageNumber)

    private fun adjustQuranMushafZoom(zoomIn: Boolean, surahNumber: Int, focusAyah: Int) {
        val view = quranMushafView
        val steps = view?.zoomSteps
        if (view != null && steps != null && steps.isNotEmpty()) {
            val next = (view.zoom.step + if (zoomIn) 1 else -1).coerceIn(0, steps.lastIndex)
            view.zoomToStep(next)
            return
        }
        quranFontSize = if (zoomIn) {
            (quranFontSize + 2f).coerceAtMost(36f)
        } else {
            (quranFontSize - 2f).coerceAtLeast(18f)
        }
        getPreferences(0).edit().putFloat("quran_font_size", quranFontSize).apply()
        quranSurah(surahNumber, focusAyah)
    }

    private fun downloadFullQuranMushafPack() {
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        val status = TextView(this).apply {
            text = "جارٍ تجهيز تنزيل صفحات المصحف…"
            textSize = 14f
            setTextColor(cream)
            setPadding(0, dp(8), 0, dp(8))
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(10))
            addView(status, LinearLayout.LayoutParams(-1, -2))
            addView(progress, LinearLayout.LayoutParams(-1, dp(24)))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("حفظ المصحف للعمل دون إنترنت")
            .setView(panel)
            .setNegativeButton("إخفاء", null)
            .create()
        dialog.setCancelable(false)
        dialog.show()
        quranMushafDataRepository.downloadFullPack(
            onProgress = { percent ->
                progress.progress = percent
                status.text = if (percent >= 99) {
                    "تم تنزيل الملف، جارٍ فك الضغط والتحقق من الصفحات…"
                } else {
                    "تنزيل صفحات المصحف: $percent٪"
                }
            },
            callback = { result ->
                if (isFinishing || isDestroyed) return@downloadFullPack
                if (dialog.isShowing) dialog.dismiss()
                result.onSuccess {
                    quranMushafOfflineStatusView?.text = "تم حفظ صفحات المصحف الـ604 على الجهاز."
                    quranMushafOfflineDownloadButton?.apply {
                        text = "صفحات المصحف محفوظة دون إنترنت"
                        isEnabled = false
                    }
                    Toast.makeText(this, "اكتمل حفظ المصحف. أصبح متاحاً دون إنترنت.", Toast.LENGTH_LONG).show()
                }.onFailure { error ->
                    Toast.makeText(
                        this,
                        error.message ?: "تعذّر تنزيل صفحات المصحف. حاول مرة أخرى.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    private fun playQuranAyah(surahNumber: Int, ayahNumber: Int, repeat: Boolean) {
        val reciterId = getPreferences(0).getString("quran_reciter", "ar.mahermuaiqly") ?: "ar.mahermuaiqly"
        val reciter = quranReciters.firstOrNull { it.edition == reciterId } ?: quranReciters.first()
        val globalNumber = try {
            quranRepository.globalAyahNumber(surahNumber, ayahNumber)
        } catch (_: Exception) {
            Toast.makeText(this, "تعذّر تحديد ملف الآية الصوتي", Toast.LENGTH_SHORT).show()
            return
        }
        stopQuranAudio()
        quranRepeatAyah = repeat
        val media = MediaPlayer()
        quranAudioPlayer = media
        quranAudioSurahNumber = -2
        quranAudioLastSurahNumber = surahNumber
        quranAudioPreparing = true
        setQuranAudioStatus(if (repeat) "جارٍ تجهيز تكرار الآية للحفظ…" else "جارٍ تجهيز تلاوة الآية…")
        try {
            media.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            val url = "https://cdn.islamic.network/quran/audio/${reciter.bitrate}/${reciter.edition}/$globalNumber.mp3"
            media.setDataSource(url)
            media.setOnPreparedListener { prepared ->
                if (quranAudioPlayer === prepared) {
                    quranAudioPreparing = false
                    prepared.start()
                    setQuranAudioStatus(if (repeat) "تتكرر الآية للحفظ بصوت ${reciter.name}" else "تُتلى الآية بصوت ${reciter.name}")
                }
            }
            media.setOnCompletionListener { completed ->
                if (quranAudioPlayer !== completed) return@setOnCompletionListener
                if (quranRepeatAyah) {
                    try {
                        completed.seekTo(0)
                        completed.start()
                        setQuranAudioStatus("إعادة الآية للحفظ")
                    } catch (_: Exception) {
                        stopQuranAudio()
                    }
                } else {
                    stopQuranAudio()
                    setQuranAudioStatus("انتهى الاستماع للآية")
                }
            }
            media.setOnErrorListener { failed, _, _ ->
                if (quranAudioPlayer === failed) {
                    stopQuranAudio()
                    setQuranAudioStatus("تعذّر تحميل الآية")
                    Toast.makeText(this, "تعذّر تحميل الآية. تحقق من اتصال الإنترنت.", Toast.LENGTH_LONG).show()
                }
                true
            }
            media.prepareAsync()
        } catch (_: Exception) {
            stopQuranAudio()
            Toast.makeText(this, "تعذّر تشغيل الآية حالياً", Toast.LENGTH_LONG).show()
        }
    }

    private fun toggleQuranAudio(surahNumber: Int) {
        val current = quranAudioPlayer
        if (current != null && quranAudioSurahNumber == surahNumber && !quranAudioPreparing) {
            try {
                if (current.isPlaying) {
                    current.pause()
                    setQuranAudioStatus("التلاوة متوقفة مؤقتاً")
                    Toast.makeText(this, "تم إيقاف التلاوة مؤقتاً", Toast.LENGTH_SHORT).show()
                } else {
                    current.start()
                    setQuranAudioStatus("التلاوة تعمل الآن")
                    Toast.makeText(this, "متابعة التلاوة", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                playQuranSurah(surahNumber)
            }
        } else {
            playQuranSurah(surahNumber)
        }
    }

    private fun quranAyahAudioFile(edition: String, globalAyahNumber: Int): File? {
        val directory = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: return null
        return File(directory, "noor_quran_${edition}_ayah_${globalAyahNumber}.mp3")
    }

    private fun downloadQuranSurah(surahNumber: Int) {
        val reciterId = getPreferences(0).getString("quran_reciter", "ar.mahermuaiqly") ?: "ar.mahermuaiqly"
        val reciter = quranReciters.firstOrNull { it.edition == reciterId } ?: quranReciters.first()
        val surah = quranRepository.loadSurahs().getOrNull(surahNumber - 1)
        if (surah == null) {
            Toast.makeText(this, "تعذّر تحديد السورة للتنزيل", Toast.LENGTH_LONG).show()
            return
        }
        val directory = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        if (directory == null) {
            Toast.makeText(this, "مساحة التخزين غير متاحة حالياً", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            var queued = 0
            var alreadySaved = 0
            for (ayahNumber in 1..surah.ayahCount) {
                val globalNumber = quranRepository.globalAyahNumber(surahNumber, ayahNumber)
                val target = quranAyahAudioFile(reciter.edition, globalNumber) ?: continue
                if (target.exists() && target.length() > 0L) {
                    alreadySaved++
                    continue
                }
                val url = "https://cdn.islamic.network/quran/audio/${reciter.bitrate}/${reciter.edition}/$globalNumber.mp3"
                val request = DownloadManager.Request(Uri.parse(url))
                    .setTitle("تنزيل سورة ${surah.name} • الآية $ayahNumber")
                    .setDescription("القارئ: ${reciter.name} • نور الصلاة")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(false)
                    .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_MUSIC, target.name)
                manager.enqueue(request)
                queued++
            }
            val message = when {
                queued == 0 && alreadySaved == surah.ayahCount -> "السورة محفوظة بالفعل للاستماع دون إنترنت"
                queued > 0 -> "بدأ تنزيل $queued آية من سورة ${surah.name}. سيكتمل الحفظ بعد انتهاء التنزيلات."
                else -> "لم يبدأ التنزيل. تحقق من مساحة التخزين."
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, "تعذّر بدء تنزيل آيات السورة. تحقق من اتصال الإنترنت ومساحة التخزين.", Toast.LENGTH_LONG).show()
        }
    }

    private fun playQuranSurah(surahNumber: Int) {
        val globalStart = try {
            quranRepository.globalAyahNumber(surahNumber, 1)
        } catch (_: Exception) {
            Toast.makeText(this, "تعذّر تحديد السورة", Toast.LENGTH_LONG).show()
            return
        }
        playQuranFromGlobalAyah(globalStart)
    }

    private fun playQuranFromGlobalAyah(globalStart: Int) {
        val surahs = quranRepository.loadSurahs()
        if (globalStart !in 1..6236) {
            Toast.makeText(this, "تعذّر تحديد موضع بداية التلاوة", Toast.LENGTH_LONG).show()
            return
        }
        val startSurah = surahs.lastOrNull { it.startLine < globalStart }
        if (startSurah == null) {
            Toast.makeText(this, "تعذّر تحديد السورة", Toast.LENGTH_LONG).show()
            return
        }
        val startAyah = globalStart - startSurah.startLine
        val reciterId = getPreferences(0).getString("quran_reciter", "ar.mahermuaiqly") ?: "ar.mahermuaiqly"
        val reciter = quranReciters.firstOrNull { it.edition == reciterId } ?: quranReciters.first()
        quranRepeat = getPreferences(0).getBoolean("quran_repeat", false)
        releaseQuranAudioPlayer()
        quranAudioQueue = (globalStart..6236).toList()
        quranAudioQueueIndex = 0
        quranAudioQueueStartGlobal = globalStart
        quranAudioQueueReciter = reciter
        quranAudioSurahNumber = startSurah.number
        quranAudioLastSurahNumber = startSurah.number
        setQuranAudioStatus("تبدأ التلاوة من سورة ${startSurah.name} • الآية $startAyah بصوت ${reciter.name}")
        playCurrentQuranQueueAyah()
    }

    private fun playCurrentQuranQueueAyah() {
        val reciter = quranAudioQueueReciter ?: return
        val globalNumber = quranAudioQueue.getOrNull(quranAudioQueueIndex) ?: return
        val currentSurah = quranRepository.loadSurahs().lastOrNull { it.startLine < globalNumber } ?: return
        val surahNumber = currentSurah.number
        val surahName = currentSurah.name
        val currentAyah = globalNumber - currentSurah.startLine
        releaseQuranAudioPlayer()
        val media = MediaPlayer()
        quranAudioPlayer = media
        quranAudioSurahNumber = surahNumber
        quranAudioLastSurahNumber = surahNumber
        quranAudioPreparing = true
        setQuranAudioStatus("جارٍ تحميل الآية $currentAyah من سورة $surahName…")
        try {
            media.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            val localAudio = quranAyahAudioFile(reciter.edition, globalNumber)
            if (localAudio != null && localAudio.exists() && localAudio.length() > 0L) {
                media.setDataSource(localAudio.absolutePath)
            } else {
                val url = "https://cdn.islamic.network/quran/audio/${reciter.bitrate}/${reciter.edition}/$globalNumber.mp3"
                media.setDataSource(url)
            }
            media.setOnPreparedListener { prepared ->
                if (quranAudioPlayer === prepared) {
                    quranAudioPreparing = false
                    prepared.start()
                    setQuranAudioStatus("سورة $surahName • الآية $currentAyah • القارئ ${reciter.name}")
                }
            }
            media.setOnCompletionListener { completed ->
                if (quranAudioPlayer !== completed) return@setOnCompletionListener
                if (quranAudioQueueIndex < quranAudioQueue.lastIndex) {
                    quranAudioQueueIndex++
                    playCurrentQuranQueueAyah()
                } else if (quranRepeat) {
                    quranAudioQueueIndex = 0
                    playCurrentQuranQueueAyah(surahNumber, surahName)
                } else {
                    releaseQuranAudioPlayer()
                    quranAudioQueue = emptyList()
                    quranAudioQueueReciter = null
                    setQuranAudioStatus("انتهت التلاوة المتتابعة حتى نهاية القرآن الكريم")
                }
            }
            media.setOnErrorListener { failed, _, _ ->
                if (quranAudioPlayer === failed) {
                    releaseQuranAudioPlayer()
                    quranAudioQueue = emptyList()
                    quranAudioQueueReciter = null
                    setQuranAudioStatus("تعذّر تحميل الآية $currentAyah من سورة $surahName")
                    Toast.makeText(this, "تعذّر تحميل آية من السورة. تحقق من اتصال الإنترنت أو أعد تنزيل السورة.", Toast.LENGTH_LONG).show()
                }
                true
            }
            media.prepareAsync()
        } catch (_: Exception) {
            releaseQuranAudioPlayer()
            quranAudioQueue = emptyList()
            quranAudioQueueReciter = null
            setQuranAudioStatus("تعذّر تشغيل سورة $surahName")
            Toast.makeText(this, "تعذّر بدء التلاوة حالياً", Toast.LENGTH_LONG).show()
        }
    }

    private fun setQuranAudioStatus(status: String) {
        quranAudioStatusText = status
        quranAudioStatusView?.text = status
    }

    private fun releaseQuranAudioPlayer() {
        quranAudioPlayer?.let {
            try { if (it.isPlaying) it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        quranAudioPlayer = null
        quranAudioPreparing = false
    }

    private fun stopQuranAudio() {
        releaseQuranAudioPlayer()
        quranAudioSurahNumber = -1
        quranAudioQueue = emptyList()
        quranAudioQueueIndex = 0
        quranAudioQueueStartGlobal = 1
        quranAudioQueueReciter = null
        quranRepeatAyah = false
        setQuranAudioStatus("التلاوة متوقفة")
    }

    private fun chooseQuranJuz() {
        val choices = Array(30) { index -> "الجزء ${index + 1}" }
        AlertDialog.Builder(this)
            .setTitle("اختر الجزء")
            .setItems(choices) { _, index ->
                try {
                    val location = quranRepository.juzStart(index + 1)
                    quranSurah(location.first, location.second)
                } catch (_: Exception) {
                    Toast.makeText(this, "تعذّر الانتقال إلى هذا الجزء", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun chooseQuranPage() {
        val input = EditText(this).apply {
            hint = "رقم الصفحة من 1 إلى 604"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            textSize = 17f
            setTextColor(cream)
        }
        AlertDialog.Builder(this)
            .setTitle("الانتقال إلى صفحة")
            .setView(input)
            .setPositiveButton("انتقال") { _, _ ->
                val pageNumber = input.text.toString().toIntOrNull()
                if (pageNumber == null || pageNumber !in 1..604) {
                    Toast.makeText(this, "أدخل رقم صفحة صحيحاً من 1 إلى 604", Toast.LENGTH_SHORT).show()
                } else {
                    try {
                        val location = quranPageStart(pageNumber)
                        quranSurah(location.first, location.second, pageNumber)
                    } catch (_: Exception) {
                        Toast.makeText(this, "تعذّر الانتقال إلى الصفحة", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun quranSearchResults(query: String) {
        val results = try {
            quranRepository.search(query)
        } catch (_: Exception) {
            Toast.makeText(this, "تعذّر البحث في المصحف", Toast.LENGTH_SHORT).show()
            return
        }
        content.removeAllViews()
        text("نتائج البحث", 25f, gold, true, Gravity.CENTER)
        text("الكلمة: $query", 13f, muted, false, Gravity.CENTER)
        content.addView(button("العودة إلى فهرس السور") { showPage("quran") }, LinearLayout.LayoutParams(-1, -2))
        if (results.isEmpty()) {
            text("لا توجد نتائج مطابقة. جرّب كتابة جزء آخر من الآية.", 14f, muted, false, Gravity.CENTER)
        } else {
            text("عدد النتائج المعروضة: ${results.size}", 12f, muted)
            results.forEach { result ->
                val resultCard = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(13), dp(11), dp(13), dp(11))
                    background = shape(panel, 14, Color.rgb(42, 91, 72))
                    setOnClickListener { quranSurah(result.surahNumber, result.ayahNumber) }
                }
                resultCard.addView(TextView(this).apply {
                    text = "سورة ${result.surahName} • الآية ${result.ayahNumber}"
                    textSize = 13f
                    setTextColor(gold)
                    typeface = Typeface.DEFAULT_BOLD
                })
                resultCard.addView(TextView(this).apply {
                    text = result.text
                    textSize = 19f
                    setTextColor(cream)
                    gravity = Gravity.CENTER
                    textDirection = View.TEXT_DIRECTION_RTL
                    typeface = Typeface.create("serif", Typeface.NORMAL)
                })
                content.addView(resultCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
        }
    }


    private fun settings() {
        text("مركز التخصيص", 27f, gold, true, Gravity.CENTER)
        text("اضبط نور الصلاة بالطريقة التي تناسبك", 13f, muted, false, Gravity.CENTER)
        gap(12)
        card {
            line("☾  نور الصلاة", 20f, gold, true, Gravity.CENTER)
            line("إعداداتك، موقعك، وتنبيهات عبادتك في مكان واحد", 12f, muted, false, Gravity.CENTER)
        }
        gap(6)
        card {
            line("المظهر", 18f, gold, true)
            val themeChoices = listOf("الوضع الداكن • زمردي", "الوضع الفاتح • هادئ")
            val themePicker = Spinner(this@MainActivity).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, themeChoices)
                setSelection(if (isLightTheme) 1 else 0)
                onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val selectedTheme = if (position == 1) "light" else "dark"
                        val currentTheme = getPreferences(0).getString("app_theme", "dark") ?: "dark"
                        if (selectedTheme != currentTheme) {
                            getPreferences(0).edit()
                                .putString("app_theme", selectedTheme)
                                .putString("page_after_theme", page)
                                .apply()
                            recreate()
                        }
                    }
                }
            }
            addView(themePicker, LinearLayout.LayoutParams(-1, -2))
            line("يمكنك التبديل بين المظهرين في أي وقت.", 12f, muted)
            line("ويدجت المواقيت: اضغط مطولاً على الشاشة الرئيسية للهاتف، ثم الأدوات، واختر «نور الصلاة».", 12f, muted)
        }
        gap(10)
        sectionHeading("المواقيت والموقع")
        card {
            line("التاريخ المعتمد للمواقيت", 18f, gold, true)
            line(SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar")).format(Date(selectedDateMillis)), 15f, cream)
            addView(button("تعديل التاريخ") {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                DatePickerDialog(this@MainActivity, { _, year, month, day ->
                    val chosen = java.util.Calendar.getInstance().apply {
                        timeInMillis = selectedDateMillis
                        set(java.util.Calendar.YEAR, year)
                        set(java.util.Calendar.MONTH, month)
                        set(java.util.Calendar.DAY_OF_MONTH, day)
                    }
                    selectedDateMillis = chosen.timeInMillis
                    getPreferences(0).edit().putLong("selected_date", selectedDateMillis).apply()
                    showPage("settings")
                }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show()
            }, LinearLayout.LayoutParams(-1, -2))
            line("هذا يغيّر تاريخ الحساب داخل التطبيق فقط، ولا يغيّر تاريخ الهاتف.", 12f, muted)
            gap(6)
            val hijriOffset = getPreferences(0).getInt("hijri_day_offset", 0).coerceIn(-2, 2)
            line("تصحيح اليوم الهجري", 15f, gold, true)
            line(
                when (hijriOffset) {
                    0 -> "بدون تصحيح"
                    1 -> "تقديم التاريخ الهجري يوماً واحداً"
                    2 -> "تقديم التاريخ الهجري يومين"
                    -1 -> "تأخير التاريخ الهجري يوماً واحداً"
                    -2 -> "تأخير التاريخ الهجري يومين"
                    else -> "بدون تصحيح"
                },
                12f,
                cream
            )
            val hijriOffsetControls = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            hijriOffsetControls.addView(button("− يوم") { adjustHijriOffset(-1) }, LinearLayout.LayoutParams(0, -2, 1f))
            hijriOffsetControls.addView(button("إعادة الضبط") {
                getPreferences(0).edit().putInt("hijri_day_offset", 0).apply()
                showPage("settings")
            }, LinearLayout.LayoutParams(0, -2, 1f))
            hijriOffsetControls.addView(button("+ يوم") { adjustHijriOffset(1) }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(hijriOffsetControls, LinearLayout.LayoutParams(-1, -2))
            line("هذا التصحيح يغيّر عرض التاريخ الهجري والمناسبات فقط، ولا يغيّر مواقيت الصلاة.", 11f, muted)
        }
        gap(10)
        card {
            line("تنبيهات الصلاة", 18f, gold, true)
            line("فعّل التنبيهات ليصلك إشعار عند دخول وقت كل صلاة. الصوت الحالي هو صوت إشعار الهاتف، وليس ملف أذان.", 13f, muted)
            val prayerSettings = listOf(
                "الفجر" to "prayer_enabled_fajr",
                "الظهر" to "prayer_enabled_dhuhr",
                "العصر" to "prayer_enabled_asr",
                "المغرب" to "prayer_enabled_maghrib",
                "العشاء" to "prayer_enabled_isha"
            )
            prayerSettings.forEach { (label, key) ->
                val toggle = Switch(this@MainActivity).apply {
                    text = "تنبيه صلاة $label"
                    setTextColor(cream)
                    isChecked = getPreferences(0).getBoolean(key, true)
                    setOnCheckedChangeListener { _, enabled ->
                        getPreferences(0).edit().putBoolean(key, enabled).apply()
                        PrayerAlarmScheduler.scheduleTodayAndTomorrow(this@MainActivity)
                    }
                }
                addView(toggle, LinearLayout.LayoutParams(-1, -2))
            }
            addView(button("تفعيل صلاحية الإشعارات") { requestPrayerNotificationPermission() }, LinearLayout.LayoutParams(-1, -2))
            addView(button("السماح بالتنبيه الدقيق في وقته") { requestExactAlarmPermission() }, LinearLayout.LayoutParams(-1, -2))
        }
        gap(10)
        card {
            line("ضبط فرق الوقت", 18f, gold, true)
            line("الفرق الحالي: ${if (timeOffsetMinutes >= 0) "+" else ""}$timeOffsetMinutes دقيقة", 15f, cream)
            addView(button("تعديل الوقت") {
                val now = java.util.Calendar.getInstance()
                val baseMinutes = (now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE) + timeOffsetMinutes + 1440) % 1440
                TimePickerDialog(this@MainActivity, { _, hour, minute ->
                    val actual = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
                    var diff = hour * 60 + minute - actual
                    if (diff > 720) diff -= 1440
                    if (diff < -720) diff += 1440
                    timeOffsetMinutes = diff
                    getPreferences(0).edit().putInt("time_offset", diff).apply()
                    PrayerAlarmScheduler.scheduleTodayAndTomorrow(this@MainActivity)
                    showPage("settings")
                }, baseMinutes / 60, baseMinutes % 60, true).show()
            }, LinearLayout.LayoutParams(-1, -2))
            line("هذا فرق تصحيحي لمواقيت التطبيق، وليس تغييراً لساعة الجهاز.", 12f, muted)
        }
        gap(10)
        card {
            line("صوت الأذان", 18f, gold, true)
            val choices = mutableListOf("أذان كامل • 2:34", "أذان قصير • 0:42", "تنبيه النظام فقط")
            if (getPreferences(0).getString("custom_adhan_uri", null) != null) {
                choices.add("صوت مخصص من الجهاز")
            }
            if (choices.none { selectedMuezzin.startsWith(it.substringBefore(" •")) }) {
                selectedMuezzin = choices[0]
                getPreferences(0).edit().putString("muezzin", selectedMuezzin).apply()
            }
            val picker = Spinner(this@MainActivity).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, choices)
                setSelection(choices.indexOfFirst { selectedMuezzin.startsWith(it.substringBefore(" •")) }.coerceAtLeast(0))
                onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                        selectedMuezzin = choices[position]
                        getPreferences(0).edit().putString("muezzin", selectedMuezzin).apply()
                        stopAdhanPreview()
                    }
                }
            }
            addView(picker, LinearLayout.LayoutParams(-1, -2))
            addView(button("معاينة الصوت المختار") { previewAdhan() }, LinearLayout.LayoutParams(-1, -2))
            addView(button("إيقاف المعاينة") { stopAdhanPreview() }, LinearLayout.LayoutParams(-1, -2))
            val savedVolume = getPreferences(0).getInt("adhan_volume", 100).coerceIn(0, 100)
            val volumeLabel = TextView(this@MainActivity).apply {
                text = "مستوى صوت الأذان: $savedVolume%"
                textSize = 12f
                setTextColor(muted)
                gravity = Gravity.CENTER
            }
            addView(volumeLabel, LinearLayout.LayoutParams(-1, -2))
            val volumeSlider = android.widget.SeekBar(this@MainActivity).apply {
                max = 100
                progress = savedVolume
                progressTintList = android.content.res.ColorStateList.valueOf(gold)
                thumbTintList = android.content.res.ColorStateList.valueOf(gold)
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(49, 83, 67))
                setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                    override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                        volumeLabel.text = "مستوى صوت الأذان: $progress%"
                        val level = progress / 100f
                        adhanPreviewPlayer?.setVolume(level, level)
                    }
                    override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                        getPreferences(0).edit()
                            .putInt("adhan_volume", (seekBar?.progress ?: savedVolume).coerceIn(0, 100))
                            .apply()
                    }
                })
            }
            addView(volumeSlider, LinearLayout.LayoutParams(-1, -2))
            addView(button("اختبار تنبيه الأذان الآن") {
                if (android.os.Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPrayerNotificationPermission()
                } else {
                    val testIntent = Intent(this@MainActivity, PrayerAlarmReceiver::class.java).apply {
                        putExtra("prayer_name", "تجربة")
                        putExtra("test_only", true)
                    }
                    sendBroadcast(testIntent)
                    Toast.makeText(this@MainActivity, "تم إرسال تنبيه تجريبي", Toast.LENGTH_SHORT).show()
                }
            }, LinearLayout.LayoutParams(-1, -2))
            addView(button("اختيار تسجيل أذان من الهاتف") { openAdhanFilePicker() }, LinearLayout.LayoutParams(-1, -2))
            line("تسجيلان صوتيان مرخّصان CC0، ويمكنك استيراد تسجيلك الخاص من الهاتف. الملف المخصص يبقى على جهازك ولا يُرفع إلى أي خدمة، ولا ننسب التسجيلات إلى مؤذنين غير موثّقين.", 12f, muted)
            line("يعمل تسجيل الأذان كاملاً في الخلفية عند تفعيل التنبيهات والسماح بصلاحيات الإشعارات والمنبهات. ويمكن إيقافه من إشعار الأذان.", 12f, muted)
            line("يمكنك ضبط مستوى تسجيل الأذان من هنا. ويظل مستوى صوت المنبه العام في الهاتف مؤثراً أيضاً.", 11f, muted)
        }
        gap(10)
        card {
            line("المدينة", 18f, gold, true)
            line(cityName, 14f, cream)
            addView(button("اختيار مدينة") {
                val names = arrayOf(
                    "عمّان، الأردن", "إربد، الأردن", "الزرقاء، الأردن", "العقبة، الأردن",
                    "السلط، الأردن", "مادبا، الأردن", "جرش، الأردن", "المفرق، الأردن",
                    "الكرك، الأردن", "معان، الأردن", "عجلون، الأردن", "الطفيلة، الأردن",
                    "القدس، فلسطين", "مكة المكرمة، السعودية",
                    "المدينة المنورة، السعودية"
                )
                val coords = arrayOf(
                    doubleArrayOf(31.9539,35.9106), doubleArrayOf(32.5556,35.8500),
                    doubleArrayOf(32.0728,36.0880), doubleArrayOf(29.5267,35.0078),
                    doubleArrayOf(32.0392,35.7272), doubleArrayOf(31.7167,35.8000),
                    doubleArrayOf(32.2808,35.8993), doubleArrayOf(32.3429,36.2080),
                    doubleArrayOf(31.1800,35.7048), doubleArrayOf(30.1920,35.7340),
                    doubleArrayOf(32.3333,35.7520), doubleArrayOf(30.8333,35.6044),
                    doubleArrayOf(31.7683,35.2137), doubleArrayOf(21.4225,39.8262),
                    doubleArrayOf(24.4672,39.6111)
                )
                AlertDialog.Builder(this@MainActivity).setTitle("اختر المدينة").setItems(names) { _, i ->
                    cityName = names[i]; latitude = coords[i][0]; longitude = coords[i][1]
                    updateMagneticDeclination()
                    locationAccuracyMeters = null
                    getPreferences(0).edit()
                        .putString("city_name", cityName)
                        .putString("city_lat", latitude.toString())
                        .putString("city_lon", longitude.toString())
                        .putString("location_mode", "manual")
                        .remove("location_accuracy_m")
                        .apply()
                    PrayerAlarmScheduler.scheduleTodayAndTomorrow(this@MainActivity)
                    PrayerTimesRemoteRepository.refreshIfNeeded(this@MainActivity, latitude, longitude)
                    showPage("settings")
                }.setNegativeButton("إلغاء", null).show()
            }, LinearLayout.LayoutParams(-1, -2))
        }
        gap(10)
        card {
            line("الموقع", 18f, gold, true)
            line("خط العرض: ${"%.4f".format(Locale.US, latitude)}", 14f, cream)
            line("خط الطول: ${"%.4f".format(Locale.US, longitude)}", 14f, cream)
            locationAccuracyMeters?.let { line("دقة آخر قراءة: ±${"%.0f".format(Locale.US, it)} متر", 12f, muted) }
            addView(button("تحديث الموقع") { requestLocation(true) }, LinearLayout.LayoutParams(-1, -2))
            line(if (locationMode == "manual") "الموقع مضبوط يدوياً" else "الموقع من الهاتف", 12f, muted)
        }
        gap(10)
        sectionHeading("مصدر مواقيت الصلاة والخصوصية")
        card {
            line("مصدر مواقيت الصلاة المتصل", 16f, gold, true)
            val onlineSwitch = Switch(this@MainActivity).apply {
                text = "السماح بتحديث المواقيت عبر الإنترنت"
                setTextColor(cream)
                isEnabled = latitude.isFinite() && longitude.isFinite() &&
                    latitude in -90.0..90.0 && longitude in -180.0..180.0
                isChecked = getPreferences(0).getBoolean("prayer_remote_consent", false) && isEnabled
                setOnCheckedChangeListener { _, enabled ->
                    getPreferences(0).edit()
                        .putBoolean("prayer_remote_consent", enabled)
                        .putBoolean("prayer_remote_prompt_dismissed", true)
                        .apply()
                    if (enabled) {
                        PrayerTimesRemoteRepository.refreshIfNeeded(this@MainActivity, latitude, longitude, force = true) {
                            if (!isFinishing && page == "settings") showPage("settings")
                        }
                        Toast.makeText(this@MainActivity, "بدأ تحديث المواقيت", Toast.LENGTH_SHORT).show()
                    } else {
                        PrayerAlarmScheduler.scheduleTodayAndTomorrow(this@MainActivity)
                        Toast.makeText(this@MainActivity, "تم إيقاف التحديث عبر الإنترنت", Toast.LENGTH_SHORT).show()
                    }
                    showPage("settings")
                }
            }
            addView(onlineSwitch, LinearLayout.LayoutParams(-1, -2))
            line("عند التفعيل يستخدم التطبيق رمز المدينة للمدن الأردنية المدعومة عند اختيار طريقة الوزارة، أو يرسل إحداثيات تقريبية بعد تقريبها إلى منزلتين عشريتين إلى prayertime.si في الحالات الأخرى. لا تُرسل الإحداثيات الدقيقة أو ملفاتك. يمكنك إيقاف هذا الخيار في أي وقت؛ وعند الإيقاف يعمل الحساب المحلي التقديري.", 12f, muted)
            line("يدعم المصدر المتصل المواقع حول العالم. في الأردن مع طريقة الوزارة يستخدم جدول المدينة المنشور لدى الخدمة، وفي بقية الحالات يستخدم طريقة الحساب التي تختارها. قد تختلف النتائج دقيقة أو دقيقتين عن أذان المسجد.", 11f, muted)
            line("يحفظ التطبيق جداول الأشهر القريبة للمدن الأردنية المدعومة، أو مواقيت الأيام السبعة القادمة للمواقع والطرق الأخرى، لتستمر التنبيهات دون اتصال بقدر البيانات المخزنة.", 12f, muted)
            addView(button("شروط مصدر المواقيت") {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://prayertime.si/terms/")))
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "تعذّر فتح الشروط حالياً", Toast.LENGTH_SHORT).show()
                }
            }, LinearLayout.LayoutParams(-1, -2))
        }
        gap(10)
        sectionHeading("التنبيهات والصوت")
        card {
            line("تنسيق الوقت", 18f, gold, true)
            val formatSwitch = Switch(this@MainActivity).apply {
                text = "عرض الوقت بنظام 24 ساعة"
                setTextColor(cream)
                isChecked = use24Hour
                setOnCheckedChangeListener { _, checked ->
                    use24Hour = checked
                    getPreferences(0).edit().putBoolean("use_24_hour", checked).apply()
                    showPage("settings")
                }
            }
            addView(formatSwitch, LinearLayout.LayoutParams(-1, -2))
        }
        gap(10)
        card {
            line("طريقة الحساب", 18f, gold, true)
            line(calculationMethod, 13f, cream)
            addView(button("اختيار طريقة الحساب") {
                val methods = arrayOf(
                    "الأردن (18° / 18°)",
                    "رابطة العالم الإسلامي (18° / 17°)",
                    "أم القرى (18.5° / 90 دقيقة)",
                    "الهيئة المصرية (19.5° / 17.5°)",
                    "أمريكا الشمالية ISNA (15° / 15°)",
                    "ديانت تركيا (18° / 17°)",
                    "سنغافورة (20° / 18°)"
                )
                AlertDialog.Builder(this@MainActivity).setTitle("طريقة الحساب").setItems(methods) { _, i ->
                    calculationMethod = methods[i]
                    getPreferences(0).edit().putString("calculation_method", calculationMethod).apply()
                    PrayerAlarmScheduler.scheduleTodayAndTomorrow(this@MainActivity)
                    PrayerTimesRemoteRepository.refreshIfNeeded(this@MainActivity, latitude, longitude, force = true)
                    showPage("settings")
                }.setNegativeButton("إلغاء", null).show()
            }, LinearLayout.LayoutParams(-1, -2))
            line("اختر طريقة الحساب المناسبة لموقعك. عند تفعيل المصدر المتصل تُرسل الطريقة المختارة للخدمة؛ وفي الأردن عند اختيار طريقة الوزارة تُستخدم بيانات جدول المدينة. قد تختلف المواقيت دقيقة أو دقيقتين عن أذان المسجد، وعند انقطاع الإنترنت يعمل الحساب المحلي كبديل تقديري.", 12f, muted)
            line("حساب وقت العصر المستخدم حالياً قياسي؛ خيار العصر الحنفي غير متاح بعد.", 11f, muted)
            if (!PrayerTimesRemoteRepository.isJordan(latitude, longitude) &&
                calculationMethod.startsWith("الأردن")) {
                line("تنبيه: طريقة الأردن ما زالت مختارة رغم أن موقعك خارج الأردن. اختر طريقة تناسب بلدك.", 11f, gold)
            }
            if (PrayerTimesRemoteRepository.isJordan(latitude, longitude)) {
                addView(button("فتح جدول وزارة الأوقاف الأردنية الرسمي") {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.awqaf.gov.jo/ar/Pages/PrayerTime")))
                    } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, "تعذر فتح الرابط حالياً", Toast.LENGTH_SHORT).show()
                    }
                }, LinearLayout.LayoutParams(-1, -2))
            }
        }
        gap(10)
        card { line("حول التطبيق", 18f, gold, true); line("نور الصلاة • الإصدار 1.2", 14f, cream); line("واجهة عربية • تصميم زمردي وذهبي", 13f, muted) }
    }

    private fun sectionHeading(title: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), dp(9), dp(3), dp(6))
        }
        val accent = View(this).apply { background = shape(gold, 3) }
        row.addView(accent, LinearLayout.LayoutParams(dp(4), dp(20)).apply { marginEnd = dp(9) })
        val label = TextView(this).apply {
            text = title; textSize = 15f; setTextColor(cream); typeface = Typeface.DEFAULT_BOLD
        }
        row.addView(label)
        content.addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    private fun card(block: LinearLayout.() -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(16), dp(17), dp(16))
            background = shape(Color.rgb(10, 48, 39), 20, Color.rgb(39, 87, 67))
            elevation = dp(2).toFloat()
            block()
        }
        content.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    private fun LinearLayout.line(value: String, size: Float, color: Int, bold: Boolean = false, gravity: Int = Gravity.START) {
        val v = TextView(this@MainActivity).apply {
            text = value; textSize = size; setTextColor(color); this.gravity = gravity
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(4))
        }
        addView(v, LinearLayout.LayoutParams(-1, -2))
    }
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false, gravity: Int = Gravity.START) {
        content.line(value, size, color, bold, gravity)
    }
    private fun gap(h: Int) { content.addView(View(this), LinearLayout.LayoutParams(1, dp(h))) }
    private fun button(label: String, action: () -> Unit) = Button(this@MainActivity).apply {
        text = label; textSize = 14f; isAllCaps = false
        setTextColor(Color.rgb(4, 35, 29))
        typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.rgb(236, 207, 139), Color.rgb(207, 164, 83))
        ).apply { cornerRadius = dp(15).toFloat() }
        elevation = dp(2).toFloat()
        setPadding(dp(14), dp(5), dp(14), dp(5))
        setOnClickListener { action() }
    }
    private fun shape(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); if (stroke != null) setStroke(dp(1), stroke)
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun qibla(): Double = QiblaCalculator.bearing(latitude, longitude)

    private fun prayerTimesMinutes(): List<Int> {
        val date = java.util.Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        val remoteTimes = PrayerTimesRemoteRepository.getTimes(this, date)
        return if (remoteTimes != null) {
            remoteTimes.map { ((it + timeOffsetMinutes) % 1440 + 1440) % 1440 }
        } else {
            PrayerTimesCalculator.calculate(
                date,
                latitude,
                longitude,
                calculationMethod,
                timeOffsetMinutes
            )
        }
    }

    private fun formatPrayerTime(minutes: Int): String {
        val h = (minutes/60)%24
        val m = minutes%60
        if (use24Hour) return "%02d:%02d".format(Locale.US,h,m)
        val suffix = if (h<12) "ص" else "م"
        val hour = if (h%12==0) 12 else h%12
        return "%d:%02d %s".format(Locale("ar"),hour,m,suffix)
    }

    private fun prayerTimes(): List<String> = prayerTimesMinutes().map { formatPrayerTime(it) }

    private fun nextPrayerInfo(): Pair<String,Int> {
        val now = java.util.Calendar.getInstance()
        val date = java.util.Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        if (now.get(java.util.Calendar.YEAR)!=date.get(java.util.Calendar.YEAR) ||
            now.get(java.util.Calendar.DAY_OF_YEAR)!=date.get(java.util.Calendar.DAY_OF_YEAR)) return "تاريخ الحساب المختار" to 0
        val current = now.get(java.util.Calendar.HOUR_OF_DAY)*60+now.get(java.util.Calendar.MINUTE)
        val times = prayerTimesMinutes()
        val indexes = listOf(0,2,3,4,5)
        val names = listOf("الفجر","الظهر","العصر","المغرب","العشاء")
        val pos = indexes.indexOfFirst { times[it]>current }
        val target = if (pos<0) times[0]+1440 else times[indexes[pos]]
        return (if (pos<0) "الفجر" else names[pos]) to (target-current)
    }

    private fun countdownLabel(minutes: Int): String = "%02d:%02d".format(Locale.US,minutes/60,minutes%60)

    private fun refreshPrayerCountdown() {
        val n = nextPrayerInfo()
        nextPrayerView?.text = n.first
        countdownView?.text = countdownLabel(n.second)
    }

    private fun applyLocation(loc: Location) {
        if (isFinishing) return
        latitude = loc.latitude
        longitude = loc.longitude
        updateMagneticDeclination()
        locationAccuracyMeters = if (loc.hasAccuracy()) loc.accuracy else null
        cityName = PrayerTimesRemoteRepository.nearestJordanCityName(latitude, longitude)
            ?.plus("، الأردن") ?: "موقعي الحالي"
        locationMode = "gps"
        getPreferences(0).edit()
            .putString("city_name", cityName)
            .putString("city_lat", latitude.toString())
            .putString("city_lon", longitude.toString())
            .putString("location_mode", "gps")
            .putFloat("location_accuracy_m", locationAccuracyMeters ?: 0f)
            .apply()
        PrayerAlarmScheduler.scheduleTodayAndTomorrow(this)
        PrayerTimesRemoteRepository.refreshIfNeeded(this, latitude, longitude) {
            PrayerWidgetProvider.updateAll(this)
            if (!isFinishing && (page == "home" || page == "calendar")) showPage(page)
        }
        if (page == "home" || page == "settings" || page == "calendar") showPage(page)
    }

    private fun requestLocation(forceGps: Boolean = false) {
        if (locationMode == "manual" && !forceGps) return
        if (locationRequestInFlight) {
            Toast.makeText(this, "جارٍ تحديد موقعك الحالي…", Toast.LENGTH_SHORT).show()
            return
        }
        val hasFine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 100)
            return
        }
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val enabledProviders = lm.getProviders(true)
            val lastKnown = enabledProviders.mapNotNull { provider ->
                try { lm.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
            }.maxByOrNull { it.time }

            val lastAge = lastKnown?.let { System.currentTimeMillis() - it.time } ?: Long.MAX_VALUE
            val lastKnownAccurate = lastKnown != null &&
                (!lastKnown.hasAccuracy() || lastKnown.accuracy <= 150f)
            if (!forceGps && lastKnown != null && lastKnownAccurate &&
                lastAge in 0..(10 * 60 * 1000L)) {
                applyLocation(lastKnown)
                return
            }

            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val provider = when {
                    hasFine && enabledProviders.contains(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    enabledProviders.contains(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    enabledProviders.isNotEmpty() -> enabledProviders.first()
                    else -> null
                }
                if (provider == null) {
                    if (lastKnown != null) applyLocation(lastKnown)
                    else Toast.makeText(this, "فعّل خدمات الموقع في الهاتف ثم أعد المحاولة", Toast.LENGTH_LONG).show()
                    return
                }
                locationRequestInFlight = true
                val mainExecutor = Executor { command -> runOnUiThread(command) }
                lm.getCurrentLocation(provider, null, mainExecutor) { current ->
                    locationRequestInFlight = false
                    if (current != null) {
                        applyLocation(current)
                    } else if (lastKnown != null) {
                        applyLocation(lastKnown)
                        Toast.makeText(this, "استخدمنا آخر موقع محفوظ لعدم توفر قراءة جديدة", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "لم نستطع تحديد الموقع. تحقق من GPS والإنترنت ثم أعد المحاولة.", Toast.LENGTH_LONG).show()
                    }
                }
            } else if (lastKnown != null) {
                applyLocation(lastKnown)
                Toast.makeText(this, "هذا الإصدار من Android يعتمد على آخر موقع محفوظ", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "لم يتوفر موقع حديث على هذا الجهاز", Toast.LENGTH_LONG).show()
            }
        } catch (_: SecurityException) {
            locationRequestInFlight = false
            Toast.makeText(this, "تحقق من صلاحية الموقع في إعدادات الهاتف", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            locationRequestInFlight = false
            Toast.makeText(this, "تعذر قراءة الموقع حالياً", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) requestLocation(true)
        if (requestCode == 200 && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            PrayerAlarmScheduler.scheduleTodayAndTomorrow(this)
            Toast.makeText(this, "تم تفعيل تنبيهات الصلاة", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestPrayerNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 200)
        } else {
            PrayerAlarmScheduler.scheduleTodayAndTomorrow(this)
            Toast.makeText(this, "تمت جدولة تنبيهات الصلاة لليوم والغد", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestExactAlarmPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val alarm = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarm.canScheduleExactAlarms()) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:$packageName")))
                } catch (_: Exception) {
                    Toast.makeText(this, "افتح إعدادات التطبيقات واسمح للمنبهات الدقيقة لتطبيق نور الصلاة", Toast.LENGTH_LONG).show()
                }
            } else {
                PrayerAlarmScheduler.scheduleTodayAndTomorrow(this)
                Toast.makeText(this, "التنبيهات الدقيقة مفعّلة", Toast.LENGTH_SHORT).show()
            }
        } else {
            PrayerAlarmScheduler.scheduleTodayAndTomorrow(this)
            Toast.makeText(this, "تمت جدولة التنبيهات", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateMagneticDeclination() {
        magneticDeclination = android.hardware.GeomagneticField(
            latitude.toFloat(),
            longitude.toFloat(),
            0f,
            System.currentTimeMillis()
        ).declination
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        val matrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        val rotation = windowManager.defaultDisplay.rotation
        val (axisX, axisY) = when (rotation) {
            android.view.Surface.ROTATION_90 ->
                SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            android.view.Surface.ROTATION_180 ->
                SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            android.view.Surface.ROTATION_270 ->
                SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        val remapped = FloatArray(9)
        SensorManager.remapCoordinateSystem(matrix, axisX, axisY, remapped)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(remapped, orientation)
        val magneticHeading = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f
        heading = QiblaCalculator.trueHeading(magneticHeading, magneticDeclination)
        hasCompassReading = true
        arrow?.updateReadings(heading, qibla().toFloat(), hasCompassReading, isLightTheme)
        val accuracyHint = if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) " • حرّك الهاتف للمعايرة" else ""
        compassInfo?.text = "القبلة ${"%.1f".format(Locale.US, qibla())}°  •  الشمال الحقيقي ${"%.0f".format(heading)}°$accuracyHint"
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
