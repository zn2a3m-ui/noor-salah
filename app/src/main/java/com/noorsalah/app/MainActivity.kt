package com.noorsalah.app

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
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
import java.util.concurrent.Executor
import kotlin.math.*

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
    private var quranFontSize = 25f
    private var adhanPreviewPlayer: MediaPlayer? = null
    private var quranAudioPlayer: MediaPlayer? = null
    private var quranAudioSurahNumber = -1
    private var quranAudioPreparing = false
    private var quranAudioStatusView: TextView? = null
    private var quranRepeat = false
    private data class QuranReciter(val name: String, val edition: String, val bitrate: Int)
    private val quranReciters = listOf(
        QuranReciter("مشاري راشد العفاسي", "ar.alafasy", 128),
        QuranReciter("محمود خليل الحصري", "ar.husary", 128),
        QuranReciter("محمد صديق المنشاوي", "ar.minshawi", 128),
        QuranReciter("عبدالرحمن السديس", "ar.sudais", 192),
        QuranReciter("عبدالباسط عبدالصمد", "ar.abdulbasit", 192)
    )
    private lateinit var sensorManager: SensorManager
    private var latitude = 31.9539
    private var longitude = 35.9106
    private var locationRequestInFlight = false
    private var locationAccuracyMeters: Float? = null
    private var heading = 0f
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
    private var arrow: TextView? = null
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
        quranFontSize = getPreferences(0).getFloat("quran_font_size", 25f).coerceIn(18f, 36f)
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
            PrayerTimesRemoteRepository.getHijriLabel(this@MainActivity, selectedCalendar)?.let {
                line(it, 13f, gold, true, Gravity.CENTER)
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
        val firstHijri = PrayerTimesRemoteRepository.getHijriDate(this, month)
        if (firstHijri == null) {
            PrayerTimesRemoteRepository.refreshMonthIfNeeded(this, latitude, longitude, year, monthIndex + 1) {
                if (!isFinishing && page == "calendar") showPage("calendar")
            }
        }

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
            val hijri = PrayerTimesRemoteRepository.getHijriDate(this, date)
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
        val selectedHijri = PrayerTimesRemoteRepository.getHijriDate(this, selectedDate)
        card {
            line("تفاصيل اليوم المحدد", 17f, gold, true)
            line(SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar")).format(selectedDate.time), 15f, cream, true)
            if (selectedHijri != null) {
                line("${selectedHijri.day} ${selectedHijri.monthArabic} ${selectedHijri.year} هـ", 16f, gold, true)
                if (selectedHijri.basis == "umm-al-qura") {
                    line("بحسب تقويم أم القرى • قد يختلف عن إعلان الرؤية المحلية", 11f, muted)
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
        text("اعرف اتجاه بيت الله الحرام أينما كنت", 13f, muted, false, Gravity.CENTER)
        gap(14)
        card {
            line("اتجاه مكة المكرمة", 15f, muted, false, Gravity.CENTER)
            line("${"%.1f".format(Locale.US, qibla())}°", 31f, gold, true, Gravity.CENTER)
            val dial = FrameLayout(this@MainActivity).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.rgb(9, 65, 49), Color.rgb(3, 31, 27))
                ).apply { shape = GradientDrawable.OVAL; setStroke(dp(2), Color.rgb(164, 132, 67)) }
                elevation = dp(4).toFloat()
            }
            val compassRing = TextView(this@MainActivity).apply {
                text = "N\n\nW                     E\n\nS"; textSize = 14f
                gravity = Gravity.CENTER; setTextColor(mutedOnDark); typeface = Typeface.DEFAULT_BOLD
            }
            dial.addView(compassRing, FrameLayout.LayoutParams(-1, -1))
            val arrowView = TextView(this@MainActivity).apply {
                text = "↑"; textSize = 76f; gravity = Gravity.CENTER; setTextColor(goldOnDark)
                rotation = qibla().toFloat() - heading
                setShadowLayer(dp(5).toFloat(), 0f, dp(2).toFloat(), Color.BLACK)
            }
            arrow = arrowView
            dial.addView(arrowView, FrameLayout.LayoutParams(-1, -1))
            addView(dial, LinearLayout.LayoutParams(dp(250), dp(250)).apply {
                gravity = Gravity.CENTER; topMargin = dp(12); bottomMargin = dp(14)
            })
            val infoView = TextView(this@MainActivity).apply {
                text = if (hasCompassReading) {
                    "اتجاه الهاتف عن الشمال الحقيقي: ${"%.0f".format(heading)}°"
                } else {
                    "جارٍ قراءة اتجاه الهاتف من البوصلة…"
                }
                textSize = 14f; gravity = Gravity.CENTER; setTextColor(cream)
            }
            compassInfo = infoView
            addView(infoView)
            line("تُصحّح البوصلة الانحراف المغناطيسي تلقائياً وفق موقعك، وقد تتأثر بالدقة والمعادن حول الهاتف.", 11f, muted, false, Gravity.CENTER)
        }
        gap(10)
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) == null) {
            text("لا يتوفر مستشعر بوصلة في هذا الجهاز. يظهر اتجاه القبلة الرقمي، لكن السهم لن يتحرك.", 12f, muted, false, Gravity.CENTER)
        }
        card {
            line("معايرة البوصلة", 16f, gold, true)
            line("أمسك الهاتف أفقياً وحرّكه على شكل رقم 8 إذا لم يستقر المؤشر. أبعده عن المعادن والمغناطيس.", 13f, muted)
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
        text("مصحف عثماني موثّق • قراءة دون اتصال بالإنترنت", 13f, muted, false, Gravity.CENTER)
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

        quranBookmarksCard()
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

    private fun quranBookmarksCard() {
        val prefs = getPreferences(0)
        val count = prefs.getInt("quran_bookmark_count", 0).coerceIn(0, 20)
        card {
            line("علاماتي المرجعية", 17f, gold, true)
            if (count == 0) {
                line("احفظ مواضعك المفضلة من داخل السورة لتظهر هنا.", 12f, muted)
            } else {
                val surahs = quranRepository.loadSurahs()
                for (index in 0 until count) {
                    val surahNumber = prefs.getInt("quran_bookmark_${index}_surah", -1)
                    val ayahNumber = prefs.getInt("quran_bookmark_${index}_ayah", -1)
                    val surah = surahs.getOrNull(surahNumber - 1) ?: continue
                    if (ayahNumber !in 1..surah.ayahCount) continue
                    val row = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    row.addView(button("${surah.name} • آية $ayahNumber") {
                        quranSurah(surahNumber, ayahNumber)
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    row.addView(button("×") {
                        removeQuranBookmark(surahNumber, ayahNumber)
                        showPage("quran")
                    }, LinearLayout.LayoutParams(dp(48), -2))
                    addView(row, LinearLayout.LayoutParams(-1, -2))
                }
            }
        }
    }

    private fun saveQuranBookmark(surahNumber: Int, ayahNumber: Int) {
        val prefs = getPreferences(0)
        val count = prefs.getInt("quran_bookmark_count", 0).coerceIn(0, 20)
        val exists = (0 until count).any { index ->
            prefs.getInt("quran_bookmark_${index}_surah", -1) == surahNumber &&
                prefs.getInt("quran_bookmark_${index}_ayah", -1) == ayahNumber
        }
        if (exists) {
            Toast.makeText(this, "هذه الآية محفوظة في علاماتك المرجعية", Toast.LENGTH_SHORT).show()
            return
        }
        if (count >= 20) {
            Toast.makeText(this, "وصلت إلى الحد الأقصى: 20 علامة مرجعية. احذف علامة قديمة أولاً.", Toast.LENGTH_LONG).show()
            return
        }
        prefs.edit()
            .putInt("quran_bookmark_${count}_surah", surahNumber)
            .putInt("quran_bookmark_${count}_ayah", ayahNumber)
            .putInt("quran_bookmark_count", count + 1)
            .apply()
        Toast.makeText(this, "تم حفظ العلامة المرجعية", Toast.LENGTH_SHORT).show()
    }

    private fun removeQuranBookmark(surahNumber: Int, ayahNumber: Int) {
        val prefs = getPreferences(0)
        val count = prefs.getInt("quran_bookmark_count", 0).coerceIn(0, 20)
        val remaining = (0 until count).mapNotNull { index ->
            val surah = prefs.getInt("quran_bookmark_${index}_surah", -1)
            val ayah = prefs.getInt("quran_bookmark_${index}_ayah", -1)
            if (surah == surahNumber && ayah == ayahNumber) null else surah to ayah
        }
        val editor = prefs.edit().putInt("quran_bookmark_count", remaining.size)
        for (index in 0 until 20) {
            if (index < remaining.size) {
                editor.putInt("quran_bookmark_${index}_surah", remaining[index].first)
                editor.putInt("quran_bookmark_${index}_ayah", remaining[index].second)
            } else {
                editor.remove("quran_bookmark_${index}_surah")
                    .remove("quran_bookmark_${index}_ayah")
            }
        }
        editor.apply()
    }

    private fun quranSurah(surahNumber: Int, focusAyah: Int = 1) {
        if (quranAudioSurahNumber != -1 && quranAudioSurahNumber != surahNumber) stopQuranAudio()
        val surah = try {
            quranRepository.loadSurahs().first { it.number == surahNumber }
        } catch (_: Exception) {
            Toast.makeText(this, "تعذّر فتح السورة", Toast.LENGTH_SHORT).show()
            return
        }
        val ayahs = try {
            quranRepository.loadAyahs(surahNumber)
        } catch (_: Exception) {
            Toast.makeText(this, "تعذّر قراءة ملف المصحف. أعد تثبيت النسخة بعد اكتمال التحقق.", Toast.LENGTH_LONG).show()
            return
        }

        content.removeAllViews()
        text("القرآن الكريم", 25f, gold, true, Gravity.CENTER)
        content.addView(button("العودة إلى فهرس السور") { showPage("quran") }, LinearLayout.LayoutParams(-1, -2))
        text("سورة ${surah.name}", 24f, cream, true, Gravity.CENTER)
        text("${surah.ayahCount} آية • ${if (surah.revelationType == "Meccan") "مكية" else "مدنية"}", 12f, muted, false, Gravity.CENTER)
        gap(8)

        card {
            line("التلاوة الصوتية للسورة", 17f, gold, true)
            val prefs = getPreferences(0)
            val savedEdition = prefs.getString("quran_reciter", "ar.alafasy") ?: "ar.alafasy"
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
                        if (prefs.getString("quran_reciter", "ar.alafasy") != chosen.edition) {
                            prefs.edit().putString("quran_reciter", chosen.edition).apply()
                            if (quranAudioSurahNumber == surahNumber) playQuranSurah(surahNumber)
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
            quranAudioStatusView = TextView(this@MainActivity).apply {
                text = "جاهز للتلاوة"
                textSize = 12f
                setTextColor(muted)
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, dp(3))
            }
            addView(quranAudioStatusView, LinearLayout.LayoutParams(-1, -2))
            line("تُبث التلاوة عبر الإنترنت من شبكة Islamic Network؛ يلزم اتصال بالإنترنت. حقوق التسجيل محفوظة للقارئ أو أصحاب الحقوق.", 11f, muted)
        }
        gap(8)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controls.addView(button("أصغر") {
            quranFontSize = (quranFontSize - 2f).coerceAtLeast(18f)
            getPreferences(0).edit().putFloat("quran_font_size", quranFontSize).apply()
            quranSurah(surahNumber, focusAyah)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        controls.addView(button("أكبر") {
            quranFontSize = (quranFontSize + 2f).coerceAtMost(36f)
            getPreferences(0).edit().putFloat("quran_font_size", quranFontSize).apply()
            quranSurah(surahNumber, focusAyah)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
        content.addView(controls, LinearLayout.LayoutParams(-1, -2))
        gap(10)
        text("اضغط على الآية لحفظ موضع القراءة والعودة إليه لاحقاً.", 11f, muted, false, Gravity.CENTER)

        var focusView: View? = null
        val savedPrefs = getPreferences(0)
        val savedAyah = if (savedPrefs.getInt("quran_last_surah", -1) == surahNumber) {
            savedPrefs.getInt("quran_last_ayah", -1)
        } else -1
        ayahs.forEach { ayah ->
            val verseCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(15), dp(13), dp(15), dp(13))
                background = shape(
                    if (ayah.number == focusAyah) Color.rgb(24, 66, 49) else panel,
                    15,
                    if (ayah.number == focusAyah) goldOnDark else if (ayah.number == savedAyah) gold else Color.rgb(42, 91, 72)
                )
                setOnClickListener {
                    getPreferences(0).edit()
                        .putInt("quran_last_surah", surahNumber)
                        .putInt("quran_last_ayah", ayah.number)
                        .apply()
                    Toast.makeText(this@MainActivity, "حُفظ موضع القراءة: ${surah.name} • ${ayah.number}", Toast.LENGTH_SHORT).show()
                }
            }
            val verseText = TextView(this).apply {
                text = ayah.text
                textSize = quranFontSize
                setTextColor(if (ayah.number == focusAyah) creamOnDark else cream)
                gravity = Gravity.CENTER
                textDirection = View.TEXT_DIRECTION_RTL
                typeface = Typeface.create("serif", Typeface.NORMAL)
                setLineSpacing(dp(7).toFloat(), 1.15f)
            }
            val verseNumber = TextView(this).apply {
                text = "﴿${ayah.number}﴾"
                textSize = 12f
                setTextColor(if (ayah.number == focusAyah) goldOnDark else gold)
                gravity = Gravity.CENTER
                setPadding(0, dp(9), 0, 0)
            }
            verseCard.addView(verseText, LinearLayout.LayoutParams(-1, -2))
            verseCard.addView(verseNumber, LinearLayout.LayoutParams(-1, -2))
            verseCard.addView(
                button("حفظ علامة مرجعية") {
                    saveQuranBookmark(surahNumber, ayah.number)
                },
                LinearLayout.LayoutParams(-1, -2)
            )
            content.addView(verseCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            if (ayah.number == focusAyah) focusView = verseCard
        }

        val nextRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        nextRow.addView(button("السورة السابقة") {
            if (surahNumber > 1) quranSurah(surahNumber - 1, 1)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        nextRow.addView(button("السورة التالية") {
            if (surahNumber < 114) quranSurah(surahNumber + 1, 1)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
        content.addView(nextRow, LinearLayout.LayoutParams(-1, -2))
        mainScroll?.post {
            focusView?.let { mainScroll?.smoothScrollTo(0, it.top) }
        }
    }

    private fun toggleQuranAudio(surahNumber: Int) {
        val current = quranAudioPlayer
        if (current != null && quranAudioSurahNumber == surahNumber && !quranAudioPreparing) {
            try {
                if (current.isPlaying) {
                    current.pause()
                    quranAudioStatusView?.text = "التلاوة متوقفة مؤقتاً"
                    Toast.makeText(this, "تم إيقاف التلاوة مؤقتاً", Toast.LENGTH_SHORT).show()
                } else {
                    current.start()
                    quranAudioStatusView?.text = "التلاوة تعمل الآن"
                    Toast.makeText(this, "متابعة التلاوة", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                playQuranSurah(surahNumber)
            }
        } else {
            playQuranSurah(surahNumber)
        }
    }

    private fun playQuranSurah(surahNumber: Int) {
        val reciterId = getPreferences(0).getString("quran_reciter", "ar.alafasy") ?: "ar.alafasy"
        val reciter = quranReciters.firstOrNull { it.edition == reciterId } ?: quranReciters.first()
        quranRepeat = getPreferences(0).getBoolean("quran_repeat", false)
        stopQuranAudio()
        val media = MediaPlayer()
        quranAudioPlayer = media
        quranAudioSurahNumber = surahNumber
        quranAudioPreparing = true
        quranAudioStatusView?.text = "جارٍ الاتصال بمصدر التلاوة…"
        try {
            media.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            val url = "https://cdn.islamic.network/quran/audio-surah/${reciter.bitrate}/${reciter.edition}/$surahNumber.mp3"
            media.setDataSource(url)
            media.setOnPreparedListener { prepared ->
                if (quranAudioPlayer === prepared) {
                    quranAudioPreparing = false
                    prepared.start()
                    quranAudioStatusView?.text = "تُتلى السورة بصوت ${reciter.name}"
                    Toast.makeText(this, "بدأت تلاوة سورة ${quranRepository.loadSurahs().getOrNull(surahNumber - 1)?.name ?: surahNumber} بصوت ${reciter.name}", Toast.LENGTH_SHORT).show()
                }
            }
            media.setOnCompletionListener { completed ->
                if (quranAudioPlayer !== completed) return@setOnCompletionListener
                if (quranRepeat) {
                    try {
                        quranAudioStatusView?.text = "إعادة التلاوة"
                        completed.seekTo(0)
                        completed.start()
                    } catch (_: Exception) {
                        stopQuranAudio()
                    }
                } else {
                    stopQuranAudio()
                    quranAudioStatusView?.text = "انتهت التلاوة"
                }
            }
            media.setOnErrorListener { failed, _, _ ->
                if (quranAudioPlayer === failed) {
                    stopQuranAudio()
                    quranAudioStatusView?.text = "تعذّر تحميل التلاوة"
                    Toast.makeText(this, "تعذّر تحميل التلاوة. تحقق من اتصال الإنترنت.", Toast.LENGTH_LONG).show()
                }
                true
            }
            media.prepareAsync()
            quranAudioStatusView?.text = "جارٍ تحميل التلاوة الصوتية…"
            Toast.makeText(this, "جارٍ تحميل التلاوة الصوتية…", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            stopQuranAudio()
            Toast.makeText(this, "تعذّر بدء التلاوة حالياً", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopQuranAudio() {
        quranAudioPlayer?.let {
            try { if (it.isPlaying) it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        quranAudioPlayer = null
        quranAudioSurahNumber = -1
        quranAudioPreparing = false
        quranAudioStatusView?.text = "التلاوة متوقفة"
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
                        val location = quranRepository.pageStart(pageNumber)
                        quranSurah(location.first, location.second)
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
            line("المواقيت بطريقة وزارة الأوقاف الموثّقة", 16f, gold, true)
            val onlineSwitch = Switch(this@MainActivity).apply {
                text = "السماح بتحديث المواقيت عبر الإنترنت"
                setTextColor(cream)
                isEnabled = calculationMethod.startsWith("الأردن") &&
                    PrayerTimesRemoteRepository.isJordan(latitude, longitude)
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
            line("عند التفعيل يُرسل اسم المدينة أو إحداثيات تقريبية بعد تقريبها إلى منزلتين عشريتين إلى prayertime.si لجلب المواقيت. لا تُرسل الإحداثيات الدقيقة أو ملفاتك. يمكنك إيقاف هذا الخيار في أي وقت؛ وعند الإيقاف يعمل الحساب المحلي التقديري.", 12f, muted)
            line("المصدر الأردني متاح حالياً داخل الأردن عند اختيار طريقة حساب الأردن؛ دعم مصادر بقية الدول قيد التطوير.", 11f, muted)
            line("يحفظ جداول الأشهر القريبة للمدن المدعومة، أو مواقيت الأيام السبعة القادمة للمواقع الأخرى، لتستمر التنبيهات دون اتصال بقدر البيانات المخزنة.", 12f, muted)
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
                val methods = arrayOf("الأردن (18° / 18°)", "رابطة العالم الإسلامي (18° / 17°)", "أم القرى (18.5° / 90 دقيقة)")
                AlertDialog.Builder(this@MainActivity).setTitle("طريقة الحساب").setItems(methods) { _, i ->
                    calculationMethod = methods[i]
                    getPreferences(0).edit().putString("calculation_method", calculationMethod).apply()
                    PrayerAlarmScheduler.scheduleTodayAndTomorrow(this@MainActivity)
                    if (calculationMethod.startsWith("الأردن")) {
                        PrayerTimesRemoteRepository.refreshIfNeeded(this@MainActivity, latitude, longitude, force = true)
                    }
                    showPage("settings")
                }.setNegativeButton("إلغاء", null).show()
            }, LinearLayout.LayoutParams(-1, -2))
            line("في الأردن تُستخدم عند الاتصال طريقة الحساب التي تنسبها خدمة prayertime.si إلى وزارة الأوقاف، وتذكر أنها طابقتها مع جدول الجهة المنشور؛ وقد تختلف المواقيت دقيقة أو دقيقتين عن أذان المسجد. إذا اخترت طريقة حساب أخرى أو انقطع الإنترنت، يعمل الحساب المحلي كبديل تقديري.", 12f, muted)
            addView(button("فتح جدول وزارة الأوقاف الأردنية الرسمي") {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.awqaf.gov.jo/ar/Pages/PrayerTime")))
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "تعذر فتح الرابط حالياً", Toast.LENGTH_SHORT).show()
                }
            }, LinearLayout.LayoutParams(-1, -2))
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
        val declination = android.hardware.GeomagneticField(
            latitude.toFloat(),
            longitude.toFloat(),
            0f,
            System.currentTimeMillis()
        ).declination
        heading = QiblaCalculator.trueHeading(magneticHeading, declination)
        hasCompassReading = true
        arrow?.rotation = qibla().toFloat() - heading
        val accuracyHint = if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) " • حرّك الهاتف للمعايرة" else ""
        compassInfo?.text = "القبلة ${"%.1f".format(Locale.US, qibla())}°  •  الشمال الحقيقي ${"%.0f".format(heading)}°$accuracyHint"
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
