package com.admurs.oyunaltyazi.tv

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
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

/** Kurulum, test ve tanılama ekranı. */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var diag: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refresher = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (resources.displayMetrics.density * 28).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        column.addView(text(getString(R.string.app_name) + "  (sürüm ${versionName()})", 30f, Color.WHITE))
        status = text("", 19f, Color.WHITE).apply { setPadding(0, pad / 3, 0, pad / 3) }
        column.addView(status)

        column.addView(text("Altyazı katmanını aç (ikisinden biri yeterli):", 18f, Color.LTGRAY))
        column.addView(button("A) Erişilebilirlik modu: ayarı aç") { openAccessibilitySettings() })
        column.addView(button("B) Alternatif mod: üstte gösterme izni ile başlat") { startAlternativeMode() })

        column.addView(text("Telefonsuz Xbox (önce B modunu aç):", 18f, Color.LTGRAY).apply { setPadding(0, pad / 2, 0, 0) })
        column.addView(button("C) Xbox ile Türkçe altyazı") {
            startActivity(Intent(this, XboxProbeActivity::class.java))
        })

        column.addView(text("Test:", 18f, Color.LTGRAY).apply { setPadding(0, pad / 2, 0, 0) })
        column.addView(button("Burada test altyazısı göster") {
            showTest("Merhaba! Altyazı katmanı çalışıyor.", 5_000)
        })
        column.addView(button("HDMI testi: 30 sn altyazı göster") {
            if (showTest("HDMI testi: Bu yazıyı oyunun üstünde görüyorsan TV hazır.", 30_000)) {
                Toast.makeText(this, "Şimdi Xbox'ın bağlı olduğu HDMI girişine geç.", Toast.LENGTH_LONG).show()
            }
        })

        column.addView(text("Tanılama kaydı:", 18f, Color.LTGRAY).apply { setPadding(0, pad / 2, 0, 0) })
        column.addView(button("Kaydı temizle") {
            DiagLog.clear(this)
            refresh()
        })
        diag = text("", 14f, Color.LTGRAY)
        column.addView(diag)

        column.addView(text(
            "Erişilebilirlik ayarı açılıp kendiliğinden kapanıyorsa B modunu dene. " +
                "İkisi de olmazsa tabletten ADB ile şu komutlardan birini çalıştır:\n" +
                "settings put secure enabled_accessibility_services $PACKAGE/$PACKAGE.OverlayService\n" +
                "settings put secure accessibility_enabled 1\n" +
                "veya: appops set $PACKAGE SYSTEM_ALERT_WINDOW allow",
            14f, Color.GRAY,
        ).apply { setPadding(0, pad / 2, 0, 0) })

        setContentView(ScrollView(this).apply {
            addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
        DiagLog.add(this, "Uygulama açıldı")
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresher)
    }

    override fun onPause() {
        handler.removeCallbacks(refresher)
        super.onPause()
    }

    private fun text(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        textSize = 19f
        setAllCaps(false)
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setOnClickListener { onClick() }
    }

    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }

    private fun showTest(message: String, durationMs: Int): Boolean {
        val overlay = SubtitleOverlay.active
        if (overlay == null) {
            Toast.makeText(this, "Önce A veya B ile katmanı aç.", Toast.LENGTH_LONG).show()
            return false
        }
        overlay.show(message, SubtitleOverlay.DEFAULT_FONT, durationMs)
        return true
    }

    private fun openAccessibilitySettings() {
        // Önce doğrudan bizim servisin sayfasını dene (yeni Android sürümleri), olmazsa genel listeyi aç.
        val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            .putExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName(this, OverlayService::class.java).flattenToString())
        val candidates = listOf(details, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        for (intent in candidates) {
            try {
                startActivity(intent)
                Toast.makeText(this, "\"${getString(R.string.service_label)}\"ı aç. TalkBack'i açma.", Toast.LENGTH_LONG).show()
                return
            } catch (_: Exception) {
            }
        }
        Toast.makeText(this, "Ayarlar açılamadı: Ayarlar > Sistem > Erişilebilirlik", Toast.LENGTH_LONG).show()
    }

    private fun startAlternativeMode() {
        if (OverlayService.instance != null) {
            Toast.makeText(this, "Erişilebilirlik modu zaten açık; gerek yok.", Toast.LENGTH_LONG).show()
            return
        }
        if (Settings.canDrawOverlays(this)) {
            startForegroundService(Intent(this, OverlayFgService::class.java))
            Toast.makeText(this, "Alternatif mod başlatılıyor…", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        try {
            startActivity(intent)
            Toast.makeText(this, "İzni ver, sonra geri dönüp B'ye tekrar bas.", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            DiagLog.add(this, "Bu TV'de 'üstte göster' izin ekranı yok; ADB komutu gerekiyor")
            Toast.makeText(this, "Bu TV izin ekranını göstermiyor. ADB komutu gerekecek (alttaki not).", Toast.LENGTH_LONG).show()
        }
    }

    private fun refresh() {
        val mode = when {
            OverlayService.instance != null -> "Erişilebilirlik modu AÇIK ✔"
            OverlayFgService.running -> "Alternatif mod AÇIK ✔"
            else -> "KAPALI ✘"
        }
        val addresses = localAddresses()
        status.text = buildString {
            append("Katman: ").append(mode).append('\n')
            append("Üstte gösterme izni: ").append(if (Settings.canDrawOverlays(this@MainActivity)) "var" else "yok").append('\n')
            append("TV adresi: ")
                .append(if (addresses.isEmpty()) "ağ yok" else addresses.joinToString { "$it:${SubtitleReceiver.PORT}" })
                .append('\n')
            append("Alınan mesaj: ").append(SubtitleReceiver.messageCount)
            SubtitleHttpServer.lastError?.let { append("\nHata: ").append(it) }
        }
        diag.text = DiagLog.read(this).ifBlank { "(kayıt yok)" }
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

    companion object {
        private const val PACKAGE = "com.admurs.oyunaltyazi.tv"
    }
}
