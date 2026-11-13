package com.tvpaste.tv

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Port 8080'de dinleyen basit HTTP sunucusu.
 * Her baglanti ayri bir thread'de islenir; ana thread bloklanmaz.
 * Istek yonlendirme isi RequestHandler'dadir.
 */
class SimpleHttpServer(
    private val port: Int,
    assetReader: (String) -> String?,
    onText: (String) -> Boolean
) {

    private val requestHandler = RequestHandler(assetReader, onText)
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    @Volatile
    private var running = false

    fun start() {
        if (running) return
        running = true
        acceptThread = Thread({ acceptLoop() }, "tvpaste-http").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            Log.w(TAG, "sunucu kapatilirken hata: ${e.message}")
        }
        serverSocket = null
        acceptThread = null
        Log.i(TAG, "Sunucu durdu")
    }

    private fun acceptLoop() {
        val socket = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(port))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sunucu baslatilamadi: ${e.message}")
            running = false
            return
        }
        serverSocket = socket
        Log.i(TAG, "Sunucu basladi: port $port")

        while (running) {
            val client = try {
                socket.accept()
            } catch (e: IOException) {
                if (running) Log.w(TAG, "accept hatasi: ${e.message}")
                break
            }
            Thread({ handle(client) }, "tvpaste-conn").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun handle(client: Socket) {
        client.use { socket ->
            try {
                socket.soTimeout = SOCKET_TIMEOUT_MS
                val input = BufferedInputStream(socket.getInputStream())
                val output = BufferedOutputStream(socket.getOutputStream())
                val request = HttpRequestReader.read(input)
                Log.i(
                    TAG,
                    "istek: ${request.method} ${request.path} govde=${request.body.size} bayt"
                )
                requestHandler.handle(request, output)
            } catch (e: HttpRequestReader.PayloadTooLarge) {
                Log.w(TAG, "istek reddedildi: govde cok buyuk")
                safeWriteError(socket, 413, "payload-too-large")
            } catch (e: HttpRequestReader.BadRequest) {
                Log.w(TAG, "istek reddedildi: ${e.message}")
                safeWriteError(socket, 400, "bad-request")
            } catch (e: Exception) {
                Log.w(TAG, "baglanti hatasi: ${e.message}")
            }
        }
    }

    private fun safeWriteError(socket: Socket, status: Int, body: String) {
        try {
            HttpResponseWriter.write(BufferedOutputStream(socket.getOutputStream()), status, body)
        } catch (e: IOException) {
            Log.w(TAG, "hata cevabi yazilamadi: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "PasteService"
        const val SOCKET_TIMEOUT_MS = 5_000
    }
}
