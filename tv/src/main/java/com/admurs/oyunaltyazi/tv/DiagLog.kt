package com.admurs.oyunaltyazi.tv

import android.app.Application
import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TV'de olanları (servis bağlandı, kapandı, çöktü…) bir dosyaya yazar; uygulama ekranında gösterilir.
 * TV'de log okumak zor olduğu için sorunları buradan teşhis ederiz.
 */
object DiagLog {
    private const val FILE = "tani.txt"
    private const val MAX_LINES = 80

    @Synchronized
    fun add(context: Context, message: String) {
        try {
            val file = File(context.filesDir, FILE)
            val time = SimpleDateFormat("dd.MM HH:mm:ss", Locale.US).format(Date())
            val old = if (file.exists()) file.readLines() else emptyList()
            file.writeText((old + "$time  $message").takeLast(MAX_LINES).joinToString("\n"))
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun read(context: Context): String = try {
        File(context.filesDir, FILE).takeIf { it.exists() }?.readText().orEmpty()
    } catch (_: Exception) {
        ""
    }

    @Synchronized
    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}

/** Uygulama genelinde yakalanmayan hataları tanılama kaydına yazar. */
class OyunAltyaziTvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            DiagLog.add(
                this,
                "ÇÖKME (${thread.name}): ${error.javaClass.simpleName}: ${error.message}\n" +
                    error.stackTrace.take(8).joinToString("\n") { "    at $it" },
            )
            previous?.uncaughtException(thread, error)
        }
    }
}
