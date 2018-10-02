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

import com.here.ort.model.CacheStatistics
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.ScanResult
import com.here.ort.model.ScanResultContainer
import com.here.ort.model.ScannerDetails
import com.here.ort.model.config.ArtifactoryCacheConfiguration
import com.here.ort.model.config.CloudStorageCacheConfiguration
import com.here.ort.utils.log
import com.here.ort.model.config.CacheConfiguration

class ScanResultsCache {
    companion object {
        var stats = CacheStatistics()

        var scanStorage : ScanStorage? = null

        fun configure(config: CacheConfiguration) {
            config.validate()

            if (config is ArtifactoryCacheConfiguration) {
                scanStorage = ArtifactoryCache(config)
                log.info { "Using Artifactory cache '${config.url}'." }
            } else if (config is CloudStorageCacheConfiguration) {
                scanStorage = CloudStorageCache(config)
                log.info { "Using Cloud Storage cache." }
            }
        }

        /**
         * Read all [ScanResult]s for this [id] from the cache.
         *
         * @param id The [Identifier] of the scanned [Package].
         *
         * @return The [ScanResultContainer] for this [id].
         */
        fun read(id: Identifier): ScanResultContainer {
            if (scanStorage == null) {
                return ScanResultContainer(id, emptyList())
            }

            return scanStorage!!.read(id).also {
                ++stats.numReads
                if (it.results.isNotEmpty()) {
                    ++stats.numHits
                }
            }
        }

        /**
         * Read the [ScanResult]s matching the [id][Package.id] of [pkg] and the [scannerDetails] from the cache.
         * [ScannerDetails.isCompatible] is used to check if the results are compatible with the
         * provided [scannerDetails]. Also [Package.sourceArtifact], [Package.vcs], and [Package.vcsProcessed] are used
         * to check if the scan result matches the expected source code location. This is important to find the correct
         * results when different revisions of a package using the same version name are used (e.g. multiple scans
         * of 1.0-SNAPSHOT during development).
         *
         * @param pkg The [Package] to look up results for.
         * @param scannerDetails Details about the scanner that was used to scan the [Package].
         *
         * @return The [ScanResultContainer] matching the [id][Package.id] of [pkg] and the [scannerDetails].
         */
        fun read(pkg: Package, scannerDetails: ScannerDetails) : ScanResultContainer {
            if (scanStorage == null) {
                ScanResultContainer(pkg.id, emptyList())
            }

            val scanResults = read(pkg.id).results.toMutableList()

            if (scanResults.isEmpty()) return ScanResultContainer(pkg.id, scanResults)

            scanResults.retainAll { it.provenance.matches(pkg) }
            if (scanResults.isEmpty()) {
                log.info {
                    "No cached scan results found for $pkg. The following entries with non-matching provenance have " +
                            "been ignored: ${scanResults.map { it.provenance }}"
                }
                return ScanResultContainer(pkg.id, scanResults)
            }

            scanResults.retainAll { scannerDetails.isCompatible(it.scanner) }
            if (scanResults.isEmpty()) {
                log.info {
                    "No cached scan results found for $scannerDetails. The following entries with incompatible " +
                            "scanners have been ignored: ${scanResults.map { it.scanner }}"
                }
                return ScanResultContainer(pkg.id, scanResults)
            }

            log.info {
                "Found ${scanResults.size} cached scan result(s) for $pkg that are compatible with $scannerDetails."
            }

            return ScanResultContainer(pkg.id, scanResults)
        }

        /**
         * Add a [ScanResult] to the [ScanResultContainer] for this [id] and write it to the cache.
         *
         * @param id The [Identifier] of the scanned [Package].
         * @param scanResult The [ScanResult]. The [ScanResult.rawResult] must not be null.
         *
         * @return If the [ScanResult] could be written to the cache.
         */
        fun add(id: Identifier, scanResult: ScanResult): Boolean {
            if (scanStorage == null) {
                return false
            }

            // Do not cache empty scan results. It is likely that something went wrong when they were created,
            // and if not, it is cheap to re-create them.
            if (scanResult.summary.fileCount == 0) {
                log.info { "Not caching scan result for '$id' because no files were scanned." }

                return false
            }

            // Do not cache scan results without raw result. The raw result can be set to null for other usages,
            // but in the cache it must never be null.
            if (scanResult.rawResult == null) {
                log.info { "Not caching scan result for '$id' because the raw result is null." }

                return false
            }

            // Do not cache scan results without provenance information, because they cannot be assigned to the revision
            // of the package source code later.
            if (scanResult.provenance.sourceArtifact == null && scanResult.provenance.vcsInfo == null) {
                log.info { "Not caching scan result for '$id' because no provenance information is available." }

                return false
            }

            return scanStorage!!.add(id, scanResult)
        }
    }
}
