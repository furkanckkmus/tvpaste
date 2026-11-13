package com.tvpaste.tv

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView

/**
 * TV ekrani: erisilebilirlik servisi durumu, sunucu durumu, yerel IP:port
 * ve servis kapaliysa uyari. Ekran 3 saniyede bir kendini yeniler.
 */
class MainActivity : Activity() {

    private lateinit var accessibilityView: TextView
    private lateinit var serverView: TextView
    private lateinit var addressView: TextView
    private lateinit var webAddressView: TextView
    private lateinit var warningView: TextView

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var alive = false

    private val refresher = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        accessibilityView = findViewById(R.id.accessibility_status)
        serverView = findViewById(R.id.server_status)
        addressView = findViewById(R.id.address)
        webAddressView = findViewById(R.id.web_address)
        warningView = findViewById(R.id.warning_service)
    }

    override fun onResume() {
        super.onResume()
        alive = true
        handler.removeCallbacks(refresher)
        handler.post(refresher)
    }

    override fun onPause() {
        alive = false
        handler.removeCallbacks(refresher)
        super.onPause()
    }

    private fun refresh() {
        val enabled = AccessibilityStatus.isEnabled(this)
        accessibilityView.text = getString(
            if (enabled) R.string.accessibility_status_on
            else R.string.accessibility_status_off
        )
        accessibilityView.setTextColor(if (enabled) COLOR_ON else COLOR_OFF)
        warningView.visibility = if (enabled) TextView.GONE else TextView.VISIBLE

        val ip = LocalIpFinder.findIpv4Address()
        addressView.text = if (ip != null) {
            getString(R.string.address_format, ip, TvPasteConfig.PORT)
        } else {
            getString(R.string.address_unknown)
        }
        webAddressView.text = if (ip != null) {
            getString(R.string.web_format, ip, TvPasteConfig.PORT)
        } else {
            getString(R.string.web_unknown)
        }

        checkServer()
    }

    /** /ping istegi ag erisimi oldugu icin ayri thread'de yapilir. */
    private fun checkServer() {
        val thread = Thread({
            val running = ServerHealth.isRunning()
            if (!alive) return@Thread
            handler.post {
                if (!alive) return@post
                serverView.text = getString(
                    if (running) R.string.server_running else R.string.server_stopped
                )
                serverView.setTextColor(if (running) COLOR_ON else COLOR_OFF)
            }
        }, "tvpaste-health")
        thread.isDaemon = true
        thread.start()
    }

    private companion object {
        const val REFRESH_MS = 3_000L
        const val COLOR_ON = 0xFF4CAF50.toInt()
        const val COLOR_OFF = 0xFFF44336.toInt()
    }
}
