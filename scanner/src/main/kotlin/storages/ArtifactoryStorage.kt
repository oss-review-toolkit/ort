/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

package com.here.ort.scanner.storages

import ch.frankel.slf4k.*

import com.here.ort.model.Identifier
import com.here.ort.model.ScanResult
import com.here.ort.model.ScanResultContainer
import com.here.ort.model.jsonMapper
import com.here.ort.model.readValue
import com.here.ort.model.yamlMapper
import com.here.ort.scanner.HTTP_CACHE_PATH
import com.here.ort.scanner.ScanResultsStorage
import com.here.ort.utils.OkHttpClientHelper
import com.here.ort.utils.log
import com.here.ort.utils.showStackTrace

import java.io.IOException
import java.net.HttpURLConnection
import java.util.SortedSet
import java.util.concurrent.TimeUnit

import okhttp3.CacheControl
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody

import okio.Okio

class ArtifactoryStorage(
    /**
     * The URL of the Artifactory server, e.g. "https://example.com/artifactory".
     */
    private val url: String,

    /**
     * The name of the Artifactory repository to use for storing scan results.
     */
    private val repository: String,

    /**
     * An Artifactory API token with read/write access to [repository].
     */
    private val apiToken: String
) : ScanResultsStorage {
    override fun read(id: Identifier): ScanResultContainer {
        val storagePath = storagePath(id)

        log.info { "Trying to read scan results for '${id.toCoordinates()}' from Artifactory storage: $storagePath" }

        val request = Request.Builder()
            .header("X-JFrog-Art-Api", apiToken)
            .cacheControl(CacheControl.Builder().maxAge(0, TimeUnit.SECONDS).build())
            .get()
            .url("$url/$repository/$storagePath")
            .build()

        val tempFile = createTempFile("ort", "scan-results.yml")

        try {
            OkHttpClientHelper.execute(HTTP_CACHE_PATH, request).use { response ->
                if (response.code() == HttpURLConnection.HTTP_OK) {
                    response.body()?.let { body ->
                        Okio.buffer(Okio.sink(tempFile)).use { it.writeAll(body.source()) }
                    }

                    if (response.cacheResponse() != null) {
                        log.info { "Retrieved $storagePath from local storage." }
                    } else {
                        log.info { "Downloaded $storagePath from Artifactory storage." }
                    }

                    return tempFile.readValue()
                } else {
                    log.info {
                        "Could not get $storagePath from Artifactory storage: ${response.code()} - " +
                                response.message()
                    }
                }
            }
        } catch (e: IOException) {
            e.showStackTrace()

            log.warn { "Could not get $storagePath from Artifactory storage: ${e.message}" }
        }

        return ScanResultContainer(id, emptyList())
    }

    override fun add(id: Identifier, scanResult: ScanResult): Boolean {
        val scanResults = ScanResultContainer(id, read(id).results + scanResult)

        val tempFile = createTempFile("ort", "scan-results.yml")
        yamlMapper.writeValue(tempFile, scanResults)

        val storagePath = storagePath(id)

        log.info { "Writing scan results for '${id.toCoordinates()}' to Artifactory storage: $storagePath" }

        val request = Request.Builder()
            .header("X-JFrog-Art-Api", apiToken)
            .put(OkHttpClientHelper.createRequestBody(tempFile))
            .url("$url/$repository/$storagePath")
            .build()

        try {
            return OkHttpClientHelper.execute(HTTP_CACHE_PATH, request).use { response ->
                (response.code() == HttpURLConnection.HTTP_CREATED).also {
                    log.info {
                        if (it) {
                            "Uploaded $storagePath to Artifactory storage."
                        } else {
                            "Could not upload $storagePath to Artifactory storage: ${response.code()} - " +
                                    response.message()
                        }
                    }
                }
            }
        } catch (e: IOException) {
            e.showStackTrace()

            log.warn { "Could not upload $storagePath to Artifactory storage: ${e.message}" }

            return false
        }
    }

    override fun listPackages(): SortedSet<Identifier> {
        val aqlQuery = """
            items.find({
                "type": "file",
                "repo": "$repository",
                "path": {"${'$'}match": "scan-results/*"},
                "name": "scan-results.yml"
            })
        """.trimIndent()

        val body = RequestBody.create(MediaType.parse("text/plain"), aqlQuery)

        val request = Request.Builder()
            .header("X-JFrog-Art-Api", apiToken)
            .post(body)
            .url("$url/api/search/aql")
            .build()

        OkHttpClientHelper.execute(HTTP_CACHE_PATH, request).use { response ->
            if (response.code() == HttpURLConnection.HTTP_OK) {
                response.body()?.let { responseBody ->
                    val json = jsonMapper.readTree(responseBody.charStream())

                    return json["results"].map { node ->
                        val elements = node["path"].textValue().split("/")
                        Identifier("${elements[1]}:${elements[2]}:${elements[3]}:${elements[4]}")
                    }.toSortedSet()
                } ?: throw IOException("Could not fetch package list: Response body is null.")
            } else {
                throw IOException("Could not fetch package list: ${response.code()} - ${response.message()}")
            }
        }
    }

    private fun storagePath(id: Identifier) = "scan-results/${id.toPath()}/scan-results.yml"
}
