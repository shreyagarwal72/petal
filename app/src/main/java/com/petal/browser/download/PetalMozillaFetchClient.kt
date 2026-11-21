package com.petal.browser.download

import mozilla.components.concept.fetch.Client
import mozilla.components.concept.fetch.MutableHeaders
import mozilla.components.concept.fetch.Request
import mozilla.components.concept.fetch.Response
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okio.BufferedSink
import java.io.IOException

/**
 * Last-resort OkHttp transport for Android Components downloads, used only if
 * GeckoViewFetchClient cannot be created. It does NOT see Gecko's cookie jar, so it is
 * not suitable for authenticated downloads.
 *
 * This is deliberately synchronous: AbstractFetchDownloadService invokes
 * concept-fetch clients from its IO dispatcher and owns cancellation/state
 * transitions around the request.
 */
class PetalOkHttpFetchClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
        .writeTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()
) : Client() {

    @Throws(IOException::class)
    override fun fetch(request: Request): Response {
        if (request.private) {
            throw IOException("PetalOkHttpFetchClient cannot be used for private requests")
        }
        val builder = okhttp3.Request.Builder().url(request.url)
        request.headers?.forEach { header -> builder.addHeader(header.name, header.value) }
        request.referrerUrl?.let { builder.header("Referer", it) }

        val method = request.method.name
        val body = request.body?.let { conceptBody ->
            object : RequestBody() {
                override fun contentType() = request.headers?.get("Content-Type")?.toMediaTypeOrNull()
                override fun writeTo(sink: BufferedSink) {
                    conceptBody.useStream { it.copyTo(sink.outputStream()) }
                }
            }
        }
        builder.method(method, body)

        val call = client.newCall(builder.build())
        val response = call.execute()
        val headers = MutableHeaders().also { mutable ->
            response.headers.forEach { (name, value) -> mutable.append(name, value) }
        }
        val responseBody = response.body
            ?: return Response(response.request.url.toString(), response.code, headers, Response.Body.empty())

        return Response(
            url = response.request.url.toString(),
            status = response.code,
            headers = headers,
            body = Response.Body(responseBody.byteStream(), response.header("Content-Type"))
        )
    }
}


/**
 * Per-URL extra request headers (Referer, Cookie, signed-URL headers, User-Agent) for downloads
 * started outside a Gecko page context, e.g. the media sniffer. DownloadState cannot carry
 * arbitrary headers, so [PetalMozillaFetchClient] injects them when the request is sent.
 */
/**
 * Wraps the real transport (Gecko's network stack, as in Firefox/Fenix) and applies headers
 * registered in [PetalDownloadHeaderRegistry]. All other behaviour (cookies, private mode,
 * redirects, proxy, tracking protection) is left to the wrapped client.
 */
class PetalMozillaFetchClient(private val delegate: Client) : Client() {
    @Throws(IOException::class)
    override fun fetch(request: Request): Response {
        val extra = PetalDownloadHeaderRegistry.headersFor(request.url, isPrivate = request.private)
        val response = if (extra.isEmpty()) {
            delegate.fetch(request)
        } else {
            val merged = MutableHeaders()
            request.headers?.forEach { header ->
                if (extra.keys.none { it.equals(header.name, ignoreCase = true) }) {
                    merged.append(header.name, header.value)
                }
            }
            extra.forEach { (name, value) -> merged.append(name, value) }
            delegate.fetch(request.copy(headers = merged))
        }
        recordExpectedChecksum(request, response)
        return response
    }

    /** Remembers a server-provided hash so the finished file can be verified (in memory only). */
    private fun recordExpectedChecksum(request: Request, response: Response) {
        try {
            if (response.status != 200 && response.status != 206) return
            val digest = response.headers.get("Digest")
            // Content-MD5 describes the message body, which is only the whole file for a 200.
            val md5 = if (response.status == 200) response.headers.get("Content-MD5") else null
            PetalChecksum.fromHeaders(digest, md5)?.let { PetalChecksumStore.recordExpected(request.url, it) }
        } catch (_: Throwable) {
        }
    }
}
