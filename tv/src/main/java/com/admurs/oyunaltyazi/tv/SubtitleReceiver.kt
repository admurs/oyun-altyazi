package com.admurs.oyunaltyazi.tv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import org.json.JSONObject

/**
 * Tabletten gelen mesajları alır (HTTP sunucusu + ağda kendini duyurma) ve katmana iletir.
 * Her iki gösterim modu da bunu kullanır.
 */
class SubtitleReceiver(private val context: Context, private val overlay: SubtitleOverlay) {

    private val main = Handler(Looper.getMainLooper())
    private var server: SubtitleHttpServer? = null
    private var nsdListener: NsdManager.RegistrationListener? = null

    fun start() {
        server = SubtitleHttpServer(PORT, onError = { DiagLog.add(context, it) }) { body -> handle(body) }.also { it.start() }
        registerNsd()
    }

    fun stop() {
        server?.stop()
        server = null
        unregisterNsd()
    }

    private fun handle(body: String): Boolean {
        val json = try {
            JSONObject(body)
        } catch (_: Exception) {
            return false
        }
        when (json.optString("type")) {
            "show" -> {
                val text = json.optString("text")
                val size = json.optInt("fontSize", SubtitleOverlay.DEFAULT_FONT)
                val duration = json.optInt("durationMs", 30_000)
                main.post { overlay.show(text, size, duration) }
            }
            "clear" -> main.post { overlay.clear() }
            else -> return false
        }
        messageCount++
        return true
    }

    private fun registerNsd() {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val info = NsdServiceInfo().apply {
            serviceName = "OyunAltyazi-TV"
            serviceType = SERVICE_TYPE
            port = PORT
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                DiagLog.add(context, "Ağ duyurusu başarısız (kod $errorCode)")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            nsdListener = listener
        } catch (e: Exception) {
            DiagLog.add(context, "Ağ duyurusu başarısız: ${e.message}")
        }
    }

    private fun unregisterNsd() {
        val listener = nsdListener ?: return
        try {
            (context.getSystemService(Context.NSD_SERVICE) as NsdManager).unregisterService(listener)
        } catch (_: Exception) {
        }
        nsdListener = null
    }

    companion object {
        const val PORT = 8788
        const val SERVICE_TYPE = "_oyunaltyazi._tcp"

        @Volatile var messageCount = 0
    }
}
