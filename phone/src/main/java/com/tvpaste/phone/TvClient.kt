package com.tvpaste.phone

import java.net.HttpURLConnection
import java.net.URL

/** Gonderim sonucu. */
sealed class SendResult {
    object Success : SendResult()
    object NoField : SendResult()
    object Unreachable : SendResult()
    data class Error(val code: Int) : SendResult()
}

/**
 * TV'ye HTTP POST /send istegi gonderir.
 * Bu fonksiyon ag erisimi yapar; UI thread'inde cagrilmamalidir.
 */
class TvClient {

    fun send(host: String, port: Int, text: String): SendResult {
        var connection: HttpURLConnection? = null
        return try {
            // IPv6 adresleri kose parantezle yazilir: http://[fe80::1]:8080
            val urlHost = if (host.contains(':')) "[$host]" else host
            val url = URL("http://$urlHost:$port/send")

            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8")

            val body = text.toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }

            when (connection.responseCode) {
                200 -> SendResult.Success
                409 -> SendResult.NoField
                else -> SendResult.Error(connection.responseCode)
            }
        } catch (e: Exception) {
            SendResult.Unreachable
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 3_000
    }
}
