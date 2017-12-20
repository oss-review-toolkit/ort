/*
 * Copyright (c) Microsoft Corporation. All rights reserved
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

package com.here.ort.scanner

import ch.frankel.slf4k.info

import com.here.ort.model.Package
import com.here.ort.utils.OkHttpClientHelper
import com.here.ort.utils.log

import java.io.File
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

import okhttp3.CacheControl
import okhttp3.Request

class ClearlyDefinedCache(
        private val url: String,
        private val apiToken: String
) : ScanResultsCache {

    override fun read(pkg: Package, target: File): Boolean {
        val cachePath = cachePath(pkg, target)

        log.info { "Trying to read scan results from ClearlyDefined service: $cachePath" }

        val request = Request.Builder()
                .header("Authorization", apiToken)
                .cacheControl(CacheControl.Builder().maxAge(0, TimeUnit.SECONDS).build())
                .get()
                .url("$url/$cachePath")
                .build()

        return OkHttpClientHelper.execute("scanner", request).use { response ->
            (response.code() == HttpURLConnection.HTTP_OK).also {
                val message = if (it) {
                    response.body()?.let { target.writeBytes(it.bytes()) }
                    if (response.cacheResponse() != null) {
                        "Retrieved $cachePath from local cache."
                    } else {
                        "Downloaded $cachePath from ClearlyDefined service."
                    }
                } else {
                    "Could not get $cachePath from ClearlyDefined service: ${response.code()} - " +
                            response.message()
                }

                log.info { message }
            }
        }
    }

    override fun write(pkg: Package, source: File): Boolean {
        val cachePath = cachePath(pkg, source)

        log.info { "Writing scan results to ClearlyDefined service: $cachePath" }

        val request = Request.Builder()
                .header("Authorization", apiToken)
                .put(OkHttpClientHelper.createRequestBody(source))
                .url("$url/$cachePath")
                .build()

        return OkHttpClientHelper.execute("scanner", request).use { response ->
            (response.code() == HttpURLConnection.HTTP_CREATED).also {
                log.info {
                    if (it) {
                        "Uploaded $cachePath to ClearlyDefined service."
                    } else {
                        "Could not upload $cachePath to ClearlyDefined service: ${response.code()} - " +
                                response.message()
                    }
                }
            }
        }
    }

    private fun cachePath(pkg: Package, resultsFile: File): String {
        val provider = getProvider(pkg.packageManager)
        val qualifiedName = (if (!pkg.namespace.isEmpty()) pkg.namespace + "/" else "") + pkg.name
        return "harvest/" +
                "${pkg.packageManager.valueOrUnderscore()}/" +
                "${provider.valueOrUnderscore()}/" +
                "${qualifiedName}/" +
                "${pkg.version.valueOrUnderscore()}/" +
                resultsFile.name
    }

    private fun getProvider(manager: String): String? {
        val map = hashMapOf(
            "npm" to "npmjs.org",
            "git" to "github.com",
            "maven" to "central.maven.org"
        )
        return map.get(manager)
    }

    private fun String?.valueOrUnderscore() = if (this == null || this.isEmpty()) "_" else this
}
