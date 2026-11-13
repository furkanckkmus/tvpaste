package com.tvpaste.tv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

/**
 * TV'yi yerel agda "_tvpaste._tcp." servis tipiyle duyurur.
 * Telefon uygulamasi TV'yi bu kayit sayesinde bulur.
 */
class NsdAnnouncer(context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private var listener: NsdManager.RegistrationListener? = null

    fun start() {
        if (listener != null) return
        val manager = nsdManager
        if (manager == null) {
            Log.w(TAG, "NSD servisi yok, duyuru yapilamiyor")
            return
        }

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = TvPasteConfig.SERVICE_NAME
            serviceType = TvPasteConfig.SERVICE_TYPE
            port = TvPasteConfig.PORT
        }

        val registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "NSD kaydedildi: ${info.serviceName}")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD kaydi basarisiz: $errorCode")
                listener = null
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.i(TAG, "NSD kaydi silindi: ${info.serviceName}")
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD kaydi silinemedi: $errorCode")
            }
        }

        listener = registrationListener
        try {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "NSD baslatilamadi: ${e.message}")
            listener = null
        }
    }

    fun stop() {
        val manager = nsdManager
        val registrationListener = listener ?: return
        listener = null
        try {
            manager?.unregisterService(registrationListener)
        } catch (e: Exception) {
            Log.w(TAG, "NSD durdurulamadi: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "PasteService"
    }
}
