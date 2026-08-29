package com.thripleq.nume.core.net

/**
 * JNI declarations for the libnetease bridge (bound in libnetease_jni.c).
 *
 * [request] runs a service call on the current thread, blocking while the
 * injected transport performs the HTTP round trip. The request layer is
 * process-global and single-threaded, so callers MUST serialize every [request]
 * (see NetEaseGateway).
 */
object NumeNative {
    init {
        System.loadLibrary("nume_jni")
    }

    /** Points libnetease's cookie jar at a file and reloads it. */
    external fun setCookieFile(path: String)

    /** Overrides the API base URL (defaults to https://music.163.com). */
    external fun setApiBase(base: String)

    /** Dispatches [op] (one of [NeteaseOp]) with [args]; returns the raw result. */
    external fun request(op: Int, args: Array<out String>): ApiResult
}