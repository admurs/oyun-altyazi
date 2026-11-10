package com.admurs.oyunaltyazi.tv

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Erişilebilirlik modu: Çizdiği pencere TV'nin HDMI girişi dahil her uygulamanın üstünde kalır.
 * Ekrandaki içeriği okumaz, olay dinlemez, ses çıkarmaz.
 */
class OverlayService : AccessibilityService() {

    private var overlay: SubtitleOverlay? = null
    private var receiver: SubtitleReceiver? = null

    override fun onCreate() {
        super.onCreate()
        DiagLog.add(this, "Erişilebilirlik servisi oluşturuldu")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        DiagLog.add(this, "Erişilebilirlik servisi BAĞLANDI")
        // Alternatif mod açıksa kapat; aynı portu iki servis dinleyemez.
        stopService(Intent(this, OverlayFgService::class.java))
        instance = this
        val o = SubtitleOverlay(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        if (o.attach()) DiagLog.add(this, "Katman penceresi hazır (erişilebilirlik)")
        overlay = o
        receiver = SubtitleReceiver(this, o).also { it.start() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        DiagLog.add(this, "Erişilebilirlik servisi kesildi (onInterrupt)")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        DiagLog.add(this, "Erişilebilirlik servisi sistem tarafından ayrıldı (kapatıldı)")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        DiagLog.add(this, "Erişilebilirlik servisi sonlandı")
        instance = null
        receiver?.stop()
        overlay?.detach()
        receiver = null
        overlay = null
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: OverlayService? = null
    }
}
