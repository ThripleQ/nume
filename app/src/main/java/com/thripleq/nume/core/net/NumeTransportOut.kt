package com.thripleq.nume.core.net

/**
 * Result of an OkHttp round trip, consumed by the native transport shim.
 * `setCookies` carries the raw "name=value; ..." values of every Set-Cookie
 * response header, kept verbatim so libnetease's jar can merge them.
 */
class NumeTransportOut(
    @JvmField val status: Int,
    @JvmField val err: String?,
    @JvmField val body: ByteArray?,
    @JvmField val setCookies: Array<String>?,
)