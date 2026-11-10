package com.admurs.oyunaltyazi

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.admurs.oyunaltyazi.core.Region
import com.admurs.oyunaltyazi.core.RegionMath
import kotlin.concurrent.thread

/**
 * Tek ekranlık kontrol paneli:
 * 1) TV'yi bul / bağlan / test altyazısı gönder
 * 2) Çeviri modelini indir
 * 3) Altyazı bölgesini ayarla
 * 4) Yakalamayı başlat, önizlemeyle kontrol et
 */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var discovery: TvDiscovery? = null

    private lateinit var tvStatus: TextView
    private lateinit var foundList: LinearLayout
    private lateinit var hostInput: EditText
    private lateinit var testInput: EditText
    private lateinit var modelStatus: TextView
    private lateinit var captureStatus: TextView
    private lateinit var preview: ImageView
    private lateinit var ocrText: TextView

    private val refresher = object : Runnable {
        override fun run() {
            refreshCaptureStatus()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
        }
        setContentView(buildUi())
        updateRegionInfo()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresher)
    }

    override fun onPause() {
        handler.removeCallbacks(refresher)
        super.onPause()
    }

    override fun onDestroy() {
        discovery?.stop()
        super.onDestroy()
    }

    // ---------- Arayüz ----------

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun buildUi(): ScrollView {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }

        column.addView(title("1. TV bağlantısı"))
        tvStatus = info("Kayıtlı TV: ${AppSettings.tvHost(this) ?: "yok"}")
        column.addView(tvStatus)
        column.addView(button("TV'yi ağda ara") { startDiscovery() })
        foundList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(foundList)
        hostInput = EditText(this).apply {
            hint = "veya TV adresini yaz (örn. 192.168.1.40)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(AppSettings.tvHost(this@MainActivity) ?: "")
        }
        column.addView(hostInput)
        column.addView(row(
            button("Adresi kaydet") { saveHost(hostInput.text.toString().trim()) },
            button("Bağlantıyı test et") { testConnection() },
        ))
        testInput = EditText(this).apply { setText("Merhaba! Bu bir deneme altyazısıdır.") }
        column.addView(testInput)
        column.addView(row(
            button("TV'ye gönder") { sendTest() },
            button("TV'de temizle") { currentTv()?.clear() },
        ))

        column.addView(title("2. Çeviri modeli"))
        modelStatus = info("İngilizce→Türkçe modeli ilk kullanımda indirilir (~30 MB, Wi-Fi önerilir).")
        column.addView(modelStatus)
        column.addView(button("Modeli şimdi indir") { downloadModel() })

        column.addView(title("3. Altyazı bölgesi (ekranın yüzdesi)"))
        column.addView(info(
            "Yakalama çalışırken Xbox'ta altyazılı bir sahnedeyken buraya dön. Önizlemede oyun görüntüsü " +
                "ve kırmızı çerçeve görünür; çerçeveyi altyazıyı kapsayacak şekilde kaydırıcılarla ayarla.",
        ))
        val region = AppSettings.region(this)
        column.addView(slider("Üst kenar", "top", region.top))
        column.addView(slider("Alt kenar", "bottom", region.bottom))
        column.addView(slider("Sol kenar", "left", region.left))
        column.addView(slider("Sağ kenar", "right", region.right))
        regionInfo = info("")
        column.addView(regionInfo)
        column.addView(button("Varsayılan bölgeye dön") { resetRegion() })
        column.addView(fontSlider())

        column.addView(title("4. Yakalama"))
        column.addView(info(
            "Başlat'a bas, izin ekranında \"Tüm ekran\"ı ya da Xbox uygulamasını seç. " +
                "Sonra Xbox uygulamasına geçip oyunu oyna. Durdurmak için bildirimdeki Durdur'a bas.",
        ))
        column.addView(row(
            button("Başlat") { requestCapture() },
            button("Durdur") { stopCapture() },
        ))
        captureStatus = info("")
        column.addView(captureStatus)
        column.addView(button("Önizlemeyi yenile") { refreshPreview() })
        preview = ImageView(this).apply {
            adjustViewBounds = true
            setBackgroundColor(Color.DKGRAY)
            minimumHeight = dp(60)
        }
        column.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        ocrText = info("")
        column.addView(ocrText)

        return ScrollView(this).apply {
            addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun title(text: String) = TextView(this).apply {
        this.text = text
        textSize = 20f
        setTextColor(Color.WHITE)
        setPadding(0, dp(20), 0, dp(6))
    }

    private fun info(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(Color.LTGRAY)
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun row(vararg views: Button) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEach { addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
    }

    private fun slider(label: String, key: String, initial: Int): LinearLayout {
        val caption = info("$label: %$initial")
        val bar = SeekBar(this).apply {
            max = 100
            progress = initial
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    caption.text = "$label: %$progress"
                    AppSettings.setRegionValue(this@MainActivity, key, progress)
                    updateRegionInfo()
                    if (fromUser) drawPreview()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }
        sliders[key] = bar
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(caption)
            addView(bar)
        }
    }

    private val sliders = mutableMapOf<String, SeekBar>()
    private lateinit var regionInfo: TextView

    private fun updateRegionInfo() {
        if (!::regionInfo.isInitialized) return
        val raw = AppSettings.region(this)
        val used = raw.sanitized()
        regionInfo.text = "Kullanılan bölge: üst %${used.top} – alt %${used.bottom}, sol %${used.left} – sağ %${used.right}" +
            if (used != raw) "  (kenarlar çakıştığı için düzeltildi)" else ""
    }

    private fun resetRegion() {
        AppSettings.resetRegion(this)
        val d = Region.DEFAULT
        sliders["top"]?.progress = d.top
        sliders["bottom"]?.progress = d.bottom
        sliders["left"]?.progress = d.left
        sliders["right"]?.progress = d.right
        updateRegionInfo()
        drawPreview()
    }

    /** Son tam kareyi kırmızı bölge çerçevesiyle gösterir. */
    private fun drawPreview() {
        val frame = CaptureService.lastFrame ?: return
        val fullWidth = CaptureService.frameWidth
        val fullHeight = CaptureService.frameHeight
        if (fullWidth <= 0 || fullHeight <= 0) return
        val copy = frame.copy(Bitmap.Config.ARGB_8888, true)
        val rect = RegionMath.cropRect(fullWidth, fullHeight, AppSettings.region(this))
        val sx = copy.width.toFloat() / fullWidth
        val sy = copy.height.toFloat() / fullHeight
        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        Canvas(copy).drawRect(rect.left * sx, rect.top * sy, rect.right * sx, rect.bottom * sy, paint)
        preview.setImageBitmap(copy)
    }

    private fun fontSlider(): LinearLayout {
        val initial = AppSettings.fontSize(this)
        val caption = info("TV yazı boyu: $initial")
        val bar = SeekBar(this).apply {
            max = 90
            progress = initial - 24
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    val size = progress + 24
                    caption.text = "TV yazı boyu: $size"
                    AppSettings.setFontSize(this@MainActivity, size)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    currentTv()?.show("Yazı boyu örneği: Bu altyazı TV'de böyle görünecek.", AppSettings.fontSize(this@MainActivity), 4000)
                }
            })
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(caption)
            addView(bar)
        }
    }

    // ---------- TV ----------

    private var testClient: TvClient? = null

    private fun currentTv(): TvClient? {
        val host = AppSettings.tvHost(this)
        if (host == null) {
            toast("Önce TV'yi seç ya da adresini kaydet.")
            return null
        }
        val existing = testClient
        if (existing != null && existingHost == host) return existing
        existing?.shutdown()
        existingHost = host
        return TvClient(host).also { testClient = it }
    }

    private var existingHost: String? = null

    private fun startDiscovery() {
        foundList.removeAllViews()
        foundList.addView(info("Aranıyor… (TV'de Oyun Altyazı TV servisi açık olmalı)"))
        discovery?.stop()
        val seen = mutableSetOf<String>()
        discovery = TvDiscovery(this) { name, host ->
            runOnUiThread {
                if (seen.add(host)) {
                    foundList.addView(button("$name — $host") { saveHost(host) })
                }
            }
        }.also { it.start() }
        handler.postDelayed({ discovery?.stop() }, 15_000)
    }

    private fun saveHost(host: String) {
        if (host.isEmpty()) {
            toast("Adres boş.")
            return
        }
        AppSettings.setTvHost(this, host)
        hostInput.setText(host)
        tvStatus.text = "Kayıtlı TV: $host"
        testConnection()
    }

    private fun testConnection() {
        val host = AppSettings.tvHost(this) ?: return toast("Önce TV adresini kaydet.")
        tvStatus.text = "Kayıtlı TV: $host — deneniyor…"
        thread {
            val ok = TvClient(host).hello()
            runOnUiThread {
                tvStatus.text = if (ok) "Kayıtlı TV: $host — bağlantı TAMAM ✔" else
                    "Kayıtlı TV: $host — ulaşılamadı ✘ (${TvClient.lastError ?: "bilinmeyen hata"})"
            }
        }
    }

    private fun sendTest() {
        val text = testInput.text.toString().trim()
        if (text.isEmpty()) return
        currentTv()?.show(text, AppSettings.fontSize(this), 8000)
        handler.postDelayed({
            TvClient.lastError?.let { toast(it) } ?: toast("Gönderildi.")
        }, 1200)
    }

    // ---------- Çeviri ----------

    private fun downloadModel() {
        modelStatus.text = "İndiriliyor…"
        val translator = SubtitleTranslator()
        translator.downloadModel()
            .addOnSuccessListener {
                thread {
                    val sample = try {
                        translator.translateBlocking("Where are you going? We need to talk.")
                    } catch (e: Exception) {
                        "çeviri hatası: ${e.message}"
                    }
                    translator.close()
                    runOnUiThread { modelStatus.text = "Model hazır ✔ Örnek: \"$sample\"" }
                }
            }
            .addOnFailureListener {
                translator.close()
                modelStatus.text = "İndirilemedi: ${it.message}"
            }
    }

    // ---------- Yakalama ----------

    private fun requestCapture() {
        if (AppSettings.tvHost(this) == null) return toast("Önce TV'yi seç.")
        if (CaptureService.running) return toast("Zaten çalışıyor.")
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_CAPTURE) return
        if (resultCode != RESULT_OK || data == null) return toast("Ekran kaydı izni verilmedi.")
        val intent = Intent(this, CaptureService::class.java)
            .putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
            .putExtra(CaptureService.EXTRA_DATA, data)
        startForegroundService(intent)
        toast("Başladı. Şimdi Xbox uygulamasına geç.")
    }

    private fun stopCapture() {
        stopService(Intent(this, CaptureService::class.java))
    }

    private fun refreshCaptureStatus() {
        captureStatus.text = buildString {
            append("Durum: ").append(CaptureService.status)
            append(" | İşlenen kare: ").append(CaptureService.frameCount)
            append(" | TV'ye giden: ").append(TvClient.sentCount)
            TvClient.lastError?.let { append("\nTV hatası: ").append(it) }
        }
    }

    private fun refreshPreview() {
        if (CaptureService.lastFrame == null) {
            toast("Henüz kare yok. Yakalamayı başlat, Xbox'ta oyunu aç ve geri dön.")
            return
        }
        drawPreview()
        ocrText.text = "Okunan: ${CaptureService.lastOcr.ifBlank { "(yazı yok)" }}\n" +
            "Çeviri: ${CaptureService.lastTranslation.ifBlank { "-" }}"
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REQ_CAPTURE = 10
        private const val REQ_NOTIFICATIONS = 11
    }
}
