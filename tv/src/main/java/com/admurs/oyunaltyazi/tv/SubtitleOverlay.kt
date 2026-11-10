package com.admurs.oyunaltyazi.tv

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * Ekranın altındaki altyazı penceresi. Hangi servisin açtığına göre pencere türü değişir:
 * erişilebilirlik modu → TYPE_ACCESSIBILITY_OVERLAY, alternatif mod → TYPE_APPLICATION_OVERLAY.
 * Tüm çağrılar ana iş parçacığında yapılmalı.
 */
class SubtitleOverlay(private val context: Context, private val windowType: Int) {

    private val main = Handler(Looper.getMainLooper())
    private var view: TextView? = null
    private val hide = Runnable { view?.visibility = View.GONE }

    private val windowManager get() = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    fun attach(): Boolean {
        if (view != null) return true
        val metrics = context.resources.displayMetrics
        val width = maxOf(metrics.widthPixels, metrics.heightPixels)
        val height = minOf(metrics.widthPixels, metrics.heightPixels)
        val text = TextView(context).apply {
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
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (height * 0.07f).toInt()
        }
        return try {
            windowManager.addView(text, params)
            view = text
            active = this
            true
        } catch (e: Exception) {
            DiagLog.add(context, "Katman penceresi açılamadı: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    fun detach() {
        main.removeCallbacks(hide)
        view?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        view = null
        if (active === this) active = null
    }

    fun show(text: String, fontSize: Int = DEFAULT_FONT, durationMs: Int = 30_000) {
        val target = view ?: return
        if (text.isBlank()) {
            clear()
            return
        }
        val metrics = context.resources.displayMetrics
        val screenHeight = minOf(metrics.widthPixels, metrics.heightPixels)
        // fontSize, 1080 satırlık ekrandaki piksel boyudur; TV'nin çözünürlüğüne ölçeklenir.
        target.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * screenHeight / 1080f)
        target.text = text
        target.visibility = View.VISIBLE
        main.removeCallbacks(hide)
        main.postDelayed(hide, durationMs.toLong())
    }

    fun clear() {
        main.removeCallbacks(hide)
        view?.visibility = View.GONE
    }

    companion object {
        const val DEFAULT_FONT = 46

        /** Şu an açık olan katman (test düğmeleri için). */
        @Volatile var active: SubtitleOverlay? = null
    }
}
