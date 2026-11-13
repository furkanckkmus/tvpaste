package com.tvpaste.tv

import java.net.HttpURLConnection
import java.net.URL

/**
 * Sunucunun calisip calismadigini kendi kendine sorar: 127.0.0.1:8080/ping
 * Bu fonksiyon ag erisimi yapar, UI thread'inde cagrilmamalidir.
 */
object ServerHealth {

    private val URL_STRING = "http://127.0.0.1:${TvPasteConfig.PORT}/ping"
    private const val TIMEOUT_MS = 700

    fun isRunning(): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(URL_STRING).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "GET"
            if (connection.responseCode != 200) {
                false
            } else {
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                body == "tvpaste-ok"
            }
        } catch (e: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }
}
