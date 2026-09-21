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
 * Adapts Petal's existing OkHttp transport to Android Components downloads.
 *
 * This is deliberately synchronous: AbstractFetchDownloadService invokes
 * concept-fetch clients from its IO dispatcher and owns cancellation/state
 * transitions around the request.
 */
class PetalMozillaFetchClient(
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
