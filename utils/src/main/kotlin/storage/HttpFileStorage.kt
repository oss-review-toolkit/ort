/*
 * Copyright (C) 2019 HERE Europe B.V.
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

package com.here.ort.utils.storage

import com.here.ort.utils.OkHttpClientHelper
import com.here.ort.utils.getUserOrtDirectory
import com.here.ort.utils.log

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

import okhttp3.CacheControl
import okhttp3.Headers.Companion.toHeaders
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * A [FileStorage] that stores files on an HTTP server.
 */
class HttpFileStorage(
    /**
     * The URL to store files at.
     */
    val url: String,

    /**
     * Custom headers that are added to all HTTP requests.
     */
    private val headers: Map<String, String> = emptyMap(),

    /**
     * A path inside the [ORT user directory][getUserOrtDirectory] used for caching HTTP responses. Defaults to
     * "cache/http".
     */
    private val cachePath: String = "cache/http",

    /**
     * The max age of an HTTP cache entry in seconds. Defaults to 0 which always validates the cached response with the
     * remote server.
     */
    private val cacheMaxAgeInSeconds: Int = 0
) : FileStorage {
    override fun read(path: String): InputStream {
        val request = Request.Builder()
            .headers(headers.toHeaders())
            .cacheControl(CacheControl.Builder().maxAge(cacheMaxAgeInSeconds, TimeUnit.SECONDS).build())
            .get()
            .url(urlForPath(path))
            .build()

        log.debug { "Reading file from storage: ${request.url}" }

        val response = OkHttpClientHelper.execute(cachePath, request)
        if (response.code == HttpURLConnection.HTTP_OK) {
            response.body?.let { body ->
                return body.byteStream()
            }

            response.close()
            throw IOException("The response body must not be null.")
        }

        response.close()
        throw IOException("Could not read from '${request.url}': ${response.code} - ${response.message}")
    }

    override fun write(path: String, inputStream: InputStream) {
        inputStream.use {
            val request = Request.Builder()
                .headers(headers.toHeaders())
                .put(it.readBytes().toRequestBody())
                .url(urlForPath(path))
                .build()

            log.debug { "Writing file to storage: ${request.url}" }

            return OkHttpClientHelper.execute(cachePath, request).use { response ->
                if (response.code != HttpURLConnection.HTTP_CREATED && response.code != HttpURLConnection.HTTP_OK) {
                    throw IOException(
                        "Could not store file at '${request.url}': ${response.code} - ${response.message}"
                    )
                }
            }
        }
    }

    private fun urlForPath(path: String) = "$url/$path"
}
