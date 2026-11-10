package com.admurs.oyunaltyazi.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager

/**
 * Alternatif mod: Erişilebilirlik izni yerine "Diğer uygulamaların üzerinde göster" izniyle çalışır.
 * Ön plan servisi olarak açık kalır; katmanı ve alıcıyı o çalıştırır.
 */
class OverlayFgService : Service() {

    private var overlay: SubtitleOverlay? = null
    private var receiver: SubtitleReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            startAsForeground()
        } catch (e: Exception) {
            DiagLog.add(this, "Alternatif mod başlatılamadı: ${e.javaClass.simpleName}: ${e.message}")
            stopSelf()
            return
        }
        if (OverlayService.instance != null) {
            DiagLog.add(this, "Erişilebilirlik modu zaten açık; alternatif moda gerek yok")
            stopSelf()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            DiagLog.add(this, "Alternatif mod: 'Diğer uygulamaların üzerinde göster' izni yok")
            stopSelf()
            return
        }
        val o = SubtitleOverlay(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        if (!o.attach()) {
            stopSelf()
            return
        }
        overlay = o
        receiver = SubtitleReceiver(this, o).also { it.start() }
        running = true
        DiagLog.add(this, "Alternatif mod AÇIK")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        running = false
        receiver?.stop()
        overlay?.detach()
        receiver = null
        overlay = null
        DiagLog.add(this, "Alternatif mod kapandı")
        super.onDestroy()
    }

    private fun startAsForeground() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Altyazı katmanı", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("Oyun Altyazı TV")
            .setContentText("Altyazı katmanı açık")
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL = "overlay"
        private const val NOTIFICATION_ID = 7

        @Volatile var running = false
    }
}
