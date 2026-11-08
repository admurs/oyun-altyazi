package com.admurs.oyunaltyazi.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.Inet4Address
import java.net.NetworkInterface

/** Kurulum ve test ekranı: servis durumu, TV'nin adresi, katman testi. */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refresher = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (resources.displayMetrics.density * 32).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        column.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 32f
            setTextColor(Color.WHITE)
        })
        status = TextView(this).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, pad / 2, 0, pad / 2)
        }
        column.addView(status)
        column.addView(button("1) Erişilebilirlik ayarlarını aç") { openAccessibilitySettings() })
        column.addView(button("2) Burada test altyazısı göster") {
            showTest("Merhaba! Altyazı katmanı çalışıyor.", 5_000)
        })
        column.addView(button("3) HDMI testi: 30 sn altyazı göster") {
            showTest("HDMI testi: Bu yazıyı oyun görüntüsünün üstünde görüyorsan TV hazır.", 30_000)
            Toast.makeText(this, "Şimdi Xbox'ın bağlı olduğu HDMI girişine geç.", Toast.LENGTH_LONG).show()
        })
        column.addView(TextView(this).apply {
            textSize = 16f
            setTextColor(Color.LTGRAY)
            setPadding(0, pad / 2, 0, 0)
            text = "Kurulum: 1'e bas, listeden \"${getString(R.string.service_label)}\" servisini aç. " +
                "Sonra 3'e basıp HDMI girişine geç; yazı oyunun üstünde görünüyorsa tabletten bağlanabilirsin."
        })
        setContentView(ScrollView(this).apply {
            addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresher)
    }

    override fun onPause() {
        handler.removeCallbacks(refresher)
        super.onPause()
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        textSize = 20f
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setOnClickListener { onClick() }
    }

    private fun showTest(text: String, durationMs: Int) {
        val service = OverlayService.instance
        if (service == null) {
            Toast.makeText(this, "Önce erişilebilirlik servisini aç (adım 1).", Toast.LENGTH_LONG).show()
            return
        }
        service.show(text, OverlayService.DEFAULT_FONT, durationMs)
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "Ayarlar açılamadı. Ayarlar > Sistem > Erişilebilirlik yolunu dene.", Toast.LENGTH_LONG).show()
        }
    }

    private fun refresh() {
        val enabled = OverlayService.instance != null
        val addresses = localAddresses()
        status.text = buildString {
            append("Katman servisi: ").append(if (enabled) "AÇIK ✔" else "KAPALI ✘").append('\n')
            append("TV adresi: ").append(if (addresses.isEmpty()) "ağ yok" else addresses.joinToString { "$it:${OverlayService.PORT}" }).append('\n')
            append("Alınan mesaj: ").append(OverlayService.messageCount)
            SubtitleHttpServer.lastError?.let { append("\nHata: ").append(it) }
        }
    }

    private fun localAddresses(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
    } catch (_: Exception) {
        emptyList()
    }
}
