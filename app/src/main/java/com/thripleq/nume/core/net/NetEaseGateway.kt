package com.thripleq.nume.core.net

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Facade over the libnetease request kernel.
 *
 * The C request layer guards its global cookie jar with an internal mutex
 * (see netease/request.h), so calls no longer need to be funneled through a
 * single serializing lock. Each [call] still hops to [io] to keep the native
 * call off the main thread, and concurrent callers now run in parallel.
 */
class NetEaseGateway(
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Points the cookie jar at file and remembers it for future sessions. */
    fun configure(cookieFile: File) {
        NumeNative.setCookieFile(cookieFile.absolutePath)
    }

    /** Merges a browser-exported cookie string into the jar and persists it. */
    fun importCookies(cookieStr: String) {
        NumeNative.importCookies(cookieStr)
    }

    /** Runs one libnetease service call, blocking on [io] while it round-trips. */
    suspend fun call(op: Int, vararg args: String): ApiResult =
        withContext(io) {
            val r = NumeNative.request(op, args)
            Log.d("NetEaseGateway", "op=$op args=${args.joinToString(",")} -> code=${r.code} err=${r.err} body=${r.body.take(200)}")
            r
        }
}