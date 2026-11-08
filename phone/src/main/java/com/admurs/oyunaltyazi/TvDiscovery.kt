package com.admurs.oyunaltyazi

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

/** Ağdaki Oyun Altyazı TV uygulamasını mDNS (NSD) ile bulur. Geri çağrılar arka plan iş parçacığında gelir. */
class TvDiscovery(context: Context, private val onFound: (name: String, host: String) -> Unit) {

    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var listener: NsdManager.DiscoveryListener? = null

    fun start() {
        stop()
        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onServiceFound(info: NsdServiceInfo) = resolve(info)
        }
        listener = discovery
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
        } catch (_: Exception) {
            listener = null
        }
    }

    fun stop() {
        val current = listener ?: return
        try {
            nsd.stopServiceDiscovery(current)
        } catch (_: Exception) {
        }
        listener = null
    }

    @Suppress("DEPRECATION")
    private fun resolve(info: NsdServiceInfo) {
        try {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                override fun onServiceResolved(info: NsdServiceInfo) {
                    val host = info.host?.hostAddress ?: return
                    onFound(info.serviceName, host)
                }
            })
        } catch (_: Exception) {
        }
    }

    companion object {
        const val SERVICE_TYPE = "_oyunaltyazi._tcp"
    }
}
