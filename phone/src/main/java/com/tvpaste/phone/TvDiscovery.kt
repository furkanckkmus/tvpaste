package com.tvpaste.phone

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

/**
 * Yerel agda "_tvpaste._tcp." servisini arar.
 *
 * ONEMLI: Android ayni anda birden fazla resolve islemini kabul etmez.
 * Bu yuzden bulunan servisler siraya alinir ve tek tek cozulur.
 */
class TvDiscovery(
    context: Context,
    private val onFound: (host: String, port: Int) -> Unit,
    private val onUnavailable: () -> Unit
) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val pending = ArrayDeque<NsdServiceInfo>()
    private val lock = Any()

    private var resolving = false
    private var stopped = false
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun start() {
        val manager = nsdManager
        if (manager == null) {
            onUnavailable()
            return
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.i(TAG, "Kesif basladi: $serviceType")
            }

            override fun onServiceFound(info: NsdServiceInfo) {
                Log.i(TAG, "Servis bulundu: ${info.serviceName}")
                synchronized(lock) { pending.addLast(info) }
                resolveNext()
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                Log.i(TAG, "Servis kayboldu: ${info.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "Kesif durdu: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Kesif baslatilamadi: $errorCode")
                onUnavailable()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Kesif durdurulamadi: $errorCode")
            }
        }

        discoveryListener = listener
        try {
            manager.discoverServices(TvPastePhone.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e(TAG, "Kesif hatasi: ${e.message}")
            onUnavailable()
        }
    }

    fun stop() {
        val manager = nsdManager
        val listener = discoveryListener ?: return
        synchronized(lock) {
            stopped = true
            pending.clear()
        }
        discoveryListener = null
        try {
            manager?.stopServiceDiscovery(listener)
        } catch (e: Exception) {
            Log.w(TAG, "Kesif durdurulamadi: ${e.message}")
        }
    }

    /** Siradaki servisi cozer. Ayni anda tek resolve calisir. */
    private fun resolveNext() {
        val info = synchronized(lock) {
            if (resolving || stopped) return
            val next = pending.removeFirstOrNull() ?: return
            resolving = true
            next
        }

        try {
            @Suppress("DEPRECATION")
            nsdManager?.resolveService(info, resolveListener())
        } catch (e: Exception) {
            Log.w(TAG, "Cozumleme baslatilamadi: ${e.message}")
            synchronized(lock) { resolving = false }
            resolveNext()
        }
    }

    private fun resolveListener(): NsdManager.ResolveListener =
        object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Cozumleme basarisiz: $errorCode")
                synchronized(lock) { resolving = false }
                resolveNext()
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress
                val port = serviceInfo.port
                Log.i(TAG, "Cozumlendi: ${serviceInfo.serviceName} -> $host:$port")

                synchronized(lock) {
                    resolving = false
                    if (stopped) return
                    stopped = true
                }

                if (host == null) {
                    resolveNext()
                    return
                }
                onFound(host, port)
                stop()
            }
        }

    private companion object {
        const val TAG = "TvDiscovery"
    }
}
