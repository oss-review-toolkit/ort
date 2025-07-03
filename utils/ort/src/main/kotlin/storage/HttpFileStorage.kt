/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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

package org.ossreviewtoolkit.utils.ort.storage

import java.io.IOException
import java.io.InputStream
import java.time.Duration
import java.util.concurrent.TimeUnit

import okhttp3.CacheControl
import okhttp3.ConnectionPool
import okhttp3.Headers.Companion.toHeaders
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper
import org.ossreviewtoolkit.utils.ort.execute

private const val HTTP_CLIENT_CONNECT_TIMEOUT_IN_SECONDS = 30L
private const val HTTP_CLIENT_KEEP_ALIVE_DURATION_IN_SECONDS = 1 * 60L
private const val HTTP_CLIENT_MAX_IDLE_CONNECTIONS = 5

/**
 * A [FileStorage] that stores files on an HTTP server.
 */
class HttpFileStorage(
    /**
     * The URL to store files at.
     */
    val url: String,

    /**
     * The query string that is appended to the combination of the URL and some additional path. Some storages process
     * authentication via parameters that are within the final URL, so certain credentials can be stored in this
     * query, e.g, "?user=standard&pwd=123". Thus, the final URL could be
     * "https://example.com/storage/path?user=standard&pwd=123".
     */
    val query: String = "",

    /**
     * Custom headers that are added to all HTTP requests.
     */
    private val headers: Map<String, String> = emptyMap(),

    /**
     * The max age of an HTTP cache entry in seconds. Defaults to 0 which always validates the cached response with the
     * remote server.
     */
    private val cacheMaxAgeInSeconds: Int = 0
) : FileStorage {
    private val httpClient by lazy {
        OkHttpClientHelper.buildClient {
            val connectionPool = ConnectionPool(
                HTTP_CLIENT_MAX_IDLE_CONNECTIONS,
                HTTP_CLIENT_KEEP_ALIVE_DURATION_IN_SECONDS,
                TimeUnit.SECONDS
            )

            connectionPool(connectionPool)
            connectTimeout(Duration.ofSeconds(HTTP_CLIENT_CONNECT_TIMEOUT_IN_SECONDS))
        }
    }

    private fun requestBuilder(): Request.Builder =
        Request.Builder()
            .headers(headers.toHeaders())
            .cacheControl(CacheControl.Builder().maxAge(cacheMaxAgeInSeconds, TimeUnit.SECONDS).build())

    override fun exists(path: String): Boolean {
        val request = requestBuilder()
            .head()
            .url(urlForPath(path))
            .build()

        return httpClient.execute(request).isSuccessful
    }

    override fun read(path: String): InputStream {
        val request = requestBuilder()
            .get()
            .url(urlForPath(path))
            .build()

        logger.debug { "Reading file from storage: ${request.url}" }

        val response = httpClient.execute(request)
        if (response.isSuccessful) {
            return response.body.byteStream()
        }

        response.close()
        throw IOException("Could not read from ${request.url}: ${response.code} - ${response.message}")
    }

    override fun write(path: String, inputStream: InputStream) {
        inputStream.use {
            val request = requestBuilder()
                .put(it.readBytes().toRequestBody())
                .url(urlForPath(path))
                .build()

            logger.debug { "Writing file to storage: ${request.url}" }

            return httpClient.execute(request).use { response ->
                if (!response.isSuccessful) {
                    throw IOException(
                        "Could not store file at ${request.url}: ${response.code} - ${response.message}"
                    )
                }
            }
        }
    }

    private fun urlForPath(path: String) = "$url/$path$query"

    override fun delete(path: String): Boolean {
        val request = requestBuilder()
            .delete()
            .url(urlForPath(path))
            .build()

        logger.debug { "Deleting file from storage: ${request.url}" }

        val response = httpClient.execute(request)
        return response.isSuccessful
    }
}
