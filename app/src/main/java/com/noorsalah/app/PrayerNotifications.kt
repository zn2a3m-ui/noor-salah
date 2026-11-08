package com.noorsalah.app

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import java.util.Calendar
import java.util.Locale
import kotlin.math.*

object PrayerAlarmScheduler {
    const val CHANNEL_ID = "noor_prayer_reminders"
    private const val ACTION_PRAYER = "com.noorsalah.app.PRAYER_REMINDER"
    private val prayerNames = listOf("الفجر", "الظهر", "العصر", "المغرب", "العشاء")

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "تنبيهات مواقيت الصلاة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيه عند دخول وقت الصلاة"
                enableVibration(true)
                setSound(Settings.System.DEFAULT_NOTIFICATION_URI, null)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun scheduleTodayAndTomorrow(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        createChannel(context)
        val now = System.currentTimeMillis()
        val base = Calendar.getInstance()
        for (dayOffset in 0..1) {
            val date = (base.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
            }
            val times = prayerTimes(context, date)
            val dayKey = date.get(Calendar.YEAR) * 5000 + date.get(Calendar.DAY_OF_YEAR) * 10
            times.forEachIndexed { index, minuteOfDay ->
                val preferenceKeys = listOf("prayer_enabled_fajr", "prayer_enabled_dhuhr", "prayer_enabled_asr", "prayer_enabled_maghrib", "prayer_enabled_isha")
                val prefs = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
                if (!prefs.getBoolean(preferenceKeys[index], true)) return@forEachIndexed
                val trigger = (date.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
                    set(Calendar.MINUTE, minuteOfDay % 60)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                if (trigger > now + 15000L) {
                    scheduleOne(context, trigger, dayKey + index, prayerNames[index])
                }
            }
        }
    }

    private fun scheduleOne(context: Context, triggerAt: Long, requestCode: Int, prayer: String) {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_PRAYER
            putExtra("prayer_name", prayer)
        }
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarm.canScheduleExactAlarms()) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (_: SecurityException) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun prayerTimes(context: Context, date: Calendar): List<Int> {
        val prefs = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
        val latitude = prefs.getString("city_lat", "31.9539")?.toDoubleOrNull() ?: 31.9539
        val longitude = prefs.getString("city_lon", "35.9106")?.toDoubleOrNull() ?: 35.9106
        val method = prefs.getString("calculation_method", "الأردن (18° / 18°)") ?: "الأردن (18° / 18°)"
        val offset = prefs.getInt("time_offset", 0)
        val day = date.get(Calendar.DAY_OF_YEAR)
        val days = if (date.getActualMaximum(Calendar.DAY_OF_YEAR) == 366) 366.0 else 365.0
        val g = 2.0 * Math.PI / days * (day - 1)
        val eq = 229.18 * (0.000075 + 0.001868*cos(g) - 0.032077*sin(g) - 0.014615*cos(2*g) - 0.040849*sin(2*g))
        val dec = 0.006918 - 0.399912*cos(g) + 0.070257*sin(g) - 0.006758*cos(2*g) + 0.000907*sin(2*g) - 0.002697*cos(3*g) + 0.00148*sin(3*g)
        val lat = Math.toRadians(latitude)
        val zone = (date.get(Calendar.ZONE_OFFSET) + date.get(Calendar.DST_OFFSET)) / 60000.0
        val noon = 720.0 - 4.0*longitude - eq + zone + offset
        fun hourAngle(altitude: Double): Double {
            val a = Math.toRadians(altitude)
            val v = (sin(a)-sin(lat)*sin(dec))/(cos(lat)*cos(dec))
            return Math.toDegrees(acos(v.coerceIn(-1.0,1.0)))*4.0
        }
        val fajrAngle = if (method.startsWith("أم القرى")) 18.5 else 18.0
        val ishaAngle = if (method.startsWith("أم القرى")) 0.0 else if (method.startsWith("الأردن")) 18.0 else 17.0
        val fajr = noon - hourAngle(-fajrAngle)
        val dhuhr = noon + 2.0
        val asrAltitude = Math.toDegrees(atan(1.0/(1.0+tan(abs(lat-dec)))))
        val asr = noon + hourAngle(asrAltitude)
        val sunset = noon + hourAngle(-0.833)
        val maghrib = sunset + if (method.startsWith("الأردن")) 5.0 else 0.0
        val isha = if (ishaAngle == 0.0) sunset + 90.0 else noon + hourAngle(-ishaAngle)
        return listOf(fajr, dhuhr, asr, maghrib, isha).map { ((it.roundToInt() % 1440) + 1440) % 1440 }
    }
}

class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayer = intent.getStringExtra("prayer_name") ?: "الصلاة"
        if (Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            PrayerAlarmScheduler.createChannel(context)
            val openApp = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, PrayerAlarmScheduler.CHANNEL_ID)
            } else {
                Notification.Builder(context).setPriority(Notification.PRIORITY_HIGH)
            }
            val notification = builder
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("حان وقت صلاة $prayer")
                .setContentText("نور الصلاة يذكّرك بالمحافظة على الصلاة")
                .setAutoCancel(true)
                .setContentIntent(openApp)
                .build()
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(prayer.hashCode(), notification)
        }
        PrayerAlarmScheduler.scheduleTodayAndTomorrow(context)
    }
}

class PrayerRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PrayerAlarmScheduler.scheduleTodayAndTomorrow(context)
    }
}
