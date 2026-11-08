package com.admurs.oyunaltyazi

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** TV uygulamasına yerel ağ üzerinden altyazı gönderir. Gönderimler sırayla, arka planda yapılır. */
class TvClient(private val host: String, private val port: Int = AppSettings.TV_PORT) {

    private val executor = Executors.newSingleThreadExecutor()

    fun show(text: String, fontSize: Int, durationMs: Int = 30_000) = post(
        JSONObject()
            .put("type", "show")
            .put("text", text)
            .put("fontSize", fontSize)
            .put("durationMs", durationMs),
    )

    fun clear() = post(JSONObject().put("type", "clear"))

    /** Bağlantıyı dener; çağıran iş parçacığını bekletir. */
    fun hello(): Boolean = try {
        val connection = open("/hello").apply { requestMethod = "GET" }
        val ok = connection.responseCode == 200 &&
            connection.inputStream.bufferedReader().readText().contains("oyunaltyazi-tv")
        connection.disconnect()
        ok
    } catch (e: Exception) {
        lastError = e.message
        false
    }

    fun shutdown() = executor.shutdown()

    private fun post(json: JSONObject) {
        if (executor.isShutdown) return
        executor.execute {
            try {
                val connection = open("/subtitle").apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                connection.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                connection.disconnect()
                lastError = if (code == 200) null else "TV yanıtı: HTTP $code"
                if (code == 200) sentCount++
            } catch (e: Exception) {
                lastError = "TV'ye ulaşılamadı: ${e.message}"
            }
        }
    }

    private fun open(path: String) = (URL("http://$host:$port$path").openConnection() as HttpURLConnection).apply {
        connectTimeout = 1500
        readTimeout = 1500
    }

    companion object {
        @Volatile var lastError: String? = null
        @Volatile var sentCount = 0
    }
}
