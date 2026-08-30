package com.thripleq.nume.core.net

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Serialized facade over the libnetease request kernel.
 *
 * The C request layer is process-global and single-threaded by contract
 * (see netease/request.h). Every [call] is funnelled through [io] and a lock so
 * concurrent callers never hit the shared cookie jar or transport state at once.
 */
class NetEaseGateway(
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val lock = Any()

    /** Points the cookie jar at file and remembers it for future sessions. */
    fun configure(cookieFile: File) {
        NumeNative.setCookieFile(cookieFile.absolutePath)
    }

    /**
     * Merges a browser-exported cookie string into the jar and persists it.
     * Serialized under the same lock as [call] so the jar is never touched
     * concurrently.
     */
    fun importCookies(cookieStr: String) {
        synchronized(lock) { NumeNative.importCookies(cookieStr) }
    }

    /** Runs one libnetease service call, blocking on [io] while it round-trips. */
    suspend fun call(op: Int, vararg args: String): ApiResult =
        withContext(io) {
            synchronized(lock) { NumeNative.request(op, args) }
        }
}