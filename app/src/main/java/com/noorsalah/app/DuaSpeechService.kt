package com.noorsalah.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Reads the reminder dua using the Arabic voice installed on the device. */
class DuaSpeechService : Service(), TextToSpeech.OnInitListener {
    private var speech: TextToSpeech? = null
    private var textToSpeak: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        textToSpeak = intent?.getStringExtra(EXTRA_TEXT).orEmpty()
        if (textToSpeak.isBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startSpeechForeground()
        speech?.shutdown()
        speech = TextToSpeech(applicationContext, this)
        return START_NOT_STICKY
    }

    private fun startSpeechForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "قراءة الدعاء صوتياً", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "تشغيل الدعاء بصوت عربي"
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
            )
        }
        val openApp = PendingIntent.getActivity(
            this, 90415, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this).setPriority(Notification.PRIORITY_LOW)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("نور الصلاة")
            .setContentText("تُقرأ كلمات الدعاء الآن")
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onInit(status: Int) {
        val engine = speech ?: run {
            stopSelf()
            return
        }
        if (status != TextToSpeech.SUCCESS) {
            stopSelf()
            return
        }
        val languageResult = engine.setLanguage(Locale("ar"))
        if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            stopSelf()
            return
        }
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        engine.setSpeechRate(0.88f)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { stopSelf() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { stopSelf() }
            override fun onError(utteranceId: String?, errorCode: Int) { stopSelf() }
        })
        val result = engine.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "noor_dua_reminder")
        if (result == TextToSpeech.ERROR) stopSelf()
    }

    override fun onDestroy() {
        speech?.stop()
        speech?.shutdown()
        speech = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TEXT = "dua_text"
        private const val CHANNEL_ID = "noor_dua_speech_playback"
        private const val NOTIFICATION_ID = 90416
    }
}
