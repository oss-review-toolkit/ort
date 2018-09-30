/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

import ch.frankel.slf4k.*

import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.ScanResult
import com.here.ort.model.ScanResultContainer
import com.here.ort.model.ScannerDetails
import com.here.ort.model.config.ArtifactoryCacheConfiguration
import com.here.ort.model.readValue
import com.here.ort.model.yamlMapper
import com.here.ort.utils.OkHttpClientHelper
import com.here.ort.utils.log
import com.here.ort.utils.showStackTrace

import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

import okhttp3.CacheControl
import okhttp3.Request

import okio.Okio

class ArtifactoryCache(
        private val config: ArtifactoryCacheConfiguration
) : ScanResultsCache {
    override fun read(id: Identifier): ScanResultContainer {
        val cachePath = cachePath(id)

        log.info { "Trying to read scan results for '$id' from Artifactory cache: $cachePath" }

        val request = Request.Builder()
                .header("X-JFrog-Art-Api", config.apiToken)
                .cacheControl(CacheControl.Builder().maxAge(0, TimeUnit.SECONDS).build())
                .get()
                .url("$config.url/$cachePath")
                .build()

        val tempFile = createTempFile("scan-results-", ".yml")

        try {
            OkHttpClientHelper.execute(HTTP_CACHE_PATH, request).use { response ->
                if (response.code() == HttpURLConnection.HTTP_OK) {
                    response.body()?.let { body ->
                        Okio.buffer(Okio.sink(tempFile)).use { it.writeAll(body.source()) }
                    }

                    if (response.cacheResponse() != null) {
                        log.info { "Retrieved $cachePath from local cache." }
                    } else {
                        log.info { "Downloaded $cachePath from Artifactory cache." }
                    }

                    return tempFile.readValue(ScanResultContainer::class.java)
                } else {
                    log.info {
                        "Could not get $cachePath from Artifactory cache: ${response.code()} - " +
                                response.message()
                    }
                }
            }
        } catch (e: IOException) {
            e.showStackTrace()

            log.warn { "Could not get $cachePath from Artifactory cache: ${e.message}" }
        }

        return ScanResultContainer(id, emptyList())
    }

    override fun add(id: Identifier, scanResult: ScanResult): Boolean {
        val scanResults = ScanResultContainer(id, read(id).results + scanResult)

        val tempFile = createTempFile("scan-results-")
        yamlMapper.writeValue(tempFile, scanResults)

        val cachePath = cachePath(id)

        log.info { "Writing scan results for '$id' to Artifactory cache: $cachePath" }

        val request = Request.Builder()
                .header("X-JFrog-Art-Api", config.apiToken)
                .put(OkHttpClientHelper.createRequestBody(tempFile))
                .url("$config.url/$cachePath")
                .build()

        try {
            return OkHttpClientHelper.execute(HTTP_CACHE_PATH, request).use { response ->
                (response.code() == HttpURLConnection.HTTP_CREATED).also {
                    log.info {
                        if (it) {
                            "Uploaded $cachePath to Artifactory cache."
                        } else {
                            "Could not upload $cachePath to Artifactory cache: ${response.code()} - " +
                                    response.message()
                        }
                    }
                }
            }
        } catch (e: IOException) {
            e.showStackTrace()

            log.warn { "Could not upload $cachePath to Artifactory cache: ${e.message}" }

            return false
        }
    }

    override fun read(pkg: Package, scannerDetails: ScannerDetails) = ScanResultContainer(pkg.id, emptyList())

    private fun cachePath(id: Identifier) = "scan-results/${id.toPath()}/scan-results.yml"
}
