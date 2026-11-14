package com.noorsalah.app

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.media.AudioAttributes
import android.net.Uri
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
    const val CHANNEL_ALERT_ID = "noor_prayer_alert_only"
    const val CHANNEL_CUSTOM_ID = "noor_prayer_adhan_custom"
    private const val ACTION_PRAYER = "com.noorsalah.app.PRAYER_REMINDER"
    private val prayerNames = listOf("الفجر", "الظهر", "العصر", "المغرب", "العشاء")

    private fun audioUri(context: Context, resourceName: String): Uri =
        Uri.parse("android.resource://${context.packageName}/raw/$resourceName")

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                "تنبيه الصلاة فقط",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيه بصوت إشعار النظام دون تسجيل أذان"
                enableVibration(true)
                setSound(Settings.System.DEFAULT_NOTIFICATION_URI, audioAttributes)
            }
            manager.createNotificationChannel(alertChannel)
            val customUri = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
                .getString("custom_adhan_uri", null)?.let { Uri.parse(it) }
            if (customUri != null) {
                try {
                    val customChannel = NotificationChannel(
                        CHANNEL_CUSTOM_ID,
                        "أذان من ملفك",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "تشغيل ملف الأذان الذي اخترته من الهاتف"
                        enableVibration(true)
                        setSound(customUri, audioAttributes)
                    }
                    manager.createNotificationChannel(customChannel)
                } catch (_: Exception) {
                    manager.deleteNotificationChannel(CHANNEL_CUSTOM_ID)
                }
            }
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
                if (!prefs.getBoolean(preferenceKeys[index], true)) {
                    cancelOne(context, dayKey + index, prayerNames[index])
                    return@forEachIndexed
                }
                // Remove the previous alarm first. This prevents an outdated alarm
                // from firing after a timezone, clock, or location change.
                cancelOne(context, dayKey + index, prayerNames[index])
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

    private fun cancelOne(context: Context, requestCode: Int, prayer: String) {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_PRAYER
            putExtra("prayer_name", prayer)
        }
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pending)
        pending.cancel()
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
        val remoteTimes = PrayerTimesRemoteRepository.getTimes(context, date)
        val baseTimes = remoteTimes ?: PrayerTimesCalculator.calculate(date, latitude, longitude, method, 0)
        val prayerKeys = listOf("fajr", "dhuhr", "asr", "maghrib", "isha")
        val prayerIndices = listOf(0, 2, 3, 4, 5)
        // Sunrise is not an obligatory prayer and must not trigger an adhan.
        return prayerIndices.mapIndexed { index, sourceIndex ->
            val correction = prefs.getInt("prayer_offset_${prayerKeys[index]}", 0).coerceIn(-5, 5)
            ((baseTimes[sourceIndex] + correction) % 1440 + 1440) % 1440
        }
    }
}


object DedicationReminderScheduler {
    const val CHANNEL_ID = "noor_dedication_reminder"
    private const val ACTION = "com.noorsalah.app.DEDICATION_REMINDER"
    private const val REQUEST_CODE = 90412
    private const val HOUR = 9

    private val duas = listOf(
        "اللهم اغفر له وارحمه، واعفُ عنه وأكرم نزله.",
        "اللهم آنس وحشته، ونوّر قبره، واجعل الجنة داره.",
        "اللهم ارفع درجته في المهديين، واخلفه في عقبه في الغابرين.",
        "اللهم اجعل ما قدّمه من خير في ميزان حسناته، وتجاوز عن تقصيره.",
        "اللهم أبدله داراً خيراً من داره، وأهلاً خيراً من أهله.",
        "اللهم افسح له في قبره مدّ بصره، واجعله روضة من رياض الجنة.",
        "اللهم اجزه عن الإحسان إحساناً، وعن الإساءة عفواً وغفراناً.",
        "اللهم اجمعه بمن يحب في جنات النعيم برحمتك يا أرحم الراحمين.",
        "اللهم اسقه من حوض نبيك شربة هنيئة لا يظمأ بعدها أبداً.",
        "اللهم اجعل قبره نوراً وسكينة، واجعل مقامه في الفردوس الأعلى.",
        "اللهم ارحمه رحمةً تطمئن بها روحه، وترفع بها درجته.",
        "اللهم تقبّل صالح عمله، واغفر له، واجعل ذكراه باباً للدعاء والخير."
    )

    fun duaForToday(): String {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        return duas[(day - 1) % duas.size]
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "الدعاء للمرحوم",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "تذكير يومي بدعاء متجدد للمرحوم"
                }
            )
        }
    }

    fun scheduleNext(context: Context) {
        val prefs = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("dedication_reminder_enabled", true)) {
            cancel(context)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        createChannel(context)
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val intent = Intent(context, DedicationReminderReceiver::class.java).apply { action = ACTION }
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending)
        } catch (_: Exception) {
            alarm.set(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending)
        }
    }

    fun cancel(context: Context) {
        val intent = Intent(context, DedicationReminderReceiver::class.java).apply { action = ACTION }
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
        pending.cancel()
    }

    fun showNotification(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        createChannel(context)
        val openApp = PendingIntent.getActivity(
            context, REQUEST_CODE, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            Notification.Builder(context).setPriority(Notification.PRIORITY_DEFAULT)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("دعاء للمرحوم محمد سليمان موسى أبو زبارق")
            .setContentText(duaForToday())
            .setStyle(Notification.BigTextStyle().bigText(duaForToday()))
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(REQUEST_CODE, notification)
    }
}

class DedicationReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DedicationReminderScheduler.showNotification(context)
        DedicationReminderScheduler.scheduleNext(context)
    }
}

class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayer = intent.getStringExtra("prayer_name") ?: "الصلاة"
        val isTest = intent.getBooleanExtra("test_only", false)
        if (Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            PrayerAlarmScheduler.createChannel(context)
            val openApp = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val preferences = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
            val selectedAudio = preferences.getString("muezzin", "تنبيه النظام فقط") ?: "تنبيه النظام فقط"
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val customUri = preferences.getString("custom_adhan_uri", null)?.let { Uri.parse(it) }
            val customAudioAvailable = if (selectedAudio.startsWith("صوت مخصص") && customUri != null) {
                try {
                    context.contentResolver.openFileDescriptor(customUri, "r")?.use { true } ?: false
                } catch (_: Exception) {
                    false
                }
            } else false
            val customChannelAvailable = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                customAudioAvailable
            } else {
                manager.getNotificationChannel(PrayerAlarmScheduler.CHANNEL_CUSTOM_ID) != null
            }
            val useCustomAudio = selectedAudio.startsWith("صوت مخصص") &&
                customAudioAvailable && customChannelAvailable
            val channelId = if (useCustomAudio) {
                PrayerAlarmScheduler.CHANNEL_CUSTOM_ID
            } else {
                PrayerAlarmScheduler.CHANNEL_ALERT_ID
            }
            val playbackStarted = if (useCustomAudio) {
                try {
                    val playbackIntent = Intent(context, AdhanPlaybackService::class.java).apply {
                        putExtra(AdhanPlaybackService.EXTRA_PRAYER, prayer)
                        putExtra(AdhanPlaybackService.EXTRA_AUDIO, selectedAudio)
                        customUri?.let { putExtra(AdhanPlaybackService.EXTRA_CUSTOM_URI, it.toString()) }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(playbackIntent)
                    } else {
                        context.startService(playbackIntent)
                    }
                    true
                } catch (_: Exception) {
                    false
                }
            } else false

            if (!playbackStarted) {
                val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Notification.Builder(context, channelId)
                } else {
                    val sound = if (useCustomAudio && customUri != null) customUri else Settings.System.DEFAULT_NOTIFICATION_URI
                    Notification.Builder(context)
                        .setPriority(Notification.PRIORITY_HIGH)
                        .setSound(sound)
                }
                val notification = builder
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle("حان وقت صلاة $prayer")
                    .setContentText("نور الصلاة يذكّرك بالمحافظة على الصلاة")
                    .setAutoCancel(true)
                    .setContentIntent(openApp)
                    .build()
                val notificationId = if (isTest) {
                    (System.currentTimeMillis() and 0x7fffffffL).toInt()
                } else prayer.hashCode()
                manager.notify(notificationId, notification)
            }
        }
        if (!isTest) {
            PrayerAlarmScheduler.scheduleTodayAndTomorrow(context)
            PrayerWidgetProvider.updateAll(context)
        }
    }
}

class PrayerRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PrayerAlarmScheduler.scheduleTodayAndTomorrow(context)
        DedicationReminderScheduler.scheduleNext(context)
        PrayerWidgetProvider.updateAll(context)
    }
}
