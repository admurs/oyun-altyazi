package com.admurs.oyunaltyazi.tv

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Base64
import com.admurs.oyunaltyazi.core.SubtitleEvent
import com.admurs.oyunaltyazi.core.SubtitleStabilizer
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

/**
 * TV'de çalışan altyazı hattı: altyazı bölgesinin görüntüsü → ML Kit OCR → stabilizatör → çeviri → katman.
 * Görüntüler arka plandaki tek bir iş parçacığında işlenir; o meşgulken gelen kareler atlanır.
 */
class SubtitlePipeline(private val context: Context) {

    private val worker = HandlerThread("altyazi-tv-ocr").apply { start() }
    private val workerHandler = Handler(worker.looper)
    private val main = Handler(Looper.getMainLooper())
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val translator = SubtitleTranslator()
    private val stabilizer = SubtitleStabilizer()

    @Volatile private var busy = false
    @Volatile var modelStatus = "çeviri modeli indiriliyor…"
        private set
    @Volatile var lastOcr = ""
        private set
    @Volatile var lastTranslation = ""
        private set
    @Volatile var processed = 0
        private set
    @Volatile var lastError: String? = null
        private set

    fun prepare() {
        translator.downloadModel()
            .addOnSuccessListener {
                modelStatus = "çeviri modeli hazır"
                DiagLog.add(context, "Çeviri modeli hazır")
            }
            .addOnFailureListener {
                modelStatus = "çeviri modeli indirilemedi: ${it.message}"
                DiagLog.add(context, "Çeviri modeli indirilemedi: ${it.message}")
            }
    }

    /** Altyazı bölgesi önceki kareyle aynı: OCR'ı tekrarlamadan son okumayı stabilizatöre ver. */
    fun submitUnchanged() {
        if (busy) return
        busy = true
        workerHandler.post {
            try {
                handleText(lastOcr)
            } finally {
                busy = false
            }
        }
    }

    /** Altyazı bölgesinin JPEG görüntüsü (base64). */
    fun submit(jpegBase64: String) {
        if (busy) return
        busy = true
        workerHandler.post {
            try {
                val bytes = Base64.decode(jpegBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@post
                val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), 5, TimeUnit.SECONDS).text
                bitmap.recycle()
                lastOcr = text
                processed++
                handleText(text)
                lastError = null
            } catch (e: Exception) {
                lastError = "${e.javaClass.simpleName}: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    private fun handleText(text: String) {
        when (val event = stabilizer.push(text)) {
            is SubtitleEvent.Show -> {
                val translated = try {
                    translator.translateBlocking(event.text)
                } catch (e: Exception) {
                    lastError = "Çeviri hatası: ${e.message}"
                    event.text
                }
                lastTranslation = translated
                DiagLog.add(context, "Altyazı: ${event.text.take(60)} → ${translated.take(60)}")
                main.post { SubtitleOverlay.active?.show(translated, SubtitleOverlay.DEFAULT_FONT, 30_000) }
            }
            SubtitleEvent.Clear -> {
                lastTranslation = ""
                main.post { SubtitleOverlay.active?.clear() }
            }
            null -> Unit
        }
    }

    fun close() {
        workerHandler.post {
            recognizer.close()
            translator.close()
            worker.quitSafely()
        }
    }
}
