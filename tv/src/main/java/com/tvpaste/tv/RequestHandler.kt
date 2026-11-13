package com.tvpaste.tv

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * HTTP isteklerini yonlendirir: /ping, /, /index.html, /send.
 * Socket islemleri SimpleHttpServer'da kalir.
 */
internal class RequestHandler(
    private val assetReader: (String) -> String?,
    private val onText: (String) -> Boolean
) {

    fun handle(request: HttpRequest, output: OutputStream) {
        if (!isAuthorized(request)) {
            HttpResponseWriter.write(output, 401, "unauthorized")
            return
        }
        when {
            request.method == "GET" && request.path == "/ping" ->
                HttpResponseWriter.write(output, 200, "tvpaste-ok")

            request.method == "GET" &&
                (request.path == "/" || request.path == "/index.html") ->
                handleIndex(output)

            request.method == "POST" && request.path == "/send" ->
                handleSend(request, output)

            else ->
                HttpResponseWriter.write(output, 404, "not-found")
        }
    }

    /** assets/index.html dosyasini UTF-8 olarak okur ve tarayiciya gonderir. */
    private fun handleIndex(output: OutputStream) {
        val html = try {
            assetReader(INDEX_ASSET)
        } catch (e: Exception) {
            Log.e(TAG, "index.html okunamadi: ${e.message}")
            null
        }
        if (html == null) {
            HttpResponseWriter.write(output, 500, "index-html-okunamadi")
            return
        }
        HttpResponseWriter.write(
            output = output,
            status = 200,
            body = html,
            contentType = "text/html; charset=utf-8",
            extraHeaders = listOf("Cache-Control: no-store")
        )
    }

    private fun handleSend(request: HttpRequest, output: OutputStream) {
        val text = request.bodyText
        Log.i(TAG, "gonderim istegi: uzunluk=${text.length}")

        when (injectOnMainThread(text)) {
            InjectResult.OK -> HttpResponseWriter.write(output, 200, "ok")
            InjectResult.NO_FIELD -> HttpResponseWriter.write(output, 409, "no-focused-field")
            InjectResult.FAILED -> HttpResponseWriter.write(output, 500, "error")
        }
    }

    /** Yazma islemini ana thread'de yapar ve en fazla 2 saniye bekler. */
    private fun injectOnMainThread(text: String): InjectResult {
        val latch = CountDownLatch(1)
        val result = AtomicReference(InjectResult.FAILED)
        val posted = Handler(Looper.getMainLooper()).post {
            try {
                result.set(if (onText(text)) InjectResult.OK else InjectResult.NO_FIELD)
            } catch (e: Exception) {
                Log.e(TAG, "yazma hatasi: ${e.message}")
            } finally {
                latch.countDown()
            }
        }
        if (!posted) return InjectResult.FAILED

        val finished = try {
            latch.await(INJECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!finished) {
            Log.w(TAG, "yazma zaman asimina ugradi")
            return InjectResult.FAILED
        }
        return result.get()
    }

    /** Simdilik her istek yetkili sayilir. Ileride PIN kontrolu buraya eklenecek. */
    @Suppress("UNUSED_PARAMETER")
    private fun isAuthorized(request: HttpRequest): Boolean {
        return true
    }

    private enum class InjectResult { OK, NO_FIELD, FAILED }

    private companion object {
        const val TAG = "PasteService"
        const val INJECT_TIMEOUT_MS = 2_000L
        const val INDEX_ASSET = "index.html"
    }
}
