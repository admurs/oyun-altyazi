package com.admurs.oyunaltyazi.tv

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import org.json.JSONObject

/**
 * Altyazı katmanı. Erişilebilirlik servisi olduğu için çizdiği pencere, TV'nin HDMI girişi
 * dahil her uygulamanın üstünde kalır; oyun görüntüsü TV'nin kendi yolundan (oyun modu) akmaya devam eder.
 */
class OverlayService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private var server: SubtitleHttpServer? = null
    private var overlay: TextView? = null
    private var nsdListener: NsdManager.RegistrationListener? = null
    private val hide = Runnable { overlay?.visibility = View.GONE }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        createOverlay()
        server = SubtitleHttpServer(PORT) { body -> handleMessage(body) }.also { it.start() }
        registerNsd()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        server?.stop()
        unregisterNsd()
        main.removeCallbacks(hide)
        overlay?.let {
            try {
                windowManager().removeView(it)
            } catch (_: Exception) {
            }
        }
        overlay = null
        super.onDestroy()
    }

    /** Ağ iş parçacığından çağrılır; mesaj geçerliyse arayüze aktarır. */
    private fun handleMessage(body: String): Boolean {
        val json = try {
            JSONObject(body)
        } catch (_: Exception) {
            return false
        }
        when (json.optString("type")) {
            "show" -> {
                val text = json.optString("text")
                val size = json.optInt("fontSize", DEFAULT_FONT)
                val duration = json.optInt("durationMs", 30_000)
                main.post { show(text, size, duration) }
            }
            "clear" -> main.post { clear() }
            else -> return false
        }
        messageCount++
        return true
    }

    fun show(text: String, fontSize: Int = DEFAULT_FONT, durationMs: Int = 30_000) {
        val view = overlay ?: return
        if (text.isBlank()) {
            clear()
            return
        }
        val metrics = resources.displayMetrics
        val screenHeight = minOf(metrics.widthPixels, metrics.heightPixels)
        // fontSize, 1080 satırlık bir ekrandaki piksel boyu olarak gelir; TV'nin çözünürlüğüne ölçeklenir.
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * screenHeight / 1080f)
        view.text = text
        view.visibility = View.VISIBLE
        main.removeCallbacks(hide)
        main.postDelayed(hide, durationMs.toLong())
    }

    fun clear() {
        main.removeCallbacks(hide)
        overlay?.visibility = View.GONE
    }

    private fun createOverlay() {
        val metrics = resources.displayMetrics
        val width = maxOf(metrics.widthPixels, metrics.heightPixels)
        val height = minOf(metrics.widthPixels, metrics.heightPixels)
        val view = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(184, 0, 0, 0))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
            val padH = (height * 0.025f).toInt()
            val padV = (height * 0.01f).toInt()
            setPadding(padH, padV, padH, padV)
            maxWidth = (width * 0.8f).toInt()
            visibility = View.GONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (height * 0.07f).toInt()
        }
        try {
            windowManager().addView(view, params)
            overlay = view
        } catch (e: Exception) {
            SubtitleHttpServer.lastError = "Katman açılamadı: ${e.message}"
        }
    }

    private fun windowManager() = getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private fun registerNsd() {
        val nsd = getSystemService(Context.NSD_SERVICE) as NsdManager
        val info = NsdServiceInfo().apply {
            serviceName = "OyunAltyazi-TV"
            serviceType = SERVICE_TYPE
            port = PORT
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                SubtitleHttpServer.lastError = "Ağ duyurusu başarısız ($errorCode)"
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            nsdListener = listener
        } catch (e: Exception) {
            SubtitleHttpServer.lastError = "Ağ duyurusu başarısız: ${e.message}"
        }
    }

    private fun unregisterNsd() {
        val listener = nsdListener ?: return
        try {
            (getSystemService(Context.NSD_SERVICE) as NsdManager).unregisterService(listener)
        } catch (_: Exception) {
        }
        nsdListener = null
    }

    companion object {
        const val PORT = 8788
        const val SERVICE_TYPE = "_oyunaltyazi._tcp"
        const val DEFAULT_FONT = 46

        @Volatile var instance: OverlayService? = null
        @Volatile var messageCount = 0
    }
}
