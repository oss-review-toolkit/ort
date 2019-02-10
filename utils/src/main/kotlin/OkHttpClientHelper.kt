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

package com.here.ort.utils

import java.io.File
import java.net.URLConnection

import okhttp3.Cache
import okhttp3.ConnectionSpec
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response

/**
 * A helper class to manage OkHttp instances backed by distinct cache directories.
 */
object OkHttpClientHelper {
    private val clients = mutableMapOf<String, OkHttpClient>()

    /**
     * Guess the media type based on the file component of a string.
     */
    fun guessMediaType(name: String): MediaType? {
        val contentType = URLConnection.guessContentTypeFromName(name) ?: "application/octet-stream"
        return MediaType.parse(contentType)
    }

    /**
     * Create a request body for the specified file.
     */
    fun createRequestBody(source: File): RequestBody = RequestBody.create(guessMediaType(source.name), source)

    /**
     * Execute a request using the client for the specified cache directory.
     */
    fun execute(cachePath: String, request: Request): Response {
        val client = clients.getOrPut(cachePath) {
            val cacheDirectory = File(getUserOrtDirectory(), cachePath)
            val maxCacheSizeInBytes = 1024L * 1024L * 1024L
            val cache = Cache(cacheDirectory, maxCacheSizeInBytes)
            val specs = listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT)
            OkHttpClient.Builder()
                    .cache(cache)
                    .connectionSpecs(specs)
                    .build()
        }

        return client.newCall(request).execute()
    }
}
