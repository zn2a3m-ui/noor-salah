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
import android.content.pm.PackageManager
import android.Manifest
import android.location.Location
import android.location.LocationManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

class MainActivity : Activity(), SensorEventListener {
    private val bg = Color.rgb(5, 31, 26)
    private val panel = Color.rgb(15, 45, 36)
    private val panelAlt = Color.rgb(20, 55, 44)
    private val gold = Color.rgb(222, 185, 105)
    private val cream = Color.rgb(247, 243, 229)
    private val muted = Color.rgb(184, 199, 184)
    private lateinit var content: LinearLayout
    private lateinit var sensorManager: SensorManager
    private var latitude = 31.9539
    private var longitude = 35.9106
    private var heading = 0f
    private var page = "home"
    private var count = 0
    private var selectedDateMillis = 0L
    private var timeOffsetMinutes = 0
    private var selectedMuezzin = "المؤذن الافتراضي"
    private var calculationMethod = "الأردن (18° / 17°)"
    private var use24Hour = true
    private var cityName = "عمّان، الأردن"
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
        window.statusBarColor = bg
        window.navigationBarColor = bg
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        latitude = getPreferences(0).getString("city_lat", "31.9539")?.toDoubleOrNull() ?: 31.9539
        longitude = getPreferences(0).getString("city_lon", "35.9106")?.toDoubleOrNull() ?: 35.9106
        cityName = getPreferences(0).getString("city_name", cityName) ?: cityName
        calculationMethod = getPreferences(0).getString("calculation_method", calculationMethod) ?: calculationMethod
        use24Hour = getPreferences(0).getBoolean("use_24_hour", true)
        count = getPreferences(0).getInt("tasbeeh", 0)
        selectedDateMillis = getPreferences(0).getLong("selected_date", 0L).let { if (it == 0L) System.currentTimeMillis() else it }
        timeOffsetMinutes = getPreferences(0).getInt("time_offset", 0)
        selectedMuezzin = getPreferences(0).getString("muezzin", "المؤذن الافتراضي") ?: "المؤذن الافتراضي"
        val shell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val scroll = ScrollView(this).apply { addView(content); isFillViewport = true }
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(dp(4), dp(5), dp(4), dp(5)); background = shape(panel, 18) }
        listOf("الرئيسية" to "home", "القبلة" to "qibla", "الأذكار" to "azkar", "القرآن" to "quran", "الإعدادات" to "settings").forEach { item ->
            val b = TextView(this@MainActivity).apply {
                text = item.first; gravity = Gravity.CENTER; textSize = 11f
                setTextColor(if (item.second == page) gold else cream)
                setPadding(dp(4), dp(12), dp(4), dp(12))
                setOnClickListener { showPage(item.second) }
            }
            nav.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
        }
        shell.addView(nav, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(10), dp(5), dp(10), dp(10)) })
        setContentView(shell)
        showPage("home")
        requestLocation()
        PrayerAlarmScheduler.createChannel(this)
        PrayerAlarmScheduler.scheduleTodayAndTomorrow(this)
    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        clockHandler.post(clockTick)
        PrayerAlarmScheduler.scheduleTodayAndTomorrow(this)
    }
    override fun onPause() { super.onPause(); sensorManager.unregisterListener(this); clockHandler.removeCallbacks(clockTick) }

    private fun showPage(target: String) {
        page = target
        content.removeAllViews()
        when (target) {
            "home" -> home()
            "qibla" -> qiblaScreen()
            "azkar" -> azkar()
            "quran" -> quran()
            "settings" -> settings()
        }
    }

    private fun home() {
        text("☾  نور الصلاة", 29f, gold, true, Gravity.CENTER)
        text("رفيقك إلى الصلاة والذكر", 14f, muted, false, Gravity.CENTER)
        gap(18)
        card {
            line(cityName, 14f, muted, false, Gravity.CENTER)
            line(SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar")).format(Date(selectedDateMillis)), 16f, cream, true, Gravity.CENTER)
        }
        gap(12)
        card {
            line("الصلاة القادمة", 14f, muted, false, Gravity.CENTER)
            val next = nextPrayerInfo()
            val name = TextView(this@MainActivity).apply { text = next.first; textSize = 24f; gravity = Gravity.CENTER; setTextColor(gold); typeface = Typeface.DEFAULT_BOLD }
            nextPrayerView = name; addView(name)
            val timer = TextView(this@MainActivity).apply { text = countdownLabel(next.second); textSize = 25f; gravity = Gravity.CENTER; setTextColor(cream); typeface = Typeface.MONOSPACE }
            countdownView = timer; addView(timer)
            line("الوقت المتبقي", 11f, muted, false, Gravity.CENTER)
        }
        gap(12)
        text("مواقيت الصلاة", 21f, gold, true)
        val names = listOf("الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء")
        val times = prayerTimes()
        names.forEachIndexed { i, name ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(13), dp(16), dp(13))
                background = shape(if (i % 2 == 0) panel else panelAlt, 13)
            }
            val n = TextView(this@MainActivity).apply { text = name; textSize = 16f; setTextColor(cream); typeface = Typeface.DEFAULT_BOLD }
            val t = TextView(this@MainActivity).apply { text = times[i]; textSize = 17f; setTextColor(gold); typeface = Typeface.DEFAULT_BOLD }
            row.addView(n, LinearLayout.LayoutParams(0, -2, 1f)); row.addView(t)
            content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
        }
        card {
            line("تنبيه مهم", 15f, gold, true)
            line("المواقيت الحالية حسابية تقديرية وليست جدولاً رسمياً معتمداً من وزارة الأوقاف الأردنية. لن نعتمدها نهائياً قبل ضبط طريقة الحساب.", 13f, muted)
        }
        gap(10)
        card {
            line("اتجاه القبلة", 17f, gold, true, Gravity.CENTER)
            line("${"%.1f".format(Locale.US, qibla())}° من الشمال", 24f, cream, true, Gravity.CENTER)
            line("افتح صفحة القبلة لاستخدام حساس الهاتف", 12f, muted, false, Gravity.CENTER)
        }
        gap(10)
        card { line("ذكر اليوم", 16f, gold, true, Gravity.CENTER); line("سُبْحَانَ اللهِ وَبِحَمْدِهِ", 21f, cream, true, Gravity.CENTER) }
    }

    private fun qiblaScreen() {
        text("اتجاه القبلة", 26f, gold, true, Gravity.CENTER)
        text("أمسك الهاتف أفقياً وبعيداً عن المعادن", 13f, muted, false, Gravity.CENTER)
        gap(22)
        card {
            line("الكعبة المشرفة", 18f, gold, true, Gravity.CENTER)
            val arrowView = TextView(this@MainActivity).apply {
                text = "➤"; textSize = 86f; gravity = Gravity.CENTER; setTextColor(gold)
                rotation = qibla().toFloat() - heading
            }
            arrow = arrowView
            addView(arrowView, LinearLayout.LayoutParams(-1, dp(155)))
            val infoView = TextView(this@MainActivity).apply {
                text = "القبلة ${"%.1f".format(Locale.US, qibla())}°  •  الهاتف ${"%.0f".format(heading)}°"
                textSize = 18f; gravity = Gravity.CENTER; setTextColor(cream)
            }
            compassInfo = infoView
            addView(infoView)
        }
        gap(12)
        card { line("معايرة البوصلة", 16f, gold, true); line("حرّك الهاتف على شكل رقم 8 عند عدم استقرار القراءة. قد تتأثر البوصلة بالمغناطيس والمعادن.", 13f, muted) }
    }

    private fun azkar() {
        text("الأذكار والمسبحة", 25f, gold, true, Gravity.CENTER)
        gap(12)
        card {
            line("سُبْحَانَ اللهِ وَبِحَمْدِهِ", 22f, cream, true, Gravity.CENTER)
            val counter = TextView(this@MainActivity).apply { text = count.toString(); textSize = 42f; gravity = Gravity.CENTER; setTextColor(gold); typeface = Typeface.DEFAULT_BOLD }
            countView = counter
            addView(counter)
            addView(button("تسبيح  +") {
                count++; getPreferences(0).edit().putInt("tasbeeh", count).apply(); countView?.text = count.toString()
            }, LinearLayout.LayoutParams(-1, -2))
            addView(button("تصفير العداد") {
                count = 0; getPreferences(0).edit().putInt("tasbeeh", 0).apply(); countView?.text = "0"
            }, LinearLayout.LayoutParams(-1, -2))
        }
        gap(12)
        card {
            line("أذكار مختارة", 18f, gold, true)
            line("أستغفر الله العظيم وأتوب إليه", 17f, cream)
            line("لا إله إلا الله وحده لا شريك له", 17f, cream)
            line("اللهم صل وسلم على نبينا محمد", 17f, cream)
        }
        text("هذه مجموعة أولية؛ سيضاف فهرس الأذكار الكامل مع مراجعة المصادر.", 12f, muted)
    }

    private fun quran() {
        text("القرآن الكريم", 26f, gold, true, Gravity.CENTER)
        gap(14)
        card {
            line("كتاب الله العزيز", 20f, cream, true, Gravity.CENTER)
            line("سيُضاف نص المصحف بالرسم العثماني من مصدر موثوق، مع فهرس السور والتلاوات والحفظ.", 14f, muted, false, Gravity.CENTER)
        }
        gap(12)
        card {
            line("قيد التجهيز", 18f, gold, true, Gravity.CENTER)
            line("لم نضع نصاً تجريبياً على أنه قرآن. لن تُعتبر هذه الميزة مكتملة قبل ربط النص الصحيح ومراجعته.", 13f, muted, false, Gravity.CENTER)
        }
    }

    private fun settings() {
        text("الإعدادات", 26f, gold, true, Gravity.CENTER)
        gap(12)
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
            addView(button("تفعيل تنبيهات الصلاة") { requestPrayerNotificationPermission() }, LinearLayout.LayoutParams(-1, -2))
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
                    showPage("settings")
                }, baseMinutes / 60, baseMinutes % 60, true).show()
            }, LinearLayout.LayoutParams(-1, -2))
            line("هذا فرق تصحيحي لمواقيت التطبيق، وليس تغييراً لساعة الجهاز.", 12f, muted)
        }
        gap(10)
        card {
            line("المؤذّن المفضّل", 18f, gold, true)
            val choices = listOf("المؤذن الافتراضي", "مشاري العفاسي", "عبدالرحمن السديس", "ماهر المعيقلي")
            val picker = Spinner(this@MainActivity).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, choices)
                setSelection(choices.indexOf(selectedMuezzin).coerceAtLeast(0))
                onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                        selectedMuezzin = choices[position]
                        getPreferences(0).edit().putString("muezzin", selectedMuezzin).apply()
                    }
                }
            }
            addView(picker, LinearLayout.LayoutParams(-1, -2))
            line("تم حفظ اختيارك. تشغيل ملفات الأذان والتنبيه التلقائي يحتاج ربط الملفات وخدمة الأذان في التحديث التالي.", 12f, muted)
        }
        gap(10)
        card {
            line("المدينة", 18f, gold, true)
            line(cityName, 14f, cream)
            addView(button("اختيار مدينة") {
                val names = arrayOf("عمّان، الأردن", "إربد، الأردن", "الزرقاء، الأردن", "العقبة، الأردن", "القدس، فلسطين", "مكة المكرمة، السعودية", "المدينة المنورة، السعودية")
                val coords = arrayOf(doubleArrayOf(31.9539,35.9106), doubleArrayOf(32.5556,35.8500), doubleArrayOf(32.0728,36.0880), doubleArrayOf(29.5267,35.0078), doubleArrayOf(31.7683,35.2137), doubleArrayOf(21.4225,39.8262), doubleArrayOf(24.4672,39.6111))
                AlertDialog.Builder(this@MainActivity).setTitle("اختر المدينة").setItems(names) { _, i ->
                    cityName = names[i]; latitude = coords[i][0]; longitude = coords[i][1]
                    getPreferences(0).edit().putString("city_name", cityName).putString("city_lat", latitude.toString()).putString("city_lon", longitude.toString()).apply()
                    PrayerAlarmScheduler.scheduleTodayAndTomorrow(this@MainActivity)
                    showPage("settings")
                }.setNegativeButton("إلغاء", null).show()
            }, LinearLayout.LayoutParams(-1, -2))
        }
        gap(10)
        card {
            line("الموقع", 18f, gold, true)
            line("خط العرض: ${"%.4f".format(Locale.US, latitude)}", 14f, cream)
            line("خط الطول: ${"%.4f".format(Locale.US, longitude)}", 14f, cream)
            addView(button("تحديث الموقع") { requestLocation() }, LinearLayout.LayoutParams(-1, -2))
        }
        gap(10)
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
                val methods = arrayOf("الأردن (18° / 17°)", "رابطة العالم الإسلامي (18° / 17°)", "أم القرى (18.5° / 90 دقيقة)")
                AlertDialog.Builder(this@MainActivity).setTitle("طريقة الحساب").setItems(methods) { _, i ->
                    calculationMethod = methods[i]
                    getPreferences(0).edit().putString("calculation_method", calculationMethod).apply()
                    PrayerAlarmScheduler.scheduleTodayAndTomorrow(this@MainActivity)
                    showPage("settings")
                }.setNegativeButton("إلغاء", null).show()
            }, LinearLayout.LayoutParams(-1, -2))
            line("الحساب الفلكي يعطي نتيجة تقديرية، وقد يختلف عن جدول الجهة الرسمية بدقائق.", 12f, muted)
        }
        gap(10)
        card { line("حول التطبيق", 18f, gold, true); line("نور الصلاة • الإصدار 1.2", 14f, cream); line("واجهة عربية • تصميم زمردي وذهبي", 13f, muted) }
    }

    private fun card(block: LinearLayout.() -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(15), dp(15), dp(15), dp(15))
            background = shape(panel, 17, Color.rgb(38, 91, 69)); block()
        }
        content.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
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
        text = label; textSize = 14f; setTextColor(bg); background = shape(gold, 13); setOnClickListener { action() }
    }
    private fun shape(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); if (stroke != null) setStroke(dp(1), stroke)
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun qibla(): Double {
        val kLat = Math.toRadians(21.4225); val kLon = Math.toRadians(39.8262)
        val lat = Math.toRadians(latitude); val dLon = kLon - Math.toRadians(longitude)
        val y = sin(dLon); val x = cos(lat) * tan(kLat) - sin(lat) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    private fun prayerTimesMinutes(): List<Int> {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        val day = cal.get(java.util.Calendar.DAY_OF_YEAR)
        val days = if (cal.getActualMaximum(java.util.Calendar.DAY_OF_YEAR) == 366) 366.0 else 365.0
        val g = 2.0 * Math.PI / days * (day - 1)
        val eq = 229.18 * (0.000075 + 0.001868*cos(g) - 0.032077*sin(g) - 0.014615*cos(2*g) - 0.040849*sin(2*g))
        val dec = 0.006918 - 0.399912*cos(g) + 0.070257*sin(g) - 0.006758*cos(2*g) + 0.000907*sin(2*g) - 0.002697*cos(3*g) + 0.00148*sin(3*g)
        val lat = Math.toRadians(latitude)
        val zone = (cal.get(java.util.Calendar.ZONE_OFFSET) + cal.get(java.util.Calendar.DST_OFFSET)) / 60000.0
        val noon = 720.0 - 4.0*longitude - eq + zone + timeOffsetMinutes
        fun ha(altitude: Double): Double {
            val a = Math.toRadians(altitude)
            val v = (sin(a)-sin(lat)*sin(dec))/(cos(lat)*cos(dec))
            return Math.toDegrees(acos(v.coerceIn(-1.0,1.0)))*4.0
        }
        val fajrAngle = if (calculationMethod.startsWith("أم القرى")) 18.5 else 18.0
        val ishaAngle = if (calculationMethod.startsWith("أم القرى")) 0.0 else 17.0
        val fajr = noon-ha(-fajrAngle)
        val sunrise = noon-ha(-0.833)
        val dhuhr = noon+2.0
        val asrAltitude = Math.toDegrees(atan(1.0/(1.0+tan(abs(lat-dec)))))
        val asr = noon+ha(asrAltitude)
        val sunset = noon+ha(-0.833)
        val isha = if (ishaAngle == 0.0) sunset+90.0 else noon+ha(-ishaAngle)
        return listOf(fajr,sunrise,dhuhr,asr,sunset,isha).map { ((it.roundToInt()%1440)+1440)%1440 }
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

    private fun requestLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 100); return
        }
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val loc: Location? = try { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: SecurityException) { null }
                ?: try { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: SecurityException) { null }
            if (loc != null) { latitude = loc.latitude; longitude = loc.longitude; if (page == "home" || page == "settings") showPage(page) }
            else Toast.makeText(this, "لم يتوفر موقع حديث؛ نستخدم الموقع الافتراضي", Toast.LENGTH_LONG).show()
        } catch (_: Exception) { Toast.makeText(this, "تعذر قراءة الموقع حالياً", Toast.LENGTH_SHORT).show() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) requestLocation()
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

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        val matrix = FloatArray(9); SensorManager.getRotationMatrixFromVector(matrix, event.values)
        val orientation = FloatArray(3); SensorManager.getOrientation(matrix, orientation)
        heading = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f
        arrow?.rotation = qibla().toFloat() - heading
        compassInfo?.text = "القبلة ${"%.1f".format(Locale.US, qibla())}°  •  الهاتف ${"%.0f".format(heading)}°"
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
