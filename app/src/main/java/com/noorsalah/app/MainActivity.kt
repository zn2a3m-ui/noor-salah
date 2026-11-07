package com.noorsalah.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import android.widget.Button
import android.location.Location
import android.location.LocationManager
import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

class MainActivity : Activity() {

    private val green = Color.rgb(8, 45, 35)
    private val gold = Color.rgb(225, 190, 105)
    private val white = Color.WHITE
    private var latitude = 31.9539
    private var longitude = 35.9106
private var locationLoaded = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }    private fun showHome() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(green)
        root.setPadding(22, 28, 22, 24)

        val scroll = ScrollView(this)
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.gravity = Gravity.CENTER_HORIZONTAL

        val logo = TextView(this)
        logo.text = "☾"
        logo.textSize = 58f
        logo.gravity = Gravity.CENTER
        logo.setTextColor(gold)

        val title = TextView(this)
        title.text = "نور الصلاة"
        title.textSize = 32f
        title.gravity = Gravity.CENTER
        title.setTextColor(gold)

        val subtitle = TextView(this)
        subtitle.text = "رفيقك إلى الصلاة والذكر"
        subtitle.textSize = 16f
        subtitle.gravity = Gravity.CENTER
        subtitle.setTextColor(white)

        content.addView(logo)
        content.addView(title)
        content.addView(subtitle)
        content.addView(space(24))

        val date = TextView(this)
        date.text = SimpleDateFormat("EEEE، dd MMMM yyyy", Locale("ar")).format(Date())
        date.textSize = 17f
        date.gravity = Gravity.CENTER
        date.setTextColor(white)
        content.addView(date)
        content.addView(space(18))

        val heading = TextView(this)
        heading.text = "مواقيت الصلاة"
        heading.textSize = 23f
        heading.setTextColor(gold)
        heading.gravity = Gravity.CENTER
        content.addView(heading)
        content.addView(space(12))

        val prayerNames = listOf("الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء")
        val prayerTimes = calculatePrayerTimes()
        for (i in prayerNames.indices) {
            content.addView(prayerCard(prayerNames[i], prayerTimes[i]))
        }

        content.addView(space(18))
        val qiblaTitle = TextView(this)
        qiblaTitle.text = "اتجاه القبلة"
        qiblaTitle.textSize = 22f
        qiblaTitle.setTextColor(gold)
        qiblaTitle.gravity = Gravity.CENTER
        content.addView(qiblaTitle)

        val qibla = TextView(this)
        qibla.text = "اتجاه القبلة من موقعك: ${"%.1f".format(qiblaDirection())}°"
        qibla.textSize = 18f
        qibla.setTextColor(white)
        qibla.gravity = Gravity.CENTER
        content.addView(qibla)

        content.addView(space(18))
        val dhikr = TextView(this)
        dhikr.text = "ذكر اليوم\nسُبْحَانَ اللهِ وَبِحَمْدِهِ"
        dhikr.textSize = 21f
        dhikr.gravity = Gravity.CENTER
        dhikr.setTextColor(gold)
        content.addView(dhikr)

        content.addView(space(20))
        val refresh = Button(this)
        refresh.text = "تحديث الموقع"
        refresh.setOnClickListener { requestLocation() }
        content.addView(refresh)

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
        requestLocation()
    }    private fun space(height: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, height
            )
        }
    }

    private fun prayerCard(name: String, time: String): TextView {
        return TextView(this).apply {
            text = "   $name                         $time   "
            textSize = 19f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(12, 16, 12, 16)
            setBackgroundColor(Color.rgb(18, 65, 50))
        }
    }

    private fun qiblaDirection(): Double {
        val kaabaLat = Math.toRadians(21.4225)
        val kaabaLon = Math.toRadians(39.8262)
        val lat = Math.toRadians(latitude)
        val lon = Math.toRadians(longitude)
        val dLon = kaabaLon - lon
        val y = sin(dLon)
        val x = cos(lat) * tan(kaabaLat) - sin(lat) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    private fun calculatePrayerTimes(): List<String> {
        val now = java.util.Calendar.getInstance()
        val day = now.get(java.util.Calendar.DAY_OF_YEAR)
        val declination = 0.409 * sin(2 * PI * day / 365.0 - 1.39)
        val lat = Math.toRadians(latitude)
        val noon = 12.0 - longitude / 15.0 +
            2.0 * sin(2 * PI * day / 365.0) * 0.12

        fun hourAngle(angle: Double): Double {
            val value = (sin(Math.toRadians(angle)) -
                sin(lat) * sin(declination)) /
                (cos(lat) * cos(declination))
            return Math.toDegrees(acos(value.coerceIn(-1.0, 1.0))) / 15.0
        }

        val fajr = noon - hourAngle(-18.0)
        val sunrise = noon - hourAngle(-0.833)
        val dhuhr = noon
        val asrAngle = Math.toDegrees(
            atan(1.0 / (1.0 + tan(abs(lat - declination))))
        )
        val asr = noon + hourAngle(asrAngle)
        val sunset = noon + hourAngle(-0.833)
        val isha = noon + hourAngle(-17.0)

        return listOf(fajr, sunrise, dhuhr, asr, sunset, isha).map {
            val minutes = ((it * 60).roundToInt() + 1440) % 1440
            "%02d:%02d".format(minutes / 60, minutes % 60)
        }
    }

    private fun requestLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ), 100
            )
            return
        }

        try {
            val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location: Location? =
                manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            if (location != null) {
                latitude = location.latitude
                longitude = location.longitude
                showHome()
            }
        } catch (_: Exception) {
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            requestLocation()
        }
    }
}