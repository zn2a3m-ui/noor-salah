package com.noorsalah.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/**
 * Plays the complete adhan as foreground media playback.
 * Exact-alarm invocations are an Android exemption for starting a foreground
 * service from the background; boot broadcasts only reschedule alarms.
 */
class AdhanPlaybackService : Service() {
    private var player: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            releasePlayer()
            stopForegroundCompat()
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val prayer = intent?.getStringExtra(EXTRA_PRAYER) ?: "الصلاة"
        val selectedAudio = intent?.getStringExtra(EXTRA_AUDIO) ?: "أذان كامل"
        val customUri = intent?.getStringExtra(EXTRA_CUSTOM_URI)?.let(Uri::parse)

        startForegroundCompat(prayer)
        startPlayback(selectedAudio, customUri)
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(prayer: String) {
        createPlaybackChannel()
        val openApp = PendingIntent.getActivity(
            this,
            72001,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, AdhanPlaybackService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            72002,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this).setPriority(Notification.PRIORITY_LOW)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("حان وقت صلاة $prayer")
            .setContentText("يُشغّل الأذان الآن • اضغط لإيقافه")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_pause,
                    "إيقاف الأذان",
                    stopPending
                ).build()
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createPlaybackChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "تشغيل الأذان",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "إشعار مستمر أثناء تشغيل تسجيل الأذان كاملاً"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun startPlayback(selectedAudio: String, customUri: Uri?) {
        releasePlayer()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val media = MediaPlayer()
        try {
            media.setAudioAttributes(audioAttributes)
            media.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
            if (selectedAudio.startsWith("صوت مخصص") && customUri != null) {
                media.setDataSource(this, customUri)
            } else {
                val resource = if (selectedAudio.startsWith("أذان قصير")) {
                    R.raw.adhan_short
                } else {
                    R.raw.adhan_full
                }
                media.setDataSource(this, Uri.parse("android.resource://$packageName/$resource"))
            }
            player = media
            media.setOnPreparedListener { prepared ->
                if (player === prepared) prepared.start()
            }
            media.setOnCompletionListener {
                releasePlayer()
                stopForegroundCompat()
                stopSelf()
            }
            media.setOnErrorListener { _, _, _ ->
                releasePlayer()
                stopForegroundCompat()
                stopSelf()
                true
            }
            media.prepareAsync()
        } catch (_: Exception) {
            try { media.release() } catch (_: Exception) {}
            player = null
            if (selectedAudio.startsWith("صوت مخصص")) {
                startPlayback("أذان كامل", null)
            } else {
                stopForegroundCompat()
                stopSelf()
            }
        }
    }

    private fun releasePlayer() {
        player?.let {
            try { if (it.isPlaying) it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        player = null
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "noor_adhan_playback"
        private const val NOTIFICATION_ID = 72003
        const val ACTION_STOP = "com.noorsalah.app.STOP_ADHAN_PLAYBACK"
        const val EXTRA_PRAYER = "prayer_name"
        const val EXTRA_AUDIO = "selected_audio"
        const val EXTRA_CUSTOM_URI = "custom_audio_uri"
    }
}
