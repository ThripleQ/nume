package com.thripleq.nume.core.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * The C→Kotlin endpoint of the injected transport. libnetease calls this
 * (through the JNI shim in libnetease_jni.c) whenever it needs to send an HTTP
 * round trip; all real I/O happens here in OkHttp.
 *
 * gzip/zlib decoding and redirect following are handled by OkHttp the same way
 * libcurl did for the desktop build.
 */
object NumeTransport {

    @JvmStatic
    fun httpRequest(
        method: String,
        url: String,
        body: String?,
        contentType: String?,
        cookieHeader: String?,
        userAgent: String?,
    ): NumeTransportOut {
        return RawHttp.send(method, url, body, contentType, cookieHeader, userAgent)
    }

    private object RawHttp {
        private val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        private val formType = "application/x-www-form-urlencoded; charset=UTF-8"
            .toMediaType()

        fun send(
            method: String,
            url: String,
            body: String?,
            contentType: String?,
            cookieHeader: String?,
            userAgent: String?,
        ): NumeTransportOut {
            val requestBody: RequestBody? =
                if (method == "POST" && body != null) {
                    (contentType?.toMediaType() ?: formType).let { body.toRequestBody(it) }
                } else {
                    null
                }

            val builder = Request.Builder()
                .url(url)
                .method(method, requestBody)

            if (!cookieHeader.isNullOrEmpty()) builder.header("Cookie", cookieHeader)
            if (!userAgent.isNullOrEmpty()) builder.header("User-Agent", userAgent)
            // libnetease's curl transport sets this unconditionally; mirror it.
            builder.header("Referer", "https://music.163.com")

            return try {
                Log.d("NumeTransport", "-> ${method} ${url}")
                client.newCall(builder.build()).execute().use { resp ->
                    val status = resp.code
                    val bytes = resp.body.bytes()
                    val cookies = resp.headers.values("Set-Cookie").takeIf { it.isNotEmpty() }
                    Log.d("NumeTransport", "<- status=${status} bytes=${bytes.size}")
                    NumeTransportOut(status, null, bytes, cookies?.toTypedArray())
                }
            } catch (e: Exception) {
                Log.e("NumeTransport", "transport failed for ${url}: ${e}", e)
                NumeTransportOut(0, e.message ?: "transport error", ByteArray(0), null)
            }
        }
    }
}