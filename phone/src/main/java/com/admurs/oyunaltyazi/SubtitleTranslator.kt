package com.admurs.oyunaltyazi

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.concurrent.TimeUnit

/** Cihazda çalışan İngilizce→Türkçe çeviri. Aynı cümle tekrar gelirse önbellekten döner. */
class SubtitleTranslator {

    private val translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.TURKISH)
            .build(),
    )

    private val cache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 300
    }

    /** Modeli (yaklaşık 30 MB) indirir; zaten varsa hemen tamamlanır. */
    fun downloadModel(): Task<Void> = translator.downloadModelIfNeeded(DownloadConditions.Builder().build())

    /** Arka plan iş parçacığında çağrılmalı. */
    fun translateBlocking(text: String): String {
        synchronized(cache) { cache[text]?.let { return it } }
        Tasks.await(downloadModel(), 120, TimeUnit.SECONDS)
        val result = Tasks.await(translator.translate(text), 10, TimeUnit.SECONDS)
        synchronized(cache) { cache[text] = result }
        return result
    }

    fun close() = translator.close()
}
