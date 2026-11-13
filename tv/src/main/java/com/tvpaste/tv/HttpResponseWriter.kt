package com.tvpaste.tv

import java.io.OutputStream

/**
 * Cok basit HTTP cevabi yazar. Her cevapta baglanti kapanir.
 */
internal object HttpResponseWriter {

    private const val DEFAULT_CONTENT_TYPE = "text/plain; charset=utf-8"

    fun write(
        output: OutputStream,
        status: Int,
        body: String,
        contentType: String = DEFAULT_CONTENT_TYPE,
        extraHeaders: List<String> = emptyList()
    ) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val head = buildString {
            append("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(bodyBytes.size).append("\r\n")
            append("Connection: close\r\n")
            for (header in extraHeaders) append(header).append("\r\n")
            append("\r\n")
        }
        output.write(head.toByteArray(Charsets.UTF_8))
        output.write(bodyBytes)
        output.flush()
    }

    private fun reason(status: Int): String = when (status) {
        200 -> "OK"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        404 -> "Not Found"
        409 -> "Conflict"
        413 -> "Payload Too Large"
        else -> "Internal Server Error"
    }
}
