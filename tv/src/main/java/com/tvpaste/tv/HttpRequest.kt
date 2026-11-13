package com.tvpaste.tv

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Basit HTTP istegi: sadece metod, yol, basliklar ve govde.
 */
internal class HttpRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: ByteArray
) {
    /** Govdeyi UTF-8 olarak cozer (Turkce karakterler bozulmaz). */
    val bodyText: String
        get() = String(body, Charsets.UTF_8)
}

/**
 * Gelen HTTP istegini okur. Govde boyutu en fazla 100 KB.
 */
internal object HttpRequestReader {

    private const val MAX_HEADER_BYTES = 16 * 1024
    const val MAX_BODY_BYTES = 100 * 1024

    /** Istek bicimi bozuksa firlatilir. */
    class BadRequest(message: String) : Exception(message)

    /** Govde 100 KB sinirini asiyorsa firlatilir. */
    class PayloadTooLarge : Exception("govde 100 KB sinirini asiyor")

    fun read(input: InputStream): HttpRequest {
        val headerBlock = readHeaderBlock(input)
        val lines = headerBlock.split("\r\n")
        val requestLine = lines.firstOrNull()?.trim().orEmpty().split(" ")
        if (requestLine.size < 2) throw BadRequest("istek satiri bozuk")

        val method = requestLine[0].uppercase()
        val path = requestLine[1].substringBefore('?')

        val headers = LinkedHashMap<String, String>()
        for (line in lines.drop(1)) {
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            val name = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim()
            headers[name] = value
        }

        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        if (contentLength < 0) throw BadRequest("content-length gecersiz")
        if (contentLength > MAX_BODY_BYTES) throw PayloadTooLarge()

        val body = readExactly(input, contentLength)
        return HttpRequest(method, path, headers, body)
    }

    /** Baslik blogunu "\r\n\r\n" gorene kadar okur. */
    private fun readHeaderBlock(input: InputStream): String {
        val pattern = byteArrayOf(13, 10, 13, 10)
        val buffer = ByteArrayOutputStream(512)
        var matched = 0
        while (buffer.size() < MAX_HEADER_BYTES) {
            val value = input.read()
            if (value == -1) throw BadRequest("baglanti erken kapandi")
            buffer.write(value)
            matched = if (value == pattern[matched].toInt()) {
                matched + 1
            } else {
                if (value == 13) 1 else 0
            }
            if (matched == pattern.size) {
                return String(buffer.toByteArray(), Charsets.ISO_8859_1)
            }
        }
        throw BadRequest("basliklar cok uzun")
    }

    private fun readExactly(input: InputStream, length: Int): ByteArray {
        val data = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(data, read, length - read)
            if (count == -1) throw BadRequest("govde eksik")
            read += count
        }
        return data
    }
}
