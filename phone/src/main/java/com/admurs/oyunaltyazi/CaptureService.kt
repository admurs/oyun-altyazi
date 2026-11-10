package com.admurs.oyunaltyazi

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import android.graphics.Matrix
import com.admurs.oyunaltyazi.core.RegionMath
import com.admurs.oyunaltyazi.core.SubtitleEvent
import com.admurs.oyunaltyazi.core.SubtitleStabilizer
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

/**
 * Ekranı (Xbox uygulamasının Remote Play görüntüsünü) okur, altyazı bölgesini kırpar,
 * ML Kit ile yazıyı tanır, çevirir ve TV'ye gönderir. Görüntü hiçbir yere kaydedilmez veya gönderilmez.
 */
class CaptureService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureWidth = 0
    private var captureHeight = 0

    private val worker = HandlerThread("altyazi-ocr").apply { start() }
    private val workerHandler = Handler(worker.looper)
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val translator = SubtitleTranslator()
    private val stabilizer = SubtitleStabilizer()
    private var tv: TvClient? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startForegroundWithNotification()
        } catch (e: Exception) {
            status = "Servis başlatılamadı: ${e.message}"
            stopSelf()
            return START_NOT_STICKY
        }
        if (projection != null) return START_NOT_STICKY

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }
        val host = AppSettings.tvHost(this)
        if (resultCode != Activity.RESULT_OK || data == null || host == null) {
            status = if (host == null) "Önce TV'yi seç." else "Ekran kaydı izni alınamadı."
            stopSelf()
            return START_NOT_STICKY
        }
        tv = TvClient(host)

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = try {
            manager.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            status = "Ekran kaydı başlatılamadı: ${e.message}"
            stopSelf()
            return START_NOT_STICKY
        }
        projection = mp
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, Handler(Looper.getMainLooper()))

        createDisplay(mp)
        running = true
        status = "Çalışıyor"
        workerHandler.post(loop)
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Tablet döndürülünce sanal ekranı yeni yöne göre yeniden boyutlandır.
        workerHandler.post { resizeDisplayIfNeeded() }
    }

    override fun onDestroy() {
        running = false
        workerHandler.removeCallbacksAndMessages(null)
        workerHandler.post {
            virtualDisplay?.release()
            imageReader?.close()
            recognizer.close()
            translator.close()
            worker.quitSafely()
        }
        try {
            projection?.stop()
        } catch (_: Exception) {
        }
        projection = null
        tv?.clear()
        tv?.shutdown()
        if (status == "Çalışıyor") status = "Durduruldu"
        super.onDestroy()
    }

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            try {
                processFrame()
            } catch (e: Exception) {
                status = "Hata: ${e.javaClass.simpleName}: ${e.message}"
            }
            if (running) workerHandler.postDelayed(this, AppSettings.FRAME_INTERVAL_MS)
        }
    }

    private fun processFrame() {
        val reader = imageReader ?: return
        val image = reader.acquireLatestImage() ?: return
        val crop: Bitmap
        try {
            val plane = image.planes[0]
            val pixelStride = plane.pixelStride
            val rowPadding = plane.rowStride - pixelStride * image.width
            val width = image.width
            val height = image.height
            val padded = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
            padded.copyPixelsFromBuffer(plane.buffer)
            val rect = RegionMath.cropRect(width, height, AppSettings.region(this))
            crop = Bitmap.createBitmap(padded, rect.left, rect.top, rect.width, rect.height)
            // Bölge ayarı için küçültülmüş tam kare (önizlemede kırmızı çerçeveyle gösterilir)
            val scale = PREVIEW_WIDTH.toFloat() / width
            lastFrame = Bitmap.createBitmap(padded, 0, 0, width, height, Matrix().apply { setScale(scale, scale) }, true)
            frameWidth = width
            frameHeight = height
            if (crop !== padded && lastFrame !== padded) padded.recycle()
        } finally {
            image.close()
        }
        lastCrop = crop

        val text = Tasks.await(recognizer.process(InputImage.fromBitmap(crop, 0)), 5, TimeUnit.SECONDS).text
        lastOcr = text
        frameCount++
        status = "Çalışıyor"

        when (val event = stabilizer.push(text)) {
            is SubtitleEvent.Show -> {
                val translated = translator.translateBlocking(event.text)
                lastTranslation = translated
                tv?.show(translated, AppSettings.fontSize(this))
            }
            SubtitleEvent.Clear -> {
                lastTranslation = ""
                tv?.clear()
            }
            null -> Unit
        }
    }

    private fun screenSize(): Triple<Int, Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.densityDpi
        return if (Build.VERSION.SDK_INT >= 30) {
            val bounds = wm.currentWindowMetrics.bounds
            Triple(bounds.width(), bounds.height(), density)
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            Triple(metrics.widthPixels, metrics.heightPixels, density)
        }
    }

    private fun createDisplay(mp: MediaProjection) {
        val (width, height, density) = screenSize()
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        captureWidth = width
        captureHeight = height
        virtualDisplay = mp.createVirtualDisplay(
            "oyun-altyazi", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, null,
        )
    }

    private fun resizeDisplayIfNeeded() {
        val display = virtualDisplay ?: return
        val (width, height, density) = screenSize()
        if (width == captureWidth && height == captureHeight) return
        val newReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        display.resize(width, height, density)
        display.surface = newReader.surface
        imageReader?.close()
        imageReader = newReader
        captureWidth = width
        captureHeight = height
    }

    private fun startForegroundWithNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Altyazı yakalama", NotificationManager.IMPORTANCE_LOW),
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, CaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("Oyun Altyazı çalışıyor")
            .setContentText("Altyazılar okunup TV'ye gönderiliyor")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_stat), "Durdur", stopIntent).build(),
            )
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    companion object {
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        const val ACTION_STOP = "com.admurs.oyunaltyazi.STOP"
        private const val CHANNEL = "capture"
        private const val NOTIFICATION_ID = 1

        @Volatile var running = false
        @Volatile var status = "Durdu"
        private const val PREVIEW_WIDTH = 640

        @Volatile var lastCrop: Bitmap? = null
        @Volatile var lastFrame: Bitmap? = null
        @Volatile var frameWidth = 0
        @Volatile var frameHeight = 0
        @Volatile var lastOcr = ""
        @Volatile var lastTranslation = ""
        @Volatile var frameCount = 0
    }
}
