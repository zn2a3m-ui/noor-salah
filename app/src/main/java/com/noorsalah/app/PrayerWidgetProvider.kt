package com.noorsalah.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PrayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
        scheduleNextWidgetUpdate(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_REFRESH) updateAll(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelNextWidgetUpdate(context)
    }

    companion object {
        private const val ACTION_WIDGET_REFRESH = "com.noorsalah.app.WIDGET_REFRESH"
        private const val WIDGET_REFRESH_REQUEST_CODE = 91001

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, PrayerWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            ids.forEach { updateWidget(context, manager, it) }
            scheduleNextWidgetUpdate(context)
        }

        private fun scheduleNextWidgetUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, PrayerWidgetProvider::class.java)
            val widgetIds = manager.getAppWidgetIds(component)
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, PrayerWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_REFRESH
            }
            val pending = PendingIntent.getBroadcast(
                context,
                WIDGET_REFRESH_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarm.cancel(pending)
            if (widgetIds.isEmpty()) return

            val prefs = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
            val latitude = prefs.getString("city_lat", "31.9539")?.toDoubleOrNull() ?: 31.9539
            val longitude = prefs.getString("city_lon", "35.9106")?.toDoubleOrNull() ?: 35.9106
            val method = prefs.getString("calculation_method", "الأردن (18° / 18°)") ?: "الأردن (18° / 18°)"
            val offset = prefs.getInt("time_offset", 0)
            val now = Calendar.getInstance()
            val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

            fun timesFor(date: Calendar): List<Int> {
                val remote = PrayerTimesRemoteRepository.getTimes(context, date)
                val base = remote ?: PrayerTimesCalculator.calculate(date, latitude, longitude, method, offset)
                return if (remote != null) base.map { ((it + offset) % 1440 + 1440) % 1440 } else base
            }

            val indexes = listOf(0, 2, 3, 4, 5)
            val todayTimes = timesFor(now)
            val nextIndex = indexes.firstOrNull { todayTimes[it] > currentMinutes }
            val targetDate = (now.clone() as Calendar)
            val targetMinute = if (nextIndex != null) {
                todayTimes[nextIndex]
            } else {
                targetDate.add(Calendar.DAY_OF_YEAR, 1)
                timesFor(targetDate)[0]
            }
            targetDate.set(Calendar.HOUR_OF_DAY, targetMinute / 60)
            targetDate.set(Calendar.MINUTE, targetMinute % 60)
            targetDate.set(Calendar.SECOND, 0)
            targetDate.set(Calendar.MILLISECOND, 0)
            targetDate.add(Calendar.MINUTE, 1)
            val triggerAt = maxOf(targetDate.timeInMillis, System.currentTimeMillis() + 60_000L)
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }

        private fun cancelNextWidgetUpdate(context: Context) {
            val intent = Intent(context, PrayerWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_REFRESH
            }
            val pending = PendingIntent.getBroadcast(
                context,
                WIDGET_REFRESH_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarm.cancel(pending)
            pending.cancel()
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
            val latitude = prefs.getString("city_lat", "31.9539")?.toDoubleOrNull() ?: 31.9539
            val longitude = prefs.getString("city_lon", "35.9106")?.toDoubleOrNull() ?: 35.9106
            val method = prefs.getString("calculation_method", "الأردن (18° / 18°)") ?: "الأردن (18° / 18°)"
            val offset = prefs.getInt("time_offset", 0)
            val city = prefs.getString("city_name", "عمّان، الأردن") ?: "عمّان، الأردن"
            val use24Hour = prefs.getBoolean("use_24_hour", true)

            val now = Calendar.getInstance()
            val today = (now.clone() as Calendar)
            val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

            fun timesFor(date: Calendar): List<Int> {
                val remote = PrayerTimesRemoteRepository.getTimes(context, date)
                val base = remote ?: PrayerTimesCalculator.calculate(
                    date, latitude, longitude, method, offset
                )
                return if (remote != null) {
                    base.map { ((it + offset) % 1440 + 1440) % 1440 }
                } else base
            }

            val todayTimes = timesFor(today)
            val prayerIndexes = listOf(0, 2, 3, 4, 5)
            val prayerNames = listOf("الفجر", "الظهر", "العصر", "المغرب", "العشاء")
            val nextPosition = prayerIndexes.indexOfFirst { todayTimes[it] > currentMinutes }

            val nextName: String
            val nextMinute: Int
            if (nextPosition >= 0) {
                nextName = prayerNames[nextPosition]
                nextMinute = todayTimes[prayerIndexes[nextPosition]]
            } else {
                val tomorrow = (today.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
                val tomorrowTimes = timesFor(tomorrow)
                nextName = "الفجر"
                nextMinute = tomorrowTimes[0]
            }

            fun formatTime(minutes: Int): String {
                val hour = (minutes / 60) % 24
                val minute = minutes % 60
                if (use24Hour) return "%02d:%02d".format(Locale.US, hour, minute)
                val suffix = if (hour < 12) "ص" else "م"
                val displayHour = if (hour % 12 == 0) 12 else hour % 12
                return "%d:%02d %s".format(Locale("ar"), displayHour, minute, suffix)
            }

            val hijri = PrayerTimesRemoteRepository.getHijriLabel(context, today)
            val dateLabel = SimpleDateFormat("d MMM", Locale("ar")).format(today.time)
            val locationLabel = if (!hijri.isNullOrBlank()) "$city • $hijri" else "$city • $dateLabel"

            val views = RemoteViews(context.packageName, R.layout.widget_prayer_times)
            views.setTextViewText(R.id.widget_location, locationLabel)
            views.setTextViewText(R.id.widget_next_prayer, "الصلاة القادمة: $nextName")
            views.setTextViewText(R.id.widget_next_time, formatTime(nextMinute))

            val labels = listOf("الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء")
            val ids = listOf(
                R.id.widget_fajr,
                R.id.widget_sunrise,
                R.id.widget_dhuhr,
                R.id.widget_asr,
                R.id.widget_maghrib,
                R.id.widget_isha
            )
            labels.forEachIndexed { index, label ->
                views.setTextViewText(ids[index], "$label\n${formatTime(todayTimes[index])}")
            }

            val openApp = PendingIntent.getActivity(
                context,
                widgetId,
                Intent(context, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            manager.updateAppWidget(widgetId, views)
        }
    }
}
