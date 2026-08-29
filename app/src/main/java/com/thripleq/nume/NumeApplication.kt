package com.thripleq.nume

import android.app.Application
import com.thripleq.nume.core.net.NetEaseGateway
import com.thripleq.nume.core.net.NumeNative
import java.io.File

/**
 * App entry point. Wires libnetease's cookie jar to an app-private file and
 * exposes the serialized gateway singleton.
 */
class NumeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val cookieFile = File(filesDir, NETEASE_COOKIE_FILE)
        // NumeNative.init() loads libnume_jni (a.k.a. libnetease_jni) here.
        NumeNative.setCookieFile(cookieFile.absolutePath)
    }

    companion object {
        const val NETEASE_COOKIE_FILE = "netease_cookies.json"
    }
}

/** Process-scoped gateway holder; replaced by Hilt-provided DI later. */
object Gateway {
    val netease by lazy { NetEaseGateway() }
}