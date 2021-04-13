/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package org.ossreviewtoolkit.utils

import java.io.IOException
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

import kotlinx.coroutines.suspendCancellableCoroutine

import okhttp3.Authenticator
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

typealias BuilderConfiguration = OkHttpClient.Builder.() -> Unit

class HttpDownloadError(val code: Int, message: String) : IOException(message)

/**
 * A helper class to manage OkHttp instances backed by distinct cache directories.
 */
object OkHttpClientHelper {
    private const val CACHE_DIRECTORY = "cache/http"
    private const val MAX_CACHE_SIZE_IN_BYTES = 1024L * 1024L * 1024L
    private const val READ_TIMEOUT_IN_SECONDS = 30L

    private val client by lazy {
        installAuthenticatorAndProxySelector()
        buildClient()
    }

    private val clients = ConcurrentHashMap<BuilderConfiguration, OkHttpClient>()

    /**
     * A constant for the "too many requests" HTTP code as HttpURLConnection has none.
     */
    const val HTTP_TOO_MANY_REQUESTS = 429

    private fun buildClient(): OkHttpClient {
        val cacheDirectory = ortDataDirectory.resolve(CACHE_DIRECTORY)
        val cache = Cache(cacheDirectory, MAX_CACHE_SIZE_IN_BYTES)
        val specs = listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT)

        // OkHttp uses Java's global ProxySelector by default, but the Authenticator for a proxy needs to be set
        // explicitly. Also note that the (non-proxy) authenticator is set here and is primarily intended for "reactive"
        // authentication, but most often "preemptive" authentication via headers is required. For proxy authentication,
        // OkHttp emulates preemptive authentication by sending a fake "OkHttp-Preemptive" response to the reactive
        // proxy authenticator.
        return OkHttpClient.Builder()
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                chain.proceed(
                    if (request.header("User-Agent") == null) {
                        request.newBuilder().header("User-Agent", Environment.ORT_USER_AGENT).build()
                    } else {
                        request
                    }
                )
            }
            .cache(cache)
            .connectionSpecs(specs)
            .readTimeout(Duration.ofSeconds(READ_TIMEOUT_IN_SECONDS))
            .authenticator(Authenticator.JAVA_NET_AUTHENTICATOR)
            .proxyAuthenticator(Authenticator.JAVA_NET_AUTHENTICATOR)
            .build()
    }

    /**
     * Build a preconfigured client that uses a cache directory inside the [ORT data directory][ortDataDirectory].
     * Proxy environment variables are by default respected, but the client can further be configured via the [block].
     */
    fun buildClient(block: BuilderConfiguration? = null): OkHttpClient =
        block?.let {
            clients.getOrPut(it) { client.newBuilder().apply(block).build() }
        } ?: client

    /**
     * Execute a [request] using the client for the specified [builder configuration][block].
     */
    fun execute(request: Request, block: BuilderConfiguration? = null): Response =
        buildClient(block).newCall(request).execute()

    /**
     * Asynchronously enqueue a [request] using the client for the specified [builder configuration][block] and await
     * its response.
     */
    suspend fun await(request: Request, block: BuilderConfiguration? = null): Response =
        buildClient(block).newCall(request).await()

    /**
     * Download from [url] and return a [Result] with a string representing the response body content on success, or a
     * [Result] wrapping a [HttpDownloadError] on failure.
     */
    fun downloadText(url: String): Result<String> {
        val request = Request.Builder().get().url(url).build()

        return execute(request).use { response ->
            log.debug {
                if (response.cacheResponse != null) {
                    "Retrieved $url from local cache."
                } else {
                    "Downloaded from $url via network."
                }
            }

            if (response.isSuccessful) {
                val text = response.body?.string().orEmpty()
                Result.success(text)
            } else {
                Result.failure(HttpDownloadError(response.code, response.message))
            }
        }
    }
}

/**
 * Asynchronously enqueue the [Call]'s request and await its [Response].
 */
suspend fun Call.await(): Response =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            cancel()
        }

        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
        })
    }
