package com.tvpaste.tv

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Erisilebilirlik servisi. Baglaninca HTTP sunucusunu baslatir ve TV'yi
 * agda duyurur; servis kapaninca ikisini de durdurur.
 */
class PasteService : AccessibilityService() {

    private var server: SimpleHttpServer? = null
    private var announcer: NsdAnnouncer? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "PasteService baglandi")
        startServer()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "PasteService unbind")
        stopServer()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.i(TAG, "PasteService yok edildi")
        stopServer()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Simdilik olaylarla ilgilenmiyoruz.
    }

    override fun onInterrupt() {
        Log.i(TAG, "PasteService kesildi")
    }

    private fun startServer() {
        if (server != null) return

        val newServer = SimpleHttpServer(
            port = TvPasteConfig.PORT,
            assetReader = { name -> readAsset(name) },
            onText = { text -> TextInjector.inject(this, text) }
        )
        newServer.start()
        server = newServer

        val newAnnouncer = NsdAnnouncer(this)
        newAnnouncer.start()
        announcer = newAnnouncer
    }

    /** assets klasorundeki dosyayi UTF-8 olarak okur. */
    private fun readAsset(name: String): String? {
        return try {
            assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Varlik okunamadi: $name (${e.message})")
            null
        }
    }

    private fun stopServer() {
        announcer?.stop()
        announcer = null
        server?.stop()
        server = null
    }

    private companion object {
        const val TAG = "PasteService"
    }
}
