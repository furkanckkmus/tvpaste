package com.tvpaste.phone

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

/**
 * Tek ekran: TV'yi bulur (veya elle IP kullanir), yaziyi TV'ye gonderir.
 */
class MainActivity : Activity() {

    private lateinit var statusView: TextView
    private lateinit var messageInput: EditText
    private lateinit var manualIpInput: EditText
    private lateinit var sendButton: Button

    private val handler = Handler(Looper.getMainLooper())

    private var discovery: TvDiscovery? = null
    private var targetHost: String? = null
    private var targetPort = TvPastePhone.DEFAULT_PORT
    private var manualIp: String? = null
    private var sending = false

    private val discoveryTimeout = Runnable {
        if (targetHost == null && manualIp == null) {
            statusView.text = getString(R.string.status_not_found)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusView = findViewById(R.id.status)
        messageInput = findViewById(R.id.message_input)
        manualIpInput = findViewById(R.id.manual_ip_input)
        sendButton = findViewById(R.id.send_button)

        findViewById<Button>(R.id.paste_button).setOnClickListener { pasteFromClipboard() }
        findViewById<Button>(R.id.clear_button).setOnClickListener { messageInput.setText("") }
        sendButton.setOnClickListener { sendText() }

        val savedIp = PhonePrefs.getManualIp(this)
        manualIp = savedIp
        manualIpInput.setText(savedIp ?: "")
        if (savedIp != null) {
            setTarget(savedIp, TvPastePhone.DEFAULT_PORT)
            statusView.text = getString(R.string.status_found, savedIp)
        }
        manualIpInput.addTextChangedListener(manualIpWatcher)
    }

    override fun onResume() {
        super.onResume()
        if (manualIp == null && targetHost == null) startDiscovery()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopDiscovery()
        super.onDestroy()
    }

    /** Elle IP yazildikca gecerliligi kontrol edilir; gecerliyse kaydedilir. */
    private val manualIpWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(s: Editable?) {
            val text = s?.toString()?.trim().orEmpty()
            when {
                text.isEmpty() -> applyManualIp(null)
                IpAddressChecker.isValid(text) -> applyManualIp(text)
                else -> Unit // yazim devam ediyor, henuz kaydetme
            }
        }
    }

    private fun applyManualIp(ip: String?) {
        if (ip == manualIp) return
        manualIp = ip
        PhonePrefs.setManualIp(this, ip ?: "")
        stopDiscovery()
        targetHost = null

        if (ip != null) {
            setTarget(ip, TvPastePhone.DEFAULT_PORT)
            statusView.text = getString(R.string.status_found, ip)
        } else {
            startDiscovery()
        }
    }

    private fun startDiscovery() {
        if (discovery != null) return
        statusView.text = getString(R.string.status_searching)

        val newDiscovery = TvDiscovery(
            context = this,
            onFound = { host, port -> onTvFound(host, port) },
            onUnavailable = { onDiscoveryUnavailable() }
        )
        discovery = newDiscovery
        newDiscovery.start()

        handler.removeCallbacks(discoveryTimeout)
        handler.postDelayed(discoveryTimeout, DISCOVERY_TIMEOUT_MS)
    }

    private fun stopDiscovery() {
        discovery?.stop()
        discovery = null
        handler.removeCallbacks(discoveryTimeout)
    }

    private fun onTvFound(host: String, port: Int) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            setTarget(host, port)
            statusView.text = getString(R.string.status_found, host)
        }
    }

    private fun onDiscoveryUnavailable() {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            statusView.text = getString(R.string.status_not_found)
        }
    }

    private fun setTarget(host: String, port: Int) {
        targetHost = host
        targetPort = port
    }

    private fun sendText() {
        val host = targetHost
        if (host == null) {
            toast(getString(R.string.toast_not_found_yet))
            return
        }
        if (sending) return

        val text = messageInput.text.toString()
        if (text.isEmpty()) {
            toast(getString(R.string.toast_empty))
            return
        }

        sending = true
        sendButton.isEnabled = false
        val port = targetPort

        Thread({
            val result = TvClient().send(host, port, text)
            runOnUiThread {
                if (!isFinishing && !isDestroyed) handleResult(result)
                sending = false
                sendButton.isEnabled = true
            }
        }, "tvpaste-send").apply {
            isDaemon = true
            start()
        }
    }

    private fun handleResult(result: SendResult) {
        when (result) {
            SendResult.Success -> {
                messageInput.setText("")
                toast(getString(R.string.toast_sent))
            }

            SendResult.NoField -> toast(getString(R.string.toast_no_field))
            SendResult.Unreachable -> toast(getString(R.string.toast_unreachable))
            is SendResult.Error -> toast(getString(R.string.toast_http_error, result.code))
        }
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = try {
            val clip = clipboard?.primaryClip
            if (clip == null || clip.itemCount == 0) {
                null
            } else {
                clip.getItemAt(0)?.coerceToText(this)?.toString()
            }
        } catch (e: Exception) {
            null
        }

        if (text.isNullOrEmpty()) {
            toast(getString(R.string.toast_clipboard_empty))
        } else {
            messageInput.setText(text)
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val DISCOVERY_TIMEOUT_MS = 8_000L
    }
}
