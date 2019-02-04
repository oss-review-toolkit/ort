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

package com.here.ort.scanner

import ch.frankel.slf4k.*

import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.ScanResult
import com.here.ort.model.ScanResultContainer
import com.here.ort.model.ScannerDetails
import com.here.ort.model.jsonMapper
import com.here.ort.model.readValue
import com.here.ort.model.yamlMapper
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
        private val apiToken: String,

        private val rewriteArtifactoryCache: Boolean
    ) : ScanResultsStorage {
    override fun read(id: Identifier): ScanResultContainer {
        val storagePath = storagePath(id)

        log.info { "Trying to read scan results for '$id' from Artifactory storage: $storagePath" }

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

    override fun read(pkg: Package, scannerDetails: ScannerDetails): ScanResultContainer {
        val scanResults = read(pkg.id).results.toMutableList()

        if (scanResults.isEmpty()) return ScanResultContainer(pkg.id, scanResults)

        // Only keep scan results whose provenance information matches the package information.
        scanResults.retainAll { it.provenance.matches(pkg) }
        if (scanResults.isEmpty()) {
            log.info {
                "No stored scan results found for $pkg. The following entries with non-matching provenance have " +
                        "been ignored: ${scanResults.map { it.provenance }}"
            }
            return ScanResultContainer(pkg.id, scanResults)
        }

        // Only keep scan results from compatible scanners.
        scanResults.retainAll { scannerDetails.isCompatible(it.scanner) }
        if (scanResults.isEmpty()) {
            log.info {
                "No stored scan results found for $scannerDetails. The following entries with incompatible scanners " +
                        "have been ignored: ${scanResults.map { it.scanner }}"
            }
            return ScanResultContainer(pkg.id, scanResults)
        }

        log.info {
            "Found ${scanResults.size} stored scan result(s) for $pkg that are compatible with $scannerDetails."
        }

        return ScanResultContainer(pkg.id, scanResults)
    }

    override fun add(id: Identifier, scanResult: ScanResult): Boolean {
        // Do not store empty scan results. It is likely that something went wrong when they were created, and if not,
        // it is cheap to re-create them.
        if (scanResult.summary.fileCount == 0) {
            log.info { "Not storing scan result for '$id' because no files were scanned." }

            return false
        }

        // Do not store scan results without raw result. The raw result can be set to null for other usages, but in the
        // storage it must never be null.
        if (scanResult.rawResult == null) {
            log.info { "Not storing scan result for '$id' because the raw result is null." }

            return false
        }

        // Do not store scan results without provenance information, because they cannot be assigned to the revision of
        // the package source code later.
        if (scanResult.provenance.sourceArtifact == null && scanResult.provenance.vcsInfo == null) {
            log.info { "Not storing scan result for '$id' because no provenance information is available." }

            return false
        }
        val scanResults = if (rewriteArtifactoryCache) {
            ScanResultContainer(id, ScanResultContainer(id, emptyList()).results + scanResult)
        } else {
            ScanResultContainer(id, read(id).results + scanResult)
        }

        val tempFile = createTempFile("ort", "scan-results.yml")
        yamlMapper.writeValue(tempFile, scanResults)

        val storagePath = storagePath(id)

        log.info { "Writing scan results for '$id' to Artifactory storage: $storagePath" }

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
