/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

import com.fasterxml.jackson.databind.JsonNode

import com.here.ort.model.Identifier
import com.here.ort.model.ScanResult
import com.here.ort.model.ScanResultContainer
import com.here.ort.model.ScannerDetails
import com.here.ort.utils.log

data class CacheStatistics(
        var numReads: Int = 0,
        var numHits: Int = 0
)

interface ScanResultsCache {

    /**
     * Read all [ScanResult]s for this [id] from the cache.
     *
     * @param id The [Identifier] of the scanned [Package].
     *
     * @return The [ScanResultContainer] for this [id].
     */
    fun read(id: Identifier): ScanResultContainer

    /**
     * Read the [ScanResult]s matching this [id] and [scannerDetails] from the cache. [ScannerDetails.isCompatible] is
     * used to check if the results are compatible with the provided [scannerDetails].
     *
     * @param id The [Identifier] of the scanned [Package].
     * @param scannerDetails Details about the scanner that was used to scan the [Package].
     *
     * @return The [ScanResultContainer] matching this [id] and [scannerDetails].
     */
    fun read(id: Identifier, scannerDetails: ScannerDetails): ScanResultContainer

    /**
     * Add a [ScanResult] to the [ScanResultContainer] for this [id] and write it to the cache.
     *
     * @param id The [Identifier] of the scanned [Package].
     * @param scanResult The [ScanResult].
     *
     * @return If the [ScanResult] could be written to the cache.
     */
    fun add(id: Identifier, scanResult: ScanResult): Boolean

    companion object : ScanResultsCache {
        var cache = object : ScanResultsCache {
            override fun read(id: Identifier) = ScanResultContainer(id, emptyList())
            override fun read(id: Identifier, scannerDetails: ScannerDetails) = ScanResultContainer(id, emptyList())
            override fun add(id: Identifier, scanResult: ScanResult) = false
        }
            private set

        var stats = CacheStatistics()

        fun configure(config: JsonNode?) {
            // Return early if there is no cache configuration.
            val cacheNode = config?.get(Main.TOOL_NAME)?.get("cache") ?: return

            val type = cacheNode["type"]?.asText() ?: throw IllegalArgumentException("Cache type is missing.")

            when (type.toLowerCase()) {
                "artifactory" -> {
                    val apiToken = cacheNode["apiToken"]?.asText()
                            ?: throw IllegalArgumentException("API token for Artifactory cache is missing.")

                    val url = cacheNode["url"]?.asText()
                            ?: throw IllegalArgumentException("URL for Artifactory cache is missing.")

                    cache = ArtifactoryCache(url, apiToken)
                    log.info { "Using Artifactory cache '$url'." }
                }
                else -> throw IllegalArgumentException("Cache type '$type' unknown.")
            }
        }

        override fun read(id: Identifier) = cache.read(id).also {
            ++stats.numReads
            if (it.results.isNotEmpty()) {
                ++stats.numHits
            }
        }

        override fun read(id: Identifier, scannerDetails: ScannerDetails) =
                cache.read(id, scannerDetails).also {
                    ++stats.numReads
                    if (it.results.isNotEmpty()) {
                        ++stats.numHits
                    }
                }

        override fun add(id: Identifier, scanResult: ScanResult) = cache.add(id, scanResult)
    }
}
