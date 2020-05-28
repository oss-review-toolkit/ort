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

import java.net.InetSocketAddress
import java.net.MalformedURLException
import java.net.Proxy
import java.net.URL
import java.time.Duration

import okhttp3.Authenticator
import okhttp3.Cache
import okhttp3.ConnectionSpec
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

private typealias BuilderConfiguration = OkHttpClient.Builder.() -> Unit

/**
 * A helper class to manage OkHttp instances backed by distinct cache directories.
 */
object OkHttpClientHelper {
    private val clients = mutableMapOf<BuilderConfiguration, OkHttpClient>()

    private const val MAX_CACHE_SIZE_IN_BYTES = 1024L * 1024L * 1024L

    /**
     * A constant for the "too many requests" HTTP code as HttpURLConnection has none.
     */
    const val HTTP_TOO_MANY_REQUESTS = 429

    /**
     * Apply HTTP proxy settings from a [url], optionally with credentials included.
     */
    fun OkHttpClient.Builder.applyProxySettingsFromUrl(url: URL): OkHttpClient.Builder {
        if (url.host.isEmpty()) return this

        val port = url.port.takeIf { it in IntRange(0, 65535) } ?: 8080
        proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(url.host, port)))

        if (url.userInfo == null) return this

        proxyAuthenticator(object : Authenticator {
            override fun authenticate(route: Route?, response: Response): Request? {
                val user = url.userInfo.substringBefore(':')
                val password = url.userInfo.substringAfter(':')
                val credential = Credentials.basic(user, password)
                return response.request.newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build()
            }
        })

        return this
    }

    /**
     * Apply HTTP proxy settings from environment variables.
     */
    fun OkHttpClient.Builder.applyProxySettingsFromEnv(): OkHttpClient.Builder {
        Os.proxy?.let { proxyUrl ->
            try {
                applyProxySettingsFromUrl(URL(proxyUrl))
            } catch (e: MalformedURLException) {
                e.printStackTrace()

                log.warn { "Invalid proxy URL '$proxyUrl' defined in environment." }
            }
        }

        return this
    }

    /**
     * Build a preconfigured client that uses a cache directory inside the [ORT data directory][getOrtDataDirectory].
     * Proxy environment variables are by default respected, but the client can further be configured via the [block].
     */
    fun buildClient(block: OkHttpClient.Builder.() -> Unit = {}): OkHttpClient =
        clients.getOrPut(block) {
            val cacheDirectory = getOrtDataDirectory().resolve("cache/http")
            val cache = Cache(cacheDirectory, MAX_CACHE_SIZE_IN_BYTES)
            val specs = listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT)
            OkHttpClient.Builder()
                .cache(cache)
                .connectionSpecs(specs)
                .readTimeout(Duration.ofSeconds(30))
                .applyProxySettingsFromEnv()
                .apply(block)
                .build()
        }

    /**
     * Execute a [request] using the client for the specified [builder configuration][block].
     */
    fun execute(request: Request, block: OkHttpClient.Builder.() -> Unit = {}): Response =
        buildClient(block).newCall(request).execute()
}
